package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInConfigResponse;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInMode;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInConfigService;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventCheckInConfigController} — request/response
 * shape and status-code mapping only. RBAC (device-eligible GET,
 * owner/organizer-only PATCH) is exercised in the full-stack integration test.
 */
@WebMvcTest(EventCheckInConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventCheckInConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckInConfigService checkInConfigService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void get_existingConfig_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(checkInConfigService.get(eventId))
                .thenReturn(new CheckInConfigResponse(eventId, CheckInMode.STANDARD, 300, null, null, null, null));

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.mode").value("STANDARD"))
                .andExpect(jsonPath("$.offlineFallbackExpirySeconds").value(300));
    }

    @Test
    void get_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(checkInConfigService.get(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/check-in-config", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_validRequest_returns200_withWarning() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(checkInConfigService.update(eq(eventId), any()))
                .thenReturn(new CheckInConfigResponse(eventId, CheckInMode.PURE_OFFLINE, 300,
                        "Switching check-in modes while a device may hold unsynced local scan data - ensure all devices have fully synced before relying on the new mode.",
                        null, null, "some-caller"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .contentType("application/json")
                        .content("{\"mode\":\"PURE_OFFLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PURE_OFFLINE"))
                .andExpect(jsonPath("$.warning").value(
                        "Switching check-in modes while a device may hold unsynced local scan data - ensure all devices have fully synced before relying on the new mode."));
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(checkInConfigService.update(any(), any())).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(checkInConfigService.update(any(), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's check-in config"));

        mockMvc.perform(patch("/api/v1/events/{eventId}/check-in-config", eventId)
                        .contentType("application/json")
                        .content("{\"mode\":\"STANDARD\"}"))
                .andExpect(status().isForbidden());
    }
}
