package com.junaldadlawan.event_ticketing_api.paymentmethod.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodRequest;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.entity.PaymentMethod;
import com.junaldadlawan.event_ticketing_api.paymentmethod.gateway.StripeGateway;
import com.junaldadlawan.event_ticketing_api.paymentmethod.repository.PaymentMethodRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceImplTest {

    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StripeCustomerService stripeCustomerService;
    @Mock
    private StripeGateway stripeGateway;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private PaymentMethodServiceImpl service;
    private UUID userId;
    private final int nextYear = YearMonth.now(ZoneOffset.UTC).getYear() + 2;

    @BeforeEach
    void setUp() {
        service = new PaymentMethodServiceImpl(paymentMethodRepository, userRepository, stripeCustomerService, stripeGateway, accessGuard);
        userId = UUID.randomUUID();
        lenient().when(accessGuard.currentUserId()).thenReturn(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).firstName("Jamie").lastName("Cruz").email("jamie@test.local").build()));
        lenient().when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(inv -> {
            PaymentMethod card = inv.getArgument(0);
            if (card.getId() == null) {
                card.setId(UUID.randomUUID());
            }
            return card;
        });
    }

    private PaymentMethodRequest mockRequest(String token, Boolean primary) {
        return new PaymentMethodRequest(token, primary, "Jamie Cruz", null, "4242", 12, nextYear);
    }

    private PaymentMethod card(boolean primary) {
        return PaymentMethod.builder().id(UUID.randomUUID()).userId(userId).holderName("Jamie Cruz").brand("visa").last4("4242")
                .expMonth(12).expYear(2099).primary(primary).build();
    }

    // ---- mock gateway ----

    @Test
    void add_mock_theFirstCardIsAlwaysPrimary_andTakesTheBrandFromTheToken() {
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0L);

        PaymentMethodResponse response = service.add(mockRequest("tok_visa", false));

        assertThat(response.primary()).isTrue();
        assertThat(response.brand()).isEqualTo("visa");
        assertThat(response.last4()).isEqualTo("4242");
        verify(stripeGateway, never()).attachAndDescribe(any(), any());
        verify(stripeCustomerService, never()).getOrCreate(any());
    }

    @Test
    void add_mock_aLaterCardIsNotPrimaryUnlessAsked_andTheOldPrimaryIsUnsetFirst() {
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(1L);
        assertThat(service.add(mockRequest("tok_visa", null)).primary()).isFalse();
        verify(paymentMethodRepository, never()).saveAndFlush(any());

        PaymentMethod old = card(true);
        when(paymentMethodRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId)).thenReturn(Optional.of(old));
        when(paymentMethodRepository.saveAndFlush(old)).thenReturn(old);

        assertThat(service.add(mockRequest("tok_mastercard", true)).primary()).isTrue();

        assertThat(old.isPrimary()).isFalse();
        InOrder order = inOrder(paymentMethodRepository);
        order.verify(paymentMethodRepository).saveAndFlush(old);
    }

    @Test
    void add_mock_aFailingTokenIsRefused() {
        assertThatThrownBy(() -> service.add(mockRequest("tok_fail_declined", false)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("declined");
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void add_mock_anExpiredCardIsRefused_butOneExpiringThisMonthIsFine() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        YearMonth lastMonth = now.minusMonths(1);
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0L);

        assertThatThrownBy(() -> service.add(new PaymentMethodRequest("tok_visa", null, null, null, "4242",
                lastMonth.getMonthValue(), lastMonth.getYear()))).isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        assertThat(service.add(new PaymentMethodRequest("tok_visa", null, null, null, "4242", now.getMonthValue(), now.getYear())).expMonth())
                .isEqualTo(now.getMonthValue());
    }

    @Test
    void add_mock_needsTheCardDetails_andAnMockStyleToken() {
        assertThatThrownBy(() -> service.add(new PaymentMethodRequest("tok_visa", null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.add(mockRequest("pm_123456789", null))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void add_withoutAHolderName_usesTheAccountName() {
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0L);

        assertThat(service.add(new PaymentMethodRequest("tok_visa", null, null, null, "4242", 12, nextYear)).holderName()).isEqualTo("Jamie Cruz");
    }

    // ---- stripe gateway ----

    @Test
    void add_stripe_attachesTheCard_andReadsItsDetailsFromStripe_ignoringTheClient() {
        when(stripeGateway.live()).thenReturn(true);
        when(stripeCustomerService.getOrCreate(userId)).thenReturn("cus_1");
        when(stripeGateway.attachAndDescribe("cus_1", "pm_abcdef123456"))
                .thenReturn(new StripeGateway.CardDetails("mastercard", "4444", 10, nextYear));
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(0L);

        // the client lies about the card: none of it is used
        PaymentMethodResponse response = service.add(new PaymentMethodRequest("pm_abcdef123456", null, null, "visa", "1111", 1, 2099));

        assertThat(response.brand()).isEqualTo("mastercard");
        assertThat(response.last4()).isEqualTo("4444");
        assertThat(response.expYear()).isEqualTo(nextYear);
    }

    @Test
    void add_stripe_aMockStyleTokenIsRefused_andASavedStripeCardIsNotSavedTwice() {
        when(stripeGateway.live()).thenReturn(true);

        assertThatThrownBy(() -> service.add(mockRequest("tok_visa", null))).isInstanceOf(BadRequestException.class);

        when(paymentMethodRepository.existsByUserIdAndStripePaymentMethodIdAndDeletedAtIsNull(userId, "pm_abcdef123456")).thenReturn(true);
        assertThatThrownBy(() -> service.add(mockRequest("pm_abcdef123456", null))).isInstanceOf(ConflictException.class);
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void add_stripe_anExpiredCardFromStripeIsRefused() {
        when(stripeGateway.live()).thenReturn(true);
        when(stripeCustomerService.getOrCreate(userId)).thenReturn("cus_1");
        when(stripeGateway.attachAndDescribe("cus_1", "pm_abcdef123456")).thenReturn(new StripeGateway.CardDetails("visa", "4242", 1, 2020));

        assertThatThrownBy(() -> service.add(mockRequest("pm_abcdef123456", null))).isInstanceOf(BadRequestException.class);
        verify(paymentMethodRepository, never()).save(any());
    }

    @Test
    void createSetupIntent_mockGivesADummySecret_stripeUsesTheCallersCustomer() {
        when(stripeGateway.live()).thenReturn(false);
        when(stripeGateway.createSetupIntent(null)).thenReturn("seti_mock_secret");
        assertThat(service.createSetupIntent().clientSecret()).isEqualTo("seti_mock_secret");
        verify(stripeCustomerService, never()).getOrCreate(any());

        when(stripeGateway.live()).thenReturn(true);
        when(stripeCustomerService.getOrCreate(userId)).thenReturn("cus_1");
        when(stripeGateway.createSetupIntent("cus_1")).thenReturn("seti_real_secret");
        assertThat(service.createSetupIntent().clientSecret()).isEqualTo("seti_real_secret");
    }

    // ---- list / primary / remove ----

    @Test
    void list_returnsTheCallersCardsOnly() {
        when(paymentMethodRepository.findByUserIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(userId))
                .thenReturn(List.of(card(true), card(false)));

        assertThat(service.list()).hasSize(2);
        assertThat(service.list().get(0).primary()).isTrue();
    }

    @Test
    void setPrimary_swapsThePrimary_oldOneFirst() {
        PaymentMethod old = card(true);
        PaymentMethod next = card(false);
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(next.getId(), userId)).thenReturn(Optional.of(next));
        when(paymentMethodRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId)).thenReturn(Optional.of(old));
        when(paymentMethodRepository.saveAndFlush(old)).thenReturn(old);

        assertThat(service.setPrimary(next.getId()).primary()).isTrue();

        assertThat(old.isPrimary()).isFalse();
        InOrder order = inOrder(paymentMethodRepository);
        order.verify(paymentMethodRepository).saveAndFlush(old);
        order.verify(paymentMethodRepository).save(next);
    }

    @Test
    void anotherUsersCard_isNotFound_forPrimaryRemoveAndCheckout() {
        UUID foreign = UUID.randomUUID();
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(foreign, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPrimary(foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.remove(foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resolveForCheckout(foreign)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void remove_thePrimaryWhileOthersExist_isRefused() {
        PaymentMethod primary = card(true);
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(primary.getId(), userId)).thenReturn(Optional.of(primary));
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(2L);

        assertThatThrownBy(() -> service.remove(primary.getId())).isInstanceOf(ConflictException.class);
        assertThat(primary.getDeletedAt()).isNull();
        verify(stripeGateway, never()).detach(any());
    }

    @Test
    void remove_theOnlyCard_softDeletes_andDetachesFromStripe() {
        PaymentMethod only = card(true);
        only.setStripePaymentMethodId("pm_abcdef123456");
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(only.getId(), userId)).thenReturn(Optional.of(only));
        when(paymentMethodRepository.countByUserIdAndDeletedAtIsNull(userId)).thenReturn(1L);
        when(stripeGateway.live()).thenReturn(true);

        service.remove(only.getId());

        assertThat(only.getDeletedAt()).isNotNull();
        verify(stripeGateway).detach("pm_abcdef123456");
    }

    @Test
    void remove_aMockCard_doesNotCallStripe() {
        PaymentMethod other = card(false);
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(other.getId(), userId)).thenReturn(Optional.of(other));
        when(stripeGateway.live()).thenReturn(false);

        service.remove(other.getId());

        assertThat(other.getDeletedAt()).isNotNull();
        verify(stripeGateway, never()).detach(any());
    }

    // ---- checkout ----

    @Test
    void resolveForCheckout_stripeGivesThePmIdAndTheCustomer_mockGivesATokenFromTheBrand() {
        PaymentMethod stripeCard = card(true);
        stripeCard.setStripePaymentMethodId("pm_abcdef123456");
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(stripeCard.getId(), userId)).thenReturn(Optional.of(stripeCard));
        when(stripeGateway.live()).thenReturn(true);
        when(stripeCustomerService.getOrCreate(userId)).thenReturn("cus_1");

        assertThat(service.resolveForCheckout(stripeCard.getId())).isEqualTo(new PaymentMethodService.SavedCardCharge("pm_abcdef123456", "cus_1"));

        PaymentMethod mockCard = card(false);
        when(paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(mockCard.getId(), userId)).thenReturn(Optional.of(mockCard));
        assertThat(service.resolveForCheckout(mockCard.getId())).isEqualTo(new PaymentMethodService.SavedCardCharge("tok_visa", null));
    }
}
