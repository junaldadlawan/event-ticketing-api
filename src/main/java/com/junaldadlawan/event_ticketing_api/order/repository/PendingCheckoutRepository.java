package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.PendingCheckout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PendingCheckoutRepository extends JpaRepository<PendingCheckout, UUID> {

    /** Locks the row so a webhook delivered twice at once finalizes the checkout only once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingCheckout p where p.providerIntentId = :intentId")
    Optional<PendingCheckout> findByProviderIntentIdForUpdate(@Param("intentId") String intentId);

    Optional<PendingCheckout> findByProviderIntentId(String intentId);

    /** Locks the row so two attaches of a saved card for one checkout cannot both reach the provider. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingCheckout p where p.id = :id and p.buyerId = :buyerId")
    Optional<PendingCheckout> findByIdAndBuyerIdForUpdate(@Param("id") UUID id, @Param("buyerId") UUID buyerId);

    /** The newest checkout started with this idempotency key (an earlier one may have failed and the key was reused). */
    Optional<PendingCheckout> findFirstByIdempotencyKeyOrderByCreatedAtDesc(UUID idempotencyKey);

    Optional<PendingCheckout> findByIdAndBuyerId(UUID id, UUID buyerId);
}
