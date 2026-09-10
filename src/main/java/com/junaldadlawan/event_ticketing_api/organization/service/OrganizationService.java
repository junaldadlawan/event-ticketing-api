package com.junaldadlawan.event_ticketing_api.organization.service;

import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationCreateRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberAssignRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {
    Organization apply(OrganizationCreateRequest request);

    List<Organization> list(String status);

    Organization get(UUID orgId);

    Organization update(UUID orgId, OrganizationUpdateRequest request);

    Organization approve(UUID orgId);

    Organization reject(UUID orgId, OrganizationRejectRequest request);

    List<OrganizationMember> listMembers(UUID orgId);

    OrganizationMember assignMember(UUID orgId, OrganizationMemberAssignRequest request);
}
