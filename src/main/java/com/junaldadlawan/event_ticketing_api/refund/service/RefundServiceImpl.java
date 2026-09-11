package com.junaldadlawan.event_ticketing_api.refund.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundCreateRequest;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundResponse;
import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.repository.RefundRepository;
import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import com.junaldadlawan.event_ticketing_api.refundpolicy.enums.RefundRuleType;
import com.junaldadlawan.event_ticketing_api.refundpolicy.repository.RefundPolicyRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 8. {@code createRefund} is the organizer/admin-initiated,
 * policy-gated path (BR-PAY-002/003); {@code refundAllForEventCancellation}
 * is the mandatory, policy-bypassing path event cancellation triggers
 * (BR-PAY-005) - both funnel through the same {@link #issueRefund} primitive
 * so the money/ticket-state mechanics are never duplicated.
 * <p>
 * Confirmed decision: a full refund (cumulative refunded amount reaches the
 * order's total) also marks every one of the order's {@code Ticket}s {@code
 * REFUNDED} - a refunded ticket is no longer a valid admission credential.
 * A partial refund leaves tickets untouched (it's a monetary adjustment,
 * not an entitlement cancellation).
 * <p>
 * Confirmed decision: an event with NO {@code RefundPolicy} row configured
 * permits no organizer/admin-initiated refunds (mirrors {@code
 * ResalePolicy}'s default-disabled precedent) - this does NOT apply to
 * {@link #refundAllForEventCancellation}, which is mandatory regardless.
 * <p>
 * Code-reviewer CRITICAL: every read-then-write on an {@code Order}'s
 * cumulative refunded amount goes through {@code OrderRepository.findByIdForUpdate}
 * (never a plain {@code findById}) - without this, two concurrent refund
 * attempts on the same order (two admins, or an admin racing a concurrent
 * event cancellation) could both read "not yet refunded" and both succeed,
 * refunding the order for more than its total. Same idiom as Phase 7's
 * {@code ResaleListingServiceImpl}/{@code TicketTransferServiceImpl}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final OrganizationAccessGuard accessGuard;
    private final TicketTypeRepository ticketTypeRepository;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RefundResponse createRefund(UUID orderId, RefundCreateRequest request) {
        // Unlocked read first, just to resolve the event/authorization -
        // cheap and doesn't need to serialize against concurrent refunds.
        Order order = getOrderOrThrow(orderId);
        Event event = resolveEventOrThrow(order);
        requireOwnerOrOrganizerOrAdmin(event.getOrganizationId());

        // Locked from here on (code-reviewer CRITICAL) - this is what
        // actually serializes two concurrent refund attempts on the same
        // order, the same way CheckoutServiceImpl locks the Cart before
        // reading its items.
        Order lockedOrder = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));

        long remaining = remainingRefundableAmount(lockedOrder);
        if (remaining <= 0) {
            throw new ConflictException("This order has already been fully refunded");
        }

        long requestedAmount = remaining;
        if (request.amount() != null) {
            MoneyDto requested = request.amount();
            if (requested.amount() <= 0) {
                throw new BadRequestException("Refund amount must be positive");
            }
            if (!requested.currency().equals(lockedOrder.getTotal().getCurrency())) {
                throw new BadRequestException("Refund amount currency must match the order's currency (" + lockedOrder.getTotal().getCurrency() + ")");
            }
            if (requested.amount() > remaining) {
                throw new BadRequestException("Refund amount exceeds the order's remaining refundable balance");
            }
            requestedAmount = requested.amount();
        }

        requireAllowedByRefundPolicy(event);

        Refund refund = issueRefund(lockedOrder, requestedAmount, lockedOrder.getTotal().getCurrency(), request.reason(), accessGuard.currentUserId());
        return RefundResponse.from(refund);
    }

    @Override
    public List<RefundResponse> listRefunds(UUID orderId) {
        Order order = getOrderOrThrow(orderId);
        requireOrderVisibility(order);
        return refundRepository.findByOrderId(orderId).stream().map(RefundResponse::from).toList();
    }

    @Override
    @Transactional
    public void refundAllForEventCancellation(UUID eventId, UUID initiatedBy) {
        List<UUID> orderIds = ticketRepository.findDistinctOrderIdsByEventId(eventId);
        for (UUID orderId : orderIds) {
            try {
                // Locked (code-reviewer CRITICAL), same reasoning as
                // createRefund - this order could otherwise be concurrently
                // refunded by an organizer/admin's own POST call.
                Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
                if (order == null || order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.CANCELLED) {
                    continue;
                }
                long remaining = remainingRefundableAmount(order);
                if (remaining <= 0) {
                    continue;
                }
                // Mandatory per BR-PAY-005 - deliberately bypasses
                // requireAllowedByRefundPolicy; the event is cancelled, so
                // every ticket holder is owed a refund regardless of the
                // organizer's configured policy.
                issueRefund(order, remaining, order.getTotal().getCurrency(), "Event cancelled", initiatedBy);
            } catch (RuntimeException e) {
                // Best-effort sweep: one order's failure must not abort
                // refunding every other ticket holder of this event - but
                // (code-reviewer HIGH) it must not vanish without a trace
                // either, unlike a declined gateway call (already visible
                // as a FAILED Refund row) - this catches everything else
                // (e.g. a missing Payment row, a bug), which would
                // otherwise look identical to "nothing went wrong" to
                // every caller, including the organizer who just cancelled
                // their event.
                log.error("Failed to refund order {} during cancellation of event {}", orderId, eventId, e);
            }
        }
    }

    /**
     * Shared money/ticket-state mechanics for both refund entry points.
     * Always persists a {@code Refund} row (COMPLETED or FAILED, mirroring
     * how checkout always persists its idempotency-key bookkeeping
     * regardless of gateway outcome) - a declined gateway reversal is not
     * an exception, it's a recorded fact the caller can see in the response.
     */
    private Refund issueRefund(Order order, long amountMinorUnits, String currency, String reason, UUID initiatedBy) {
        Payment payment = paymentRepository.findByOrderId(order.getId()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + order.getId()));

        Money amount = Money.builder().amount(amountMinorUnits).currency(currency).build();
        PaymentResult result = paymentGatewayClient.refund(payment.getGatewayRef(), amount);

        Refund refund = Refund.builder()
                .orderId(order.getId())
                .amount(amount)
                .reason(reason)
                .initiatedBy(initiatedBy)
                .status(result.successful() ? RefundStatus.COMPLETED : RefundStatus.FAILED)
                .build();
        Refund saved = refundRepository.save(refund);

        if (result.successful()) {
            long totalRefunded = refundRepository.sumCompletedAmountByOrderId(order.getId());
            if (totalRefunded >= order.getTotal().getAmount()) {
                order.setStatus(OrderStatus.REFUNDED);
                // Code-reviewer CRITICAL: Ticket.orderId never changes on a
                // transfer or resale (Phase 7) - only ownerId does. Without
                // this check, refunding order O would silently invalidate a
                // ticket that its original buyer has since legitimately
                // transferred/sold away, stripping the CURRENT holder's
                // valid entitlement even though they never sold it back and
                // were paid nothing. Only invalidate tickets still actually
                // held by the order's own buyer.
                for (Ticket ticket : ticketRepository.findByOrderId(order.getId())) {
                    if (ticket.getOwnerId().equals(order.getBuyerId())) {
                        ticket.setStatus(TicketStatus.REFUNDED);
                        ticketRepository.save(ticket);
                        restockAndNotifyWaitlistIfGeneralAdmission(ticket);
                    }
                }
            } else {
                order.setStatus(OrderStatus.PARTIALLY_REFUNDED);
            }
            orderRepository.save(order);
            // BR-NOTIFY-001 (Phase 11). Fired for both a full and a partial
            // refund - either way the buyer was just refunded money and
            // should be told. Never throws (NFR 5.2).
            notificationService.notify(order.getBuyerId(), NotificationType.REFUND_CONFIRMATION, "Refund", saved.getId());
        }

        return saved;
    }

    /**
     * Phase 11 (BR-WAIT-002/003): a refunded ticket frees up one unit of
     * inventory. Restricted to GA tickets ({@code seatId == null}) -
     * reserved-seating {@code TicketType}s never had {@code
     * quantityAvailable} decremented at checkout in the first place (see
     * {@code CheckoutServiceImpl.doCheckout}: seated items only ever flip
     * {@code Seat.status}), so restocking it here for a seated ticket would
     * fabricate inventory that was never actually reserved via that
     * counter. Mirrors {@code CartServiceImpl.releaseHold}'s exact GA
     * restock idiom (locked read, increment, save).
     */
    private void restockAndNotifyWaitlistIfGeneralAdmission(Ticket ticket) {
        if (ticket.getSeatId() != null) {
            return;
        }
        ticketTypeRepository.findByIdForUpdate(ticket.getTicketTypeId()).ifPresent(ticketType -> {
            ticketType.setQuantityAvailable(ticketType.getQuantityAvailable() + 1);
            ticketTypeRepository.save(ticketType);
        });
        waitlistService.notifyNextInLineIfAvailable(ticket.getEventId(), ticket.getTicketTypeId());
    }

    private long remainingRefundableAmount(Order order) {
        long alreadyRefunded = refundRepository.sumCompletedAmountByOrderId(order.getId());
        return order.getTotal().getAmount() - alreadyRefunded;
    }

    /**
     * BR-PAY-003. {@code NO_REFUNDS} or no policy row at all -> denied;
     * {@code REFUNDABLE_UNTIL_N_DAYS} -> denied once the event is within
     * {@code daysBeforeEvent} days of starting; {@code CUSTOM} -> always
     * allowed (its terms are informational text, not a machine-checkable
     * rule).
     */
    private void requireAllowedByRefundPolicy(Event event) {
        RefundPolicy policy = refundPolicyRepository.findByEventId(event.getId()).orElse(null);
        if (policy == null || policy.getRuleType() == RefundRuleType.NO_REFUNDS) {
            throw new ConflictException("Refund not permitted under the event's refund policy");
        }
        if (policy.getRuleType() == RefundRuleType.REFUNDABLE_UNTIL_N_DAYS) {
            int days = policy.getDaysBeforeEvent() != null ? policy.getDaysBeforeEvent() : 0;
            Instant cutoff = event.getStartAt().minus(days, ChronoUnit.DAYS);
            if (!Instant.now().isBefore(cutoff)) {
                throw new ConflictException("Refund not permitted under the event's refund policy");
            }
        }
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    /**
     * Order has no eventId column of its own (confirmed decision #3) - "the
     * order's event" is resolved via any one of its tickets, same idiom as
     * {@code OrderServiceImpl}.
     */
    private Event resolveEventOrThrow(Order order) {
        Optional<Ticket> anyTicket = ticketRepository.findFirstByOrderId(order.getId());
        UUID eventId = anyTicket.orElseThrow(() ->
                new ResourceNotFoundException("No tickets found for order " + order.getId())).getEventId();
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    /** BR-PAY-002: refund creation is organizer/admin only, deliberately NOT buyer-initiated (unlike order/refund-list visibility below). */
    private void requireOwnerOrOrganizerOrAdmin(UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the event's organizer/owner or an admin may issue a refund for this order");
        }
    }

    /**
     * BR-CART-004-equivalent order visibility (buyer, event organizer/owner,
     * or admin) - mirrors {@code OrderServiceImpl.requireVisibility}
     * (replicated rather than reused: no shared order-access-guard
     * component exists yet, same reasoning as {@code CheckoutServiceImpl}'s
     * own replicated buyer check).
     */
    private void requireOrderVisibility(Order order) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        if (order.getBuyerId().equals(callerId)) {
            return;
        }
        Optional<Ticket> anyTicket = ticketRepository.findFirstByOrderId(order.getId());
        if (anyTicket.isEmpty()) {
            throw new ForbiddenException("Only the order's buyer or an admin may view this order's refunds");
        }
        Event event = eventRepository.findByIdAndDeletedAtIsNull(anyTicket.get().getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + anyTicket.get().getEventId() + " not found"));
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the order's buyer, the event's organizer/owner, or an admin may view this order's refunds");
        }
    }
}
