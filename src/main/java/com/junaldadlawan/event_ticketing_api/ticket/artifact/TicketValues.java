package com.junaldadlawan.event_ticketing_api.ticket.artifact;

/**
 * The text each template field key prints for one ticket (already formatted). Dynamic values differ
 * per ticket; static ones are the event's. An empty string prints nothing.
 * Package-private plumbing for {@link TicketCanvasRenderer}.
 */
record TicketValues(
        String ticketType,
        String section,
        String row,
        String seat,
        String ticketNumber,
        String attendeeName,
        String eventName,
        String eventDate,
        String eventTime,
        String venue) {
}
