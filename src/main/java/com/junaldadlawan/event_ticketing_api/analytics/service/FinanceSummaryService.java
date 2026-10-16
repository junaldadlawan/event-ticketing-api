package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.FinanceSummaryResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationFinanceResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface FinanceSummaryService {

    /**
     * {@code GET /analytics/platform/finance} - admin only. The platform's money for the orders placed in the period (UTC days, both included;
     * default the last 30 days, at most 366), in {@code currency} (default PHP). 400 for a bad period.
     */
    FinanceSummaryResponse platformSummary(LocalDate from, LocalDate to, String currency);

    /**
     * {@code GET /organizations/{orgId}/finance} - the organization's owner or an admin. The organization's own money for the same period
     * rules; never the platform's figures. 404 for an unknown organization, 403 for anyone else.
     */
    OrganizationFinanceResponse organizationSummary(UUID organizationId, LocalDate from, LocalDate to, String currency);
}
