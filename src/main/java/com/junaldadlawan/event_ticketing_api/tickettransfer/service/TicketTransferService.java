package com.junaldadlawan.event_ticketing_api.tickettransfer.service;

import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;

import java.util.List;
import java.util.UUID;

public interface TicketTransferService {

    /** {@code POST /tickets/{ticketId}/transfer} - owning buyer only. */
    Ticket transfer(UUID ticketId, UUID toUserId);

    /** {@code GET /tickets/{ticketId}/transfers} - owning buyer, event organizer, or admin. */
    List<TicketTransfer> listTransfers(UUID ticketId);

    /**
     * Shared ownership-change primitive (BR-TRANSFER-002/005): reassigns
     * {@code owner_id}, bumps {@code credentialVersion}, regenerates the
     * credential, and records the audit row - used by both {@link
     * #transfer} and the resale-purchase flow ({@code
     * ResaleListingServiceImpl}), which has already locked/validated the
     * ticket itself before calling this.
     */
    Ticket recordTransfer(Ticket ticket, UUID toUserId, TransferSource source);
}
