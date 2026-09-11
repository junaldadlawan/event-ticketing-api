package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.GoneException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link CartCheckoutController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors {@code
 * CartControllerTest}. Security-filter enforcement (401/403, buyer-only
 * access) and the real idempotency/concurrency/locking mechanics are
 * exercised separately in {@code CheckoutIntegrationTest}.
 */
@WebMvcTest(CartCheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartCheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private OrderResponse orderResponse(UUID orderId) {
        return new OrderResponse(orderId, UUID.randomUUID(), PayeeType.ORGANIZATION, UUID.randomUUID(),
                OrderStatus.PAID, null, new MoneyDto(1000L, "USD"), List.of(), Instant.now(),
                UUID.randomUUID().toString(), Instant.now());
    }

    // ---- request validation ----

    @Test
    void checkout_missingIdempotencyKeyHeader_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_malformedIdempotencyKeyHeader_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_blankPaymentMethodToken_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_missingPaymentMethodToken_returns400() throws Exception {
        UUID cartId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- happy path / response shape ----

    @Test
    void checkout_validRequest_returns201WithOrderBody() throws Exception {
        UUID cartId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), eq(idempotencyKey), eq("tok_ok")))
                .thenReturn(orderResponse(orderId));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payeeType").value("ORGANIZATION"))
                .andExpect(jsonPath("$.tickets").isArray())
                .andExpect(jsonPath("$.tickets.length()").value(0));
    }

    // ---- status-code mapping for each documented/implemented failure mode ----

    @Test
    void checkout_nonOwner_returns403() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new ForbiddenException("Only the cart's own buyer may check out this cart"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkout_unknownCart_returns404() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new ResourceNotFoundException("Cart " + cartId + " not found"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkout_emptyCart_returns409() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new ConflictException("Cart has no items to check out"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_expiredHold_returns410() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new GoneException("A hold expired before checkout completed."));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isGone());
    }

    @Test
    void checkout_paymentDeclined_returns402() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new PaymentFailedException("Payment declined by gateway for the provided payment method token"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_fail"}
                                """))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void checkout_promoUsageLimitExhausted_returns422() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new UnprocessableEntityException("Promo code has reached its total usage limit"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void checkout_inFlightDuplicateIdempotencyKey_returns409() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new ConflictException("Checkout already in progress for this idempotency key"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_crossBuyerIdempotencyKeyReuse_returns403() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(checkoutService.checkout(eq(cartId), any(), any()))
                .thenThrow(new ForbiddenException("This idempotency key was issued by a different buyer"));

        mockMvc.perform(post("/api/v1/carts/{cartId}/checkout", cartId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                                {"paymentMethodToken":"tok_ok"}
                                """))
                .andExpect(status().isForbidden());
    }
}
