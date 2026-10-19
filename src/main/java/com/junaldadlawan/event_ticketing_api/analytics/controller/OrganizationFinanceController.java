package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationEarningsResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.OrganizationFinanceResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.TransactionResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.FinanceSummaryService;
import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An organization's own income, earnings over time and money lines. Falls under the existing {@code /api/v1/organizations/**} authenticated
 * matcher; only the organization's owner or an admin may read it (checked in the service).
 */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@RequiredArgsConstructor
public class OrganizationFinanceController {

    private final FinanceSummaryService financeSummaryService;

    @GetMapping("/finance")
    public OrganizationFinanceResponse summary(@PathVariable UUID orgId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) String currency,
                                               @RequestParam(required = false) UUID eventId) {
        return financeSummaryService.organizationSummary(orgId, from, to, currency, eventId);
    }

    @GetMapping("/finance/earnings")
    public OrganizationEarningsResponse earnings(@PathVariable UUID orgId,
                                                 @RequestParam(defaultValue = "DAY") OrganizationEarningsResponse.Interval interval,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String currency,
                                                 @RequestParam(required = false) UUID eventId) {
        return financeSummaryService.organizationEarnings(orgId, interval, from, to, currency, eventId);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(@PathVariable UUID orgId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(required = false) UUID eventId,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return financeSummaryService.organizationTransactions(orgId, from, to, eventId, pageable);
    }
}
