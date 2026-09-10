package com.junaldadlawan.event_ticketing_api.organization.repository;

import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMemberId;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMemberId> {
    Optional<OrganizationMember> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrganizationMember> findByOrganizationId(UUID organizationId);

    boolean existsByUserIdAndOrganizationIdAndRolesContaining(UUID userId, UUID organizationId, OrganizationRole role);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    boolean existsByUserIdAndRolesIn(UUID userId, Set<OrganizationRole> roles);
}
