package com.junaldadlawan.event_ticketing_api.tickettype.service;

import com.junaldadlawan.event_ticketing_api.common.exception.UnprocessableEntityException;
import com.junaldadlawan.event_ticketing_api.ticket.repository.TicketRepository;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** BR-TICKET-LIMIT-001: no buyer ends up with more than {@code maxPerUser} tickets of a type. Used by the cart and checkout. */
@Component
@RequiredArgsConstructor
public class TicketTypeBuyerLimit {

    private final TicketRepository ticketRepository;

    /**
     * 422 when what the buyer already bought plus {@code wanted} (this type's tickets in the cart after the change, or
     * being checked out) is over the limit. {@code alreadyInCart} only feeds the message's "you already have".
     */
    public void require(TicketType ticketType, UUID buyerId, int wanted, int alreadyInCart) {
        Integer limit = ticketType.getMaxPerUser();
        if (limit == null) {
            return;
        }
        long bought = ticketRepository.countBoughtBy(buyerId, ticketType.getId());
        if (bought + wanted > limit) {
            throw new UnprocessableEntityException("You can buy at most " + limit + " " + ticketType.getName()
                    + " tickets; you already have " + (bought + alreadyInCart) + ".");
        }
    }
}
