package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ResaleListingController} (cancel + purchase) —
 * mirrors {@code CartCheckoutControllerTest}'s request-validation/
 * status-code-mapping style for {@code purchase}, which runs like checkout.
 */
@WebMvcTest(ResaleListingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResaleListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResaleListingService resaleListingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private OrderResponse orderResponse(UUID orderId, UUID sellerId) {
        return new OrderResponse(orderId, UUID.randomUUID(), PayeeType.USER, sellerId, OrderStatus.PAID, null,
                new MoneyDto(1000L, "USD"), List.of(), Instant.now(), UUID.randomUUID().toString(), Instant.now());
    }

    // ---- DELETE /resale-listings/{listingId} ----

    @Test
    void cancel_owningSeller_returns204() throws Exception {
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancel_unknownListing_returns404() throws Exception {
        UUID listingId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Resale listing " + listingId + " not found"))
                .when(resaleListingService).cancel(listingId);

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_nonOwningSeller_returns403() throws Exception {
        UUID listingId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("Only the listing's owning seller may cancel it"))
                .when(resaleListingService).cancel(listingId);

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_notActive_returns409() throws Exception {
        UUID listingId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ConflictException("Only an active listing may be cancelled"))
                .when(resaleListingService).cancel(listingId);

        mockMvc.perform(delete("/api/v1/resale-listings/{listingId}", listingId))
                .andExpect(status().isConflict());
    }

    // ---- POST /resale-listings/{listingId}/purchase ----

    @Test
    void purchase_missingIdempotencyKeyHeader_returns400() throws Exception {
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchase_blankPaymentMethodToken_returns400() throws Exception {
        UUID listingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchase_validRequest_returns201_withUserPayee() throws Exception {
        UUID listingId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(resaleListingService.purchase(eq(listingId), any(), eq("tok_ok")))
                .thenReturn(orderResponse(orderId, sellerId));

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.payeeType").value("USER"))
                .andExpect(jsonPath("$.payeeId").value(sellerId.toString()));
    }

    @Test
    void purchase_listingNoLongerActive_returns409() throws Exception {
        UUID listingId = UUID.randomUUID();
        when(resaleListingService.purchase(eq(listingId), any(), any()))
                .thenThrow(new ConflictException("This listing is no longer active"));

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void purchase_buyerIsSeller_returns403() throws Exception {
        UUID listingId = UUID.randomUUID();
        when(resaleListingService.purchase(eq(listingId), any(), any()))
                .thenThrow(new ForbiddenException("Cannot purchase your own resale listing"));

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchase_paymentDeclined_returns402() throws Exception {
        UUID listingId = UUID.randomUUID();
        when(resaleListingService.purchase(eq(listingId), any(), any()))
                .thenThrow(new PaymentFailedException("declined"));

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_fail\"}"))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void purchase_unknownListing_returns404() throws Exception {
        UUID listingId = UUID.randomUUID();
        when(resaleListingService.purchase(eq(listingId), any(), any()))
                .thenThrow(new ResourceNotFoundException("Resale listing " + listingId + " not found"));

        mockMvc.perform(post("/api/v1/resale-listings/{listingId}/purchase", listingId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"paymentMethodToken\":\"tok_ok\"}"))
                .andExpect(status().isNotFound());
    }
}
