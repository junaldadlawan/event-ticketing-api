package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutResponse;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutWalletResponse;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Sending a generated payout to the organization's payout account, checking on it, and looking at the wallet it is paid from. Admin only (checked in the service); authenticated by {@code anyRequest()}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PayoutTransferController {

    private final PayoutService payoutService;

    @PostMapping("/payouts/{payoutId}/send")
    public PayoutResponse send(@PathVariable UUID payoutId) {
        return payoutService.send(payoutId);
    }

    /** Cancels a SCHEDULED payout and releases its orders, which are owed (and eligible for a new payout) again. */
    @PostMapping("/payouts/{payoutId}/cancel")
    public PayoutResponse cancel(@PathVariable UUID payoutId) {
        return payoutService.cancel(payoutId);
    }

    /** The platform's payout wallet: whether it is activated, what it holds and its limits. */
    @GetMapping("/payout-wallet")
    public PayoutWalletResponse wallet() {
        return payoutService.wallet();
    }

    @PostMapping("/payouts/{payoutId}/refresh")
    public PayoutResponse refresh(@PathVariable UUID payoutId) {
        return payoutService.refresh(payoutId);
    }
}
