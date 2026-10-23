package com.junaldadlawan.event_ticketing_api.organization.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationChangeRejectRequest;
import com.junaldadlawan.event_ticketing_api.organization.dto.OrganizationChangeRequestResponse;
import com.junaldadlawan.event_ticketing_api.organization.enums.ChangeRequestStatus;
import com.junaldadlawan.event_ticketing_api.organization.service.OrganizationChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** BR-ORG-009: change requests of an approved organization, from the organization's side and from the admin's. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrganizationChangeRequestController {

    private final OrganizationChangeRequestService service;

    @GetMapping("/organizations/{orgId}/change-requests")
    public List<OrganizationChangeRequestResponse> list(@PathVariable UUID orgId) {
        return service.list(orgId);
    }

    @DeleteMapping("/organizations/{orgId}/change-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID orgId, @PathVariable UUID id) {
        service.withdraw(orgId, id);
    }

    @GetMapping("/admin/organization-change-requests")
    public PageResponse<OrganizationChangeRequestResponse> search(@RequestParam(required = false) ChangeRequestStatus status,
                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return service.search(status, pageable);
    }

    @PostMapping("/admin/organization-change-requests/{id}/approve")
    public OrganizationChangeRequestResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/admin/organization-change-requests/{id}/reject")
    public OrganizationChangeRequestResponse reject(@PathVariable UUID id, @Valid @RequestBody OrganizationChangeRejectRequest request) {
        return service.reject(id, request.reason());
    }
}
