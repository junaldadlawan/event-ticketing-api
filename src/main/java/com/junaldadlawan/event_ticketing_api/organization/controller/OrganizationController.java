package com.junaldadlawan.event_ticketing_api.organization.controller;

import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationCreateRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberAssignRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse apply(@Valid @RequestBody OrganizationCreateRequest request) {
        return OrganizationResponse.from(organizationService.apply(request));
    }

    @GetMapping
    public List<OrganizationResponse> list(@RequestParam(required = false) String status) {
        return organizationService.list(status).stream().map(OrganizationResponse::from).toList();
    }

    @GetMapping("/{orgId}")
    public OrganizationResponse get(@PathVariable UUID orgId) {
        return OrganizationResponse.from(organizationService.get(orgId));
    }

    @PatchMapping("/{orgId}")
    public OrganizationResponse update(@PathVariable UUID orgId, @Valid @RequestBody OrganizationUpdateRequest request) {
        return OrganizationResponse.from(organizationService.update(orgId, request));
    }

    @PostMapping("/{orgId}/approve")
    public OrganizationResponse approve(@PathVariable UUID orgId) {
        return OrganizationResponse.from(organizationService.approve(orgId));
    }

    @PostMapping("/{orgId}/reject")
    public OrganizationResponse reject(@PathVariable UUID orgId,
                                        @RequestBody(required = false) OrganizationRejectRequest request) {
        return OrganizationResponse.from(organizationService.reject(orgId, request));
    }

    @GetMapping("/{orgId}/members")
    public List<OrganizationMemberResponse> listMembers(@PathVariable UUID orgId) {
        return organizationService.listMembers(orgId).stream().map(OrganizationMemberResponse::from).toList();
    }

    @PostMapping("/{orgId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationMemberResponse assignMember(@PathVariable UUID orgId,
                                                     @Valid @RequestBody OrganizationMemberAssignRequest request) {
        return OrganizationMemberResponse.from(organizationService.assignMember(orgId, request));
    }
}
