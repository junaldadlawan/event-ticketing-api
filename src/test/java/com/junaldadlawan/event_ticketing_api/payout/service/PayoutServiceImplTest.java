package com.junaldadlawan.event_ticketing_api.payout.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutGenerateRequest;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;
import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutAccountRepository;
import com.junaldadlawan.event_ticketing_api.payout.security.AccountNumberCipher;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutCardRepository;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import com.junaldadlawan.event_ticketing_api.payout.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link PayoutServiceImpl} (no Spring context) —
 * mirrors {@code ResalePolicyServiceImplTest}'s owner/organizer/admin
 * authorization style. There is no payout-generation job anywhere in this
 * codebase (per the dispatch), so every {@code Payout} row here is seeded
 * directly rather than produced by any real flow.
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PayoutCardRepository payoutCardRepository;

    @Mock
    private PayoutAccountRepository payoutAccountRepository;

    @Mock
    private PayoutTransferClient transferClient;

    @Mock
    private AccountNumberCipher cipher;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Mock
    private com.junaldadlawan.event_ticketing_api.event.repository.EventRepository eventRepository;

    @Mock
    private com.junaldadlawan.event_ticketing_api.payout.repository.PayoutEligibilityRepository eligibilityRepository;

    private PayoutServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, organizationRepository, accessGuard, orderRepository, refundRepository, payoutCardRepository,
                payoutAccountRepository, transferClient, cipher, transactionManager,
                eventRepository, eligibilityRepository);
        orgId = UUID.randomUUID();
    }

    private Payout payout() {
        return Payout.builder()
                .id(UUID.randomUUID())
                .organizationId(orgId)
                .gross(Money.builder().amount(10_000L).currency("USD").build())
                .fees(Money.builder().amount(500L).currency("USD").build())
                .net(Money.builder().amount(9_500L).currency("USD").build())
                .periodStart(LocalDate.of(2026, 8, 1))
                .periodEnd(LocalDate.of(2026, 8, 31))
                .status(PayoutStatus.PAID)
                .build();
    }

    @Test
    void list_unknownOrganization_throwsResourceNotFound() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(orgId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard, payoutRepository);
    }

    @Test
    void list_owner_returnsPagedPayouts() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID ownerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payout()), pageable, 1));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).organizationId()).isEqualTo(orgId);
        assertThat(result.getContent().get(0).net().amount()).isEqualTo(9_500L);
        assertThat(result.getContent().get(0).status()).isEqualTo(PayoutStatus.PAID);
    }

    @Test
    void list_organizer_returnsPagedPayouts() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID organizerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void list_admin_bypassesOrgRoleCheck() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(accessGuard.isAdmin()).thenReturn(true);
        Pageable pageable = PageRequest.of(0, 20);
        when(payoutRepository.findByOrganizationId(eq(orgId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(payout()), pageable, 1));

        Page<PayoutResponse> result = service.list(orgId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(accessGuard, never()).currentUserId();
        verify(accessGuard, never()).hasRole(any(), any(), any());
    }

    @Test
    void list_nonMemberStranger_throwsForbidden() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        UUID strangerId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(orgId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(payoutRepository);
    }

    @Test
    void list_crossOrgOwner_throwsForbidden() {
        UUID otherOrgId = UUID.randomUUID();
        when(organizationRepository.existsById(otherOrgId)).thenReturn(true);
        UUID ownerOfDifferentOrgId = UUID.randomUUID();
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerOfDifferentOrgId);
        when(accessGuard.hasRole(ownerOfDifferentOrgId, otherOrgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(ownerOfDifferentOrgId, otherOrgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.list(otherOrgId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- generate(): deducts the platform fees ----

    private Order order(long total, long fee, OrderStatus status) {
        return Order.builder().id(UUID.randomUUID()).buyerId(UUID.randomUUID()).payeeType(PayeeType.ORGANIZATION)
                .payeeId(orgId).status(status).total(Money.builder().amount(total).currency("USD").build())
                .platformFeeAmount(fee).createdBy("x").build();
    }

    private PayoutGenerateRequest period() {
        return new PayoutGenerateRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    private void adminAndOrg() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
    }

    private PayoutCard withPrimaryCard() {
        PayoutCard card = PayoutCard.builder().id(UUID.randomUUID()).organizationId(orgId).holderName("Demo Events Co.")
                .brand("Visa").last4("4242").expMonth(12).expYear(2099).paymentToken("tok_test_12345678").primary(true).build();
        when(payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(card));
        return card;
    }

    @Test
    void generate_sumsGrossFeesAndNet_andMarksTheOrdersAsSettled() {
        adminAndOrg();
        withPrimaryCard();
        Order first = order(1100, 100, OrderStatus.PAID);
        Order second = order(2200, 200, OrderStatus.PAID);
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(first, second));
        when(refundRepository.sumCompletedAmountByOrderId(any())).thenReturn(0L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> {
            Payout p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.gross().amount()).isEqualTo(3300L);
        assertThat(response.fees().amount()).isEqualTo(300L);
        assertThat(response.net().amount()).isEqualTo(3000L);
        assertThat(response.gross().currency()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(first.getPayoutId()).isEqualTo(response.id());
        assertThat(second.getPayoutId()).isEqualTo(response.id());
        verify(orderRepository).saveAll(List.of(first, second));
        verify(accessGuard).requireAdmin();
    }

    @Test
    void generate_recordsThePrimaryCardAsTheDestination() {
        adminAndOrg();
        PayoutCard card = withPrimaryCard();
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order(1000, 0, OrderStatus.PAID)));
        when(refundRepository.sumCompletedAmountByOrderId(any())).thenReturn(0L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.payoutCardId()).isEqualTo(card.getId());
    }

    @Test
    void generate_withPayMongo_recordsThePayoutAccountAsTheDestination_notACard() {
        adminAndOrg();
        when(transferClient.requiresAccount()).thenReturn(true);
        PayoutAccount account = PayoutAccount.builder().id(UUID.randomUUID()).organizationId(orgId).build();
        when(payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(account));
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order(1000, 0, OrderStatus.PAID)));
        when(refundRepository.sumCompletedAmountByOrderId(any())).thenReturn(0L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.payoutAccountId()).isEqualTo(account.getId());
        assertThat(response.payoutCardId()).isNull();
        verifyNoInteractions(payoutCardRepository);
    }

    @Test
    void generate_withPayMongoAndNoPayoutAccount_throwsConflict_andCreatesNothing() {
        adminAndOrg();
        when(transferClient.requiresAccount()).thenReturn(true);
        when(payoutAccountRepository.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(orgId, period()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("payout account");
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void generate_withoutAPrimaryCard_throwsConflict_andCreatesNothing() {
        adminAndOrg();
        when(payoutCardRepository.findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(orgId, period()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("primary payout card");
        verify(payoutRepository, never()).save(any());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void generate_aPartialRefund_reducesTheGross_andTheFeeIsKeptInFull() {
        adminAndOrg();
        withPrimaryCard();
        Order partlyRefunded = order(1100, 100, OrderStatus.PARTIALLY_REFUNDED);
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(partlyRefunded));
        when(refundRepository.sumCompletedAmountByOrderId(partlyRefunded.getId())).thenReturn(300L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.gross().amount()).isEqualTo(800L);
        assertThat(response.fees().amount()).isEqualTo(100L);
        assertThat(response.net().amount()).isEqualTo(700L);
    }

    @Test
    void generate_aRefundThatEatsIntoTheFee_capsTheFeeAtWhatIsLeft_andNetNeverGoesNegative() {
        adminAndOrg();
        withPrimaryCard();
        Order order = order(1100, 100, OrderStatus.PARTIALLY_REFUNDED);
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order));
        when(refundRepository.sumCompletedAmountByOrderId(order.getId())).thenReturn(1050L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.gross().amount()).isEqualTo(50L);
        assertThat(response.fees().amount()).isEqualTo(50L);
        assertThat(response.net().amount()).isZero();
    }

    @Test
    void generate_ordersWithoutAFee_payOutInFull() {
        adminAndOrg();
        withPrimaryCard();
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order(1000, 0, OrderStatus.PAID)));
        when(refundRepository.sumCompletedAmountByOrderId(any())).thenReturn(0L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        PayoutResponse response = service.generate(orgId, period());

        assertThat(response.fees().amount()).isZero();
        assertThat(response.net().amount()).isEqualTo(1000L);
    }

    @Test
    void generate_queriesTheWholePeriodInUtc_endDayIncluded() {
        adminAndOrg();
        withPrimaryCard();
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(orgId, period())).isInstanceOf(ConflictException.class);

        verify(orderRepository).findDueForPayout(orgId,
                java.time.Instant.parse("2026-08-01T00:00:00Z"), java.time.Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void generate_noOrdersDue_throwsConflict_andCreatesNothing() {
        adminAndOrg();
        withPrimaryCard();
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(orgId, period())).isInstanceOf(ConflictException.class);
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void generate_mixedCurrencies_throwsConflict_andCreatesNothing() {
        adminAndOrg();
        withPrimaryCard();
        Order euro = order(1000, 0, OrderStatus.PAID);
        euro.setTotal(Money.builder().amount(1000).currency("EUR").build());
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order(1000, 0, OrderStatus.PAID), euro));

        assertThatThrownBy(() -> service.generate(orgId, period())).isInstanceOf(ConflictException.class);
        verify(payoutRepository, never()).save(any());
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void generate_invalidPeriods_throwBadRequest() {
        adminAndOrg();

        assertThatThrownBy(() -> service.generate(orgId, new PayoutGenerateRequest(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.generate(orgId,
                new PayoutGenerateRequest(LocalDate.now(java.time.ZoneOffset.UTC), LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1))))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).findDueForPayout(any(), any(), any());
    }

    @Test
    void generate_unknownOrganization_throwsResourceNotFound() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.generate(orgId, period())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generate_aNonAdmin_isForbidden_andNothingIsRead() {
        org.mockito.Mockito.doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.generate(orgId, period())).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(orderRepository, payoutRepository);
    }

    // ---- the payment gateway's fee: who bears it decides what comes off the organizer's share ----

    private Order orderWithGatewayFee(long total, long platformFee, long gatewayFee,
                                      com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer payer) {
        Order order = order(total, platformFee, OrderStatus.PAID);
        order.setGatewayFeeAmount(gatewayFee);
        order.setGatewayFeePayer(payer);
        return order;
    }

    @Test
    void generate_aForeignCardWhenTheOrganizationBearsTheFee_deductsTheForeignFeeThatApplied() {
        Order order = orderWithGatewayFee(1000, 0, 50, com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.ORGANIZATION);
        order.setGatewayFeeAppliedAmount(80L);

        PayoutResponse response = generateFor(order);

        assertThat(response.fees().amount()).isEqualTo(80L);
        assertThat(response.net().amount()).isEqualTo(920L);
    }

    @Test
    void generate_aForeignCardWhenTheCustomerPaidTheFee_doesNotChargeTheOrganizerTheExtra_thePlatformAbsorbsIt() {
        Order order = orderWithGatewayFee(1050, 0, 50, com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.CUSTOMER);
        order.setGatewayFeeAppliedAmount(80L);

        PayoutResponse response = generateFor(order);

        assertThat(response.fees().amount()).isEqualTo(50L);   // only what the customer paid comes off
        assertThat(response.net().amount()).isEqualTo(1000L);
    }

    @Test
    void generate_anOrderWithoutAnAppliedFee_usesTheFeeItWasPlacedWith() {
        Order order = orderWithGatewayFee(1000, 0, 50, com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.ORGANIZATION);

        assertThat(generateFor(order).fees().amount()).isEqualTo(50L);
    }

    private PayoutResponse generateFor(Order order) {
        adminAndOrg();
        withPrimaryCard();
        when(orderRepository.findDueForPayout(eq(orgId), any(), any())).thenReturn(List.of(order));
        when(refundRepository.sumCompletedAmountByOrderId(any())).thenReturn(0L);
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        return service.generate(orgId, period());
    }

    @Test
    void generate_aGatewayFeeTheCustomerPaid_comesOffTheGross_soTheOrganizerKeepsTheTicketPrice() {
        // ticket 1000 + platform fee 100 + gateway fee 55 paid by the buyer = 1155
        PayoutResponse response = generateFor(orderWithGatewayFee(1155, 100, 55,
                com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.CUSTOMER));

        assertThat(response.gross().amount()).isEqualTo(1155L);
        assertThat(response.fees().amount()).isEqualTo(155L);
        assertThat(response.net().amount()).isEqualTo(1000L);
    }

    @Test
    void generate_aGatewayFeeTheOrganizationBears_isDeductedFromItsShare() {
        // the buyer paid ticket 1000 + platform fee 100 = 1100; the gateway fee 55 is the organization's
        PayoutResponse response = generateFor(orderWithGatewayFee(1100, 100, 55,
                com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.ORGANIZATION));

        assertThat(response.gross().amount()).isEqualTo(1100L);
        assertThat(response.fees().amount()).isEqualTo(155L);
        assertThat(response.net().amount()).isEqualTo(945L);
    }

    @Test
    void generate_aGatewayFeeThePlatformAbsorbs_takesNothingOffTheOrganizer() {
        PayoutResponse response = generateFor(orderWithGatewayFee(1100, 100, 55,
                com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeePayer.PLATFORM));

        assertThat(response.fees().amount()).isEqualTo(100L);
        assertThat(response.net().amount()).isEqualTo(1000L);
    }

    @Test
    void generate_anOrderBeforeGatewayFeesExisted_isUnchanged() {
        PayoutResponse response = generateFor(order(1100, 100, OrderStatus.PAID));   // no payer, no gateway fee

        assertThat(response.fees().amount()).isEqualTo(100L);
        assertThat(response.net().amount()).isEqualTo(1000L);
    }
}
