package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingCreateRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResalePurchaseRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * All resale-listing endpoints: listing a ticket is addressed through the
 * ticket, browsing active listings through the event, cancel/purchase by the
 * listing's own id.
 * <p>
 * {@code GET /events/{eventId}/resale-listings} is public (openapi.yaml's
 * {@code security: []}), already covered by SecurityConfig's existing broad
 * {@code GET /api/v1/events/**} permitAll matcher. {@code POST
 * /tickets/{ticketId}/resale-listings} falls through to SecurityConfig's
 * generic {@code anyRequest().authenticated()}.
 */
@RestController
@RequiredArgsConstructor
public class ResaleListingController {

    private final ResaleListingService resaleListingService;

    @PostMapping("/api/v1/tickets/{ticketId}/resale-listings")
    @ResponseStatus(HttpStatus.CREATED)
    public ResaleListingResponse create(@PathVariable UUID ticketId, @Valid @RequestBody ResaleListingCreateRequest request) {
        return resaleListingService.create(ticketId, request);
    }

    @GetMapping("/api/v1/events/{eventId}/resale-listings")
    public PageResponse<ResaleListingResponse> listActive(@PathVariable UUID eventId, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(resaleListingService.listActive(eventId, pageable));
    }

    @DeleteMapping("/api/v1/resale-listings/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID listingId) {
        resaleListingService.cancel(listingId);
    }

    @PostMapping("/api/v1/resale-listings/{listingId}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse purchase(@PathVariable UUID listingId,
                                   @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                   @Valid @RequestBody ResalePurchaseRequest request) {
        return resaleListingService.purchase(listingId, idempotencyKey, request.paymentMethodToken());
    }
}
