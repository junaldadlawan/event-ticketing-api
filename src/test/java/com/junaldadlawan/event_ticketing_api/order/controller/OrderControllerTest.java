package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.order.service.OrderService;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link OrderController}'s own behavior (response mapping,
 * status codes) — mirrors {@code TicketTypeControllerTest}/{@code
 * CartCheckoutControllerTest}. Security-filter enforcement (401/403,
 * cross-org rejection, the buyer-vs-organizer-scoping distinction between
 * endpoints) is exercised separately in the full-stack {@code
 * OrderAccessIntegrationTest}.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private TicketResponse ticketResponse(UUID orderId) {
        return new TicketResponse(UUID.randomUUID(), orderId, UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), "ABC-A2B3C4", TicketStatus.VALID, Instant.now(), Instant.now());
    }

    private OrderResponse orderResponse(UUID orderId) {
        return new OrderResponse(orderId, UUID.randomUUID(), PayeeType.ORGANIZATION, UUID.randomUUID(),
                OrderStatus.PAID, null, new MoneyDto(1000L, "USD"), List.of(ticketResponse(orderId)), Instant.now(),
                UUID.randomUUID().toString(), Instant.now());
    }

    // ---- GET /orders/{orderId} ----

    @Test
    void getOrder_existingOrder_returns200_ticketsNeverExposeCredential() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(orderResponse(orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.tickets[0].credential").doesNotExist());
    }

    @Test
    void getOrder_unknownOrder_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenThrow(new ResourceNotFoundException("Order " + orderId + " not found"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_unauthorizedCaller_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId))
                .thenThrow(new ForbiddenException("Only the order's buyer, the event's organizer/owner, or an admin may view this order"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isForbidden());
    }

    // ---- GET /orders/{orderId}/tickets ----

    @Test
    void getOrderTickets_existingOrder_returns200_withoutCredential() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderTickets(orderId)).thenReturn(List.of(ticketResponse(orderId)));

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].credential").doesNotExist());
    }

    @Test
    void getOrderTickets_unauthorizedCaller_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderTickets(orderId))
                .thenThrow(new ForbiddenException("Only the order's buyer, the event's organizer/owner, or an admin may view this order"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", orderId))
                .andExpect(status().isForbidden());
    }

    // ---- GET /users/me/orders ----

    @Test
    void listMyOrders_returns200_pagedShape() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.listMyOrders(any())).thenReturn(new PageImpl<>(List.of(orderResponse(orderId)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- GET /events/{eventId}/orders ----

    @Test
    void listEventOrders_organizerOrAdmin_returns200_pagedShape() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderService.listEventOrders(org.mockito.ArgumentMatchers.eq(eventId), any()))
                .thenReturn(new PageImpl<>(List.of(orderResponse(orderId)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()));
    }

    @Test
    void listEventOrders_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(orderService.listEventOrders(org.mockito.ArgumentMatchers.eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId))
                .andExpect(status().isNotFound());
    }

    /**
     * Response-mapping proof (not the real security filter) that this
     * endpoint's own forbidden path maps to 403 — including for a caller who
     * happens to be the order's buyer but lacks an organizer/owner role
     * (verified for real in {@code OrderAccessIntegrationTest}).
     */
    @Test
    void listEventOrders_buyerWithoutOrganizerRole_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(orderService.listEventOrders(org.mockito.ArgumentMatchers.eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may view this event's orders"));

        mockMvc.perform(get("/api/v1/events/{eventId}/orders", eventId))
                .andExpect(status().isForbidden());
    }
}
