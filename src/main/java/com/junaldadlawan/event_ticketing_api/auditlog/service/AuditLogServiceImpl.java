package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Phase 13 (BR-NFR-005). {@link #record} mirrors {@code
 * NotificationServiceImpl.notify}'s exact reasoning: {@code
 * REQUIRES_NEW} + never-rethrows, so a failure writing an audit-log row can
 * never roll back the sensitive action (refund, cancellation, etc.) it's
 * logging - same NFR 5.2 concern, applied here to audit logging instead of
 * notification delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final OrganizationAccessGuard accessGuard;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId) {
        try {
            AuditLogEntry entry = AuditLogEntry.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .build();
            auditLogEntryRepository.saveAndFlush(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log entry (actorId={}, action={}, targetType={}, targetId={})",
                    actorId, action, targetType, targetId, e);
        }
    }

    @Override
    public Page<AuditLogEntry> list(UUID actorIdFilter, Pageable pageable) {
        accessGuard.requireAdmin();
        return actorIdFilter != null
                ? auditLogEntryRepository.findByActorId(actorIdFilter, pageable)
                : auditLogEntryRepository.findAll(pageable);
    }
}
