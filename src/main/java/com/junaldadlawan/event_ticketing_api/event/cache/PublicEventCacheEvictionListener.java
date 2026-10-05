package com.junaldadlawan.event_ticketing_api.event.cache;

import com.junaldadlawan.event_ticketing_api.common.config.CacheConfig;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Clears the public event caches whenever an {@code Event} - or a {@code Venue},
 * whose snapshot is embedded in every event response - is inserted, updated or
 * deleted.
 * <p>
 * Hooked in as a JPA entity listener rather than as annotations on individual
 * service methods so that EVERY write path is covered: all the service
 * methods (update, publish, cancel, delete), the moderation actions that
 * suspend/reinstate an event straight through the repository, a venue
 * rename, any future writer, and tests that save through repositories. Eviction
 * is deferred until the surrounding transaction has COMMITTED, so a concurrent
 * read can't re-populate the cache from pre-commit data.
 * <p>
 * Instantiated by Spring (Hibernate's SpringBeanContainer), so the
 * {@link CacheManager} is the one belonging to the same application context.
 */
@Slf4j
@Component
public class PublicEventCacheEvictionListener {

    private final CacheManager cacheManager;

    public PublicEventCacheEvictionListener(@Lazy CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAll(entity);
                }
            });
        } else {
            evictAll(entity);
        }
    }

    private void evictAll(Object entity) {
        for (String name : List.of(CacheConfig.PUBLIC_EVENT, CacheConfig.PUBLIC_EVENT_SEARCH)) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        log.debug("Cleared public event caches after a change to {}", entity.getClass().getSimpleName());
    }
}
