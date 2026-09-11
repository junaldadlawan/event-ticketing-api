package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link EventScannerDeviceController} ({@code POST
 * /events/{eventId}/scanner-devices}) — request/response shape and
 * status-code mapping. RBAC and BR-CHECKIN-008's pure_offline enforcement
 * are exercised in the full-stack integration test.
 */
@WebMvcTest(EventScannerDeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventScannerDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScannerDeviceService scannerDeviceService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void authorize_validRequest_returns201_withCredential() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(scannerDeviceService.authorize(eq(eventId), any())).thenReturn(
                new ScannerDeviceResponse(deviceId, eventId, "Gate 1", ScannerDeviceStatus.ACTIVE,
                        "device-credential-value", null, "caller", null, null));

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(deviceId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.deviceLabel").value("Gate 1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.credential").value("device-credential-value"));
    }

    @Test
    void authorize_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(scannerDeviceService.authorize(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void authorize_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(scannerDeviceService.authorize(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's scanner devices"));

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorize_pureOfflineModeAlreadyActive_noForceReplace_returns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(scannerDeviceService.authorize(eq(eventId), any()))
                .thenThrow(new ConflictException("pure_offline mode already has an active device; retry with force_replace true"));

        mockMvc.perform(post("/api/v1/events/{eventId}/scanner-devices", eventId)
                        .contentType("application/json")
                        .content("{\"deviceLabel\":\"Gate 2\"}"))
                .andExpect(status().isConflict());
    }
}
