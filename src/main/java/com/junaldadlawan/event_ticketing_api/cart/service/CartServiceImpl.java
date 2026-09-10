package com.junaldadlawan.event_ticketing_api.cart.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.AppliedPromoCodeResponse;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemResponse;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartItemRepository;
import com.junaldadlawan.event_ticketing_api.cart.repository.CartRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import com.junaldadlawan.event_ticketing_api.promocode.repository.PromoCodeRepository;
import com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeUsageLimitGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.enums.TicketTypeKind;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    // BR-INV-003 says "default 10-15 minutes"; 15 chosen and kept as a
    // named constant (used exactly once, at hold-placement time) rather
    // than hardcoded in multiple places, so it can become configurable
    // later without a shotgun edit.
    private static final Duration HOLD_DURATION = Duration.ofMinutes(15);

    // Allow-list (not deny-list) of EventStatus values whose ticket types are
    // purchasable, so a future new status doesn't silently become
    // purchasable by omission. DRAFT/CANCELLED/COMPLETED are excluded.
    private static final Set<EventStatus> PURCHASABLE_EVENT_STATUSES =
            Set.of(EventStatus.PUBLISHED, EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;
    private final EventRepository eventRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUsageLimitGuard promoCodeUsageLimitGuard;
    private final OrganizationAccessGuard accessGuard;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public CartResponse create() {
        Cart cart = Cart.builder()
                .buyerId(accessGuard.currentUserId())
                .build();
        // saveAndFlush (not save): @CreationTimestamp/@UpdateTimestamp are
        // only populated onto the in-memory entity when the INSERT actually
        // executes. Since this whole method runs in one transaction, a
        // plain save() would defer that INSERT to commit time - after
        // toResponse() below has already read the (still-null) timestamps.
        cart = cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse get(UUID cartId) {
        Cart cart = getCartOrThrow(cartId);
        requireOwnerBuyer(cart);
        // Read-only from the caller's point of view (no new holds placed),
        // but still performs the same release side effects as any other
        // contention point: a buyer revisiting their own stale cart should
        // see accurate state, not silently-expired-but-not-yet-cleaned-up
        // holds.
        releaseExpiredHoldsForCart(cart.getId());
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(UUID cartId, CartItemCreateRequest request) {
        Cart cart = getCartOrThrow(cartId);
        requireOwnerBuyer(cart);

        TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(request.ticketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + request.ticketTypeId() + " not found"));
        Event event = eventRepository.findByIdAndDeletedAtIsNull(ticketType.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + ticketType.getEventId() + " not found"));

        // Purchasability check, not visibility: only an event that's actually
        // on sale is purchasable. Allow-listed (rather than DRAFT/CANCELLED/
        // COMPLETED deny-listed) so a future new EventStatus value doesn't
        // silently become purchasable by omission.
        if (!PURCHASABLE_EVENT_STATUSES.contains(event.getStatus())) {
            throw new ConflictException("Ticket type is not currently on sale");
        }

        Instant now = Instant.now();
        if (now.isBefore(ticketType.getSaleStartAt()) || now.isAfter(ticketType.getSaleEndAt())) {
            throw new ConflictException("Ticket type is not currently on sale");
        }

        // A cart is constrained to items from a single event at a time: a
        // PromoCode is event-scoped (see applyPromoCode below) and the
        // upcoming checkout dispatch's Order has a single payeeId, so a cart
        // spanning two events (or organizations) has no coherent single
        // total/payee. An empty cart may add an item from any event, which
        // implicitly sets the cart's event for subsequent adds.
        List<CartItem> existingItems = cartItemRepository.findByCartId(cart.getId());
        if (!existingItems.isEmpty()) {
            UUID cartEventId = existingItems.stream()
                    .map(CartItem::getTicketTypeId)
                    .map(id -> ticketTypeRepository.findById(id).map(TicketType::getEventId).orElse(null))
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
            if (cartEventId != null && !cartEventId.equals(ticketType.getEventId())) {
                throw new BadRequestException("Cart already contains items from a different event");
            }
        }

        if (ticketType.getKind() == TicketTypeKind.GENERAL_ADMISSION) {
            addGaItem(cart, ticketType, request, now);
        } else {
            addReservedSeatItem(cart, ticketType, request, now);
        }

        return toResponse(cart);
    }

    private void addGaItem(Cart cart, TicketType ticketTypeRef, CartItemCreateRequest request, Instant now) {
        int quantity = request.quantity() != null ? request.quantity() : 1;

        // Lock the TicketType row first: this is what actually prevents two
        // concurrent requests from both succeeding against the same
        // shrinking pool (BR-INV-005/006, BR-NFR-001). The release-then-check
        // step below happens under this same lock, in this same transaction.
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(ticketTypeRef.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticketTypeRef.getId() + " not found"));
        // Bug fix (found during Phase 5a concurrency verification): addItem()
        // already loaded this same TicketType row (unlocked) a few lines up
        // to check event status/sale window. Because Hibernate's persistence
        // context is an identity map, the findByIdForUpdate() call above
        // *does* issue a real "select ... for no key update" and *does*
        // block on the DB-held row lock - but since an entity with this same
        // id is already managed in this transaction, Hibernate returns that
        // same (now-stale) Java instance without refreshing its fields from
        // the just-executed locked query. Without this refresh(), two
        // concurrent requests for the last unit of a GA ticket type could
        // both observe the pre-lock quantityAvailable and both succeed
        // (confirmed by concurrent-POST testing: two 201s, final
        // quantityAvailable clamped at 0 instead of one 409). refresh()
        // forces the managed instance's fields to be reloaded from the DB
        // now that the lock is actually held, so the availability check
        // below sees genuinely current data.
        entityManager.refresh(ticketType);

        releaseExpiredGaHolds(ticketType, now);

        if (ticketType.getQuantityAvailable() < quantity) {
            throw new ConflictException("Seat or GA quantity no longer available");
        }
        ticketType.setQuantityAvailable(ticketType.getQuantityAvailable() - quantity);
        ticketTypeRepository.save(ticketType);

        CartItem item = CartItem.builder()
                .cartId(cart.getId())
                .ticketTypeId(ticketType.getId())
                .seatId(null)
                .quantity(quantity)
                .holdExpiresAt(now.plus(HOLD_DURATION))
                .build();
        cartItemRepository.save(item);
    }

    private void releaseExpiredGaHolds(TicketType lockedTicketType, Instant now) {
        List<CartItem> expired = cartItemRepository.findByTicketTypeIdAndHoldExpiresAtBefore(lockedTicketType.getId(), now);
        for (CartItem item : expired) {
            lockedTicketType.setQuantityAvailable(lockedTicketType.getQuantityAvailable() + item.getQuantity());
            cartItemRepository.delete(item);
        }
    }

    private void addReservedSeatItem(Cart cart, TicketType ticketType, CartItemCreateRequest request, Instant now) {
        if (request.seatId() == null) {
            throw new BadRequestException("seatId is required for reserved-seating ticket types");
        }

        // Lock the Seat row first, same reasoning as the GA path above.
        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat " + request.seatId() + " not found"));

        SeatMap seatMap = seatMapRepository.findById(seat.getSeatMapId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat map for seat " + seat.getId() + " not found"));
        if (!seatMap.getEventId().equals(ticketType.getEventId())) {
            throw new BadRequestException("Seat does not belong to this ticket type's event");
        }

        // Release a stale hold specifically blocking this seat, if any,
        // before evaluating availability.
        cartItemRepository.findByTicketTypeIdAndHoldExpiresAtBefore(ticketType.getId(), now).stream()
                .filter(item -> seat.getId().equals(item.getSeatId()))
                .findFirst()
                .ifPresent(staleItem -> {
                    cartItemRepository.delete(staleItem);
                    seat.setStatus(SeatStatus.AVAILABLE);
                });

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new ConflictException("Seat or GA quantity no longer available");
        }
        seat.setStatus(SeatStatus.HELD);
        seatRepository.save(seat);

        CartItem item = CartItem.builder()
                .cartId(cart.getId())
                .ticketTypeId(ticketType.getId())
                .seatId(seat.getId())
                .quantity(1)
                .holdExpiresAt(now.plus(HOLD_DURATION))
                .build();
        cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public void removeItem(UUID cartId, UUID itemId) {
        Cart cart = getCartOrThrow(cartId);
        requireOwnerBuyer(cart);

        CartItem item = cartItemRepository.findById(itemId)
                .filter(ci -> ci.getCartId().equals(cartId))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item " + itemId + " not found"));

        releaseHold(item);
        cartItemRepository.delete(item);
    }

    /** Same release logic used by expired-hold reclaim, for explicit removal. */
    private void releaseHold(CartItem item) {
        if (item.getSeatId() == null) {
            ticketTypeRepository.findByIdForUpdate(item.getTicketTypeId()).ifPresent(ticketType -> {
                ticketType.setQuantityAvailable(ticketType.getQuantityAvailable() + item.getQuantity());
                ticketTypeRepository.save(ticketType);
            });
        } else {
            seatRepository.findByIdForUpdate(item.getSeatId()).ifPresent(seat -> {
                seat.setStatus(SeatStatus.AVAILABLE);
                seatRepository.save(seat);
            });
        }
    }

    @Override
    @Transactional
    public void releaseExpiredHoldsForCart(UUID cartId) {
        Instant now = Instant.now();
        for (CartItem item : cartItemRepository.findByCartId(cartId)) {
            if (item.getHoldExpiresAt().isBefore(now)) {
                releaseHold(item);
                cartItemRepository.delete(item);
            }
        }
    }

    @Override
    @Transactional
    public CartResponse applyPromoCode(UUID cartId, String code) {
        Cart cart = getCartOrThrow(cartId);
        requireOwnerBuyer(cart);

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        if (items.isEmpty()) {
            throw new UnprocessableEntityException("Cart is empty; a promo code needs at least one item to apply to");
        }

        Set<UUID> ticketTypeIds = items.stream().map(CartItem::getTicketTypeId).collect(Collectors.toSet());
        Set<UUID> eventIds = new HashSet<>();
        for (UUID ticketTypeId : ticketTypeIds) {
            ticketTypeRepository.findById(ticketTypeId).ifPresent(tt -> eventIds.add(tt.getEventId()));
        }
        // A PromoCode is scoped to one eventId; a cart spanning more than
        // one event's ticket types has no single event to match it against.
        if (eventIds.size() != 1) {
            throw new UnprocessableEntityException("Promo code is inapplicable to the items in this cart");
        }
        UUID eventId = eventIds.iterator().next();

        PromoCode promoCode = promoCodeRepository.findByEventIdAndCodeAndDeletedAtIsNull(eventId, code)
                .orElseThrow(() -> new UnprocessableEntityException("Promo code is invalid or inapplicable to this cart"));

        Instant now = Instant.now();
        if (now.isBefore(promoCode.getValidFrom()) || now.isAfter(promoCode.getValidUntil())) {
            // BR-PROMO-005
            throw new UnprocessableEntityException("Promo code has expired or is not yet valid");
        }

        // Empty applicableTicketTypeIds means "applies to all ticket types
        // on the event" - a reasonable default, not otherwise stated in the
        // spec but implied by PromoCode being event-scoped with an optional
        // ticket-type restriction.
        Set<UUID> applicable = promoCode.getApplicableTicketTypeIds();
        if (applicable != null && !applicable.isEmpty() && !applicable.containsAll(ticketTypeIds)) {
            // BR-PROMO-007
            throw new UnprocessableEntityException("Promo code is not applicable to one or more ticket types in this cart");
        }

        // BR-PROMO-002/006 usage-limit enforcement, factored into
        // PromoCodeUsageLimitGuard so CheckoutServiceImpl.doCheckout can
        // re-run the identical check immediately before charging.
        promoCodeUsageLimitGuard.checkUsageLimits(promoCode, cart.getBuyerId());

        cart.setPromoCodeId(promoCode.getId());
        // saveAndFlush so the response reflects the freshly-bumped
        // updated_at rather than the pre-update in-memory value (see the
        // comment on create()).
        cart = cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removePromoCode(UUID cartId) {
        Cart cart = getCartOrThrow(cartId);
        requireOwnerBuyer(cart);
        cart.setPromoCodeId(null);
        cart = cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    private Cart getCartOrThrow(UUID cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " not found"));
    }

    /**
     * Buyer-only, always - openapi documents no admin/organizer bypass for
     * cart visibility or mutation (unlike Event/TicketType's owner-or-
     * organizer-or-admin pattern).
     */
    private void requireOwnerBuyer(Cart cart) {
        UUID callerId = accessGuard.currentUserId();
        if (!callerId.equals(cart.getBuyerId())) {
            throw new ForbiddenException("Only the cart's own buyer may access this cart");
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemResponse> itemResponses = items.stream().map(CartItemResponse::from).toList();

        long subtotal = 0L;
        String currency = "USD"; // default for an empty cart / cart with no resolvable prices
        boolean currencyResolved = false;
        for (CartItem item : items) {
            TicketType ticketType = ticketTypeRepository.findById(item.getTicketTypeId()).orElse(null);
            if (ticketType == null || ticketType.getPrice() == null) {
                // Ticket type vanished (e.g. soft-deleted) after being added to
                // the cart - skip its contribution rather than failing the
                // whole cart view.
                continue;
            }
            subtotal += ticketType.getPrice().getAmount() * item.getQuantity();
            if (!currencyResolved) {
                currency = ticketType.getPrice().getCurrency();
                currencyResolved = true;
            }
        }

        AppliedPromoCodeResponse appliedPromoCode = null;
        long total = subtotal;
        if (cart.getPromoCodeId() != null) {
            PromoCode promoCode = promoCodeRepository.findById(cart.getPromoCodeId()).orElse(null);
            if (promoCode != null) {
                long discount = computeDiscount(promoCode, subtotal);
                total = subtotal - discount;
                appliedPromoCode = new AppliedPromoCodeResponse(promoCode.getCode(), new MoneyDto(discount, currency));
            }
        }

        return new CartResponse(
                cart.getId(),
                cart.getBuyerId(),
                itemResponses,
                appliedPromoCode,
                new MoneyDto(total, currency),
                cart.getCreatedAt(),
                cart.getUpdatedAt());
    }

    private long computeDiscount(PromoCode promoCode, long subtotal) {
        BigDecimal subtotalBd = BigDecimal.valueOf(subtotal);
        BigDecimal discountBd;
        if (promoCode.getDiscountType() == DiscountType.PERCENTAGE) {
            discountBd = subtotalBd.multiply(promoCode.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        } else {
            discountBd = promoCode.getDiscountValue().setScale(0, RoundingMode.HALF_UP);
        }
        // Clamp into [0, subtotal] on the BigDecimal BEFORE longValueExact():
        // an unexpected extreme discountValue (e.g. a pre-existing PERCENTAGE
        // row saved before the create()-time upper-bound check below existed)
        // combined with a large subtotal could otherwise overflow long in the
        // multiply/divide above and throw an uncaught ArithmeticException
        // from longValueExact() instead of clamping gracefully. This is
        // defense in depth on top of PromoCodeServiceImpl.create()'s
        // discountValue <= 100 check for PERCENTAGE, not a replacement for it.
        if (discountBd.compareTo(BigDecimal.ZERO) < 0) {
            discountBd = BigDecimal.ZERO;
        } else if (discountBd.compareTo(subtotalBd) > 0) {
            discountBd = subtotalBd;
        }
        return discountBd.longValueExact();
    }
}
