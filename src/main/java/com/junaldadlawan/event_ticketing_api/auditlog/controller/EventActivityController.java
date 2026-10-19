package com.junaldadlawan.event_ticketing_api.auditlog.controller;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BR-EVENT-006: the event management screen's activity history - who did what to the event, and when. */
@RestController
@RequiredArgsConstructor
public class EventActivityController {

    private final AuditLogService auditLogService;

    @GetMapping("/api/v1/events/{eventId}/activity")
    public PageResponse<AuditLogEntryResponse> activity(@PathVariable UUID eventId,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(auditLogService.listForEvent(eventId, pageable));
    }
}
