package com.junaldadlawan.event_ticketing_api.layout.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.SeatLayoutSummary;
import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;
import com.junaldadlawan.event_ticketing_api.layout.entity.SeatLayout;
import com.junaldadlawan.event_ticketing_api.layout.repository.SeatLayoutRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayoutValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLayoutServiceImpl implements SeatLayoutService {

    private final SeatLayoutRepository repository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final PresetLayoutService presetLayoutService;
    private final VenueLayoutValidator validator;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    public List<SeatLayoutSummary> list(UUID orgId) {
        requireOrganization(orgId);
        requireReader(orgId);
        return repository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(orgId).stream().map(SeatLayoutSummary::from).toList();
    }

    @Override
    @Transactional
    public SeatLayoutResponse create(UUID orgId, SeatLayoutRequest request) {
        requireOrganization(orgId);
        requireEditor(orgId);
        return saveNew(orgId, request.name().trim(), validator.validate(request.layout()), null);
    }

    @Override
    public SeatLayoutResponse get(UUID layoutId) {
        SeatLayout layout = getOrThrow(layoutId);
        requireReader(layout.getOrganizationId());
        return SeatLayoutResponse.of(layout, objectMapper.readValue(layout.getLayout(), VenueLayout.class));
    }

    @Override
    @Transactional
    public SeatLayoutResponse replace(UUID layoutId, SeatLayoutRequest request) {
        SeatLayout layout = repository.findForUpdateByIdAndDeletedAtIsNull(layoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Layout " + layoutId + " not found"));
        requireEditor(layout.getOrganizationId());
        VenueLayout clean = validator.validate(request.layout());
        String name = request.name().trim();
        requireFreeName(layout.getOrganizationId(), name, layoutId);

        layout.setName(name);
        layout.setVersion(layout.getVersion() + 1);
        apply(layout, clean);
        SeatLayout saved = repository.save(layout);
        auditLogService.recordByCurrentUser("layout.updated", "SeatLayout", saved.getId(), null, saved.getOrganizationId(),
                details(saved.getName(), saved.getVersion(), clean));
        return SeatLayoutResponse.of(saved, clean);
    }

    @Override
    @Transactional
    public void delete(UUID layoutId) {
        SeatLayout layout = getOrThrow(layoutId);
        requireEditor(layout.getOrganizationId());
        if (eventRepository.existsByLayoutIdAndDeletedAtIsNullAndEndAtAfterAndStatusNot(layoutId, Instant.now(), EventStatus.CANCELLED)) {
            throw new ConflictException("An upcoming event still uses this layout, so it cannot be deleted");
        }
        layout.markDeleted();
        repository.save(layout);
        auditLogService.recordByCurrentUser("layout.deleted", "SeatLayout", layoutId, null, layout.getOrganizationId(), AuditDetails.named(layout.getName()));
    }

    @Override
    @Transactional
    public SeatLayoutResponse copyPreset(UUID orgId, UUID presetId) {
        requireOrganization(orgId);
        requireEditor(orgId);
        PresetLayout preset = presetLayoutService.getReadable(presetId);
        return saveNew(orgId, preset.getName(), objectMapper.readValue(preset.getLayout(), VenueLayout.class), presetId);
    }

    private SeatLayoutResponse saveNew(UUID orgId, String name, VenueLayout clean, UUID presetId) {
        requireFreeName(orgId, name, null);
        SeatLayout layout = SeatLayout.builder().organizationId(orgId).name(name).version(1).build();
        apply(layout, clean);
        SeatLayout saved = repository.save(layout);
        Map<String, Object> details = details(saved.getName(), saved.getVersion(), clean);
        if (presetId != null) {
            details.put("presetId", presetId);
        }
        auditLogService.recordByCurrentUser("layout.created", "SeatLayout", saved.getId(), null, orgId, details);
        return SeatLayoutResponse.of(saved, clean);
    }

    private void apply(SeatLayout layout, VenueLayout clean) {
        layout.setWidth(clean.width());
        layout.setHeight(clean.height());
        layout.setSeatCount(clean.seatCount());
        layout.setLayout(objectMapper.writeValueAsString(clean));
    }

    /** What layout.* and layout_preset.* entries record: the name, version and size, not the seats themselves. */
    static Map<String, Object> details(String name, int version, VenueLayout layout) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("version", version);
        details.put("sections", layout.sections().size());
        details.put("seats", layout.seatCount());
        return details;
    }

    private void requireFreeName(UUID orgId, String name, UUID self) {
        repository.findByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(orgId, name)
                .filter(other -> !other.getId().equals(self))
                .ifPresent(other -> {
                    throw new ConflictException("The organization already has a layout named \"" + other.getName() + "\"");
                });
    }

    private SeatLayout getOrThrow(UUID layoutId) {
        return repository.findByIdAndDeletedAtIsNull(layoutId).orElseThrow(() -> new ResourceNotFoundException("Layout " + layoutId + " not found"));
    }

    private void requireOrganization(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization " + orgId + " not found");
        }
    }

    private void requireReader(UUID orgId) {
        if (!accessGuard.isAdmin() && !accessGuard.isMember(accessGuard.currentUserId(), orgId)) {
            throw new ForbiddenException("Only members of the organization or an admin may view its seat layouts");
        }
    }

    private void requireEditor(UUID orgId) {
        UUID callerId = accessGuard.currentUserId();
        if (!accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER) && !accessGuard.hasPermission(callerId, orgId, OrganizationPermission.EVENTS)) {
            throw new ForbiddenException("Only the organization's owner or organizer may edit its seat layouts");
        }
    }
}
