package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import com.junaldadlawan.event_ticketing_api.order.repository.CheckoutIdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Isolated transactional-boundary helper for claiming/releasing a checkout
 * idempotency key, deliberately kept as a separate Spring bean (not inlined
 * into {@code CheckoutServiceImpl}) so its {@code REQUIRES_NEW} methods
 * actually get proxied - Spring AOP self-invocation wouldn't apply
 * propagation semantics if these lived on the same class that calls them.
 * <p>
 * {@code REQUIRES_NEW} is the whole point here: claiming a key has to commit
 * immediately, independent of the caller's still-open outer checkout
 * transaction, so that a genuinely concurrent duplicate request (racing in a
 * separate transaction) can actually observe the placeholder row and get a
 * 409 - a plain insert-then-continue inside the same transaction as the rest
 * of checkout would never be visible to another transaction until the whole
 * checkout commits or rolls back, which would defeat that "request already
 * in-flight" case entirely.
 */
@Component
@RequiredArgsConstructor
public class CheckoutIdempotencyKeyManager {

    private final CheckoutIdempotencyKeyRepository repository;

    /** Result of {@link #claim}: which row is now the record of truth, and whether this call is the one that created it. */
    public record ClaimOutcome(CheckoutIdempotencyKey key, boolean freshlyClaimed) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimOutcome claim(UUID idempotencyKey, UUID buyerId, UUID cartId) {
        Optional<CheckoutIdempotencyKey> existing = repository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return new ClaimOutcome(existing.get(), false);
        }
        try {
            CheckoutIdempotencyKey key = CheckoutIdempotencyKey.builder()
                    .id(idempotencyKey)
                    .buyerId(buyerId)
                    .cartId(cartId)
                    .orderId(null)
                    .build();
            return new ClaimOutcome(repository.saveAndFlush(key), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a genuine race to a concurrent request inserting the same
            // key at almost the same instant; fall back to reading whatever
            // it wrote instead of failing this request outright.
            return repository.findById(idempotencyKey)
                    .map(k -> new ClaimOutcome(k, false))
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Frees a claimed key for retry after checkout didn't actually complete
     * (payment failure, expired hold, or any other failure - see
     * CheckoutServiceImpl's catch-all). Committed independently of the
     * caller's outer transaction for the same reason as {@link #claim}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID idempotencyKey) {
        // findById + delete (not deleteById) so a second/duplicate delete
        // call for a key that's already gone doesn't throw
        // EmptyResultDataAccessException.
        repository.findById(idempotencyKey).ifPresent(repository::delete);
    }
}
