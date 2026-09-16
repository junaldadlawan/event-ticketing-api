package com.junaldadlawan.event_ticketing_api.moderation.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.EventService;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionCreateRequest;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionResponse;
import com.junaldadlawan.event_ticketing_api.moderation.entity.ModerationAction;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationActionType;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import com.junaldadlawan.event_ticketing_api.moderation.repository.ModerationActionRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * BR-ADMIN-002. Original design confirmed this session (not in openapi.yaml/
 * the ERD): a single unified {@code ModerationAction} log doubles as the
 * reason/audit trail the roadmap calls for. Every {@code SUSPEND}/{@code
 * REINSTATE}/{@code REMOVE} mutates the target's REAL status/state (not
 * just a log entry) and, for {@code REMOVE}, reuses the target module's own
 * existing soft-delete rather than duplicating it.
 */
@Service
@RequiredArgsConstructor
public class ModerationActionServiceImpl implements ModerationActionService {

    private final ModerationActionRepository moderationActionRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OrganizationAccessGuard accessGuard;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Override
    public ModerationActionResponse create(ModerationActionCreateRequest request) {
        accessGuard.requireAdmin();
        UUID adminId = accessGuard.currentUserId();

        String previousStatus = switch (request.targetType()) {
            case ORGANIZATION -> applyToOrganization(request.targetId(), request.action());
            case EVENT -> applyToEvent(request.targetId(), request.action());
            case USER -> applyToUser(request.targetId(), request.action());
        };

        ModerationAction action = ModerationAction.builder()
                .targetType(request.targetType())
                .targetId(request.targetId())
                .action(request.action())
                .reason(request.reason())
                .previousStatus(previousStatus)
                .performedBy(adminId)
                .build();
        ModerationAction saved = moderationActionRepository.save(action);
        // BR-NFR-005 (Phase 13): sensitive-action audit trail, for every
        // action (SUSPEND/REINSTATE/REMOVE are all "admin interventions" per
        // BR-NFR-005's framing, not just suspend). Never throws.
        auditLogService.record(adminId, "moderation." + saved.getAction().name().toLowerCase(),
                saved.getTargetType().name(), saved.getTargetId());
        return ModerationActionResponse.from(saved);
    }

    @Override
    public Page<ModerationActionResponse> list(ModerationTargetType targetTypeFilter, UUID targetIdFilter, Pageable pageable) {
        accessGuard.requireAdmin();
        Page<ModerationAction> page;
        if (targetTypeFilter != null && targetIdFilter != null) {
            page = moderationActionRepository.findByTargetTypeAndTargetId(targetTypeFilter, targetIdFilter, pageable);
        } else if (targetTypeFilter != null) {
            page = moderationActionRepository.findByTargetType(targetTypeFilter, pageable);
        } else if (targetIdFilter != null) {
            page = moderationActionRepository.findByTargetId(targetIdFilter, pageable);
        } else {
            page = moderationActionRepository.findAll(pageable);
        }
        return page.map(ModerationActionResponse::from);
    }

    // ---- ORGANIZATION ----

