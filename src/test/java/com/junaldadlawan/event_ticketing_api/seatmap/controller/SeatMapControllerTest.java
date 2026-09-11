package com.junaldadlawan.event_ticketing_api.seatmap.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.service.SeatMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link SeatMapController}'s own behavior (response mapping,
 * status codes) — mirrors {@code TicketTypeControllerTest}. Security-filter
 * enforcement (401/403, cross-org rejection, public/draft gating) is
 * exercised separately in the full-stack integration test.
 */
@WebMvcTest(SeatMapController.class)
@AutoConfigureMockMvc(addFilters = false)
class SeatMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatMapService seatMapService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void get_seatMapExists_returns200WithSeats() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        SeatMap seatMap = SeatMap.builder().id(seatMapId).eventId(eventId).build();
        Seat seat1 = Seat.builder().id(UUID.randomUUID()).seatMapId(seatMapId).section("A").row("1").seatNumber("1").status(SeatStatus.AVAILABLE).build();
        Seat seat2 = Seat.builder().id(UUID.randomUUID()).seatMapId(seatMapId).section("A").row("1").seatNumber("2").status(SeatStatus.SOLD).build();
        when(seatMapService.getSeatMap(eventId)).thenReturn(seatMap);
        when(seatMapService.getSeats(seatMapId)).thenReturn(List.of(seat1, seat2));

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seatMapId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].status").value("SOLD"));
    }

    @Test
    void get_noSeatMap_returns404WithExpectedMessage() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(seatMapService.getSeatMap(eventId)).thenThrow(new ResourceNotFoundException("Event has no seat map"));

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event has no seat map"));
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(seatMapService.getSeatMap(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_draftEventUnauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(seatMapService.getSeatMap(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may view this event's seat map"));

        mockMvc.perform(get("/api/v1/events/{eventId}/seatmap", eventId))
                .andExpect(status().isForbidden());
    }
}
