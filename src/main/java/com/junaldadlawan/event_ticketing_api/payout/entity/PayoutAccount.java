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
 * The bank or e-wallet account an organization is paid out to (PayMongo sends to accounts, not cards). One live account per
 * organization (a partial unique index). The full number is stored only encrypted and is never returned in a response or logged.
 * Soft-deleted through {@link Auditable#markDeleted()}.
 */
@Entity
@Table(name = "payout_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutAccount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** The bank's BIC as PayMongo lists it (its {@code provider_code}), needed to address the transfer. */
    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "account_number_encrypted", nullable = false, length = 500)
    private String accountNumberEncrypted;

    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;
}
