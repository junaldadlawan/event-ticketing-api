package com.junaldadlawan.event_ticketing_api.resalelisting.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingCreateRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResaleListing;
import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResalePurchaseIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResaleListingRepository;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResalePurchaseIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
import com.junaldadlawan.event_ticketing_api.resalepolicy.repository.ResalePolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;
import com.junaldadlawan.event_ticketing_api.tickettransfer.service.TicketTransferService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Mockito unit tests for {@link ResaleListingServiceImpl} (no Spring
 * context) — mirrors {@code CheckoutServiceImplTest}'s style since {@code
 * purchase} runs like a checkout (idempotency claim/replay/conflict,
 * payment-failure short-circuit, Order/Payment side effects). Covers {@code
 * create}'s full authorization/validation chain (BR-TRANSFER-003/004: the
 * price-cap math specifically) and {@code purchase}'s cross-module reuse of
 * {@code TicketTransferService.recordTransfer}.
 */
@ExtendWith(MockitoExtension.class)
class ResaleListingServiceImplTest {

    @Mock
    private ResaleListingRepository resaleListingRepository;
    @Mock
    private ResalePolicyRepository resalePolicyRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayClient paymentGatewayClient;
    @Mock
    private ResalePurchaseIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private ResalePurchaseIdempotencyKeyManager idempotencyKeyManager;
    @Mock
    private TicketTransferService ticketTransferService;
    @Mock
    private OrganizationAccessGuard accessGuard;

