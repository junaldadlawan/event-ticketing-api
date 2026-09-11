package com.junaldadlawan.event_ticketing_api.payout.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Transactional/status-bearing tier per the ERD's audit-column policy -
 * created_at/updated_at only. System-generated on a schedule per
 * openapi.yaml (BR-PAY-006: "must record gross sales, platform fees, and
 * net payable, per organizer") - this dispatch exposes read access only
 * (see {@code PayoutService}), no generation job exists yet to ever
 * populate this table, matching openapi's own "read access only, not
 * manual creation" scoping for this pass.
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "gross_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "gross_currency", nullable = false, length = 3))
    })
    private Money gross;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "fees_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "fees_currency", nullable = false, length = 3))
    })
    private Money fees;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "net_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "net_currency", nullable = false, length = 3))
    })
    private Money net;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
