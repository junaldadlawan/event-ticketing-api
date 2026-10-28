package com.junaldadlawan.event_ticketing_api.organization.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private OrganizationStatus status = OrganizationStatus.PENDING;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** An admin asked the applicant to revise this pending application (BR-ORG-010); cleared when the applicant edits it or it is decided. */
    @Column(name = "revision_requested", nullable = false)
    @Builder.Default
    private boolean revisionRequested = false;

    @Column(name = "revision_message", length = 500)
    private String revisionMessage;

    @Column(name = "revision_requested_at")
    private java.time.Instant revisionRequestedAt;

    /** Optional details, changed through an admin-approved change request (BR-ORG-009). */
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "description", length = 2000)
    private String description;

    /** ISO 3166-1 alpha-2, an enabled platform country when chosen; PH for organizations from before countries existed. */
    @Column(name = "country", nullable = false, length = 2)
    @Builder.Default
    private String country = "PH";

    /** The country's currency, fixed when the organization is approved (null before); later changes to the country's currency do not touch it. */
    @Column(name = "currency", length = 3)
    private String currency;

    @ElementCollection
    @CollectionTable(
            name = "organization_documents",
            joinColumns = @JoinColumn(name = "organization_id"),
            // No FK constraints, matching every other table in this schema (see V6 migration).
            foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<OrganizationDocument> documents = new ArrayList<>();
}
