package com.junaldadlawan.event_ticketing_api.checkin.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.FallbackScanBatchRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidateScanRequest;
import com.junaldadlawan.event_ticketing_api.checkin.dto.ValidationResultResponse;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Both endpoints are {@code deviceAuth}-only - see {@code SecurityConfig}'s {@code ROLE_SCANNER_DEVICE} matchers. */
@RestController
@RequestMapping("/api/v1/check-in")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/validate")
    public ValidationResultResponse validate(@Valid @RequestBody ValidateScanRequest request) {
        return checkInService.validate(request);
    }

    @PostMapping("/fallback-scans")
    public List<ValidationResultResponse> submitFallbackScans(@Valid @RequestBody FallbackScanBatchRequest request) {
        return checkInService.submitFallbackScans(request);
    }
}