    private ResaleListingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResaleListingServiceImpl(
                resaleListingRepository, resalePolicyRepository, ticketRepository, ticketTypeRepository,
                eventRepository, orderRepository, paymentRepository, paymentGatewayClient,
                idempotencyKeyRepository, idempotencyKeyManager, ticketTransferService, accessGuard);
    }

    // ---- fixtures ----

    private Ticket ticket(UUID id, UUID ownerId, UUID eventId, UUID ticketTypeId, TicketStatus status) {
        return Ticket.builder()
                .id(id).orderId(UUID.randomUUID()).eventId(eventId).ticketTypeId(ticketTypeId).seatId(null)
                .ownerId(ownerId).ticketNumber("ABC-A2B3C4").credential("cred").credentialVersion(0).status(status)
                .build();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id).organizationId(organizationId).title("Concert").description("d").category("music")
                .status(EventStatus.PUBLISHED).ticketPrefix("ABC").startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC").build();
    }

    private TicketType ticketType(UUID id, UUID eventId, long price, String currency) {
        return TicketType.builder()
                .id(id).eventId(eventId).name("GA").kind(TicketTypeKind.GENERAL_ADMISSION)
                .price(Money.builder().amount(price).currency(currency).build())
                .quantityTotal(10).quantityAvailable(10)
                .saleStartAt(Instant.now().minus(1, ChronoUnit.DAYS)).saleEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .maxPerOrder(10).build();
    }

    private ResalePolicy policy(UUID eventId, boolean enabled, PriceCapRule rule, Long feeAmount, String feeCurrency) {
        return ResalePolicy.builder()
                .id(UUID.randomUUID()).eventId(eventId).enabled(enabled).priceCapRule(rule)
                .feeAmount(feeAmount != null ? Money.builder().amount(feeAmount).currency(feeCurrency).build() : null)
                .build();
    }

    private ResaleListingCreateRequest createRequest(long amount, String currency) {
        return new ResaleListingCreateRequest(new MoneyDto(amount, currency));
    }

    // ---- create() ----

    @Test
    void create_unknownTicket_throwsResourceNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(resalePolicyRepository, resaleListingRepository);
    }

    @Test
    void create_nonOwningCaller_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, UUID.randomUUID(), UUID.randomUUID(), TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(callerId);

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_ticketNotValid_throwsConflict() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, UUID.randomUUID(), UUID.randomUUID(), TicketStatus.USED)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_noResalePolicyRowAtAll_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, UUID.randomUUID(), TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void create_resaleDisabled_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, UUID.randomUUID(), TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, false, null, null, null)));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_askingPriceCurrencyMismatch_throwsBadRequest() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.NONE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "EUR")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_faceValueCap_askingPriceAboveFaceValue_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1001L, "USD")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cap");
    }

    @Test
    void create_faceValueCap_askingPriceExactlyAtFaceValue_succeeds() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(false);
        when(resaleListingRepository.save(any(ResaleListing.class))).thenAnswer(inv -> inv.getArgument(0));

        ResaleListingResponse response = service.create(ticketId, createRequest(1000L, "USD"));

        assertThat(response.status()).isEqualTo(ResaleListingStatus.ACTIVE);
        assertThat(response.askingPrice().amount()).isEqualTo(1000L);
    }

    /**
     * Code-reviewer MEDIUM: the cap must anchor to what THIS ticket actually
     * cost at issuance ({@code Ticket.faceValue}), not the ticket type's
     * CURRENT price - otherwise an organizer raising tiered/early-bird
     * pricing after this ticket was bought would let it resell above what
     * BR-TRANSFER-004's cap is meant to enforce. Ticket type's current price
     * (1500) is now higher than what this ticket actually cost (1000); the
     * cap must still be 1000.
     */
    @Test
    void create_faceValueCap_usesTicketsOwnFaceValue_notTicketTypesCurrentHigherPrice() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Ticket ticketWithFaceValue = ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID);
        ticketWithFaceValue.setFaceValue(Money.builder().amount(1000L).currency("USD").build());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketWithFaceValue));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1500L, "USD")));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1200L, "USD")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("cap");
    }

    @Test
    void create_faceValuePlusFeeCap_askingPriceWithinFacePlusFee_succeeds() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE_PLUS_FEE, 200L, "USD")));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(false);
        when(resaleListingRepository.save(any(ResaleListing.class))).thenAnswer(inv -> inv.getArgument(0));

        // cap = 1000 + 200 = 1200
        ResaleListingResponse response = service.create(ticketId, createRequest(1200L, "USD"));

        assertThat(response.status()).isEqualTo(ResaleListingStatus.ACTIVE);
    }

    @Test
    void create_faceValuePlusFeeCap_askingPriceAboveFacePlusFee_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE_PLUS_FEE, 200L, "USD")));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1201L, "USD")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_faceValuePlusFeeCap_noFeeConfigured_treatedAsZeroFee_capEqualsFaceValue() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        // FACE_VALUE_PLUS_FEE configured but organizer never set a feeAmount.
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.FACE_VALUE_PLUS_FEE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1001L, "USD")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_noneCapRule_anyPriceAllowed() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.NONE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(false);
        when(resaleListingRepository.save(any(ResaleListing.class))).thenAnswer(inv -> inv.getArgument(0));

        ResaleListingResponse response = service.create(ticketId, createRequest(1_000_000L, "USD"));

        assertThat(response.status()).isEqualTo(ResaleListingStatus.ACTIVE);
    }

    @Test
    void create_nullPriceCapRule_treatedSameAsNone_anyPriceAllowed() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, null, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(false);
        when(resaleListingRepository.save(any(ResaleListing.class))).thenAnswer(inv -> inv.getArgument(0));

        ResaleListingResponse response = service.create(ticketId, createRequest(50_000L, "USD"));

        assertThat(response.status()).isEqualTo(ResaleListingStatus.ACTIVE);
    }

    @Test
    void create_ticketAlreadyHasActiveListing_throwsConflict() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.NONE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ConflictException.class);
        verify(resaleListingRepository, never()).save(any());
    }

    @Test
    void create_concurrentRaceLostAtDbLevel_dataIntegrityViolation_mappedToConflict() {
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket(ticketId, ownerId, eventId, ticketTypeId, TicketStatus.VALID)));
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        when(resalePolicyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy(eventId, true, PriceCapRule.NONE, null, null)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1000L, "USD")));
        when(resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)).thenReturn(false);
        when(resaleListingRepository.save(any(ResaleListing.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(ticketId, createRequest(1000L, "USD")))
                .isInstanceOf(ConflictException.class);
    }

    // ---- cancel() ----

    @Test
    void cancel_unknownListing_throwsResourceNotFound() {
        UUID listingId = UUID.randomUUID();
        when(resaleListingRepository.findById(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(listingId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancel_nonSeller_throwsForbidden() {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ResaleListing listing = ResaleListing.builder().id(listingId).sellerId(sellerId).status(ResaleListingStatus.ACTIVE)
                .ticketId(UUID.randomUUID()).eventId(UUID.randomUUID())
                .askingPrice(Money.builder().amount(1000L).currency("USD").build()).build();
        when(resaleListingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(accessGuard.currentUserId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.cancel(listingId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_notActive_throwsConflict() {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ResaleListing listing = ResaleListing.builder().id(listingId).sellerId(sellerId).status(ResaleListingStatus.SOLD)
                .ticketId(UUID.randomUUID()).eventId(UUID.randomUUID())
                .askingPrice(Money.builder().amount(1000L).currency("USD").build()).build();
        when(resaleListingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(accessGuard.currentUserId()).thenReturn(sellerId);

        assertThatThrownBy(() -> service.cancel(listingId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_owningSeller_activeListing_succeeds() {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ResaleListing listing = ResaleListing.builder().id(listingId).sellerId(sellerId).status(ResaleListingStatus.ACTIVE)
                .ticketId(UUID.randomUUID()).eventId(UUID.randomUUID())
                .askingPrice(Money.builder().amount(1000L).currency("USD").build()).build();
        when(resaleListingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(accessGuard.currentUserId()).thenReturn(sellerId);

        service.cancel(listingId);

        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.CANCELLED);
        assertThat(listing.getResolvedAt()).isNotNull();
        verify(resaleListingRepository).save(listing);
    }

    // ---- listActive() ----

    @Test
    void listActive_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listActive(eventId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listActive_success_returnsOnlyActiveListingsMapped() {
        UUID eventId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, UUID.randomUUID())));
        ResaleListing listing = ResaleListing.builder().id(UUID.randomUUID()).eventId(eventId).sellerId(UUID.randomUUID())
                .ticketId(UUID.randomUUID()).status(ResaleListingStatus.ACTIVE)
                .askingPrice(Money.builder().amount(1000L).currency("USD").build()).build();
        when(resaleListingRepository.findByEventIdAndStatus(eventId, ResaleListingStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(listing)));

        var page = service.listActive(eventId, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(ResaleListingStatus.ACTIVE);
    }

    // ---- purchase() ----

    private ResaleListing activeListing(UUID listingId, UUID ticketId, UUID sellerId, UUID eventId, long amount) {
        return ResaleListing.builder()
                .id(listingId).ticketId(ticketId).eventId(eventId).sellerId(sellerId)
                .askingPrice(Money.builder().amount(amount).currency("USD").build())
                .status(ResaleListingStatus.ACTIVE)
                .build();
    }

    @Test
    void purchase_crossBuyerIdempotencyKey_throwsForbidden() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        ResalePurchaseIdempotencyKey existing = ResalePurchaseIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(UUID.randomUUID()).listingId(listingId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(existing, false));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void purchase_replayWithCompletedKey_returnsSameOrder_noRecharge() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID existingOrderId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        ResalePurchaseIdempotencyKey existing = ResalePurchaseIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(buyerId).listingId(listingId).orderId(existingOrderId).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(existing, false));
        Order existingOrder = Order.builder().id(existingOrderId).buyerId(buyerId).payeeType(PayeeType.USER)
                .payeeId(UUID.randomUUID()).status(OrderStatus.PAID)
                .total(Money.builder().amount(1000L).currency("USD").build()).createdBy(buyerId.toString()).build();
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(ticketRepository.findByOrderId(existingOrderId)).thenReturn(List.of());

        OrderResponse response = service.purchase(listingId, idempotencyKey, "tok_ok");

        assertThat(response.id()).isEqualTo(existingOrderId);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void purchase_inFlightDuplicateKey_throwsConflict() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        ResalePurchaseIdempotencyKey existing = ResalePurchaseIdempotencyKey.builder()
                .id(idempotencyKey).buyerId(buyerId).listingId(listingId).orderId(null).build();
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(existing, false));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void purchase_listingNotFound_throwsResourceNotFound_andFreesKey() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    @Test
    void purchase_listingNoLongerActive_throwsConflict_andFreesKey() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        ResaleListing sold = activeListing(listingId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1000L);
        sold.setStatus(ResaleListingStatus.SOLD);
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(sold));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ConflictException.class);
        verify(idempotencyKeyManager).delete(idempotencyKey);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void purchase_sellerAttemptsToBuyOwnListing_throwsForbidden_andFreesKey() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(sellerId);
        when(idempotencyKeyManager.claim(idempotencyKey, sellerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        ResaleListing listing = activeListing(listingId, UUID.randomUUID(), sellerId, UUID.randomUUID(), 1000L);
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ForbiddenException.class);
        verify(idempotencyKeyManager).delete(idempotencyKey);
        verifyNoInteractions(paymentGatewayClient);
    }

    /**
     * Code-reviewer CRITICAL, defense in depth: if the ticket's current
     * owner ever diverges from the listing's {@code sellerId} (e.g. a direct
     * transfer that somehow bypassed {@code TicketTransferServiceImpl}'s own
     * auto-cancel), the purchase must refuse rather than pay out to
     * whoever the listing says the seller is.
     */
    @Test
    void purchase_ticketOwnerNoLongerMatchesListingSeller_throwsConflict_andFreesKey() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID actualCurrentOwnerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        ResaleListing listing = activeListing(listingId, ticketId, sellerId, UUID.randomUUID(), 1000L);
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(ticketRepository.findByIdForUpdate(ticketId))
                .thenReturn(Optional.of(ticket(ticketId, actualCurrentOwnerId, listing.getEventId(), UUID.randomUUID(), TicketStatus.VALID)));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_ok"))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(paymentGatewayClient);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(ticketTransferService, never()).recordTransfer(any(), any(), any());
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    @Test
    void purchase_paymentDeclined_throwsPaymentFailed_listingStaysActive_andFreesKey() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        ResaleListing listing = activeListing(listingId, ticketId, sellerId, UUID.randomUUID(), 1000L);
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(ticketRepository.findByIdForUpdate(ticketId))
                .thenReturn(Optional.of(ticket(ticketId, sellerId, listing.getEventId(), UUID.randomUUID(), TicketStatus.VALID)));
        when(paymentGatewayClient.charge(eq("tok_fail"), any())).thenReturn(PaymentResult.failure("declined"));

        assertThatThrownBy(() -> service.purchase(listingId, idempotencyKey, "tok_fail"))
                .isInstanceOf(PaymentFailedException.class);

        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.ACTIVE);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(ticketTransferService, never()).recordTransfer(any(), any(), any());
        verify(idempotencyKeyManager).delete(idempotencyKey);
    }

    @Test
    void purchase_success_chargesSellerAsPayee_createsOrderAndPayment_recordsTransfer_marksListingSold() {
        UUID listingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(buyerId);
        when(idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId))
                .thenReturn(new ResalePurchaseIdempotencyKeyManager.ClaimOutcome(null, true));
        ResaleListing listing = activeListing(listingId, ticketId, sellerId, eventId, 1500L);
        when(resaleListingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        Ticket ticket = ticket(ticketId, sellerId, eventId, UUID.randomUUID(), TicketStatus.VALID);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(paymentGatewayClient.charge(eq("tok_ok"), any())).thenReturn(PaymentResult.success("mock_ref_resale"));
        UUID savedOrderId = UUID.randomUUID();
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.saveAndFlush(orderCaptor.capture())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(savedOrderId);
            o.setCreatedAt(Instant.now());
            return o;
        });
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        Ticket transferredTicket = ticket(ticketId, buyerId, eventId, ticket.getTicketTypeId(), TicketStatus.VALID);
        transferredTicket.setCredentialVersion(1);
        when(ticketTransferService.recordTransfer(ticket, buyerId, TransferSource.RESALE)).thenReturn(transferredTicket);
        ResalePurchaseIdempotencyKey keyRow = ResalePurchaseIdempotencyKey.builder().id(idempotencyKey).buyerId(buyerId).listingId(listingId).build();
        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.of(keyRow));

        OrderResponse response = service.purchase(listingId, idempotencyKey, "tok_ok");

        assertThat(response.id()).isEqualTo(savedOrderId);
        assertThat(response.payeeType()).isEqualTo(PayeeType.USER);
        assertThat(response.payeeId()).isEqualTo(sellerId);
        assertThat(response.buyerId()).isEqualTo(buyerId);
        assertThat(response.total().amount()).isEqualTo(1500L);
        assertThat(response.tickets()).hasSize(1);
        assertThat(response.tickets().get(0).ownerId()).isEqualTo(buyerId);

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getBuyerId()).isEqualTo(buyerId);
        assertThat(savedOrder.getPayeeType()).isEqualTo(PayeeType.USER);
        assertThat(savedOrder.getPayeeId()).isEqualTo(sellerId);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getOrderId()).isEqualTo(savedOrderId);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(savedPayment.getGatewayRef()).isEqualTo("mock_ref_resale");

        verify(ticketTransferService).recordTransfer(ticket, buyerId, TransferSource.RESALE);
        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.SOLD);
        assertThat(listing.getBuyerOrderId()).isEqualTo(savedOrderId);
        assertThat(listing.getResolvedAt()).isNotNull();
        assertThat(keyRow.getOrderId()).isEqualTo(savedOrderId);
        verify(idempotencyKeyRepository).save(keyRow);
        verify(idempotencyKeyManager, never()).delete(any());
    }
}
