package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventResaleListingController} — mirrors {@code
 * EventTicketTemplateControllerTest}'s paginated-listing style.
 */
@WebMvcTest(EventResaleListingController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventResaleListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResaleListingService resaleListingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
}
