package com.junaldadlawan.event_ticketing_api.payout.controller;

import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardRequest;
import com.junaldadlawan.event_ticketing_api.payout.dto.PayoutCardResponse;
import com.junaldadlawan.event_ticketing_api.payout.service.PayoutCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The cards an organization is paid out to. {@code /api/v1/organizations/**} and {@code /api/v1/payout-cards/**} are both
 * covered by authenticated matchers (the latter by {@code anyRequest()}). The owner or an admin manages the cards;
 * the owner, organizer or an admin lists them.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PayoutCardController {

    private final PayoutCardService payoutCardService;

    @GetMapping("/organizations/{orgId}/payout-cards")
    public List<PayoutCardResponse> list(@PathVariable UUID orgId) {
        return payoutCardService.list(orgId);
    }

    @PostMapping("/organizations/{orgId}/payout-cards")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutCardResponse add(@PathVariable UUID orgId, @Valid @RequestBody PayoutCardRequest request) {
        return payoutCardService.add(orgId, request);
    }

    @PutMapping("/payout-cards/{cardId}/primary")
    public PayoutCardResponse setPrimary(@PathVariable UUID cardId) {
        return payoutCardService.setPrimary(cardId);
    }

    @DeleteMapping("/payout-cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID cardId) {
        payoutCardService.remove(cardId);
    }
}
