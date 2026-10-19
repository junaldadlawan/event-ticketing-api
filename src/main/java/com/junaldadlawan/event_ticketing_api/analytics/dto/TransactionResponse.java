package com.junaldadlawan.event_ticketing_api.analytics.dto;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FinanceSummaryRepository.TransactionRow;
import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One money line of an organization: a SALE (an order placed: what the buyer paid, the platform and gateway fees that come off, and the rest),
 * a REFUND (a completed refund: money going out, so {@code net} is negative) or a PAYOUT (a payout created: its net goes out, so {@code net} is
 * negative). {@code orderId} is null for a payout; {@code status} is the order's, the refund's or the payout's own status.
 */
public record TransactionResponse(
        UUID id,
        String type,
        Instant occurredAt,
        String description,
        String eventTitle,
        UUID orderId,
        MoneyDto gross,
        MoneyDto fees,
        MoneyDto net,
        String status) implements Serializable {

    public static TransactionResponse from(TransactionRow row) {
        return new TransactionResponse(row.id(), row.type(), row.occurredAt(), row.description(), row.eventTitle(), row.orderId(),
                new MoneyDto(row.gross(), row.currency()), new MoneyDto(row.fees(), row.currency()), new MoneyDto(row.net(), row.currency()), row.status());
    }
}
