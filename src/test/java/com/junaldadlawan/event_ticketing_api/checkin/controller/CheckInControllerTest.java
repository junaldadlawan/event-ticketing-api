package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketSummaryDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidationResultResponse;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link CheckInController} ({@code POST /check-in/validate},
 * {@code POST /check-in/fallback-scans}) — request validation, response
 * shape, and status-code mapping. deviceAuth-only enforcement (a user JWT
 * must NOT work here) is exercised in the full-stack integration test.
 */
@WebMvcTest(CheckInController.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckInService checkInService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    // ---- POST /check-in/validate ----

    @Test
    void validate_validScan_returns200_withValidResult() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(checkInService.validate(any())).thenReturn(new ValidationResultResponse(ticketId, CheckInResult.VALID,
                Instant.now(), new TicketSummaryDto("ABC-000001", "GA", null)));

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .contentType("application/json")
                        .content("{\"credential\":\"some-credential\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.result").value("VALID"))
                .andExpect(jsonPath("$.ticketSummary.ticketNumber").value("ABC-000001"));
    }

    @Test
    void validate_missingCredential_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .contentType("application/json")
                        .content("{\"deviceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_missingDeviceId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/check-in/validate")
                        .contentType("application/json")
                        .content("{\"credential\":\"some-credential\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_deviceIdMismatchWithAuthenticatedDevice_returns403() throws Exception {
        when(checkInService.validate(any())).thenThrow(new ForbiddenException("device_id does not match the authenticated device"));

        mockMvc.perform(post("/api/v1/check-in/validate")
                        .contentType("application/json")
                        .content("{\"credential\":\"some-credential\",\"deviceId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- POST /check-in/fallback-scans ----

    @Test
    void submitFallbackScans_validBatch_returns200_withPerScanResults() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(checkInService.submitFallbackScans(any())).thenReturn(List.of(
                new ValidationResultResponse(ticketId, CheckInResult.VALID, Instant.now(), null),
                new ValidationResultResponse(null, CheckInResult.INVALID, Instant.now(), null)));

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"scans\":["
                                + "{\"rawCredential\":\"cred1\",\"capturedAt\":\"2026-01-01T00:00:00Z\"},"
                                + "{\"rawCredential\":\"garbage\",\"capturedAt\":\"2026-01-01T00:01:00Z\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].result").value("VALID"))
                .andExpect(jsonPath("$[1].result").value("INVALID"));
    }

    @Test
    void submitFallbackScans_emptyScansList_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"scans\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFallbackScans_missingEventId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .contentType("application/json")
                        .content("{\"scans\":[{\"rawCredential\":\"cred1\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFallbackScans_notPureOfflineMode_returns409() throws Exception {
        when(checkInService.submitFallbackScans(any()))
                .thenThrow(new ConflictException("Fallback scans are only accepted for events in pure_offline mode"));

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"scans\":["
                                + "{\"rawCredential\":\"cred1\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void submitFallbackScans_eventIdDoesNotMatchDevicesOwnEvent_returns400() throws Exception {
        when(checkInService.submitFallbackScans(any()))
                .thenThrow(new BadRequestException("event_id does not match the authenticated device's event"));

        mockMvc.perform(post("/api/v1/check-in/fallback-scans")
                        .contentType("application/json")
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"scans\":["
                                + "{\"rawCredential\":\"cred1\",\"capturedAt\":\"2026-01-01T00:00:00Z\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
