package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Two endpoints under the same path prefix but opposite auth models: {@code
 * DELETE} is owning-organizer/admin (user JWT), {@code GET .../dataset} is
 * {@code deviceAuth}-only ({@code SecurityConfig} gates it to {@code
 * ROLE_SCANNER_DEVICE} specifically) - kept on one controller since they
 * share the {@code /scanner-devices/{deviceId}} path.
 */
@RestController
@RequestMapping("/api/v1/scanner-devices/{deviceId}")
@RequiredArgsConstructor
public class ScannerDeviceController {

    private final ScannerDeviceService scannerDeviceService;
    private final CheckInService checkInService;

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID deviceId) {
        scannerDeviceService.revoke(deviceId);
    }

    @GetMapping("/dataset")
    public TicketDatasetResponse getDataset(@PathVariable UUID deviceId) {
        return checkInService.getDataset(deviceId);
    }
}
