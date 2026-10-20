package com.junaldadlawan.event_ticketing_api.auditlog.controller;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.service.ActivityArea;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BR-ADMIN-007: the activity log behind each management tab - an organization's, or the platform's. */
@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final AuditLogService auditLogService;

    @GetMapping("/api/v1/activity")
    public PageResponse<AuditLogEntryResponse> activity(@RequestParam ActivityArea area,
                                                        @RequestParam(required = false) UUID organizationId,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(auditLogService.listActivity(area, organizationId, pageable));
    }
}
