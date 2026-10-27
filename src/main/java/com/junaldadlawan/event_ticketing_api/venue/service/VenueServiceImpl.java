package com.junaldadlawan.event_ticketing_api.venue.service;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.region.service.RegionService;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import com.junaldadlawan.event_ticketing_api.venue.enums.VenueType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;
    private final RegionService regionService;

    @Override
    public Venue create(UUID orgId, VenueCreateRequest request) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + orgId + " not found"));
        // Phase 12 (BR-ADMIN-002): matches EventServiceImpl.createEvent's
        // existing APPROVED-only gate - a suspended/pending/rejected
        // organization can't stand up new venues either.
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new ForbiddenException("Organization is not approved");
        }
        UUID callerId = accessGuard.currentUserId();
        requireOwnerOrOrganizer(callerId, orgId);

        Venue venue = Venue.builder()
                .organizationId(orgId)
                .name(request.name())
                .address(text("address", request.address(), 500))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .venueType(venueType(request.venueType()))
                .country(country(request.country()))
                .province(text("province", request.province(), 100))
                .city(text("city", request.city(), 100))
                .description(text("description", request.description(), 2000))
                .maxCapacity(request.maxCapacity())
                .build();
        return venueRepository.save(venue);
    }

    @Override
    public List<Venue> list(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization " + orgId + " not found");
        }
        UUID callerId = accessGuard.currentUserId();
        if (!accessGuard.isMember(callerId, orgId) && !accessGuard.isAdmin()) {
            throw new ForbiddenException("Not authorized to view this organization's venues");
        }
        return venueRepository.findByOrganizationIdAndDeletedAtIsNull(orgId);
    }

    @Override
    public Venue get(UUID venueId) {
        return getOrThrow(venueId);
    }

    @Override
    public Venue update(UUID venueId, VenueUpdateRequest request) {
        Venue venue = getOrThrow(venueId);
        UUID callerId = accessGuard.currentUserId();
        requireOwnerOrOrganizer(callerId, venue.getOrganizationId());

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Venue name must not be blank");
            }
            venue.setName(request.name());
        }
        if (request.address() != null) {
            venue.setAddress(text("address", request.address(), 500));   // blank clears it
        }
        if (request.latitude() != null) {
            venue.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            venue.setLongitude(request.longitude());
        }
        // the detail fields: left out = unchanged, null (or blank) = cleared
        if (request.venueType() != null) {
            venue.setVenueType(venueType(request.venueType().orElse(null)));
        }
        if (request.country() != null) {
            venue.setCountry(country(request.country().orElse(null)));
        }
        if (request.province() != null) {
            venue.setProvince(text("province", request.province().orElse(null), 100));
        }
        if (request.city() != null) {
            venue.setCity(text("city", request.city().orElse(null), 100));
        }
        if (request.description() != null) {
            venue.setDescription(text("description", request.description().orElse(null), 2000));
        }
        if (request.maxCapacity() != null) {
            venue.setMaxCapacity(request.maxCapacity().orElse(null));
        }
        return venueRepository.save(venue);
    }

    /** Trimmed; blank is null; a too long or HTML value is a 400. */
    private static String text(String field, String value, int max) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new BadRequestException(field + " must be at most " + max + " characters");
        }
        if (trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) {
            throw new BadRequestException(field + " must not contain HTML");
        }
        return trimmed;
    }

    private static VenueType venueType(String value) {
        String trimmed = text("venueType", value, 200);
        if (trimmed == null) {
            return null;
        }
        try {
            return VenueType.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("venueType must be one of " + Arrays.stream(VenueType.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    /** The upper-case code of an enabled platform country, or a 400. */
    private String country(String value) {
        String trimmed = text("country", value, 200);
        return trimmed == null ? null : regionService.requireEnabledCountry(trimmed);
    }

    public Venue getOrThrow(UUID venueId) {
        return venueRepository.findById(venueId)
                .filter(venue -> venue.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + venueId + " not found"));
    }

    private void requireOwnerOrOrganizer(UUID callerId, UUID orgId) {
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)
                || accessGuard.hasPermission(callerId, orgId, OrganizationPermission.EVENTS);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner or organizer may manage its venues");
        }
    }
}
