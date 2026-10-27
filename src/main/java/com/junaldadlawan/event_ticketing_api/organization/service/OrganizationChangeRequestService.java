package com.junaldadlawan.event_ticketing_api.organization.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationChangeRequestResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationChangeRequest;
import com.junaldadlawan.event_ticketing_api.organization.enums.ChangeRequestStatus;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationChangeRequestRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.region.service.RegionService;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BR-ORG-009: an APPROVED organization is edited through a change request. The owner (or an organizer with TEAM) asks; the organization changes
 * only when an admin approves. One PENDING request per organization: asking again replaces it (the same row, with the new values and the new asker).
 * The editable details are the name and the optional contact details; the documents (a list that REPLACES the current ones on approval, at least one) are editable here too, and a name change does not trigger a
 * new document check (the admin sees the old and the new name side by side and decides).
 */
@Service
@RequiredArgsConstructor
public class OrganizationChangeRequestService {

    private static final List<String> FIELDS = List.of("name", "contactEmail", "contactPhone", "address", "description", "country");
    private static final Map<String, Function<Organization, String>> GETTERS = Map.of(
            "name", Organization::getName, "contactEmail", Organization::getContactEmail, "contactPhone", Organization::getContactPhone,
            "address", Organization::getAddress, "description", Organization::getDescription, "country", Organization::getCountry);
    private static final Map<String, BiConsumer<Organization, String>> SETTERS = Map.of(
            "name", Organization::setName, "contactEmail", Organization::setContactEmail, "contactPhone", Organization::setContactPhone,
            "address", Organization::setAddress, "description", Organization::setDescription, "country", Organization::setCountry);

    private final OrganizationChangeRequestRepository requestRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final RegionService regionService;
    private final ObjectMapper objectMapper;

    // ---- a pending application: revision asked by an admin, edited by the applicant (BR-ORG-010) ----

    /** Admin only: the application stays PENDING, gets the revision flag with the message and the date, and the applicant is told. */
    @Transactional
    public Organization requestRevision(UUID orgId, String message) {
        accessGuard.requireAdmin();
        Organization organization = organizationOrThrow(orgId);
        if (organization.getStatus() != OrganizationStatus.PENDING) {
            throw new ConflictException("Only a pending application can be sent back for revision");
        }
        String text = message.trim();
        organization.setRevisionRequested(true);
        organization.setRevisionMessage(text);
        organization.setRevisionRequestedAt(Instant.now());
        Organization saved = organizationRepository.save(organization);
        Map<String, Object> details = AuditDetails.named(organization.getName());
        details.put("message", text);
        auditLogService.recordByCurrentUser("organization.revision_requested", "Organization", orgId, null, orgId, details);
        applicantOf(organization).ifPresent(applicant -> notificationService.notifyWithMessage(applicant, NotificationType.ORGANIZATION_REVISION_REQUESTED,
                "Organization", orgId, "Please revise your application for " + organization.getName() + ": " + text));
        return saved;
    }

