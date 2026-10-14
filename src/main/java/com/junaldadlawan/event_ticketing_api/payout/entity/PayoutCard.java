package com.junaldadlawan.event_ticketing_api.payout.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A card an organization is paid out to. Only what the payment gateway hands back is stored: a token, brand, last 4,
 * expiry and holder name. The full card number and the security code never reach this server. Exactly one live card
 * per organization is primary (enforced by a partial unique index); soft-deleted through {@link Auditable#markDeleted()}.
 */
@Entity
@Table(name = "payout_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutCard extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Column(name = "brand", nullable = false, length = 30)
    private String brand;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "exp_month", nullable = false)
    private int expMonth;

    @Column(name = "exp_year", nullable = false)
    private int expYear;

    /** The gateway's token for the card. Never returned in a response, never logged. */
    @Column(name = "payment_token", nullable = false)
    private String paymentToken;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
