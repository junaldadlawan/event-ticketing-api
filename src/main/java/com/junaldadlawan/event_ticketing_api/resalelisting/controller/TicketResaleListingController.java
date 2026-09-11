package com.junaldadlawan.event_ticketing_api.resalelisting.controller;

import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingCreateRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Separate controller from {@code TicketController} (matches how {@code
 * EventPromoCodeController} is split from {@code EventController}) - {@code
 * POST /tickets/{ticketId}/resale-listings} delegates to {@link
 * ResaleListingService}, a different module. Security: falls through to
 * SecurityConfig's generic {@code anyRequest().authenticated()} - no
 * top-level prefix here is touched by any existing matcher.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/resale-listings")
@RequiredArgsConstructor
public class TicketResaleListingController {

    private final ResaleListingService resaleListingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResaleListingResponse create(@PathVariable UUID ticketId, @Valid @RequestBody ResaleListingCreateRequest request) {
        return resaleListingService.create(ticketId, request);
    }
}
