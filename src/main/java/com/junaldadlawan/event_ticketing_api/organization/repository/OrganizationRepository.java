package com.junaldadlawan.event_ticketing_api.organization.repository;

import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {

    /** Organizations a user applied for ({@code createdBy} is the applicant's user id), whatever their status. */
    List<Organization> findByCreatedByAndDeletedAtIsNull(String createdBy);

    /** Phase 14 (BR-ANALYTICS-002): "active organizers" - approved, not removed. */
    long countByStatusAndDeletedAtIsNull(OrganizationStatus status);
}
