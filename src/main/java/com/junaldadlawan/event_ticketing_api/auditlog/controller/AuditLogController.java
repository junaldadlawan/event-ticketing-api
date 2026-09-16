package com.junaldadlawan.event_ticketing_api.auditlog.controller;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/v1/audit-log} - admin only (SecurityConfig {@code hasRole("ADMIN")}
 * matcher, plus {@code AuditLogServiceImpl.list}'s own {@code requireAdmin()}
 * defense-in-depth, same idiom as {@code ModerationActionController}).
 * <p>
 * Query param is {@code actorId} (camelCase), not openapi.yaml's
 * {@code actor_id} - matches this codebase's own established convention
 * (e.g. {@code EventController.search}'s {@code startsAfter}/{@code
 * startsBefore}), not the spec's snake_case.
 */
@RestController
@RequestMapping("/api/v1/audit-log")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResponse<AuditLogEntryResponse> list(@RequestParam(required = false) UUID actorId,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(auditLogService.list(actorId, pageable).map(AuditLogEntryResponse::from));
    }
}
