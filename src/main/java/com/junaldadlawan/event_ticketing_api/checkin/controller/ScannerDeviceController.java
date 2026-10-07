package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceAuthorizeRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;
import com.junaldadlawan.event_ticketing_api.checkin.dto.TicketDatasetResponse;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * All scanner-device endpoints. Authorizing a device is addressed through the
 * event; the other two share the {@code /scanner-devices/{deviceId}} path but
 * have opposite auth models: {@code DELETE} is owning-organizer/admin (user
 * JWT), {@code GET .../dataset} is {@code deviceAuth}-only ({@code
 * SecurityConfig} gates it to {@code ROLE_SCANNER_DEVICE} specifically).
 */
@RestController
@RequiredArgsConstructor
public class ScannerDeviceController {

    private final ScannerDeviceService scannerDeviceService;
    private final CheckInService checkInService;

    @PostMapping("/api/v1/events/{eventId}/scanner-devices")
    @ResponseStatus(HttpStatus.CREATED)
    public ScannerDeviceResponse authorize(@PathVariable UUID eventId, @Valid @RequestBody ScannerDeviceAuthorizeRequest request) {
        return scannerDeviceService.authorize(eventId, request);
    }

    @DeleteMapping("/api/v1/scanner-devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID deviceId) {
        scannerDeviceService.revoke(deviceId);
    }

    @GetMapping("/api/v1/scanner-devices/{deviceId}/dataset")
    public TicketDatasetResponse getDataset(@PathVariable UUID deviceId) {
        return checkInService.getDataset(deviceId);
    }
}
