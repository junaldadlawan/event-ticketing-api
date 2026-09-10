package com.junaldadlawan.event_ticketing_api.organization.repository;

import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {
}
