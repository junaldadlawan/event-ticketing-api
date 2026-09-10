package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BR-PROMO-002/006 usage-limit enforcement, factored out of {@code
 * CartServiceImpl.applyPromoCode} so {@code CheckoutServiceImpl.doCheckout}
 * can re-run the exact same check immediately before charging (code-reviewer
 * HIGH finding: limits were previously only checked at apply-time, never
 * re-validated at checkout, letting real usage exceed the configured limit
 * if other buyers exhausted the code in between). Counts completed (status
 * == PAID) Orders referencing the promo code's {@code code} string directly,
 * rather than a separate usage-counter column that could drift from the real
 * Order data - same reasoning as the original inline check.
 */
@Component
@RequiredArgsConstructor
public class PromoCodeUsageLimitGuard {

    private final OrderRepository orderRepository;

    /**
     * @throws UnprocessableEntityException if the promo code has reached its
     *                                       total or per-buyer usage limit.
     */
    public void checkUsageLimits(PromoCode promoCode, UUID buyerId) {
        if (promoCode.getUsageLimitTotal() != null) {
            long totalUsage = orderRepository.countByPromoCodeAndStatus(promoCode.getCode(), OrderStatus.PAID);
            if (totalUsage >= promoCode.getUsageLimitTotal()) {
                throw new UnprocessableEntityException("Promo code has reached its total usage limit");
            }
        }
        if (promoCode.getUsageLimitPerBuyer() != null) {
            long buyerUsage = orderRepository.countByPromoCodeAndBuyerIdAndStatus(
                    promoCode.getCode(), buyerId, OrderStatus.PAID);
            if (buyerUsage >= promoCode.getUsageLimitPerBuyer()) {
                throw new UnprocessableEntityException("Promo code has reached its per-buyer usage limit");
            }
        }
    }
}
