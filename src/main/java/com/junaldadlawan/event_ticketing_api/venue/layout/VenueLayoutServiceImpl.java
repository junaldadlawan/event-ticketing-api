package com.junaldadlawan.event_ticketing_api.venue.layout;

import com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueLayoutServiceImpl implements VenueLayoutService {

    /** What an empty plan looks like before a layout is saved. */
    static final double DEFAULT_WIDTH = 1000;
    static final double DEFAULT_HEIGHT = 800;

    private final VenueService venueService;
    private final VenueLayoutRepository repository;
    private final VenueLayoutValidator validator;
    private final OrganizationAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    @Override
    public VenueLayoutResponse get(UUID venueId) {
        Venue venue = venueService.get(venueId);
        if (!accessGuard.isAdmin()) {
            UUID callerId = accessGuard.currentUserId();
            if (!accessGuard.isMember(callerId, venue.getOrganizationId())) {
                throw new ForbiddenException("Only members of the venue's organization or an admin may view its seat layout");
            }
        }
        return repository.find(venueId)
                .map(stored -> VenueLayoutResponse.of(venueId, stored.version(), stored.layout()))
                .orElseGet(() -> new VenueLayoutResponse(venueId, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT, List.of(), List.of()));
    }

    @Override
    public VenueLayoutResponse replace(UUID venueId, VenueLayout layout) {
        Venue venue = venueService.get(venueId);
        UUID callerId = accessGuard.currentUserId();
        // the same rule as editing the venue itself
        if (!accessGuard.hasRole(callerId, venue.getOrganizationId(), OrganizationRole.OWNER)
                && !accessGuard.hasPermission(callerId, venue.getOrganizationId(), OrganizationPermission.EVENTS)) {
            throw new ForbiddenException("Only the organization's owner or organizer may edit its venues' seat layouts");
        }
        VenueLayout clean = validator.validate(layout);
        int version = repository.save(venueId, clean, callerId.toString());

        int seats = clean.seatCount();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", venue.getName());
        details.put("version", version);
        details.put("sections", clean.sections().size());
        details.put("seats", seats);
        auditLogService.recordByCurrentUser("venue.layout.updated", "Venue", venueId, null, venue.getOrganizationId(), details);
        return VenueLayoutResponse.of(venueId, version, clean);
    }
}
