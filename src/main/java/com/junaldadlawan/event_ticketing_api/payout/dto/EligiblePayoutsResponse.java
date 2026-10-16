package com.junaldadlawan.event_ticketing_api.payout.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * What can be paid out now, one line per organization and event (the batch payout screen). {@code eligible} is the net a payout of that line
 * would pay (gross after refunds minus the platform fee and the gateway fee that come off), {@code status} is READY or ON_HOLD with the
 * {@code holdReason}. The summary counts the organizations with a READY line and sums the READY and the ON_HOLD amounts.
 */
public record EligiblePayoutsResponse(Summary summary, List<Item> items) implements Serializable {

    public enum Status { READY, ON_HOLD }

    public record Summary(long eligibleOrganizations, MoneyDto eligibleAmount, MoneyDto onHold) implements Serializable {
    }

    public record Item(UUID organizationId, String organizationName, UUID eventId, String eventTitle, MoneyDto eligible, Status status,
                       String holdReason) implements Serializable {
    }
}
