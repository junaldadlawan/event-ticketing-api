package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.GoneException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeUsageLimitGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checkout orchestration (Phase 5b). Kept as its own service/module rather
 * than folded into {@code CartServiceImpl} given how correctness-sensitive
 * this is (money, idempotency, inventory finalization) - isolating it makes
 * the transaction/idempotency boundaries easier to reason about and review
 * in one place. See {@link CheckoutIdempotencyKeyManager} for why the
 * idempotency-key claim/release steps live on a separate bean.
 */
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    // 36-char alphabet minus ambiguous O/0, I/1 (BR-TICKET-005's
    // recommendation) - 32^6 combinations per event, retried-until-unique
    // against TicketRepository.existsByEventIdAndTicketNumber, same idiom as
    // EventServiceImpl.generateUniqueTicketPrefix.
    private static final String TICKET_NUMBER_SUFFIX_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TICKET_NUMBER_SUFFIX_LENGTH = 6;
    private static final int MAX_TICKET_NUMBER_ATTEMPTS = 50;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CheckoutIdempotencyKeyRepository idempotencyKeyRepository;
    private final CheckoutIdempotencyKeyManager idempotencyKeyManager;
    private final PaymentGatewayClient paymentGatewayClient;
    private final OrganizationAccessGuard accessGuard;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUsageLimitGuard promoCodeUsageLimitGuard;
    private final TicketRepository ticketRepository;
    private final TicketCredentialService ticketCredentialService;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional(noRollbackFor = GoneException.class)
    public OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken) {
        UUID buyerId = accessGuard.currentUserId();

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));
        // Same one-line buyer-only check as CartServiceImpl.requireOwnerBuyer
        // (replicated rather than reused: it's a one-liner, and CartService
        // doesn't expose the Cart entity itself to reuse it as a helper).
        if (!buyerId.equals(cart.getBuyerId())) {
            throw new ForbiddenException("Only the cart's own buyer may check out this cart");
        }

        CheckoutIdempotencyKeyManager.ClaimOutcome claim = idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId);
        if (!claim.freshlyClaimed()) {
            CheckoutIdempotencyKey existing = claim.key();
            // Defense against key reuse across users: this idempotency key
            // was already claimed by someone else.
            if (!existing.getBuyerId().equals(buyerId)) {
                throw new ForbiddenException("This idempotency key was issued by a different buyer");
            }
            if (existing.getOrderId() != null) {
                // Replay of an already-completed checkout: return the same
                // Order, don't recharge, don't re-touch inventory.
                Order order = orderRepository.findById(existing.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order " + existing.getOrderId() + " not found"));
                return OrderResponse.from(order, ticketRepository.findByOrderId(order.getId()));
            }
            // orderId is still null: a genuinely concurrent duplicate
            // request is in-flight right now.
            throw new ConflictException("Checkout already in progress for this idempotency key");
        }

        try {
            return doCheckout(cart, buyerId, paymentMethodToken, idempotencyKey);
        } catch (RuntimeException ex) {
            // Any failure past this point means the key was never actually
            // used for a completed checkout - free it for retry. This
            // generalizes the two documented failure paths (402 payment
            // failure, 410 expired hold) to any other failure too (e.g. an
            // empty-cart 409), so an idempotency key never gets stuck
            // unusable after a failed attempt.
            idempotencyKeyManager.delete(idempotencyKey);
            throw ex;
        }
    }

    private OrderResponse doCheckout(Cart cart, UUID buyerId, String paymentMethodToken, UUID idempotencyKey) {
        UUID cartId = cart.getId();

        // Lock the Cart row FIRST, before reading any CartItems (code-reviewer
        // CRITICAL 1). This is what actually serializes two concurrent
        // checkout() calls on the SAME cart using two DIFFERENT idempotency
        // keys - the idempotency-key claim above only dedupes by key value,
        // not by cart, so without this lock both requests could otherwise
        // read the same not-yet-deleted CartItem rows, both charge, and both
        // insert an Order. The second request blocks here until the first
        // attempt's transaction commits (deleting the CartItems below), then
        // - under Postgres read-committed semantics - sees a fresh snapshot
        // with those rows already gone and correctly falls into the
        // "cart has no items to check out" 409 path a few lines down.
        Cart lockedCart = cartRepository.findByIdForUpdate(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        if (items.isEmpty()) {
            throw new ConflictException("Cart has no items to check out");
        }

        Instant now = Instant.now();
        boolean anyExpired = items.stream().anyMatch(item -> item.getHoldExpiresAt().isBefore(now));
        if (anyExpired) {
            // Reuse CartServiceImpl's exact lazy-release logic (locks +
            // restores TicketType.quantityAvailable / Seat.status) instead
            // of reimplementing it here. This write is preserved even
            // though we're about to throw, thanks to this method's
            // @Transactional(noRollbackFor = GoneException.class).
            cartService.releaseExpiredHoldsForCart(cartId);
            throw new GoneException("A hold expired before checkout completed.");
        }

        // Reuse CartService.get()'s already-computed total (subtotal minus
        // any applied promo-code discount) instead of duplicating that
        // pricing/discount math here.
        CartResponse cartView = cartService.get(cartId);
        MoneyDto totalDto = cartView.total();
        String promoCode = cartView.appliedPromoCode() != null ? cartView.appliedPromoCode().code() : null;
        Money total = Money.builder().amount(totalDto.amount()).currency(totalDto.currency()).build();

        // BR-PROMO-006 (code-reviewer HIGH finding): re-validate the applied
        // promo code's usage limits immediately before charging, not just at
        // applyPromoCode() time - other buyers may have exhausted the code's
        // usageLimitTotal/usageLimitPerBuyer in the window between this
        // buyer's applyPromoCode() call and this checkout() call.
        if (lockedCart.getPromoCodeId() != null) {
            PromoCode appliedPromoCode = promoCodeRepository.findById(lockedCart.getPromoCodeId()).orElse(null);
            if (appliedPromoCode != null) {
                promoCodeUsageLimitGuard.checkUsageLimits(appliedPromoCode, buyerId);
            }
        }

        // Also resolved here (rather than after payment) so the same lookup
        // can be reused below for ticket issuance without an extra query.
        Event event = resolveEvent(items);
        UUID payeeId = event.getOrganizationId();

        PaymentResult result = paymentGatewayClient.charge(paymentMethodToken, total);
        if (!result.successful()) {
            // BR-CART-002: no Order/Payment row is ever persisted on this
            // path. Cart and holds remain fully intact - nothing above this
            // point touched CartItem/TicketType/Seat state.
            throw new PaymentFailedException(
                    result.failureReason() != null ? result.failureReason() : "Payment failed");
        }

        Order order = Order.builder()
                .buyerId(buyerId)
                .payeeType(PayeeType.ORGANIZATION)
                .payeeId(payeeId)
                .status(OrderStatus.PAID)
                .promoCode(promoCode)
                .total(total)
                .cartId(cartId)
                .createdBy(buyerId.toString())
                .build();
        final Order savedOrder = orderRepository.saveAndFlush(order);

        Payment payment = Payment.builder()
                .orderId(savedOrder.getId())
                .gatewayRef(result.gatewayRef())
                .amount(total)
                .status(PaymentStatus.COMPLETED)
                .build();
        paymentRepository.save(payment);

        // Convert each hold into its final sold state AND issue one Ticket
        // row per admission unit (Phase 6a confirmed decision #2): a GA
        // CartItem with quantity=N issues N separate Ticket rows (each its
        // own credential/ticket number); a reserved-seating CartItem
        // (quantity always 1) issues exactly 1, tied to its seat.
        List<Ticket> issuedTickets = new ArrayList<>();
        for (CartItem item : items) {
            if (item.getSeatId() != null) {
                // Reserved seating: flip HELD -> SOLD.
                seatRepository.findByIdForUpdate(item.getSeatId())
                        .ifPresent(seat -> {
                            seat.setStatus(SeatStatus.SOLD);
                            seatRepository.save(seat);
                        });
                issuedTickets.add(issueTicket(savedOrder, event, item.getTicketTypeId(), item.getSeatId(), buyerId));
            } else {
                // GA: TicketType.quantityAvailable was already decremented at
                // hold-placement time (Phase 5a) - that decrement simply
                // becomes permanent, nothing further to do to TicketType.
                for (int i = 0; i < item.getQuantity(); i++) {
                    issuedTickets.add(issueTicket(savedOrder, event, item.getTicketTypeId(), null, buyerId));
                }
            }
            cartItemRepository.delete(item);
        }
        // Force a flush so @CreationTimestamp/@UpdateTimestamp are actually
        // populated on each issued Ticket before OrderResponse.from reads
        // them below - without this, tickets.save() alone only schedules the
        // INSERT; Hibernate doesn't assign the generated timestamp values
        // until flush, so the response could otherwise (non-deterministically,
        // depending on whether some other query happened to trigger an
        // auto-flush first) serialize createdAt/updatedAt as null.
        ticketRepository.flush();

        lockedCart.setPromoCodeId(null);
        cartRepository.save(lockedCart);

        idempotencyKeyRepository.findById(idempotencyKey).ifPresent(key -> {
            key.setOrderId(savedOrder.getId());
            idempotencyKeyRepository.save(key);
        });

        return OrderResponse.from(savedOrder, issuedTickets);
    }

    /**
     * A cart is constrained to a single event's ticket types (Phase 5a), so
     * any item's ticket type resolves to the same event/organization -
     * looking at the first item is enough.
     */
    private Event resolveEvent(List<CartItem> items) {
        UUID ticketTypeId = items.get(0).getTicketTypeId();
        TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));
        return eventRepository.findByIdAndDeletedAtIsNull(ticketType.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticketType.getEventId() + " not found"));
    }

    /**
     * Issues one Ticket row: a fresh random id (needed up front so the
     * credential can embed it - see {@code Ticket}'s javadoc), a
     * per-event-unique human-readable ticket number (BR-TICKET-002/005), and
     * the HMAC-signed credential (BR-TICKET-001).
     */
    private Ticket issueTicket(Order order, Event event, UUID ticketTypeId, UUID seatId, UUID ownerId) {
        UUID ticketId = UUID.randomUUID();
        String ticketNumber = generateUniqueTicketNumber(event.getId(), event.getTicketPrefix());
        String credential = ticketCredentialService.generate(ticketId);
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .orderId(order.getId())
                .eventId(event.getId())
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .ownerId(ownerId)
                .ticketNumber(ticketNumber)
                .credential(credential)
                .status(TicketStatus.VALID)
                .build();
        return ticketRepository.save(ticket);
    }

    /**
     * Retry-until-unique idiom mirrored from {@code
     * EventServiceImpl.generateUniqueTicketPrefix} - BR-TICKET-005: the
     * 6-character suffix only needs to be unique within its own event, not
     * platform-wide.
     */
    private String generateUniqueTicketNumber(UUID eventId, String prefix) {
        for (int attempt = 0; attempt < MAX_TICKET_NUMBER_ATTEMPTS; attempt++) {
            StringBuilder suffix = new StringBuilder(TICKET_NUMBER_SUFFIX_LENGTH);
            for (int i = 0; i < TICKET_NUMBER_SUFFIX_LENGTH; i++) {
                suffix.append(TICKET_NUMBER_SUFFIX_ALPHABET.charAt(random.nextInt(TICKET_NUMBER_SUFFIX_ALPHABET.length())));
            }
            String candidate = prefix + "-" + suffix;
            if (!ticketRepository.existsByEventIdAndTicketNumber(eventId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique ticket number after " + MAX_TICKET_NUMBER_ATTEMPTS + " attempts");
    }
}
