package com.junaldadlawan.event_ticketing_api.analytics.controller;

import com.junaldadlawan.event_ticketing_api.analytics.dto.FinanceSummaryResponse;
import com.junaldadlawan.event_ticketing_api.analytics.service.FinanceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The admin's money summary. Admin only: the SecurityConfig matcher and the service both check it. */
@RestController
@RequestMapping("/api/v1/analytics/platform/finance")
@RequiredArgsConstructor
public class PlatformFinanceController {

    private final FinanceSummaryService financeSummaryService;

    @GetMapping
    public FinanceSummaryResponse summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) String currency) {
        return financeSummaryService.platformSummary(from, to, currency);
    }
}
