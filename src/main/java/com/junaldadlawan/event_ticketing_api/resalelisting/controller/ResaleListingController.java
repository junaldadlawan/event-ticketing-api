package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResalePurchaseRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resale-listings/{listingId}")
@RequiredArgsConstructor
public class ResaleListingController {

    private final ResaleListingService resaleListingService;

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID listingId) {
        resaleListingService.cancel(listingId);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse purchase(@PathVariable UUID listingId,
                                   @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                   @Valid @RequestBody ResalePurchaseRequest request) {
        return resaleListingService.purchase(listingId, idempotencyKey, request.paymentMethodToken());
    }
}
