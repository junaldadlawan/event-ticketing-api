package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.dto.AuditLogEntryResponse;
import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
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

    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId) {
        record(actorId, action, targetType, targetId, null, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId) {
        record(actorId, action, targetType, targetId, eventId, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId,
                       Map<String, Object> details) {
        record(actorId, action, targetType, targetId, eventId, null, details);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, UUID targetId, UUID eventId,
                       UUID organizationId, Map<String, Object> details) {
        String detailsJson = null;
        try {
            detailsJson = details == null ? null : JSON.writeValueAsString(details);
            // an event's entries belong to its organization (for the per-tab logs) without every caller looking it up
            UUID organization = organizationId != null || eventId == null ? organizationId
                    : eventRepository.findById(eventId).map(Event::getOrganizationId).orElse(null);
            AuditLogEntry entry = AuditLogEntry.builder()
                    .actorId(actorId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .eventId(eventId)
                    .organizationId(organization)
                    .details(detailsJson)
                    .build();
            auditLogEntryRepository.saveAndFlush(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log entry (actorId={}, action={}, targetType={}, targetId={})",
                    actorId, action, targetType, targetId, e);
        }
        // The audit LOG line is emitted either way (it is the independent
        // stream), so every action the DB audit trail records also appears
        // in the log, with the same details - no extra call sites needed.
        BusinessAuditLogger.recordAs(actorId == null ? "system" : actorId, action, targetType, targetId,
                BusinessAuditLogger.Outcome.SUCCESS, detailsJson);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId) {
        recordByCurrentUser(action, targetType, targetId, eventId, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId,
                                    Map<String, Object> details) {
        recordByCurrentUser(action, targetType, targetId, eventId, null, details);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordByCurrentUser(String action, String targetType, UUID targetId, UUID eventId,
                                    UUID organizationId, Map<String, Object> details) {
        UUID actorId;
        try {
            actorId = accessGuard.currentUserId();
        } catch (Exception e) {
            actorId = null;   // nobody signed in: the system did it (e.g. a payout result arriving by webhook)
        }
        record(actorId, action, targetType, targetId, eventId, organizationId, details);
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
        return toResponses(auditLogEntryRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable));
    }

    @Override
    public Page<AuditLogEntryResponse> list(UUID actorIdFilter, Pageable pageable) {
        accessGuard.requireAdmin();
        return toResponses(actorIdFilter != null
                ? auditLogEntryRepository.findByActorId(actorIdFilter, pageable)
                : auditLogEntryRepository.findAll(pageable));
    }

    @Override
    public Page<AuditLogEntryResponse> listActivity(ActivityArea area, UUID organizationId, Pageable pageable) {
        if (organizationId == null) {
            if (!area.hasPlatformTab()) {
                throw new BadRequestException(area + " has no platform-wide log; pass organizationId");
            }
            if (!accessGuard.isAdmin()) {
                throw new ForbiddenException("Only an admin may read the platform's activity");
            }
        } else {
            if (!area.hasOrganizationTab()) {
                throw new BadRequestException(area + " has no organization log; leave out organizationId");
            }
            organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization " + organizationId + " not found"));
            requireOrganizationReader(area, organizationId);
        }
        // always newest first, whatever sort the caller sent
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return toResponses(auditLogEntryRepository.findAll(area.entries(organizationId), newestFirst));
    }

    private void requireOrganizationReader(ActivityArea area, UUID organizationId) {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID callerId = accessGuard.currentUserId();
        boolean allowed = accessGuard.hasRole(callerId, organizationId, OrganizationRole.OWNER)
                || (area.organizationReader() == ActivityArea.Reader.OWNER_OR_ORGANIZER
                    && accessGuard.hasRole(callerId, organizationId, OrganizationRole.ORGANIZER));
        if (!allowed) {
            throw new ForbiddenException(area.organizationReader() == ActivityArea.Reader.OWNER
                    ? "Only the organization's owner or an admin may see its " + area + " activity"
                    : "Only the organization's owner, organizer, or an admin may see its " + area + " activity");
        }
    }

    /** One lookup each for the page's actors and events; a deleted user still shows by id, the system as null. */
    private Page<AuditLogEntryResponse> toResponses(Page<AuditLogEntry> page) {
        // plain HashMaps: an entry's actor or event may be null (the system, a non-event action)
        Set<UUID> actorIds = page.stream().map(AuditLogEntry::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!actorIds.isEmpty()) {
            userRepository.findAllById(actorIds).forEach(user -> names.put(user.getId(), user.fullName()));
        }
        Set<UUID> eventIds = page.stream().map(AuditLogEntry::getEventId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> titles = new HashMap<>();
        if (!eventIds.isEmpty()) {
            eventRepository.findAllById(eventIds).forEach(event -> titles.put(event.getId(), event.getTitle()));
        }
        return page.map(entry -> AuditLogEntryResponse.from(entry, names.get(entry.getActorId()), titles.get(entry.getEventId())));
    }
}
