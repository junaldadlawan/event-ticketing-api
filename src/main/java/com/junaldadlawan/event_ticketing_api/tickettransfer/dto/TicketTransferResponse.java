package com.junaldadlawan.event_ticketing_api.tickettransfer.dto;

import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link TicketTransfer}, matching openapi.yaml's {@code TicketTransfer} schema. */
public record TicketTransferResponse(
        UUID id,
        UUID ticketId,
        UUID fromUserId,
        UUID toUserId,
        TransferSource source,
        Instant transferredAt) implements Serializable {

    public static TicketTransferResponse from(TicketTransfer transfer) {
        return new TicketTransferResponse(
                transfer.getId(),
                transfer.getTicketId(),
                transfer.getFromUserId(),
                transfer.getToUserId(),
                transfer.getSource(),
                transfer.getTransferredAt());
    }
}
