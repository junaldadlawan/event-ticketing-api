package com.junaldadlawan.event_ticketing_api.resalelisting.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/** Matches openapi.yaml's {@code POST /tickets/{ticketId}/resale-listings} request body. */
public record ResaleListingCreateRequest(
        @NotNull
        @Valid
        MoneyDto askingPrice) implements Serializable {
}
