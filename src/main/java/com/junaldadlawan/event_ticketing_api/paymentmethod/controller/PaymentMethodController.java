package com.junaldadlawan.event_ticketing_api.paymentmethod.controller;

import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodRequest;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.PaymentMethodResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.dto.SetupIntentResponse;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.PaymentMethodService;
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
 * The signed-in user's own saved cards. Any authenticated user (CUSTOMER, ADMIN, SUPER_ADMIN) may use these for their own
 * cards, so SecurityConfig lists them before the admin-only {@code /api/v1/users/**} matcher.
 */
@RestController
@RequestMapping("/api/v1/users/me/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public List<PaymentMethodResponse> list() {
        return paymentMethodService.list();
    }

    @PostMapping("/setup-intent")
    public SetupIntentResponse setupIntent() {
        return paymentMethodService.createSetupIntent();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse add(@Valid @RequestBody PaymentMethodRequest request) {
        return paymentMethodService.add(request);
    }

    @PutMapping("/{id}/primary")
    public PaymentMethodResponse setPrimary(@PathVariable UUID id) {
        return paymentMethodService.setPrimary(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        paymentMethodService.remove(id);
    }
}
