package com.junaldadlawan.event_ticketing_api.resalelisting.service;

import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResalePurchaseIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.resalelisting.repository.ResalePurchaseIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors {@code CheckoutIdempotencyKeyManager} exactly (same {@code
 * REQUIRES_NEW} reasoning - claiming a key must commit independently of the
 * caller's still-open outer purchase transaction so a genuinely concurrent
 * duplicate request can observe it and get a 409), scoped to a resale
 * listing purchase instead of a cart checkout.
 */
@Component
@RequiredArgsConstructor
public class ResalePurchaseIdempotencyKeyManager {

    private final ResalePurchaseIdempotencyKeyRepository repository;

    public record ClaimOutcome(ResalePurchaseIdempotencyKey key, boolean freshlyClaimed) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimOutcome claim(UUID idempotencyKey, UUID buyerId, UUID listingId) {
        Optional<ResalePurchaseIdempotencyKey> existing = repository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return new ClaimOutcome(existing.get(), false);
        }
        try {
            ResalePurchaseIdempotencyKey key = ResalePurchaseIdempotencyKey.builder()
                    .id(idempotencyKey)
                    .buyerId(buyerId)
                    .listingId(listingId)
                    .orderId(null)
                    .build();
            return new ClaimOutcome(repository.saveAndFlush(key), true);
        } catch (DataIntegrityViolationException e) {
            return repository.findById(idempotencyKey)
                    .map(k -> new ClaimOutcome(k, false))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID idempotencyKey) {
        repository.findById(idempotencyKey).ifPresent(repository::delete);
    }
}
