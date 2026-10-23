package com.junaldadlawan.event_ticketing_api.organization.controller;

import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationChangeRequestResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationCreateRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberAssignRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationMemberRolesRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationUpdateRequest;
import com.junaldadlawan.event_ticketing_api.organization.service.OrganizationChangeRequestService;
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
    private final OrganizationChangeRequestService changeRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse apply(@Valid @RequestBody OrganizationCreateRequest request) {
        return OrganizationResponse.from(organizationService.apply(request));
    }

    @GetMapping
    public List<OrganizationResponse> list(@RequestParam(required = false) String status) {
        return organizationService.list(status).stream().map(OrganizationResponse::from).toList();
    }

    // Declared before "/{orgId}" so "mine" is never read as an id.
    @GetMapping("/mine")
    public List<OrganizationResponse> mine() {
        return organizationService.mine().stream().map(OrganizationResponse::from).toList();
    }

    @GetMapping("/{orgId}")
    public OrganizationResponse get(@PathVariable UUID orgId) {
        return OrganizationResponse.from(organizationService.get(orgId));
    }

    /** BR-ORG-009: does not change the organization; stores a change request an admin approves. 202 with the request. */
    @PatchMapping("/{orgId}")
    public org.springframework.http.ResponseEntity<Object> update(@PathVariable UUID orgId, @Valid @RequestBody OrganizationUpdateRequest request) {
        if (organizationService.get(orgId).getStatus() == com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus.PENDING) {
            // BR-ORG-010: the applicant edits a pending application directly
            return org.springframework.http.ResponseEntity.ok(OrganizationResponse.from(changeRequestService.reviseApplication(orgId, request)));
        }
        return org.springframework.http.ResponseEntity.accepted().body(changeRequestService.request(orgId, request));
    }

    /** BR-ORG-010: an admin asks the applicant to revise a pending application; it stays pending, with the flag and the message. */
    @PostMapping("/{orgId}/request-revision")
    public OrganizationResponse requestRevision(@PathVariable UUID orgId, @Valid @RequestBody com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationRevisionRequest request) {
        return OrganizationResponse.from(changeRequestService.requestRevision(orgId, request.message()));
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
        return organizationService.listMembersWithDetails(orgId);
    }

    /** BR-ORG-008: the caller's own roles and permissions in this organization. */
    @GetMapping("/{orgId}/members/me")
    public OrganizationMemberResponse myMembership(@PathVariable UUID orgId) {
        return organizationService.myMembership(orgId);
    }

    /** BR-ORG-007: set a member's roles to exactly this set (owner only). */
    @PutMapping("/{orgId}/members/{userId}/roles")
    public OrganizationMemberResponse setMemberRoles(@PathVariable UUID orgId, @PathVariable UUID userId,
                                                     @Valid @RequestBody OrganizationMemberRolesRequest request) {
        return organizationService.setMemberRoles(orgId, userId, request.roles(), request.permissions());
    }

    /** BR-ORG-007: remove a member and all their roles (owner only). */
    @DeleteMapping("/{orgId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID orgId, @PathVariable UUID userId) {
        organizationService.removeMember(orgId, userId);
    }

    @PostMapping("/{orgId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationMemberResponse assignMember(@PathVariable UUID orgId,
                                                     @Valid @RequestBody OrganizationMemberAssignRequest request) {
        return OrganizationMemberResponse.from(organizationService.assignMember(orgId, request));
    }
}
