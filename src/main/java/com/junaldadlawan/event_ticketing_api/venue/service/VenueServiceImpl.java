package com.junaldadlawan.event_ticketing_api.venue.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueCreateRequest;
import com.junaldadlawan.event_ticketing_api.venue.dto.VenueUpdateRequest;
import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import com.junaldadlawan.event_ticketing_api.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessGuard accessGuard;

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
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
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
            venue.setAddress(request.address());
        }
        if (request.latitude() != null) {
            venue.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            venue.setLongitude(request.longitude());
        }
        return venueRepository.save(venue);
    }

    public Venue getOrThrow(UUID venueId) {
        return venueRepository.findById(venueId)
                .filter(venue -> venue.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + venueId + " not found"));
    }

    private void requireOwnerOrOrganizer(UUID callerId, UUID orgId) {
        boolean isOwnerOrOrganizer = accessGuard.hasRole(callerId, orgId, OrganizationRole.OWNER)
                || accessGuard.hasRole(callerId, orgId, OrganizationRole.ORGANIZER);
        if (!isOwnerOrOrganizer) {
            throw new ForbiddenException("Only the organization's owner or organizer may manage its venues");
        }
    }
}
