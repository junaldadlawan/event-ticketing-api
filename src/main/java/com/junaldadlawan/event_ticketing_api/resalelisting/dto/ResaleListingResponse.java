package com.junaldadlawan.event_ticketing_api.resalelisting.dto;

import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResaleListing;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link ResaleListing}, matching openapi.yaml's {@code ResaleListing} schema. */
public record ResaleListingResponse(
        UUID id,
        UUID ticketId,
        UUID eventId,
        UUID sellerId,
        MoneyDto askingPrice,
        ResaleListingStatus status,
        Instant listedAt,
        Instant resolvedAt,
        UUID buyerOrderId,
        Instant updatedAt) implements Serializable {

    public static ResaleListingResponse from(ResaleListing listing) {
        return new ResaleListingResponse(
                listing.getId(),
                listing.getTicketId(),
                listing.getEventId(),
                listing.getSellerId(),
                new MoneyDto(listing.getAskingPrice().getAmount(), listing.getAskingPrice().getCurrency()),
                listing.getStatus(),
                listing.getListedAt(),
                listing.getResolvedAt(),
                listing.getBuyerOrderId(),
                listing.getUpdatedAt());
    }
}
