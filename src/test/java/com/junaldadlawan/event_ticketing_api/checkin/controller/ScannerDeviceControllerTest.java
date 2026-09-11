package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetEntryDto;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceService;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link ScannerDeviceController} ({@code DELETE
 * /scanner-devices/{deviceId}}, {@code GET .../dataset}) — request/response
 * shape and status-code mapping. The two endpoints' opposite auth models
 * (user JWT for DELETE, deviceAuth-only for GET dataset) are exercised in
 * the full-stack integration test, since this slice runs with the filter
 * chain disabled.
 */
@WebMvcTest(ScannerDeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScannerDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScannerDeviceService scannerDeviceService;

    @MockitoBean
    private CheckInService checkInService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void revoke_existingDevice_returns204() throws Exception {
        UUID deviceId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId))
                .andExpect(status().isNoContent());
    }

    @Test
    void revoke_unknownDevice_returns404() throws Exception {
        UUID deviceId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Scanner device " + deviceId + " not found"))
                .when(scannerDeviceService).revoke(deviceId);

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void revoke_unauthorizedCaller_returns403() throws Exception {
        UUID deviceId = UUID.randomUUID();
        doThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's scanner devices"))
                .when(scannerDeviceService).revoke(deviceId);

        mockMvc.perform(delete("/api/v1/scanner-devices/{deviceId}", deviceId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDataset_ownDevice_returns200_withHashedCredentials() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(checkInService.getDataset(deviceId)).thenReturn(new TicketDatasetResponse(eventId, Instant.now(),
                List.of(new TicketDatasetEntryDto(ticketId, "sha256-hash-not-the-raw-credential", "valid"))));

        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.tickets[0].ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.tickets[0].credentialHash").value("sha256-hash-not-the-raw-credential"))
                .andExpect(jsonPath("$.tickets[0].status").value("valid"));
    }

    @Test
    void getDataset_differentDeviceIdThanAuthenticated_returns403() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(checkInService.getDataset(deviceId)).thenThrow(new ForbiddenException("This dataset belongs to a different device"));

        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDataset_unknownDevice_returns404() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(checkInService.getDataset(deviceId)).thenThrow(new ResourceNotFoundException("Scanner device " + deviceId + " not found"));

        mockMvc.perform(get("/api/v1/scanner-devices/{deviceId}/dataset", deviceId))
                .andExpect(status().isNotFound());
    }
}
