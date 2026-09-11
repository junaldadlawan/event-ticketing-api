package com.junaldadlawan.event_ticketing_api.resalepolicy.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.resalepolicy.enums.PriceCapRule;
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

import java.util.UUID;

/**
 * Managed-resource tier per the ERD's audit-column policy: full audit set,
 * same as {@code TicketTemplate}/{@code TicketType}/{@code PromoCode}. One
 * per event ("Event ||--o| ResalePolicy: has"), enforced by a unique index
 * on {@code event_id} (V14) rather than the ERD's literal depiction of
 * {@code event_id} itself as the primary key - no other table in this schema
 * uses a borrowed/shared primary key, and {@code TicketTemplate} sets the
 * precedent of an event-scoped config row still getting its own generated
 * id. {@code docs/} is target design, not a binding schema (see CLAUDE.md).
 */
@Entity
@Table(name = "resale_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResalePolicy extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_cap_rule", length = 30)
    private PriceCapRule priceCapRule;

    /** Only meaningful when {@code priceCapRule == FACE_VALUE_PLUS_FEE}. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "fee_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "fee_currency", length = 3))
    })
    private Money feeAmount;
}
