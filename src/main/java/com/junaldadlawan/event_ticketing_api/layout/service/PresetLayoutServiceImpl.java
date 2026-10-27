package com.junaldadlawan.event_ticketing_api.layout.service;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditDetails;
import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutRequest;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutResponse;
import com.junaldadlawan.event_ticketing_api.layout.dto.PresetLayoutSummary;
import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;
import com.junaldadlawan.event_ticketing_api.layout.repository.PresetLayoutRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayoutValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresetLayoutServiceImpl implements PresetLayoutService {

    private final PresetLayoutRepository repository;
    private final VenueLayoutValidator validator;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    public List<PresetLayoutSummary> list() {
        requireReader();
        // the admin also needs the inactive ones, to turn them back on
        List<PresetLayout> presets = accessGuard.isAdmin() ? repository.findByDeletedAtIsNullOrderByNameAsc()
                : repository.findByActiveTrueAndDeletedAtIsNullOrderByNameAsc();
        return presets.stream().map(PresetLayoutSummary::from).toList();
    }

    @Override
    public PresetLayoutResponse get(UUID presetId) {
        PresetLayout preset = getReadable(presetId);
        return PresetLayoutResponse.of(preset, objectMapper.readValue(preset.getLayout(), VenueLayout.class));
    }

    @Override
    public PresetLayout getReadable(UUID presetId) {
        requireReader();
        return repository.findByIdAndDeletedAtIsNull(presetId)
                .filter(preset -> preset.isActive() || accessGuard.isAdmin())
                .orElseThrow(() -> new ResourceNotFoundException("Layout preset " + presetId + " not found"));
    }

    @Override
    @Transactional
    public PresetLayoutResponse create(PresetLayoutRequest request) {
        accessGuard.requireAdmin();
        VenueLayout clean = validator.validate(request.layout());
        PresetLayout preset = PresetLayout.builder().version(1).active(request.active() == null || request.active()).build();
        return save(preset, request, clean, "layout_preset.created");
    }

    @Override
    @Transactional
    public PresetLayoutResponse replace(UUID presetId, PresetLayoutRequest request) {
        accessGuard.requireAdmin();
        PresetLayout preset = repository.findForUpdateByIdAndDeletedAtIsNull(presetId)
                .orElseThrow(() -> new ResourceNotFoundException("Layout preset " + presetId + " not found"));
        VenueLayout clean = validator.validate(request.layout());
        if (request.active() != null) {
            preset.setActive(request.active());
        }
        preset.setVersion(preset.getVersion() + 1);
        return save(preset, request, clean, "layout_preset.updated");
    }

    @Override
    @Transactional
    public void delete(UUID presetId) {
        accessGuard.requireAdmin();
        PresetLayout preset = repository.findByIdAndDeletedAtIsNull(presetId)
                .orElseThrow(() -> new ResourceNotFoundException("Layout preset " + presetId + " not found"));
        preset.markDeleted();
        repository.save(preset);
        auditLogService.recordByCurrentUser("layout_preset.deleted", "PresetLayout", presetId, null, AuditDetails.named(preset.getName()));
    }

    private PresetLayoutResponse save(PresetLayout preset, PresetLayoutRequest request, VenueLayout clean, String action) {
        String name = request.name().trim();
        repository.findByNameIgnoreCaseAndDeletedAtIsNull(name)
                .filter(other -> !other.getId().equals(preset.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A preset named \"" + other.getName() + "\" already exists");
                });
        preset.setName(name);
        preset.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        preset.setWidth(clean.width());
        preset.setHeight(clean.height());
        preset.setSeatCount(clean.seatCount());
        preset.setLayout(objectMapper.writeValueAsString(clean));
        PresetLayout saved = repository.save(preset);
        Map<String, Object> details = SeatLayoutServiceImpl.details(saved.getName(), saved.getVersion(), clean);
        details.put("active", saved.isActive());
        auditLogService.recordByCurrentUser(action, "PresetLayout", saved.getId(), null, details);
        return PresetLayoutResponse.of(saved, clean);
    }

    /** Admins, and anyone who is an owner or organizer of some organization. */
    private void requireReader() {
        if (!accessGuard.isAdmin() && accessGuard.managedOrganizationIds(accessGuard.currentUserId()).isEmpty()) {
            throw new ForbiddenException("Only organizers and admins may view layout presets");
        }
    }
}
