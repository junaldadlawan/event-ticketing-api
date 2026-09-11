package com.junaldadlawan.event_ticketing_api.waitlist.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistEntryResponse;
import com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link WaitlistController} — mirrors {@code
 * TicketResaleListingControllerTest}'s style. Security/RBAC enforcement
 * (the new {@code SecurityConfig} matcher for {@code GET
 * /users/me/waitlist-entries}) is exercised separately in the full-stack
 * integration test.
 */
@WebMvcTest(WaitlistController.class)
@AutoConfigureMockMvc(addFilters = false)
class WaitlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaitlistService waitlistService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private WaitlistEntryResponse response(UUID eventId, UUID ticketTypeId, int position) {
        return new WaitlistEntryResponse(UUID.randomUUID(), eventId, ticketTypeId, UUID.randomUUID(), position, null, null, Instant.now());
    }

    // ---- POST /events/{eventId}/waitlist ----

    @Test
    void join_specificTicketType_returns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), eq(ticketTypeId))).thenReturn(response(eventId, ticketTypeId, 1));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.ticketTypeId").value(ticketTypeId.toString()))
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void join_omittedTicketTypeId_stillReturns201_andPassesNullThrough() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), isNull())).thenReturn(response(eventId, null, 1));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketTypeId").doesNotExist());
    }

    @Test
    void join_omittedBodyEntirely_stillReturns201() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), isNull())).thenReturn(response(eventId, null, 1));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId))
                .andExpect(status().isCreated());
    }

    @Test
    void join_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void join_ticketTypeNotBelongingToEvent_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), eq(ticketTypeId)))
                .thenThrow(new BadRequestException("Ticket type " + ticketTypeId + " does not belong to event " + eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{\"ticketTypeId\":\"" + ticketTypeId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void join_notSoldOut_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), any()))
                .thenThrow(new ConflictException("This event is not currently sold out"));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void join_alreadyOnWaitlist_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.join(eq(eventId), any()))
                .thenThrow(new ConflictException("You are already on this waitlist"));

        mockMvc.perform(post("/api/v1/events/{eventId}/waitlist", eventId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // ---- GET /users/me/waitlist-entries ----

    @Test
    void listMyEntries_returns200_asPlainJsonArray_notPaginationWrapper() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(waitlistService.listMyEntries()).thenReturn(List.of(response(eventId, null, 1)));

        mockMvc.perform(get("/api/v1/users/me/waitlist-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                // Not wrapped in a "content"/"page" pagination envelope.
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void listMyEntries_noEntries_returns200_withEmptyArray() throws Exception {
        when(waitlistService.listMyEntries()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/waitlist-entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
