package com.junaldadlawan.event_ticketing_api.paymentmethod.repository;

import com.junaldadlawan.event_ticketing_api.paymentmethod.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    /** Scoped to the owner: another user's card id is simply not found. */
    Optional<PaymentMethod> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /** The user's cards, primary first, then oldest first. */
    List<PaymentMethod> findByUserIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(UUID userId);

    Optional<PaymentMethod> findByUserIdAndPrimaryTrueAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndStripePaymentMethodIdAndDeletedAtIsNull(UUID userId, String stripePaymentMethodId);

    /** Including removed cards (a removed card is only brought back by an explicit "save this card"). */
    Optional<PaymentMethod> findFirstByUserIdAndPaymongoPaymentMethodId(UUID userId, String paymongoPaymentMethodId);

    List<PaymentMethod> findByUserIdAndPaymongoPaymentMethodIdIsNullAndDeletedAtIsNull(UUID userId);
}
