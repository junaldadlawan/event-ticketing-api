package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Retention of the funnel log (BR-ANALYTICS-003): raw view and cart rows are kept 13 months, enough for the longest (366-day) funnel range,
 * then deleted. Runs daily; deleting by age is idempotent, so several instances running it at once is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunnelRetentionJob {

    static final int RETENTION_MONTHS = 13;

    private final FunnelRepository funnelRepository;

    @Scheduled(cron = "${app.analytics.retention-cron:0 30 3 * * *}", zone = "UTC")
    public void purge() {
        try {
            int deleted = purgeOlderThan(ZonedDateTime.now(ZoneOffset.UTC).minusMonths(RETENTION_MONTHS).toInstant());
            if (deleted > 0) {
                log.info("Funnel retention removed {} row(s) older than {} months", deleted, RETENTION_MONTHS);
            }
        } catch (RuntimeException e) {
            // tomorrow's run retries; a failure must not stop the scheduler thread
            log.warn("Funnel retention failed: {}", e.toString());
        }
    }

    int purgeOlderThan(Instant before) {
        return funnelRepository.deleteOlderThan(before);
    }
}
