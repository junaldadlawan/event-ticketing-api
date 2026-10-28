package com.junaldadlawan.event_ticketing_api.region.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-fetches the enabled currencies' USD rates once a day. Safe on several instances: a second run just stores the same rates again. */
@Component
@RequiredArgsConstructor
public class ExchangeRateRefreshJob {

    private final RegionService regionService;

    @Scheduled(cron = "${app.exchange-rates.refresh-cron:0 15 0 * * *}", zone = "UTC")
    public void refresh() {
        regionService.refreshRatesBySystem();
    }
}
