package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.GoneException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.PayMongoCustomerService;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.PaymentMethodService;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.gateway.CardSavingUnavailableException;
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
import com.junaldadlawan.event_ticketing_api.gatewayfee.enums.GatewayFeeCardOrigin;
import com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeQuote;
import com.junaldadlawan.event_ticketing_api.gatewayfee.service.GatewayFeeService;
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
import com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeBuyerLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Checkout orchestration (Phase 5b). Kept as its own service/module rather
 * than folded into {@code CartServiceImpl} given how correctness-sensitive
 * this is (money, idempotency, inventory finalization) - isolating it makes
 * the transaction/idempotency boundaries easier to reason about and review
 * in one place. See {@link CheckoutIdempotencyKeyManager} for why the
 * idempotency-key claim/release steps live on a separate bean.
 */
@Slf4j
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
    private final GatewayFeeService gatewayFeeService;
    private final PayMongoCustomerService payMongoCustomerService;
    private final PaymentMethodService paymentMethodService;
    private final CardVaultingStatus cardVaultingStatus;
    private final TicketTypeBuyerLimit ticketTypeBuyerLimit;
    private final com.junaldadlawan.event_ticketing_api.analytics.service.CartActivityRecorder cartActivityRecorder;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional(noRollbackFor = GoneException.class)
    public OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken, String customerId, List<UUID> itemIds) {
        Set<UUID> selection = toSelection(itemIds);
        String selectionKey = selectionKey(selection);
        UUID buyerId = accessGuard.currentUserId();

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));
        // Same one-line buyer-only check as CartServiceImpl.requireOwnerBuyer
        // (replicated rather than reused: it's a one-liner, and CartService
        // doesn't expose the Cart entity itself to reuse it as a helper).
        if (!buyerId.equals(cart.getBuyerId())) {
            throw new ForbiddenException("Only the cart's own buyer may check out this cart");
        }

        CheckoutIdempotencyKeyManager.ClaimOutcome claim = idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, selectionKey);
        if (!claim.freshlyClaimed()) {
            CheckoutIdempotencyKey existing = claim.key();
            // Defense against key reuse across users: this idempotency key
            // was already claimed by someone else.
            if (!existing.getBuyerId().equals(buyerId)) {
                throw new ForbiddenException("This idempotency key was issued by a different buyer");
            }
            requireSameSelection(existing, selectionKey);
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
            return doCheckout(cart, buyerId, paymentMethodToken, customerId, idempotencyKey, selection);
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
                         PlatformFeeQuote fee, GatewayFeeQuote gatewayFee, Money total, boolean partial) {
    }

    private OrderResponse doCheckout(Cart cart, UUID buyerId, String paymentMethodToken, String customerId, UUID idempotencyKey,
                                     Set<UUID> selection) {
        Quote quote = prepare(cart, buyerId, false, selection);
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

        return fulfill(quote, buyerId, result.gatewayRef(), idempotencyKey, null);
    }

    /**
     * Locks the cart and checks everything that must hold before money moves: items, expired holds, paused ticket types, promo
     * usage, and the price (tickets minus promo plus the platform fee). Throws without charging when something is wrong.
     */
    private Quote prepare(Cart cart, UUID buyerId, boolean onBehalfOfBuyer, Set<UUID> selection) {
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
        if (selection != null) {
            // only the selected items are priced, held to their expiry and sold; the rest of the cart is left exactly as it is
            if (!items.stream().map(CartItem::getId).collect(Collectors.toSet()).containsAll(selection)) {
                throw new BadRequestException("A selected item is not in this cart");
            }
            items = items.stream().filter(item -> selection.contains(item.getId())).toList();
        }
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
            // BR-TICKET-LIMIT-001: checked again here, as other orders may have been paid since these were added to the cart
            int checkingOut = items.stream().filter(item -> item.getTicketTypeId().equals(heldType.getId()))
                    .mapToInt(CartItem::getQuantity).sum();
            ticketTypeBuyerLimit.require(heldType, lockedCart.getBuyerId(), checkingOut, 0);
        }

        // Reuse CartService.get()'s already-computed amounts (subtotal minus any applied promo-code discount, plus the
        // platform fee) instead of duplicating that pricing/discount math here. The ticket total is the cart total
        // without the fee; the fee itself is quoted again below, next to the event, so the order can snapshot the rule.
        // the payment webhook has no logged-in user, so it reads the cart for the buyer who started the checkout
        List<UUID> selectedIds = selection == null ? null : List.copyOf(selection);
        CartResponse cartView = selection == null
                ? (onBehalfOfBuyer ? cartService.getForBuyer(cartId, buyerId) : cartService.get(cartId))
                : (onBehalfOfBuyer ? cartService.getForBuyer(cartId, buyerId, selectedIds) : cartService.get(cartId, selectedIds));
        MoneyDto totalDto = cartView.total();
        String promoCode = cartView.appliedPromoCode() != null ? cartView.appliedPromoCode().code() : null;
        long ticketTotal = totalDto.amount() - cartView.platformFee().amount() - cartView.gatewayFee().amount();

        // BR-PROMO-006 (code-reviewer HIGH finding): re-validate the applied
        // promo code's usage limits immediately before charging, not just at
        // applyPromoCode() time - other buyers may have exhausted the code's
        // usageLimitTotal/usageLimitPerBuyer in the window between this
        // buyer's applyPromoCode() call and this checkout() call.
        // (priced for a selection, the cart's code counts only when it fits those items: cartView then shows it, otherwise it stays on the cart)
        if (selection == null ? lockedCart.getPromoCodeId() != null : cartView.appliedPromoCode() != null) {
            PromoCode appliedPromoCode = promoCodeRepository.findById(lockedCart.getPromoCodeId()).orElse(null);
            // Paused or out of its window since it was applied: refuse before charging. The cart still shows the code
            // (discount 0), so the message names it and says why - the buyer removes it, or retries once it is resumed.
            if (appliedPromoCode == null || appliedPromoCode.getDeletedAt() != null) {
                throw new UnprocessableEntityException("Promo code is invalid or inapplicable to this cart");
            }
            if (appliedPromoCode.isPaused()) {
                throw new UnprocessableEntityException("Promo code " + appliedPromoCode.getCode()
                        + " is paused. Remove it from your cart to check out.");
            }
            if (!appliedPromoCode.isValidAt(Instant.now())) {
                throw new UnprocessableEntityException("Promo code " + appliedPromoCode.getCode()
                        + " is not valid right now. Remove it from your cart to check out.");
            }
            promoCodeUsageLimitGuard.checkUsageLimits(appliedPromoCode, buyerId);
        }

        // Also resolved here (rather than after payment) so the same lookup
        // can be reused below for ticket issuance without an extra query.
        Event event = resolveEvent(items);
        // BR-EVENT-005: refused before charging; in the payment webhook this throws, and the payment is refunded
        EventCompletionService.requireNotEnded(event);
        UUID payeeId = event.getOrganizationId();

        // The platform fee is added on top of the ticket price: the buyer is charged ticket total + fee, the organizer
        // keeps the ticket total (the fee is deducted from the organizer's side when the payout is generated).
        int ticketCount = items.stream().mapToInt(CartItem::getQuantity).sum();
        PlatformFeeQuote fee = platformFeeService.quote(payeeId, event.getId(), ticketTotal, totalDto.currency(), ticketCount);
        // The payment gateway's fee is charged to the buyer only when management made the customer bear it; otherwise it is kept on the
        // order for the payout (organization) or absorbed (platform). It is worked out on ticket + platform fee, like the cart shows it.
        GatewayFeeQuote gatewayFee = gatewayFeeService.quote(ticketTotal + fee.chargedToBuyer(), totalDto.currency());
        Money total = Money.builder().amount(ticketTotal + fee.chargedToBuyer() + gatewayFee.chargedToBuyer()).currency(totalDto.currency()).build();

        return new Quote(lockedCart, items, event, payeeId, promoCode, fee, gatewayFee, total, selection != null);
    }

    /** DOMESTIC for a card issued in the Philippines, FOREIGN for one issued anywhere else, null when the gateway did not say. */
    private static GatewayFeeCardOrigin cardOrigin(String cardCountry) {
        if (cardCountry == null || cardCountry.isBlank()) {
            return null;
        }
        return "PH".equalsIgnoreCase(cardCountry.trim()) ? GatewayFeeCardOrigin.DOMESTIC : GatewayFeeCardOrigin.FOREIGN;
    }

    /** Issues the order, payment record and tickets for a payment that succeeded, and converts the holds into sold seats. */
    private OrderResponse fulfill(Quote quote, UUID buyerId, String gatewayRef, UUID idempotencyKey, String cardCountry) {
        Cart lockedCart = quote.lockedCart();
        UUID cartId = lockedCart.getId();
        List<CartItem> items = quote.items();
        Event event = quote.event();
        UUID payeeId = quote.payeeId();
        String promoCode = quote.promoCode();
        PlatformFeeQuote fee = quote.fee();
        GatewayFeeQuote gatewayFee = quote.gatewayFee();
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
                .platformFeePayer(fee.payer())
                .gatewayFeeAmount(gatewayFee.amount())
                .gatewayFeePayer(gatewayFee.payer())
                .gatewayFeePercentage(gatewayFee.percentage())
                .gatewayFeeFlatAmount(gatewayFee.flatAmount())
                .gatewayFeeCardOrigin(cardOrigin(cardCountry))
                .gatewayFeeAppliedAmount(cardOrigin(cardCountry) == GatewayFeeCardOrigin.FOREIGN ? gatewayFee.foreignAmount() : gatewayFee.amount())
                .gatewayFeeForeignPercentage(gatewayFee.foreignPercentage())
                .gatewayFeeForeignFlatAmount(gatewayFee.foreignFlatAmount())
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
            // the order is PAID (synchronous checkout or the payment webhook): the line ends as a sale in the funnel log
            cartActivityRecorder.record(item, item.getQuantity(),
                    com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository.CartAction.CHECKOUT);
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

        // a code that did not fit the selection stays on the cart for the rest
        if (!quote.partial() || promoCode != null) {
            lockedCart.setPromoCodeId(null);
            cartRepository.save(lockedCart);
        }

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
    public CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey, List<UUID> itemIds, boolean saveCard) {
        Set<UUID> selection = toSelection(itemIds);
        String selectionKey = selectionKey(selection);
        UUID buyerId = accessGuard.currentUserId();
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));
        if (!buyerId.equals(cart.getBuyerId())) {
            throw new ForbiddenException("Only the cart's own buyer may check out this cart");
        }

        CheckoutIdempotencyKeyManager.ClaimOutcome claim = idempotencyKeyManager.claim(idempotencyKey, buyerId, cartId, selectionKey);
        if (!claim.freshlyClaimed()) {
            if (!claim.key().getBuyerId().equals(buyerId)) {
                throw new ForbiddenException("This idempotency key was issued by a different buyer");
            }
            requireSameSelection(claim.key(), selectionKey);
            // A repeated start with the same key returns the checkout it already created (still waiting, or already paid).
            return pendingCheckoutRepository.findFirstByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey)
                    .map(CheckoutStartResponse::from)
                    .orElseThrow(() -> new ConflictException("Checkout already in progress for this idempotency key"));
        }

        try {
            Quote quote = prepare(cart, buyerId, false, selection);
            // Saving the card is secondary: if the PayMongo customer cannot be made, the buyer still pays, the card is just not saved
            String saveForCustomer = null;
            if (saveCard && cardVaultingStatus.unavailable()) {
                log.info("The card will not be saved for user {}: PayMongo refused card vaulting a moment ago", buyerId);
            } else if (saveCard) {
                try {
                    saveForCustomer = payMongoCustomerService.getOrCreate(buyerId);
                } catch (RuntimeException e) {
                    log.warn("The card will not be saved for user {}: the PayMongo customer could not be created: {}", buyerId, e.toString());
                }
            }
            PaymentGatewayClient.PaymentIntentInfo intent = null;
            if (saveForCustomer != null) {
                try {
                    intent = paymentGatewayClient.createPaymentIntent(quote.total(), "Event ticket order", "checkout-" + idempotencyKey, saveForCustomer);
                } catch (CardSavingUnavailableException e) {
                    // only this refusal falls back: any other 4xx or a 5xx still fails the checkout as before
                    log.warn("PayMongo refused to save the card; the payment goes ahead without saving it: {}", e.getMessage());
                    cardVaultingStatus.markUnavailable();
                    saveForCustomer = null;
                }
            }
            if (intent == null) {
                intent = paymentGatewayClient.createPaymentIntent(quote.total(), "Event ticket order", "checkout-" + idempotencyKey);
            }
            String cardSaving = !saveCard ? "NOT_REQUESTED" : saveForCustomer != null ? "REQUESTED" : "UNAVAILABLE";
            PendingCheckout pending = pendingCheckoutRepository.save(PendingCheckout.builder()
                    .cartId(cartId)
                    .buyerId(buyerId)
                    .idempotencyKey(idempotencyKey)
                    .providerIntentId(intent.intentId())
                    .clientKey(intent.clientKey())
                    .amount(quote.total().getAmount())
                    .currency(quote.total().getCurrency())
                    .itemIds(selectionKey)
                    .saveCard(saveForCustomer != null)
                    .cardSaving(cardSaving)
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
    public void onPaymentPaid(String providerIntentId, String providerPaymentId, long amount, String currency, String cardCountry) {
        Money paid = Money.builder().amount(amount).currency(currency.toUpperCase()).build();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String problem = null;
        try {
            tx.executeWithoutResult(status -> issueOrder(providerIntentId, providerPaymentId, paid, cardCountry));
        } catch (ApiException e) {
            problem = e.getMessage() != null ? e.getMessage() : "The order could not be issued";
        }
        if (problem != null) {
            String reason = problem;
            tx.executeWithoutResult(status -> refundUnissuable(providerIntentId, providerPaymentId, paid, reason));
        } else {
            rememberVaultedCard(providerIntentId);
        }
    }

    /** "Save this card": the payment went through, so read what PayMongo vaulted and keep it. Best effort: it never undoes the order. */
    private void rememberVaultedCard(String providerIntentId) {
        try {
            PendingCheckout pending = pendingCheckoutRepository.findByProviderIntentId(providerIntentId).orElse(null);
            if (pending == null) {
                log.info("Save card after payment {}: no pending checkout found", providerIntentId);
                return;
            }
            log.info("Save card after payment {}: checkout={} saveCard={} status={}", providerIntentId, pending.getId(), pending.isSaveCard(), pending.getStatus());
            if (!pending.isSaveCard() || pending.getStatus() != PendingCheckoutStatus.PAID) {
                return;
            }
            String customerId = payMongoCustomerService.getOrCreate(pending.getBuyerId());
            int saved = paymentMethodService.saveVaultedCards(pending.getBuyerId(), customerId);
            log.info("Save card after payment {}: paymongoCustomer={} cardsSaved={}", providerIntentId, customerId, saved);
        } catch (RuntimeException e) {
            log.warn("The card paid with could not be saved for later (payment {}): {}", providerIntentId, e.toString());
        }
    }

    @Override
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public CheckoutStartResponse attachSavedCard(UUID checkoutId, String paymentMethodId, String securityCode, String returnUrl) {
        PendingCheckout pending = pendingCheckoutRepository.findByIdAndBuyerIdForUpdate(checkoutId, accessGuard.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkout " + checkoutId + " not found"));
        if (pending.getStatus() != PendingCheckoutStatus.PENDING || pending.getAttachedAt() != null) {
            return CheckoutStartResponse.from(pending);   // a repeated call: the card was already attached, never attach twice
        }
        PaymentGatewayClient.AttachOutcome outcome = paymentGatewayClient.attachSavedCard(
                pending.getProviderIntentId(), pending.getClientKey(), paymentMethodId, securityCode, returnUrl);
        if (outcome.failureReason() != null) {
            // nothing was charged and the cart was never touched: the buyer can try again with the same cart
            pending.setStatus(PendingCheckoutStatus.FAILED);
            pending.setFailureReason(truncate(outcome.failureReason()));
            pendingCheckoutRepository.save(pending);
            idempotencyKeyManager.delete(pending.getIdempotencyKey());
            BusinessAuditLogger.recordAs(pending.getBuyerId(), "checkout.payment_failed", "Cart", pending.getCartId(),
                    BusinessAuditLogger.Outcome.FAILURE, pending.getFailureReason());
            throw new PaymentFailedException(outcome.failureReason());
        }
        pending.setAttachedAt(Instant.now());
        pending.setRedirectUrl(outcome.redirectUrl());
        return CheckoutStartResponse.from(pendingCheckoutRepository.save(pending));
    }

    /** Inside the webhook's transaction: issues the order for a paid checkout, or throws an ApiException to roll everything back. */
    private void issueOrder(String providerIntentId, String providerPaymentId, Money paid, String cardCountry) {
        PendingCheckout pending = pendingCheckoutRepository.findByProviderIntentIdForUpdate(providerIntentId).orElse(null);
        if (pending == null || pending.getStatus() != PendingCheckoutStatus.PENDING) {
            return;   // not ours, or a webhook delivered twice: already handled
        }
        Cart cart = cartRepository.findById(pending.getCartId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + pending.getCartId() + " not found"));
        Quote quote = prepare(cart, pending.getBuyerId(), true, parseSelection(pending.getItemIds()));
        if (quote.total().getAmount() != paid.getAmount() || !quote.total().getCurrency().equalsIgnoreCase(paid.getCurrency())) {
            throw new ConflictException("The amount paid does not match the order");
        }
        OrderResponse order = fulfill(quote, pending.getBuyerId(), providerPaymentId, pending.getIdempotencyKey(), cardCountry);
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

    /** null = the whole cart; an empty list is a mistake (it would otherwise charge everything), not "nothing". */
    private static Set<UUID> toSelection(List<UUID> itemIds) {
        if (itemIds == null) {
            return null;
        }
        if (itemIds.isEmpty()) {
            throw new BadRequestException("itemIds must not be empty: leave it out to pay the whole cart");
        }
        return new LinkedHashSet<>(itemIds);
    }

    private static String selectionKey(Set<UUID> selection) {
        return selection == null ? null : selection.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    private static Set<UUID> parseSelection(String key) {
        return key == null ? null : Arrays.stream(key.split(",")).map(UUID::fromString).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void requireSameSelection(CheckoutIdempotencyKey existing, String selectionKey) {
        if (!Objects.equals(existing.getItemIds(), selectionKey)) {
            throw new ConflictException("This idempotency key was already used for a different selection of items");
        }
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
