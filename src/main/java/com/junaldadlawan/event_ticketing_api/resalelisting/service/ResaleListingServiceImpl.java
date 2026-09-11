package com.junaldadlawan.event_ticketing_api.resalelisting.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
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
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResaleListingServiceImpl implements ResaleListingService {

    private final ResaleListingRepository resaleListingRepository;
    private final ResalePolicyRepository resalePolicyRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ResalePurchaseIdempotencyKeyRepository idempotencyKeyRepository;
    private final ResalePurchaseIdempotencyKeyManager idempotencyKeyManager;
    private final TicketTransferService ticketTransferService;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional
    public ResaleListingResponse create(UUID ticketId, ResaleListingCreateRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        UUID callerId = accessGuard.currentUserId();
        if (!ticket.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("Only the ticket's owning buyer may list it for resale");
        }
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new ConflictException("Only a valid ticket may be listed for resale");
        }

        Event event = eventRepository.findByIdAndDeletedAtIsNull(ticket.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticket.getEventId() + " not found"));
        ResalePolicy policy = resalePolicyRepository.findByEventId(event.getId()).orElse(null);
        if (policy == null || !policy.isEnabled()) {
            throw new ForbiddenException("Resale is disabled for this event");
        }

        TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticket.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticket.getTicketTypeId() + " not found"));
        // Anchored to what THIS ticket actually cost at issuance (code-reviewer
        // MEDIUM), not the ticket type's current price - falls back to the
        // current price only for tickets issued before Ticket.faceValue existed.
        Money faceValue = ticket.getFaceValue() != null ? ticket.getFaceValue() : ticketType.getPrice();
        Money askingPrice = Money.builder().amount(request.askingPrice().amount()).currency(request.askingPrice().currency()).build();
        if (!askingPrice.getCurrency().equals(faceValue.getCurrency())) {
            throw new BadRequestException("Asking price currency must match the ticket's face-value currency (" + faceValue.getCurrency() + ")");
        }
        requirePriceWithinCap(policy, faceValue, askingPrice);

        if (resaleListingRepository.existsByTicketIdAndStatus(ticketId, ResaleListingStatus.ACTIVE)) {
            throw new ConflictException("This ticket already has an active resale listing");
        }

        ResaleListing listing = ResaleListing.builder()
                .ticketId(ticketId)
                .eventId(event.getId())
                .sellerId(callerId)
                .askingPrice(askingPrice)
                .status(ResaleListingStatus.ACTIVE)
                .build();
        try {
            return ResaleListingResponse.from(resaleListingRepository.save(listing));
        } catch (DataIntegrityViolationException e) {
            // Lost a race to a concurrent create for the same ticket - V14's
            // partial unique index (ticket_id WHERE status='ACTIVE') is the
            // backstop, same idiom as CheckoutIdempotencyKeyManager.claim.
            throw new ConflictException("This ticket already has an active resale listing");
        }
    }

    /**
     * BR-TRANSFER-004: {@code NONE}/no policy row's rule means no cap at
     * all; {@code FACE_VALUE} caps at the ticket's actual face value (what
     * was paid at issuance); {@code FACE_VALUE_PLUS_FEE} adds the policy's
     * configured fee (treated as 0 if the organizer never set one).
     */
    private void requirePriceWithinCap(ResalePolicy policy, Money faceValue, Money askingPrice) {
        PriceCapRule rule = policy.getPriceCapRule();
        if (rule == null || rule == PriceCapRule.NONE) {
            return;
        }
        long cap = faceValue.getAmount();
        if (rule == PriceCapRule.FACE_VALUE_PLUS_FEE && policy.getFeeAmount() != null) {
            cap += policy.getFeeAmount().getAmount();
        }
        if (askingPrice.getAmount() > cap) {
            throw new ForbiddenException("Asking price exceeds this event's resale price cap");
        }
    }

    @Override
    @Transactional
    public void cancel(UUID listingId) {
        ResaleListing listing = resaleListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Resale listing " + listingId + " not found"));
        UUID callerId = accessGuard.currentUserId();
        if (!listing.getSellerId().equals(callerId)) {
            throw new ForbiddenException("Only the listing's owning seller may cancel it");
        }
        if (listing.getStatus() != ResaleListingStatus.ACTIVE) {
            throw new ConflictException("Only an active listing may be cancelled");
        }
        listing.setStatus(ResaleListingStatus.CANCELLED);
        listing.setResolvedAt(Instant.now());
        resaleListingRepository.save(listing);
    }

    @Override
    public Page<ResaleListingResponse> listActive(UUID eventId, Pageable pageable) {
        eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        return resaleListingRepository.findByEventIdAndStatus(eventId, ResaleListingStatus.ACTIVE, pageable)
                .map(ResaleListingResponse::from);
    }

    @Override
    @Transactional
    public OrderResponse purchase(UUID listingId, UUID idempotencyKey, String paymentMethodToken) {
        UUID buyerId = accessGuard.currentUserId();

        ResalePurchaseIdempotencyKeyManager.ClaimOutcome claim = idempotencyKeyManager.claim(idempotencyKey, buyerId, listingId);
        if (!claim.freshlyClaimed()) {
            ResalePurchaseIdempotencyKey existing = claim.key();
            if (!existing.getBuyerId().equals(buyerId)) {
                throw new ForbiddenException("This idempotency key was issued by a different buyer");
            }
            if (existing.getOrderId() != null) {
                Order order = orderRepository.findById(existing.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order " + existing.getOrderId() + " not found"));
                return OrderResponse.from(order, ticketRepository.findByOrderId(order.getId()));
            }
            throw new ConflictException("Purchase already in progress for this idempotency key");
        }

        try {
            return doPurchase(listingId, buyerId, paymentMethodToken, idempotencyKey);
        } catch (RuntimeException ex) {
            idempotencyKeyManager.delete(idempotencyKey);
            throw ex;
        }
    }

    private OrderResponse doPurchase(UUID listingId, UUID buyerId, String paymentMethodToken, UUID idempotencyKey) {
        // Locked first, same reasoning as CheckoutServiceImpl's cart lock -
        // this is what actually serializes two concurrent purchase attempts
        // on the SAME listing under two different idempotency keys.
        ResaleListing listing = resaleListingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Resale listing " + listingId + " not found"));
        if (listing.getStatus() != ResaleListingStatus.ACTIVE) {
            throw new ConflictException("This listing is no longer active");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw new ForbiddenException("Cannot purchase your own resale listing");
        }

        // Also locked - a concurrent direct transfer of the same ticket
        // (TicketTransferServiceImpl.transfer) locks via the same method.
        Ticket ticket = ticketRepository.findByIdForUpdate(listing.getTicketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + listing.getTicketId() + " not found"));
        // Defense in depth (code-reviewer CRITICAL): TicketTransferServiceImpl.transfer
        // auto-cancels a ticket's own active listing on direct transfer, so
        // this should be unreachable in practice - but re-verifying here
        // means a stale listing can never pay out to someone who no longer
        // owns the ticket, regardless of how it became stale.
        if (!ticket.getOwnerId().equals(listing.getSellerId())) {
            throw new ConflictException("This listing is no longer valid - the ticket's ownership has changed");
        }

        PaymentResult result = paymentGatewayClient.charge(paymentMethodToken, listing.getAskingPrice());
        if (!result.successful()) {
            // No Order/Payment/listing/ticket state touched on this path -
            // listing remains ACTIVE for retry, same shape as checkout's 402.
            throw new PaymentFailedException(result.failureReason() != null ? result.failureReason() : "Payment failed");
        }

        Order order = Order.builder()
                .buyerId(buyerId)
                .payeeType(PayeeType.USER)
                .payeeId(listing.getSellerId())
                .status(OrderStatus.PAID)
                .promoCode(null)
                .total(listing.getAskingPrice())
                .cartId(null)
                .createdBy(buyerId.toString())
                .build();
        Order savedOrder = orderRepository.saveAndFlush(order);

        Payment payment = Payment.builder()
                .orderId(savedOrder.getId())
                .gatewayRef(result.gatewayRef())
                .amount(listing.getAskingPrice())
                .status(PaymentStatus.COMPLETED)
                .build();
        paymentRepository.save(payment);

        Ticket transferredTicket = ticketTransferService.recordTransfer(ticket, buyerId, TransferSource.RESALE);

        listing.setStatus(ResaleListingStatus.SOLD);
        listing.setResolvedAt(Instant.now());
        listing.setBuyerOrderId(savedOrder.getId());
        resaleListingRepository.save(listing);

        idempotencyKeyRepository.findById(idempotencyKey).ifPresent(key -> {
            key.setOrderId(savedOrder.getId());
            idempotencyKeyRepository.save(key);
        });

        return OrderResponse.from(savedOrder, List.of(transferredTicket));
    }
}
