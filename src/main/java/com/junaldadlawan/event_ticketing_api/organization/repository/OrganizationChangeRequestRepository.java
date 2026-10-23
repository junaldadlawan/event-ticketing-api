package com.junaldadlawan.event_ticketing_api.organization.repository;

import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationChangeRequest;
import com.junaldadlawan.event_ticketing_api.organization.enums.ChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationChangeRequestRepository extends JpaRepository<OrganizationChangeRequest, UUID> {

    Optional<OrganizationChangeRequest> findByIdAndDeletedAtIsNull(UUID id);

    Optional<OrganizationChangeRequest> findByOrganizationIdAndStatusAndDeletedAtIsNull(UUID organizationId, ChangeRequestStatus status);

    List<OrganizationChangeRequest> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    @Query("select r from OrganizationChangeRequest r where r.deletedAt is null and (:status is null or r.status = :status) order by r.createdAt desc")
    Page<OrganizationChangeRequest> search(@Param("status") ChangeRequestStatus status, Pageable pageable);
}
