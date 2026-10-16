package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, UUID> {

    /** The organization's live payout account: where its payouts are sent. */
    Optional<PayoutAccount> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
