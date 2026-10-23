package com.junaldadlawan.event_ticketing_api.checkin.history;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only scan history of an event: owner, organizer, check-in staff of its organization, admin. */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
public class EventCheckInHistoryController {

    private final CheckInHistoryService service;

    @GetMapping("/check-in-records")
    public PageResponse<CheckInHistoryItem> records(@PathVariable UUID eventId,
                                                    @RequestParam(required = false) CheckInResult result,
                                                    @RequestParam(required = false) CheckInSourceType sourceType,
                                                    @RequestParam(required = false) UUID scannerDeviceId,
                                                    @RequestParam(required = false) String ticketNumber,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return service.history(eventId, result, sourceType, scannerDeviceId, ticketNumber, pageable);
    }

    @GetMapping("/check-in-summary")
    public CheckInSummaryResponse summary(@PathVariable UUID eventId,
                                          @RequestParam(required = false) CheckInResult result,
                                          @RequestParam(required = false) CheckInSourceType sourceType,
                                          @RequestParam(required = false) UUID scannerDeviceId,
                                          @RequestParam(required = false) String ticketNumber) {
        return service.summary(eventId, result, sourceType, scannerDeviceId, ticketNumber);
    }

    @GetMapping("/scanner-devices")
    public List<ScannerDeviceSummary> scannerDevices(@PathVariable UUID eventId) {
        return service.devices(eventId);
    }
}
