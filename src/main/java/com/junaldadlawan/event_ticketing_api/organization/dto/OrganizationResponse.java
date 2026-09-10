package com.junaldadlawan.event_ticketing_api.organization.dto;

import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Organization}
 */
public record OrganizationResponse(
        UUID id,
        String name,
        OrganizationStatus status,
        List<DocumentDto> documents,
        UUID ownerId,
        String rejectionReason,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) implements Serializable {

    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getStatus(),
                organization.getDocuments().stream().map(DocumentDto::from).toList(),
                organization.getOwnerId(),
                organization.getRejectionReason(),
                organization.getCreatedBy(),
                organization.getCreatedAt(),
                organization.getUpdatedBy(),
                organization.getUpdatedAt());
    }
}
