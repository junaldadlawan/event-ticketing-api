package com.junaldadlawan.event_ticketing_api.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process (per application instance) caches for the PUBLIC event reads:
 * <ul>
 *   <li>{@value #PUBLIC_EVENT} - {@code GET /events/{id}} (non-draft only),
 *       keyed by event id;</li>
 *   <li>{@value #PUBLIC_EVENT_SEARCH} - {@code GET /events}, keyed by the
 *       filters and page.</li>
 * </ul>
 * Both are bounded by size and expire after a TTL. Writes evict them
 * immediately (see {@code PublicEventCacheEvictionListener}); the TTL is the
 * backstop for a missed invalidation and for staleness on OTHER instances if
 * the service ever runs more than one task (Redis would be the upgrade then).
 * <p>
 * Deliberately NOT cached: anything with live inventory (ticket types, seat
 * map), anything caller-dependent ({@code GET /events/managed}, draft events),
 * and everything that writes.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PUBLIC_EVENT = "publicEvent";
    public static final String PUBLIC_EVENT_SEARCH = "publicEventSearch";
    /** Public category list; evicted by CategoryServiceImpl on every admin write. */
    public static final String CATEGORIES = "categories";

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.event.ttl-seconds:30}") long eventTtlSeconds,
            @Value("${app.cache.event.max-size:1000}") long eventMaxSize,
            @Value("${app.cache.event-search.ttl-seconds:30}") long searchTtlSeconds,
            @Value("${app.cache.event-search.max-size:500}") long searchMaxSize,
            @Value("${app.cache.category.ttl-seconds:60}") long categoryTtlSeconds) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build(PUBLIC_EVENT, eventTtlSeconds, eventMaxSize),
                build(PUBLIC_EVENT_SEARCH, searchTtlSeconds, searchMaxSize),
                build(CATEGORIES, categoryTtlSeconds, 10)));
        return manager;
    }

    private static CaffeineCache build(String name, long ttlSeconds, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build());
    }
}
