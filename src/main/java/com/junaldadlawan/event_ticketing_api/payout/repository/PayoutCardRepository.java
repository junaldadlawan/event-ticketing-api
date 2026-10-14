package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.entity.PayoutCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutCardRepository extends JpaRepository<PayoutCard, UUID> {

    Optional<PayoutCard> findByIdAndDeletedAtIsNull(UUID id);

    List<PayoutCard> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID organizationId);

    /** The organization's primary card: where its payouts go. */
    Optional<PayoutCard> findByOrganizationIdAndPrimaryTrueAndDeletedAtIsNull(UUID organizationId);

    long countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
