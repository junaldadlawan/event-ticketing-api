package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceAuthorizeRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ScannerDeviceResponse;
import com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/scanner-devices")
@RequiredArgsConstructor
public class EventScannerDeviceController {

    private final ScannerDeviceService scannerDeviceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScannerDeviceResponse authorize(@PathVariable UUID eventId, @RequestBody ScannerDeviceAuthorizeRequest request) {
        return scannerDeviceService.authorize(eventId, request);
    }
}
