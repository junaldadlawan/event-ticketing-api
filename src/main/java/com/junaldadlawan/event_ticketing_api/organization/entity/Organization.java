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
