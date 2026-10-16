package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.payout.dto.EligiblePayoutsResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutBatchRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.enums.EventScope;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The batch payout screen: what can be paid out now (per organization and event) and generating the chosen lines in one go. Admin only,
 * checked in the service; the paths fall under SecurityConfig's authenticated catch-all.
 */
@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutBatchController {

    private final PayoutService payoutService;

    @GetMapping("/eligible")
    public EligiblePayoutsResponse eligible(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                                            @RequestParam(required = false) UUID organizationId,
                                            @RequestParam(required = false) EventScope eventScope) {
        return payoutService.eligible(periodStart, periodEnd, organizationId, eventScope);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PayoutResponse> batch(@Valid @RequestBody PayoutBatchRequest request) {
        return payoutService.generateBatch(request);
    }
}
