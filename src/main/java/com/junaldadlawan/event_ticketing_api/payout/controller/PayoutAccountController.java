package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutAccountResponse;
import com.junaldadlawan.event_ticketing_api.payout.gateway.PayoutTransferClient;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The bank or e-wallet account an organization is paid out to, and the list of banks to pick it from. Both paths are authenticated
 * ({@code /api/v1/organizations/**} and {@code anyRequest()}); who may do what is checked in the service.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PayoutAccountController {

    private final PayoutAccountService payoutAccountService;
    private final PayoutTransferClient transferClient;

    @GetMapping("/organizations/{orgId}/payout-account")
    public PayoutAccountResponse get(@PathVariable UUID orgId) {
        return payoutAccountService.get(orgId);
    }

    @PutMapping("/organizations/{orgId}/payout-account")
    public PayoutAccountResponse set(@PathVariable UUID orgId, @Valid @RequestBody PayoutAccountRequest request) {
        return payoutAccountService.set(orgId, request);
    }

    @DeleteMapping("/organizations/{orgId}/payout-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID orgId) {
        payoutAccountService.remove(orgId);
    }

    /** The banks and e-wallets a payout can be sent to: {@code code} is what {@code bankCode} takes. */
    @GetMapping("/payout-banks")
    public List<PayoutTransferClient.Bank> banks() {
        return transferClient.banks();
    }
}
