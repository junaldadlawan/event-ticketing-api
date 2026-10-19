package com.junaldadlawan.event_ticketing_api.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs (the event completion sweep). Every job must be safe to run on several instances
 * at once, since there is no distributed lock yet (R-10). Off in the test run (see the pom), which shares the dev database.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.events.completion-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
