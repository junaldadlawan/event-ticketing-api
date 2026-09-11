package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link TicketResaleListingController} — mirrors {@code
 * TicketControllerTest}'s style. Security-filter enforcement is exercised
 * separately in the full-stack integration test.
 */
@WebMvcTest(TicketResaleListingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketResaleListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResaleListingService resaleListingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
