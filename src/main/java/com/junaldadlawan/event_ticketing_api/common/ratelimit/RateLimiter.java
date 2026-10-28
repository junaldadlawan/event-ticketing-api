package com.junaldadlawan.event_ticketing_api.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed one-minute-window limiter shared across endpoints (BR-NFR-004): at most {@code limit} calls per client per minute in a
 * named bucket, counted in memory and forgotten when the minute ends. Same approach as {@code analytics.ViewRateLimiter}; a bucket
 * keeps login, checkout and promo counts apart so one does not use up another's budget.
 * shortcut: counts per app instance, so with N instances a client gets up to N x limit; move to a shared store (Redis) when we scale out.
 */
@Component
public class RateLimiter {

    private volatile long minute = -1;
    private volatile ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /** True while the client is within {@code limit} for this bucket this minute; false once it is over. */
    public boolean tryAcquire(String bucket, String clientKey, int limit) {
        long now = System.currentTimeMillis() / 60_000;
        if (now != minute) {
            synchronized (this) {
                if (now != minute) {
                    counts = new ConcurrentHashMap<>();
                    minute = now;
                }
            }
        }
        String key = bucket + "|" + (clientKey == null ? "" : clientKey);
        return counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet() <= limit;
    }

    /**
     * The client a limit counts against: behind the load balancer the last X-Forwarded-For entry (the address the balancer itself saw,
     * which the client cannot forge), else the connection's address. Used in memory only, never stored.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