    private String applyToOrganization(UUID organizationId, ModerationActionType action) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + organizationId + " not found"));

        return switch (action) {
            case SUSPEND -> {
                if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
                    throw new ConflictException("This organization is already suspended");
                }
                String previous = organization.getStatus().name();
                organization.setStatus(OrganizationStatus.SUSPENDED);
                organizationRepository.save(organization);
                notifyBestEffort(organization.getOwnerId());
                yield previous;
            }
            case REINSTATE -> {
                if (organization.getStatus() != OrganizationStatus.SUSPENDED) {
                    throw new ConflictException("This organization is not currently suspended");
                }
                OrganizationStatus restored = moderationActionRepository
                        .findFirstByTargetIdAndActionOrderByCreatedAtDesc(organizationId, ModerationActionType.SUSPEND)
                        .map(ModerationAction::getPreviousStatus)
                        .map(OrganizationStatus::valueOf)
                        .orElse(OrganizationStatus.APPROVED);
                organization.setStatus(restored);
                organizationRepository.save(organization);
                notifyBestEffort(organization.getOwnerId());
                yield OrganizationStatus.SUSPENDED.name();
            }
            case REMOVE -> {
                if (organization.getDeletedAt() != null) {
                    throw new ConflictException("This organization has already been removed");
                }
                String previous = organization.getStatus().name();
                organization.markDeleted();
                organizationRepository.save(organization);
                notifyBestEffort(organization.getOwnerId());
                yield previous;
            }
        };
    }

    // ---- EVENT ----

    private String applyToEvent(UUID eventId, ModerationActionType action) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));

        return switch (action) {
            case SUSPEND -> {
                if (event.getStatus() == EventStatus.SUSPENDED) {
                    throw new ConflictException("This event is already suspended");
                }
                String previous = event.getStatus().name();
                event.setStatus(EventStatus.SUSPENDED);
                eventRepository.save(event);
                yield previous;
            }
            case REINSTATE -> {
                if (event.getStatus() != EventStatus.SUSPENDED) {
                    throw new ConflictException("This event is not currently suspended");
                }
                // No single obvious pre-suspension default exists for an
                // Event (unlike Organization's APPROVED) - DRAFT is the
                // narrowest, safest fallback (every event starts there) if
                // somehow no prior SUSPEND row can be found.
                EventStatus restored = moderationActionRepository
                        .findFirstByTargetIdAndActionOrderByCreatedAtDesc(eventId, ModerationActionType.SUSPEND)
                        .map(ModerationAction::getPreviousStatus)
                        .map(EventStatus::valueOf)
                        .orElse(EventStatus.DRAFT);
                event.setStatus(restored);
                eventRepository.save(event);
                yield EventStatus.SUSPENDED.name();
            }
            case REMOVE -> {
                if (event.getDeletedAt() != null) {
                    throw new ConflictException("This event has already been removed");
                }
                String previous = event.getStatus().name();
                // Reuses EventServiceImpl's existing soft-delete rather than
                // duplicating the markDeleted()/save() one-liner - its own
                // owner/organizer-or-admin check trivially passes here since
                // this method already required admin above.
                eventService.delete(eventId);
                yield previous;
            }
        };
        // No per-event notification recipient: an Event has no single
        // obvious owner (BR-ADMIN-002 dispatch: "skip, no single obvious
        // recipient without extra lookups - keep this minimal").
    }

    // ---- USER ----

    private String applyToUser(UUID userId, ModerationActionType action) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        return switch (action) {
            case SUSPEND -> {
                if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
                    throw new ConflictException("This user is already suspended");
                }
                // Always ACTIVE beforehand - a User's accountStatus only ever
                // has two values, so no history lookup is needed (simpler
                // than Organization/Event's multi-value status).
                user.setAccountStatus(AccountStatus.SUSPENDED);
                userRepository.save(user);
                notifyBestEffort(userId);
                yield AccountStatus.ACTIVE.name();
            }
            case REINSTATE -> {
                if (user.getAccountStatus() != AccountStatus.SUSPENDED) {
                    throw new ConflictException("This user is not currently suspended");
                }
                user.setAccountStatus(AccountStatus.ACTIVE);
                userRepository.save(user);
                notifyBestEffort(userId);
                yield AccountStatus.SUSPENDED.name();
            }
            case REMOVE -> {
                if (user.getDeletedAt() != null) {
                    throw new ConflictException("This user has already been removed");
                }
                String previous = user.getAccountStatus().name();
                // Reuses UserServiceImpl's existing markDeleted() soft-delete.
                userService.delete(userId);
                notifyBestEffort(userId);
                yield previous;
            }
        };
    }

    /**
     * Nice-to-have, not the core requirement (per the confirmed design) -
     * best-effort, never throws (NFR 5.2). Skipped entirely when there's no
     * obvious recipient (e.g. an Organization with no ownerId set).
     */
    private void notifyBestEffort(UUID recipientId) {
        if (recipientId == null) {
            return;
        }
        notificationService.notify(recipientId, NotificationType.ACCOUNT_MODERATION_ACTION, "ModerationAction", null);
    }
}
