package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.PaymentFailedException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import com.junaldadlawan.event_ticketing_api.order.enums.PayeeType;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ResaleListingController} (create, list, cancel, purchase) —
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

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private OrderResponse orderResponse(UUID orderId, UUID sellerId) {
        return new OrderResponse(orderId, UUID.randomUUID(), PayeeType.USER, sellerId, OrderStatus.PAID, null,
                new MoneyDto(0L, "USD"), new MoneyDto(1000L, "USD"), List.of(), Instant.now(), UUID.randomUUID().toString(), Instant.now());
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

    @Test
    void listActive_existingEvent_returns200_pagedShape() throws Exception {
        UUID eventId = UUID.randomUUID();
        ResaleListingResponse listing = new ResaleListingResponse(UUID.randomUUID(), UUID.randomUUID(), eventId,
                UUID.randomUUID(), new MoneyDto(1000L, "USD"), ResaleListingStatus.ACTIVE, Instant.now(), null, null, null);
        when(resaleListingService.listActive(eq(eventId), any()))
                .thenReturn(new PageImpl<>(List.of(listing), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-listings", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listActive_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(resaleListingService.listActive(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/resale-listings", eventId))
                .andExpect(status().isNotFound());
    }

    private ResaleListingResponse response(UUID ticketId) {
        return new ResaleListingResponse(UUID.randomUUID(), ticketId, UUID.randomUUID(), UUID.randomUUID(),
                new MoneyDto(1000L, "USD"), ResaleListingStatus.ACTIVE, Instant.now(), null, null, null);
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any())).thenReturn(response(ticketId));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_missingAskingPrice_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidCurrencyFormat_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"us\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownTicket_returns404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any()))
                .thenThrow(new ResourceNotFoundException("Ticket " + ticketId + " not found"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_nonOwningCaller_returns403() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any()))
                .thenThrow(new ForbiddenException("Only the ticket's owning buyer may list it for resale"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_resaleDisabledOrAbovePriceCap_returns403() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any()))
                .thenThrow(new ForbiddenException("Resale is disabled for this event"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_currencyMismatch_returns400FromService() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any()))
                .thenThrow(new BadRequestException("Asking price currency must match the ticket's face-value currency (USD)"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_alreadyHasActiveListing_returns409() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(resaleListingService.create(eq(ticketId), any()))
                .thenThrow(new ConflictException("This ticket already has an active resale listing"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/resale-listings", ticketId)
                        .contentType("application/json")
                        .content("{\"askingPrice\":{\"amount\":1000,\"currency\":\"USD\"}}"))
                .andExpect(status().isConflict());
    }
}
