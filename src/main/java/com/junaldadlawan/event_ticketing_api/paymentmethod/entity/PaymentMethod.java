package com.junaldadlawan.event_ticketing_api.paymentmethod.entity;

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
 * A card a user saved to pay with. Only the provider's description of the card is stored (brand, last 4, expiry, holder
 * name) and the provider's payment-method id; never a card number or a security code. Exactly one live card per user is
 * primary (partial unique index); soft-deleted through {@link Auditable#markDeleted()}.
 */
@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The Stripe {@code pm_...} id. Null for a card saved while the mock gateway is active. Never returned in a response or logged. */
    @Column(name = "stripe_payment_method_id", length = 100)
    private String stripePaymentMethodId;

    /** The PayMongo {@code pm_...} id of the vaulted card. Null for a card saved without a payment (it cannot be charged again). Never returned or logged. */
    @Column(name = "paymongo_payment_method_id", length = 100)
    private String paymongoPaymentMethodId;

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

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
