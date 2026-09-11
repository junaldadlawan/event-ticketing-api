package com.junaldadlawan.event_ticketing_api.tickettransfer.repository;

import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTransferRepository extends JpaRepository<TicketTransfer, UUID> {

    /** {@code GET /tickets/{ticketId}/transfers} - oldest-first ownership history. */
    List<TicketTransfer> findByTicketIdOrderByTransferredAtAsc(UUID ticketId);
}