    /**
     * The applicant edits their own PENDING application directly: name, details and documents (no change request yet). Clears the revision flag and
     * tells the admins. Only the applicant (the creator) may.
     */
    @Transactional
    public Organization reviseApplication(UUID orgId, OrganizationUpdateRequest body) {
        Organization organization = organizationOrThrow(orgId);
        UUID callerId = accessGuard.currentUserId();
        if (organization.getStatus() != OrganizationStatus.PENDING || !applicantOf(organization).map(callerId::equals).orElse(false)) {
            throw new ForbiddenException("Only the applicant may edit a pending application");
        }
        Map<String, Object> changed = changedFields(organization, body);
        requireSupportedCountry(changed);
        if (changed.isEmpty()) {
            throw new BadRequestException("Nothing to change: the values sent are the application's current ones");
        }
        Map<String, Object> before = valuesOf(organization, changed.keySet());
        applyChanges(organization, changed);
        organization.setRevisionRequested(false);
        organization.setRevisionMessage(null);
        organization.setRevisionRequestedAt(null);
        Organization saved = organizationRepository.save(organization);
        Map<String, Object> changes = AuditDetails.changes(before, new LinkedHashMap<>(changed));
        Map<String, Object> details = changes == null ? AuditDetails.named(organization.getName()) : new LinkedHashMap<>(changes);
        auditLogService.recordByCurrentUser("organization.application_revised", "Organization", orgId, null, orgId, details);
        String text = organization.getName() + " revised its application. Review it again.";
        for (UUID adminId : userRepository.findIdsByRoleIn(EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN))) {
            notificationService.notifyWithMessage(adminId, NotificationType.ORGANIZATION_APPLICATION_REVISED, "Organization", orgId, text);
        }
        return saved;
    }

    /** The applicant is the creator of the application (its createdBy is their user id). */
    private static java.util.Optional<UUID> applicantOf(Organization organization) {
        try {
            return java.util.Optional.of(UUID.fromString(organization.getCreatedBy()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }

    // ---- the organization's side ----

    @Transactional
    public OrganizationChangeRequestResponse request(UUID orgId, OrganizationUpdateRequest body) {
        Organization organization = organizationOrThrow(orgId);
        UUID callerId = accessGuard.currentUserId();
        if (!accessGuard.hasPermission(callerId, orgId, OrganizationPermission.TEAM)) {
            throw new ForbiddenException("Only the organization's owner, or an organizer with the TEAM permission, may request a change");
        }
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Only an approved organization may be updated");
        }
        Map<String, Object> requested = changedFields(organization, body);
        requireSupportedCountry(requested);
        if (requested.isEmpty()) {
            throw new BadRequestException("Nothing to change: the values sent are the organization's current ones");
        }

        OrganizationChangeRequest pending = requestRepository.findByOrganizationIdAndStatusAndDeletedAtIsNull(orgId, ChangeRequestStatus.PENDING).orElse(null);
        boolean replaced = pending != null;
        if (replaced) {
            pending.setChangesJson(write(requested));
            pending.setRequestedBy(callerId);
        } else {
            pending = OrganizationChangeRequest.builder().organizationId(orgId).requestedBy(callerId).changesJson(write(requested)).build();
        }
        OrganizationChangeRequest saved = requestRepository.save(pending);

        Map<String, Object> changes = AuditDetails.changes(valuesOf(organization, requested.keySet()), new LinkedHashMap<>(requested));
        Map<String, Object> details = changes == null ? null : new LinkedHashMap<>(changes);
        if (details != null && replaced) {
            details.put("replacedPendingRequest", true);
        }
        auditLogService.recordByCurrentUser("organization.change_requested", "Organization", orgId, null, orgId, details);
        String message = organization.getName() + " asked to change its details (" + String.join(", ", requested.keySet()) + "). Review it in the organization change requests.";
        for (UUID adminId : userRepository.findIdsByRoleIn(EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN))) {
            notificationService.notifyWithMessage(adminId, NotificationType.ORGANIZATION_CHANGE_REQUESTED, "OrganizationChangeRequest", saved.getId(), message);
        }
        return toResponse(saved, organization);
    }

    /** Members and admins: the pending request and the history, newest first. */
    @Transactional(readOnly = true)
    public List<OrganizationChangeRequestResponse> list(UUID orgId) {
        Organization organization = organizationOrThrow(orgId);
        if (!accessGuard.isAdmin() && !accessGuard.isMember(accessGuard.currentUserId(), orgId)) {
            throw new ForbiddenException("Only the organization's members or an admin may see its change requests");
        }
        return requestRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(orgId).stream()
                .map(request -> toResponse(request, organization)).toList();
    }

    /** The requester withdraws their own PENDING request (a soft delete: it leaves the list, the audit log keeps it). */
    @Transactional
    public void withdraw(UUID orgId, UUID requestId) {
        OrganizationChangeRequest request = requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .filter(r -> r.getOrganizationId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Change request " + requestId + " not found"));
        if (!request.getRequestedBy().equals(accessGuard.currentUserId())) {
            throw new ForbiddenException("Only the person who made a change request may withdraw it");
        }
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ConflictException("Only a pending change request can be withdrawn");
        }
        request.markDeleted();
        requestRepository.save(request);
        auditLogService.recordByCurrentUser("organization.change_withdrawn", "Organization", orgId, null, orgId, null);
    }

    // ---- the admin's side ----

    @Transactional(readOnly = true)
    public PageResponse<OrganizationChangeRequestResponse> search(ChangeRequestStatus status, Pageable pageable) {
        accessGuard.requireAdmin();
        var page = requestRepository.search(status, pageable);
        Map<UUID, Organization> organizations = organizationRepository.findAllById(page.getContent().stream().map(OrganizationChangeRequest::getOrganizationId).distinct().toList())
                .stream().collect(Collectors.toMap(Organization::getId, o -> o));
        return PageResponse.from(page.map(request -> toResponse(request, organizations.get(request.getOrganizationId()))));
    }

    @Transactional
    public OrganizationChangeRequestResponse approve(UUID requestId) {
        accessGuard.requireAdmin();
        OrganizationChangeRequest request = pendingOrThrow(requestId);
        Organization organization = organizationOrThrow(request.getOrganizationId());
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ConflictException("The organization is no longer approved, so its details cannot be changed");
        }
        Map<String, Object> requested = read(request.getChangesJson());
        requireSupportedCountry(requested);   // it may have been disabled since it was asked for
        Map<String, Object> before = valuesOf(organization, requested.keySet());   // includes the replaced documents: they stay in the audit trail
        applyChanges(organization, requested);
        organizationRepository.save(organization);
        decide(request, ChangeRequestStatus.APPROVED, null);
        auditLogService.recordByCurrentUser("organization.change_approved", "Organization", organization.getId(), null, organization.getId(),
                AuditDetails.changes(before, new LinkedHashMap<>(requested)));
        notificationService.notifyWithMessage(request.getRequestedBy(), NotificationType.ORGANIZATION_CHANGE_DECIDED, "OrganizationChangeRequest", request.getId(),
                "Your change to " + organization.getName() + " was approved and is now live.");
        return toResponse(request, organization);
    }

    @Transactional
    public OrganizationChangeRequestResponse reject(UUID requestId, String reason) {
        accessGuard.requireAdmin();
        OrganizationChangeRequest request = pendingOrThrow(requestId);
        Organization organization = organizationOrThrow(request.getOrganizationId());
        decide(request, ChangeRequestStatus.REJECTED, reason.trim());
        Map<String, Object> details = AuditDetails.named(organization.getName());
        details.put("reason", reason.trim());
        auditLogService.recordByCurrentUser("organization.change_rejected", "Organization", organization.getId(), null, organization.getId(), details);
        notificationService.notifyWithMessage(request.getRequestedBy(), NotificationType.ORGANIZATION_CHANGE_DECIDED, "OrganizationChangeRequest", request.getId(),
                "Your change to " + organization.getName() + " was rejected: " + reason.trim());
        return toResponse(request, organization);
    }

    // ---- helpers ----

    /** A country change must name an enabled platform country (400 otherwise). */
    private void requireSupportedCountry(Map<String, Object> changes) {
        if (changes.get("country") instanceof String country) {
            regionService.requireEnabledCountry(country);
        }
    }

    private void decide(OrganizationChangeRequest request, ChangeRequestStatus status, String reason) {
        request.setStatus(status);
        request.setDecidedBy(accessGuard.currentUserId());
        request.setDecidedAt(Instant.now());
        request.setRejectionReason(reason);
        requestRepository.save(request);
    }

    private OrganizationChangeRequest pendingOrThrow(UUID requestId) {
        OrganizationChangeRequest request = requestRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request " + requestId + " not found"));
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ConflictException("This change request was already " + request.getStatus().name().toLowerCase());
        }
        return request;
    }

    private Organization organizationOrThrow(UUID orgId) {
        return organizationRepository.findById(orgId).filter(o -> o.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + orgId + " not found"));
    }

    /** The fields sent that differ from the organization's current values. An empty string for a detail clears it; the name cannot be blank. */
    private static Map<String, Object> changedFields(Organization organization, OrganizationUpdateRequest body) {
        Map<String, String> sent = new LinkedHashMap<>();
        sent.put("name", body.name());
        sent.put("contactEmail", body.contactEmail());
        sent.put("contactPhone", body.contactPhone());
        sent.put("address", body.address());
        sent.put("description", body.description());
        sent.put("country", body.country());
        Map<String, Object> changed = new LinkedHashMap<>();
        for (String field : FIELDS) {
            String value = sent.get(field);
            if (value == null) {
                continue;   // not sent = not changed
            }
            String trimmed = field.equals("country") ? value.trim().toUpperCase(java.util.Locale.ROOT) : value.trim();
            if ((field.equals("name") || field.equals("country")) && trimmed.isEmpty()) {
                throw new BadRequestException(field + " must not be blank");
            }
            String normalized = trimmed.isEmpty() ? null : trimmed;
            if (!java.util.Objects.equals(normalized, GETTERS.get(field).apply(organization))) {
                changed.put(field, normalized);
            }
        }
        if (body.documents() != null) {
            if (body.documents().isEmpty()) {
                throw new BadRequestException("At least one document is required");
            }
            List<Map<String, String>> requestedDocuments = body.documents().stream().map(d -> document(d.type().trim(), d.url().trim())).toList();
            if (!requestedDocuments.equals(documentsOf(organization))) {   // the same files again are not a change
                changed.put("documents", requestedDocuments);
            }
        }
        return changed;
    }

    private static Map<String, String> document(String type, String url) {
        Map<String, String> document = new LinkedHashMap<>();
        document.put("type", type);
        document.put("url", url);
        return document;
    }

    private static List<Map<String, String>> documentsOf(Organization organization) {
        return organization.getDocuments().stream().map(d -> document(d.getType(), d.getUrl())).toList();
    }

    /** The organization's current value of a field: text, or for documents the list of {type, url}. */
    private static Object valueOf(Organization organization, String field) {
        return field.equals("documents") ? documentsOf(organization) : GETTERS.get(field).apply(organization);
    }

    /** Applies the requested values; the documents list REPLACES the organization's documents. */
    @SuppressWarnings("unchecked")
    private static void applyChanges(Organization organization, Map<String, Object> changes) {
        changes.forEach((field, value) -> {
            if (field.equals("documents")) {
                organization.getDocuments().clear();
                for (Map<String, String> document : (List<Map<String, String>>) value) {
                    organization.getDocuments().add(com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationDocument.builder()
                            .type(document.get("type")).url(document.get("url")).build());
                }
            } else {
                SETTERS.get(field).accept(organization, (String) value);
            }
        });
    }

    private static Map<String, Object> valuesOf(Organization organization, Iterable<String> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            values.put(field, valueOf(organization, field));
        }
        return values;
    }

    private OrganizationChangeRequestResponse toResponse(OrganizationChangeRequest request, Organization organization) {
        Map<String, Object> requested = read(request.getChangesJson());
        Map<String, Object> current = new LinkedHashMap<>();
        if (organization != null) {
            requested.keySet().forEach(field -> current.put(field, valueOf(organization, field)));
        }
        return new OrganizationChangeRequestResponse(request.getId(), request.getOrganizationId(), organization == null ? null : organization.getName(),
                request.getRequestedBy(), requested, current, request.getStatus(), request.getCreatedAt(), request.getDecidedBy(),
                request.getDecidedAt(), request.getRejectionReason());
    }

    private String write(Map<String, Object> changes) {
        return objectMapper.writeValueAsString(changes);
    }

    private Map<String, Object> read(String json) {
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
    }
}
