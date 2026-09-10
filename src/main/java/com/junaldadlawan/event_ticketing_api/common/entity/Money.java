package com.junaldadlawan.event_ticketing_api.common.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared value object for a monetary amount, per openapi.yaml's {@code Money}
 * schema. Not an entity — no {@code @Id}, doesn't extend {@link Auditable} —
 * just an {@code @Embeddable} composed into owning entities via
 * {@code @Embedded} (e.g. {@code TicketType.price}). Intentionally kept
 * generic in {@code common/} rather than a feature module: future phases
 * (Order, Payment, PromoCode, RefundPolicy, Payout) all carry {@code Money}
 * fields of their own per the ERD.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Money {

    /** Minor units (e.g. cents). */
    private long amount;

    /** ISO 4217 currency code, e.g. "USD". */
    private String currency;
}
