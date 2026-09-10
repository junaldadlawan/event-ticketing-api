package com.junaldadlawan.event_ticketing_api.promocode.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.promocode.enums.DiscountType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Managed resource" tier per the ERD's audit-column policy — full audit
 * set, same as {@code Event}/{@code TicketType}/{@code SeatMap}.
 * <p>
 * {@code code} is unique per event (not platform-wide) — enforced by a DB
 * unique constraint on {@code (event_id, code)} in the V10 migration, not by
 * any JPA-level constraint here.
 * <p>
 * {@code discountValue} is a {@link BigDecimal} (openapi.yaml types it as
 * {@code number}, not {@code integer}) because it represents either a 0-100
 * percentage or a fixed minor-units amount depending on {@code
 * discountType}, so BigDecimal avoids floating-point rounding issues.
 * <p>
 * {@code applicableTicketTypeIds} mirrors the established {@code
 * OrganizationMember.roles}/{@code Organization.documents} pattern — a
 * child table, no FK constraint.
 */
@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @ElementCollection
    @CollectionTable(
            name = "promo_code_applicable_ticket_types",
            joinColumns = @JoinColumn(name = "promo_code_id"),
            // No FK constraints, matching every other table in this schema (see V6 migration).
            foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    @Column(name = "ticket_type_id")
    @Builder.Default
    private Set<UUID> applicableTicketTypeIds = new HashSet<>();

    @Column(name = "usage_limit_total")
    private Integer usageLimitTotal;

    @Column(name = "usage_limit_per_buyer")
    private Integer usageLimitPerBuyer;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;
}
