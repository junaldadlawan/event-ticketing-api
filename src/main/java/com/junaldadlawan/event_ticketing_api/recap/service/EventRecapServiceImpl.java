package com.junaldadlawan.event_ticketing_api.recap.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventCompletionService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageRequest;
import com.junaldadlawan.event_ticketing_api.recap.dto.RecapMessageResponse;
import com.junaldadlawan.event_ticketing_api.recap.entity.EventRecapMessage;
import com.junaldadlawan.event_ticketing_api.recap.repository.EventRecapMessageRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventRecapServiceImpl implements EventRecapService {

    /** Statuses a published event can be in; DRAFT and CANCELLED events have no public recap. */
    private static final Set<EventStatus> PUBLISHED = EnumSet.of(EventStatus.PUBLISHED, EventStatus.ON_SALE,
            EventStatus.SOLD_OUT, EventStatus.COMPLETED);

    private final EventRecapMessageRepository recapRepository;
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessGuard accessGuard;
    private final UploadedFileUrls uploadedFileUrls;
    private final AuditLogService auditLogService;

    @Override
    public RecapMessageResponse post(UUID eventId, RecapMessageRequest request) {
        Event event = getEventOrThrow(eventId);
        UUID callerId = requireEventsPermission(event);
        requireEnded(event);
        String body = request.body() == null ? "" : request.body().trim();
        String imageUrl = request.imageUrl() == null || request.imageUrl().isBlank() ? null : requireOwnImage(request.imageUrl());
        requireBodyOrImage(body, imageUrl);

        EventRecapMessage saved = recapRepository.save(EventRecapMessage.builder().eventId(eventId).authorUserId(callerId)
                .body(body).imageUrl(imageUrl).build());
        auditLogService.recordByCurrentUser("recap_message.created", "EventRecapMessage", saved.getId(), eventId, describe(saved));
        return RecapMessageResponse.from(saved, nameOf(callerId), organizationNameOf(event));
    }

    @Override
    @Transactional
    public RecapMessageResponse update(UUID eventId, UUID messageId, RecapMessageRequest request) {
        Event event = getEventOrThrow(eventId);
        requireEventsPermission(event);
        requireEnded(event);
        EventRecapMessage message = getMessageOrThrow(eventId, messageId);
        Map<String, Object> before = AuditDetails.snapshot(message, RecapMessageRequest.class);
        String previousImage = message.getImageUrl();

        if (request.body() != null) {
            message.setBody(request.body().trim());
        }
        if (request.imageUrl() != null) {
            message.setImageUrl(request.imageUrl().isBlank() ? null : requireOwnImage(request.imageUrl()));
        }
        requireBodyOrImage(message.getBody(), message.getImageUrl());

        EventRecapMessage saved = recapRepository.save(message);
        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(saved, RecapMessageRequest.class));
        if (changes != null) {
            auditLogService.recordByCurrentUser("recap_message.updated", "EventRecapMessage", messageId, eventId, changes);
        }
        if (previousImage != null && !Objects.equals(previousImage, saved.getImageUrl())) {
            uploadedFileUrls.deleteIfUnreferenced(previousImage);
        }
        return RecapMessageResponse.from(saved, nameOf(saved.getAuthorUserId()), organizationNameOf(event));
    }

    @Override
    @Transactional
    public void delete(UUID eventId, UUID messageId) {
        Event event = getEventOrThrow(eventId);
        if (!accessGuard.isAdmin()) {
            requireEventsPermission(event);
        }
        EventRecapMessage message = getMessageOrThrow(eventId, messageId);
        message.markDeleted();
        recapRepository.save(message);
        auditLogService.recordByCurrentUser("recap_message.deleted", "EventRecapMessage", messageId, eventId, describe(message));
        if (message.getImageUrl() != null) {
            uploadedFileUrls.deleteIfUnreferenced(message.getImageUrl());
        }
    }

    @Override
    public List<RecapMessageResponse> feed(UUID eventId) {
        Event event = getEventOrThrow(eventId);
        boolean isPublic = PUBLISHED.contains(event.getStatus()) && EventCompletionService.hasEnded(event);
        if (!isPublic && !mayManage(event)) {
            throw new ResourceNotFoundException("Event " + eventId + " has no public recap");
        }
        String organizationName = organizationNameOf(event);
        return recapRepository.findFeed(eventId).stream()
                .map(row -> RecapMessageResponse.from((EventRecapMessage) row[0], fullName((String) row[1], (String) row[2]), organizationName))
                .toList();
    }

    /** The caller, when they are the owner or an organizer with EVENTS (BR-ORG-008); 403 otherwise. */
    private UUID requireEventsPermission(Event event) {
        UUID callerId = accessGuard.currentUserId();
        if (!accessGuard.hasPermission(callerId, event.getOrganizationId(), OrganizationPermission.EVENTS)) {
            throw new ForbiddenException("Only the organization's owner or an organizer with the Events permission may post to the recap");
        }
        return callerId;
    }

    /** For the feed, where the caller may be signed out: owner / EVENTS organizer, or an admin. */
    private boolean mayManage(Event event) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }
        return accessGuard.isAdmin()
                || accessGuard.hasPermission(accessGuard.currentUserId(), event.getOrganizationId(), OrganizationPermission.EVENTS);
    }

    private static void requireEnded(Event event) {
        if (!EventCompletionService.hasEnded(event)) {
            throw new ConflictException("The recap opens once the event has ended");
        }
    }

    private static void requireBodyOrImage(String body, String imageUrl) {
        if ((body == null || body.isBlank()) && imageUrl == null) {
            throw new BadRequestException("A recap message needs a body or an image");
        }
    }

    /** The trimmed URL when it is one of our own existing uploads, else a 400 (as for posts). */
    private String requireOwnImage(String url) {
        if (uploadedFileUrls.ownFileName(url).isEmpty()) {
            throw new BadRequestException("imageUrl must be the URL of an image uploaded through POST /api/v1/uploads");
        }
        return url.trim();
    }

    private static Map<String, Object> describe(EventRecapMessage message) {
        String body = message.getBody();
        return AuditDetails.named(body.isBlank() ? "(picture)" : body.length() > 80 ? body.substring(0, 80) + "…" : body);
    }

    private EventRecapMessage getMessageOrThrow(UUID eventId, UUID messageId) {
        return recapRepository.findByIdAndEventIdAndDeletedAtIsNull(messageId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Recap message " + messageId + " not found"));
    }

    private Event getEventOrThrow(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    private String nameOf(UUID userId) {
        return userRepository.findById(userId).map(u -> fullName(u.getFirstName(), u.getLastName())).orElse(null);
    }

    private String organizationNameOf(Event event) {
        return organizationRepository.findById(event.getOrganizationId()).map(Organization::getName).orElse(null);
    }

    private static String fullName(String first, String last) {
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }
}
