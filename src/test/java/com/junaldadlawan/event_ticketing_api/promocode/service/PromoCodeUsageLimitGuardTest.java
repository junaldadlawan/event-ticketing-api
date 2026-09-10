package com.junaldadlawan.event_ticketing_api.promocode.service;

import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.repository.OrderRepository;
import com.junaldadlawan.event_ticketing_api.promocode.entity.PromoCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the BR-PROMO-002/006 usage-limit logic factored out of
 * {@code CartServiceImpl.applyPromoCode} (code-reviewer HIGH finding) so
 * {@code CheckoutServiceImpl.doCheckout} can reuse the identical check.
 */
@ExtendWith(MockitoExtension.class)
class PromoCodeUsageLimitGuardTest {

    @Mock
    private OrderRepository orderRepository;

    private PromoCodeUsageLimitGuard guard;

    private PromoCode promoCode(Integer usageLimitTotal, Integer usageLimitPerBuyer) {
        return PromoCode.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .code("SAVE10")
                .discountValue(BigDecimal.TEN)
                .usageLimitTotal(usageLimitTotal)
                .usageLimitPerBuyer(usageLimitPerBuyer)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        guard = new PromoCodeUsageLimitGuard(orderRepository);
    }

    @Test
    void noLimitsConfigured_neverQueriesOrRejects() {
        PromoCode promo = promoCode(null, null);
        UUID buyerId = UUID.randomUUID();

        assertThatCode(() -> guard.checkUsageLimits(promo, buyerId)).doesNotThrowAnyException();
    }

    @Test
    void totalUsageBelowLimit_allows() {
        PromoCode promo = promoCode(5, null);
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.countByPromoCodeAndStatus("SAVE10", OrderStatus.PAID)).thenReturn(4L);

        assertThatCode(() -> guard.checkUsageLimits(promo, buyerId)).doesNotThrowAnyException();
    }

    @Test
    void totalUsageAtLimit_rejects() {
        PromoCode promo = promoCode(5, null);
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.countByPromoCodeAndStatus("SAVE10", OrderStatus.PAID)).thenReturn(5L);

        assertThatThrownBy(() -> guard.checkUsageLimits(promo, buyerId))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("total usage limit");
    }

    @Test
    void perBuyerUsageAtLimit_rejects() {
        PromoCode promo = promoCode(null, 1);
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.countByPromoCodeAndBuyerIdAndStatus("SAVE10", buyerId, OrderStatus.PAID)).thenReturn(1L);

        assertThatThrownBy(() -> guard.checkUsageLimits(promo, buyerId))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("per-buyer usage limit");
    }

    @Test
    void perBuyerUsageBelowLimit_allows() {
        PromoCode promo = promoCode(null, 2);
        UUID buyerId = UUID.randomUUID();
        when(orderRepository.countByPromoCodeAndBuyerIdAndStatus("SAVE10", buyerId, OrderStatus.PAID)).thenReturn(1L);

        assertThatCode(() -> guard.checkUsageLimits(promo, buyerId)).doesNotThrowAnyException();
    }
}
