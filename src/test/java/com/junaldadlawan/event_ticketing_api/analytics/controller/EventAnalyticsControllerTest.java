package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.EventAnalyticsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsService;
import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventAnalyticsController}'s own behavior - real
 * SecurityConfig/owner-or-organizer-or-admin enforcement is exercised in
 * {@code AnalyticsIntegrationTest}.
 */
@WebMvcTest(EventAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void get_validRequest_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(analyticsService.getEventAnalytics(eventId)).thenReturn(
                new EventAnalyticsResponse(eventId, 10, new MoneyDto(50000L, "USD"), 5, List.of()));

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsSold").value(10))
                .andExpect(jsonPath("$.revenue.amount").value(50000))
                .andExpect(jsonPath("$.remainingInventory").value(5));
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(analyticsService.getEventAnalytics(eventId)).thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_roselessStranger_returns403() throws Exception {
        when(analyticsService.getEventAnalytics(any())).thenThrow(new ForbiddenException("Only the event's organizer/owner or an admin may view its analytics"));

        mockMvc.perform(get("/api/v1/events/{eventId}/analytics", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
