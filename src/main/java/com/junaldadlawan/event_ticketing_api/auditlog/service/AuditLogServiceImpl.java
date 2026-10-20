package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final OrganizationAccessGuard accessGuard;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId) {
        record(actorId, action, targetType, targetId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId) {
        record(actorId, action, targetType, targetId, eventId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId,
                       Map<String, Object> details) {
        try {
            AuditLogEntry entry = AuditLogEntry.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .eventId(eventId)
                    .details(details == null ? null : JSON.writeValueAsString(details))
                    .build();
            auditLogEntryRepository.saveAndFlush(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log entry (actorId={}, action={}, targetType={}, targetId={})",
                    actorId, action, targetType, targetId, e);
        }
        // The audit LOG line is emitted either way (it is the independent
        // stream), so every action the DB audit trail already records
        // also appears in the log - no extra call sites needed for those.
        BusinessAuditLogger.recordAs(actorId == null ? "system" : actorId, action, targetType, targetId,
                BusinessAuditLogger.Outcome.SUCCESS, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId) {
        recordByCurrentUser(action, targetType, targetId, eventId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId,
                                    Map<String, Object> details) {
        UUID actorId;
        try {
            actorId = accessGuard.currentUserId();
        } catch (Exception e) {
            // never throws (the action it logs already happened); a write always has a signed-in caller
            log.error("Failed to resolve the caller for audit log entry (action={}, targetId={})", action, targetId, e);
            return;
        }
        record(actorId, action, targetType, targetId, eventId, details);
    }

    @Override
    public Page<AuditLogEntryResponse> listForEvent(UUID eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.OWNER)
                    && !accessGuard.hasRole(callerId, event.getOrganizationId(), OrganizationRole.ORGANIZER)) {
                throw new ForbiddenException("Only the organization's owner, organizer, or an admin may see this event's activity");
            }
        }
        Page<AuditLogEntry> page = auditLogEntryRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable);
        // one lookup for the page's actors; a deleted user still shows by id, the system as null
        Set<UUID> actorIds = page.stream().map(AuditLogEntry::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::fullName));
        return page.map(entry -> AuditLogEntryResponse.from(entry, names.get(entry.getActorId())));
    }

    @Override
    public Page<AuditLogEntry> list(UUID actorIdFilter, Pageable pageable) {
        accessGuard.requireAdmin();
        return actorIdFilter != null
                ? auditLogEntryRepository.findByActorId(actorIdFilter, pageable)
                : auditLogEntryRepository.findAll(pageable);
    }
}
