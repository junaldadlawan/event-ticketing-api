package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.FinanceSummaryResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationEarningsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationFinanceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.TransactionResponse;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

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
     * rules; never the platform's figures; only the event {@code eventId} when set. 404 for an unknown organization or event, 403 for anyone
     * else, 400 for an event of another organization.
     */
    OrganizationFinanceResponse organizationSummary(UUID organizationId, LocalDate from, LocalDate to, String currency, UUID eventId);

    /** {@code GET /organizations/{orgId}/finance/earnings} - the same access, period, currency and event rules: the income per day or month. */
    OrganizationEarningsResponse organizationEarnings(UUID organizationId, OrganizationEarningsResponse.Interval interval, LocalDate from, LocalDate to,
                                                      String currency, UUID eventId);

    /** {@code GET /organizations/{orgId}/transactions} - the same access, period and event rules: the money lines, newest first, every currency. */
    PageResponse<TransactionResponse> organizationTransactions(UUID organizationId, LocalDate from, LocalDate to, UUID eventId, Pageable pageable);
}
