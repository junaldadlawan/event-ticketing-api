package com.junaldadlawan.event_ticketing_api.venue.repository;

import com.junaldadlawan.event_ticketing_api.venue.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    List<Venue> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
