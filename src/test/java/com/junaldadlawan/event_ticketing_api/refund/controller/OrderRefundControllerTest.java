package com.junaldadlawan.event_ticketing_api.refund.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.refund.dto.RefundResponse;
import com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus;
import com.junaldadlawan.event_ticketing_api.refund.service.RefundService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link OrderRefundController}'s own behavior (request
 * validation, response mapping, status codes) — mirrors {@code
 * TicketResaleListingControllerTest}. Security-filter enforcement and the
 * real authorization/policy-gate matrices live in the integration test.
 */
@WebMvcTest(OrderRefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderRefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundService refundService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RefundResponse response(UUID orderId, RefundStatus status) {
        return new RefundResponse(UUID.randomUUID(), orderId, new MoneyDto(500L, "USD"), "not as described",
                UUID.randomUUID(), status, Instant.now(), null);
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any())).thenReturn(response(orderId, RefundStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not as described\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void create_gatewayDeclined_stillReturns201_withFailedStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any())).thenReturn(response(orderId, RefundStatus.FAILED));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not as described\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void create_missingReason_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankReason_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidAmountCurrencyFormat_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"partial\",\"amount\":{\"amount\":100,\"currency\":\"usd\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unauthorizedCaller_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any()))
                .thenThrow(new ForbiddenException("Only the event's organizer/owner or an admin may issue a refund for this order"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not as described\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any()))
                .thenThrow(new ResourceNotFoundException("Order " + orderId + " not found"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not as described\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_deniedByRefundPolicy_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any()))
                .thenThrow(new ConflictException("Refund not permitted under the event's refund policy"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not as described\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_amountExceedsRemainingBalance_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.createRefund(eq(orderId), any()))
                .thenThrow(new BadRequestException("Refund amount exceeds the order's remaining refundable balance"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/refunds", orderId)
                        .contentType("application/json")
                        .content("{\"reason\":\"too much\",\"amount\":{\"amount\":999999,\"currency\":\"USD\"}}"))
                .andExpect(status().isBadRequest());
    }

    // ---- list() ----

    @Test
    void list_existingOrder_returns200_withRefunds() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.listRefunds(orderId)).thenReturn(List.of(response(orderId, RefundStatus.COMPLETED)));

        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()));
    }

    @Test
    void list_unauthorizedCaller_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.listRefunds(orderId))
                .thenThrow(new ForbiddenException("Only the order's buyer, the event's organizer/owner, or an admin may view this order's refunds"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(refundService.listRefunds(orderId)).thenThrow(new ResourceNotFoundException("Order " + orderId + " not found"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/refunds", orderId))
                .andExpect(status().isNotFound());
    }
}
