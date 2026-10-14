package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.GoneException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import com.junaldadlawan.event_ticketing_api.order.entity.PendingCheckout;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.enums.PaymentStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PendingCheckoutStatus;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentGatewayClient;
import com.junaldadlawan.event_ticketing_api.order.gateway.PaymentResult;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PaymentRepository;
import com.junaldadlawan.event_ticketing_api.order.repository.PendingCheckoutRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeQuote;
import com.junaldadlawan.event_ticketing_api.platformfee.service.PlatformFeeService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final NotificationService notificationService;
    private final PromoCodeUsageLimitGuard promoCodeUsageLimitGuard;
    private final TicketRepository ticketRepository;
    private final TicketCredentialService ticketCredentialService;
    private final PlatformFeeService platformFeeService;
    private final PendingCheckoutRepository pendingCheckoutRepository;
    private final PlatformTransactionManager transactionManager;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional(noRollbackFor = GoneException.class)
    public OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken) {
        return checkout(cartId, idempotencyKey, paymentMethodToken, null);
    }

    @Override
    @Transactional(noRollbackFor = GoneException.class)
    public OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken, String customerId) {
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
            return doCheckout(cart, buyerId, paymentMethodToken, customerId, idempotencyKey);
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

    /** Everything a checkout needs to be priced and issued, worked out once from the locked cart. */
    private record Quote(Cart lockedCart, List<CartItem> items, Event event, UUID payeeId, String promoCode,
                         PlatformFeeQuote fee, Money total) {
    }

    private OrderResponse doCheckout(Cart cart, UUID buyerId, String paymentMethodToken, String customerId, UUID idempotencyKey) {
        Quote quote = prepare(cart, buyerId, false);
        Money total = quote.total();

        // the checkout's own idempotency key goes to the gateway too, so a retried checkout cannot charge twice
        PaymentResult result = paymentGatewayClient.charge(paymentMethodToken, customerId, total, "checkout-" + idempotencyKey);
        if (!result.successful()) {
            // BR-CART-002: no Order/Payment row is ever persisted on this
            // path. Cart and holds remain fully intact - nothing above this
            // point touched CartItem/TicketType/Seat state.
            BusinessAuditLogger.recordAs(buyerId, "checkout.payment_failed", "Cart", cart.getId(),
                    BusinessAuditLogger.Outcome.FAILURE, result.failureReason());
            throw new PaymentFailedException(
                    result.failureReason() != null ? result.failureReason() : "Payment failed");
        }

        return fulfill(quote, buyerId, result.gatewayRef(), idempotencyKey);
    }

    /**
     * Locks the cart and checks everything that must hold before money moves: items, expired holds, paused ticket types, promo
     * usage, and the price (tickets minus promo plus the platform fee). Throws without charging when something is wrong.
     */
    private Quote prepare(Cart cart, UUID buyerId, boolean onBehalfOfBuyer) {
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

        // The organizer may have paused a ticket type AFTER it was added to this cart (adding is refused while
        // paused, but a cart that already holds it must not slip through). Checked before anything is charged;
        // the cart and its holds stay as they are (they expire on their normal timer) and the buyer can retry
        // once sales resume - the catch in checkout() frees the idempotency key for exactly that.
        List<UUID> cartTicketTypeIds = items.stream().map(CartItem::getTicketTypeId).distinct().toList();
        for (TicketType heldType : ticketTypeRepository.findAllById(cartTicketTypeIds)) {
            if (heldType.isSalesPaused()) {
                throw new ConflictException("Sales for ticket type '" + heldType.getName() + "' are paused");
            }
        }

        // Reuse CartService.get()'s already-computed amounts (subtotal minus any applied promo-code discount, plus the
        // platform fee) instead of duplicating that pricing/discount math here. The ticket total is the cart total
        // without the fee; the fee itself is quoted again below, next to the event, so the order can snapshot the rule.
        // the payment webhook has no logged-in user, so it reads the cart for the buyer who started the checkout
        CartResponse cartView = onBehalfOfBuyer ? cartService.getForBuyer(cartId, buyerId) : cartService.get(cartId);
        MoneyDto totalDto = cartView.total();
        String promoCode = cartView.appliedPromoCode() != null ? cartView.appliedPromoCode().code() : null;
        long ticketTotal = totalDto.amount() - cartView.platformFee().amount();

        // BR-PROMO-006 (code-reviewer HIGH finding): re-validate the applied
        // promo code's usage limits immediately before charging, not just at
        // applyPromoCode() time - other buyers may have exhausted the code's
        // usageLimitTotal/usageLimitPerBuyer in the window between this
        // buyer's applyPromoCode() call and this checkout() call.
        if (lockedCart.getPromoCodeId() != null) {
            PromoCode appliedPromoCode = promoCodeRepository.findById(lockedCart.getPromoCodeId()).orElse(null);
            // Paused or deleted since it was applied: refuse before charging (the buyer can remove the code, or retry
            // once it is resumed). Same message as an unknown code - nothing is revealed about why.
            if (appliedPromoCode == null || !appliedPromoCode.isRedeemable()) {
                throw new UnprocessableEntityException("Promo code is invalid or inapplicable to this cart");
            }
            promoCodeUsageLimitGuard.checkUsageLimits(appliedPromoCode, buyerId);
        }

        // Also resolved here (rather than after payment) so the same lookup
        // can be reused below for ticket issuance without an extra query.
        Event event = resolveEvent(items);
        UUID payeeId = event.getOrganizationId();

        // The platform fee is added on top of the ticket price: the buyer is charged ticket total + fee, the organizer
        // keeps the ticket total (the fee is deducted from the organizer's side when the payout is generated).
        PlatformFeeQuote fee = platformFeeService.quote(payeeId, event.getId(), ticketTotal, totalDto.currency());
        Money total = Money.builder().amount(ticketTotal + fee.amount()).currency(totalDto.currency()).build();

        return new Quote(lockedCart, items, event, payeeId, promoCode, fee, total);
    }

    /** Issues the order, payment record and tickets for a payment that succeeded, and converts the holds into sold seats. */
    private OrderResponse fulfill(Quote quote, UUID buyerId, String gatewayRef, UUID idempotencyKey) {
        Cart lockedCart = quote.lockedCart();
        UUID cartId = lockedCart.getId();
        List<CartItem> items = quote.items();
        Event event = quote.event();
        UUID payeeId = quote.payeeId();
        String promoCode = quote.promoCode();
        PlatformFeeQuote fee = quote.fee();
        Money total = quote.total();

        Order order = Order.builder()
                .buyerId(buyerId)
                .payeeType(PayeeType.ORGANIZATION)
                .payeeId(payeeId)
                .status(OrderStatus.PAID)
                .promoCode(promoCode)
                .platformFeeAmount(fee.amount())
                .platformFeeScope(fee.scope())
                .platformFeeType(fee.type())
                .platformFeePercentage(fee.percentage())
                .platformFeeFlatAmount(fee.flatAmount())
                .total(total)
                .cartId(cartId)
                .createdBy(buyerId.toString())
                .build();
        final Order savedOrder = orderRepository.saveAndFlush(order);

        Payment payment = Payment.builder()
                .orderId(savedOrder.getId())
                .gatewayRef(gatewayRef)
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

        // BR-NOTIFY-001 (Phase 11). notificationService.notify never throws
        // (NFR 5.2) so this can't fail an otherwise-successful checkout.
        notificationService.notify(buyerId, NotificationType.ORDER_CONFIRMATION, "Order", savedOrder.getId());
        notificationService.notify(buyerId, NotificationType.PAYMENT_RECEIPT, "Order", savedOrder.getId());

        BusinessAuditLogger.recordAs(buyerId, "checkout.completed", "Order", savedOrder.getId(),
                BusinessAuditLogger.Outcome.SUCCESS,
                "total=" + total.getAmount() + " " + total.getCurrency() + " platformFee=" + fee.amount()
                        + " tickets=" + issuedTickets.size());

        return OrderResponse.from(savedOrder, issuedTickets);
    }

    // ---- checkouts confirmed by the payment provider's webhook (PayMongo): the order is issued only once the payment succeeded ----

    @Override
    public boolean isAsynchronous() {
        return paymentGatewayClient.confirmsByWebhook();
    }

    @Override
    @Transactional(noRollbackFor = GoneException.class)
    public CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey) {
        UUID buyerId = accessGuard.currentUserId();
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));
        if (!buyerId.equals(cart.getBuyerId())) {
            throw new ForbiddenException("Only the cart's own buyer may check out this cart");
        }

        CheckoutIdempotencyKeyManager.ClaimOutcome claim = idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId);
        if (!claim.freshlyClaimed()) {
            if (!claim.key().getBuyerId().equals(buyerId)) {
                throw new ForbiddenException("This idempotency key was issued by a different buyer");
            }
            // A repeated start with the same key returns the checkout it already created (still waiting, or already paid).
            return pendingCheckoutRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
                    .map(CheckoutStartResponse::from)
                    .orElseThrow(() -> new ConflictException("Checkout already in progress for this idempotency key"));
        }

        try {
            Quote quote = prepare(cart, buyerId, false);
            PaymentGatewayClient.PaymentIntentInfo intent = paymentGatewayClient.createPaymentIntent(
                    quote.total(), "Event ticket order", "checkout-" + idempotencyKey);
            PendingCheckout pending = pendingCheckoutRepository.save(PendingCheckout.builder()
                    .cartId(cartId)
                    .buyerId(buyerId)
                    .idempotencyKey(idempotencyKey)
                    .providerIntentId(intent.intentId())
                    .clientKey(intent.clientKey())
                    .amount(quote.total().getAmount())
                    .currency(quote.total().getCurrency())
                    .status(PendingCheckoutStatus.PENDING)
                    .build());
            BusinessAuditLogger.recordAs(buyerId, "checkout.started", "Cart", cartId, BusinessAuditLogger.Outcome.SUCCESS,
                    "total=" + pending.getAmount() + " " + pending.getCurrency());
            return CheckoutStartResponse.from(pending);
        } catch (RuntimeException ex) {
            idempotencyKeyManager.delete(idempotencyKey);
            throw ex;
        }
    }

    /**
     * Webhook: the payment succeeded. The order is issued in its own transaction; if it cannot be (the hold expired while the buyer
     * was paying, the amount differs, ...) that transaction is rolled back cleanly and the payment is refunded in a second one. A
     * technical failure (database down) is NOT treated as "cannot issue": it propagates, the webhook answers 500 and PayMongo retries.
     */
    @Override
    public void onPaymentPaid(String providerIntentId, String providerPaymentId, long amount, String currency) {
        Money paid = Money.builder().amount(amount).currency(currency.toUpperCase()).build();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String problem = null;
        try {
            tx.executeWithoutResult(status -> issueOrder(providerIntentId, providerPaymentId, paid));
        } catch (ApiException e) {
            problem = e.getMessage() != null ? e.getMessage() : "The order could not be issued";
        }
        if (problem != null) {
            String reason = problem;
            tx.executeWithoutResult(status -> refundUnissuable(providerIntentId, providerPaymentId, paid, reason));
        }
    }

    /** Inside the webhook's transaction: issues the order for a paid checkout, or throws an ApiException to roll everything back. */
    private void issueOrder(String providerIntentId, String providerPaymentId, Money paid) {
        PendingCheckout pending = pendingCheckoutRepository.findByProviderIntentIdForUpdate(providerIntentId).orElse(null);
        if (pending == null || pending.getStatus() != PendingCheckoutStatus.PENDING) {
            return;   // not ours, or a webhook delivered twice: already handled
        }
        Cart cart = cartRepository.findById(pending.getCartId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + pending.getCartId() + " not found"));
        Quote quote = prepare(cart, pending.getBuyerId(), true);
        if (quote.total().getAmount() != paid.getAmount() || !quote.total().getCurrency().equalsIgnoreCase(paid.getCurrency())) {
            throw new ConflictException("The amount paid does not match the order");
        }
        OrderResponse order = fulfill(quote, pending.getBuyerId(), providerPaymentId, pending.getIdempotencyKey());
        pending.setStatus(PendingCheckoutStatus.PAID);
        pending.setOrderId(order.id());
        pendingCheckoutRepository.save(pending);
    }

    /** The buyer paid but the order cannot be issued: give the money back and let them start again. */
    private void refundUnissuable(String providerIntentId, String providerPaymentId, Money paid, String problem) {
        PendingCheckout pending = pendingCheckoutRepository.findByProviderIntentIdForUpdate(providerIntentId).orElse(null);
        if (pending == null || pending.getStatus() != PendingCheckoutStatus.PENDING) {
            return;
        }
        PaymentResult refund = paymentGatewayClient.refund(providerPaymentId, paid);
        pending.setStatus(PendingCheckoutStatus.REFUNDED);
        pending.setFailureReason(truncate(problem + (refund.successful() ? "" : " (the refund FAILED and needs manual attention)")));
        pendingCheckoutRepository.save(pending);
        idempotencyKeyManager.delete(pending.getIdempotencyKey());
        BusinessAuditLogger.recordAs(pending.getBuyerId(), "checkout.paid_but_refunded", "Cart", pending.getCartId(),
                BusinessAuditLogger.Outcome.FAILURE, problem + (refund.successful() ? " (refunded)" : " (REFUND FAILED: needs manual attention)"));
    }

    @Override
    @Transactional
    public void onPaymentFailed(String providerIntentId, String reason) {
        PendingCheckout pending = pendingCheckoutRepository.findByProviderIntentIdForUpdate(providerIntentId).orElse(null);
        if (pending == null || pending.getStatus() != PendingCheckoutStatus.PENDING) {
            return;
        }
        pending.setStatus(PendingCheckoutStatus.FAILED);
        pending.setFailureReason(truncate(reason != null && !reason.isBlank() ? reason : "The payment failed"));
        pendingCheckoutRepository.save(pending);
        // nothing was charged and the cart is untouched: free the key so the buyer can try again
        idempotencyKeyManager.delete(pending.getIdempotencyKey());
        BusinessAuditLogger.recordAs(pending.getBuyerId(), "checkout.payment_failed", "Cart", pending.getCartId(),
                BusinessAuditLogger.Outcome.FAILURE, pending.getFailureReason());
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutStartResponse getStatus(UUID checkoutId) {
        return pendingCheckoutRepository.findByIdAndBuyerId(checkoutId, accessGuard.currentUserId())
                .map(CheckoutStartResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout " + checkoutId + " not found"));
    }

    private static String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500);
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
        // Snapshotted at issuance (Phase 7, code-reviewer MEDIUM) so a later
        // resale's price cap (BR-TRANSFER-004) is anchored to what this
        // buyer actually paid, not whatever the ticket type's price happens
        // to be at resale time.
        Money faceValue = ticketTypeRepository.findById(ticketTypeId)
                .map(TicketType::getPrice)
                .map(price -> Money.builder().amount(price.getAmount()).currency(price.getCurrency()).build())
                .orElse(null);
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .orderId(order.getId())
                .eventId(event.getId())
                .ticketTypeId(ticketTypeId)
                .seatId(seatId)
                .ownerId(ownerId)
                .ticketNumber(ticketNumber)
                .faceValue(faceValue)
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
