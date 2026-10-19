package com.junaldadlawan.event_ticketing_api.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * At most {@code limit} recorded views per client per minute, so a script cannot inflate an event's view count. A fixed one-minute window,
 * counted in memory and forgotten when the minute ends.
 * shortcut: counts per app instance, so with N instances a client gets up to N x limit; move to a shared store (Redis) when we scale out.
 */
@Component
public class ViewRateLimiter {

    private final int limit;
    private volatile long minute = -1;
    private volatile ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public ViewRateLimiter(@Value("${app.analytics.views-per-minute:60}") int limit) {
        this.limit = limit;
    }

    public boolean tryAcquire(String clientKey) {
        long now = System.currentTimeMillis() / 60_000;
        if (now != minute) {
            synchronized (this) {
                if (now != minute) {
                    counts = new ConcurrentHashMap<>();
                    minute = now;
                }
            }
        }
        return counts.computeIfAbsent(clientKey == null ? "" : clientKey, key -> new AtomicInteger()).incrementAndGet() <= limit;
    }
}
