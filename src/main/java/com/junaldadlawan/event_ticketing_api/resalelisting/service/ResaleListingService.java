package com.junaldadlawan.event_ticketing_api.resalelisting.service;

import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingCreateRequest;
import com.junaldadlawan.event_ticketing_api.resalelisting.dto.ResaleListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ResaleListingService {

    /** {@code POST /tickets/{ticketId}/resale-listings} - owning buyer only. */
    ResaleListingResponse create(UUID ticketId, ResaleListingCreateRequest request);

    /** {@code DELETE /resale-listings/{listingId}} - owning seller only. */
    void cancel(UUID listingId);

    /** {@code GET /events/{eventId}/resale-listings} - public, active listings only. */
    Page<ResaleListingResponse> listActive(UUID eventId, Pageable pageable);

    /** {@code POST /resale-listings/{listingId}/purchase} - runs like checkout, seller as payee. */
    OrderResponse purchase(UUID listingId, UUID idempotencyKey, String paymentMethodToken);
}
