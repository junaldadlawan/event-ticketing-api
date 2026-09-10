package com.junaldadlawan.event_ticketing_api.organization.entity;

import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Append-only, historical-log-tier entity per the ERD's audit policy —
 * does NOT extend {@code Auditable}; only carries a creation timestamp
 * ({@code assignedAt}).
 * <p>
 * Implements {@link Persistable} because the composite {@code @IdClass} key
 * is assigned by the application (not generated): without it, Spring Data
 * sees a non-null id on a brand-new instance and calls {@code merge()}
 * instead of {@code persist()}, which raced the {@code organization_members}
 * insert against the {@code organization_member_roles} insert and tripped
 * that child table's FK constraint.
 */
@Entity
@Table(name = "organization_members")
@IdClass(OrganizationMemberId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMember implements Persistable<OrganizationMemberId> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "organization_member_roles",
            joinColumns = {
                    @JoinColumn(name = "user_id", referencedColumnName = "user_id"),
                    @JoinColumn(name = "organization_id", referencedColumnName = "organization_id")
            },
            // No FK constraints, matching every other table in this schema (see V6
            // migration). Also sidesteps a Hibernate ddl-auto=update quirk: for this
            // composite-key join, its auto-generated (implicit, columnless) FK ends up
            // with a column order that doesn't match organization_members' actual PK
            // order, silently producing a permanently-failing constraint.
            foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<OrganizationRole> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "assigned_at", updatable = false, nullable = false)
    private Instant assignedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public OrganizationMemberId getId() {
        return new OrganizationMemberId(userId, organizationId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
