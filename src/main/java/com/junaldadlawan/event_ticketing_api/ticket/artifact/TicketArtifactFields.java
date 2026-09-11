package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import java.awt.image.BufferedImage;

/**
 * The dynamic content drawn into a rendered ticket artifact (Phase 6b
 * confirmed decisions #2/#5) — package-private plumbing shared by {@link
 * PngTicketRenderer} and {@link PdfTicketRenderer}. Deliberately carries no
 * {@code logoUrl}/{@code backgroundImageUrl} — those two branding fields are
 * stored/returned via the {@code TicketTemplate} API but not fetched/drawn
 * into the artifact itself (confirmed decision #2).
 */
record TicketArtifactFields(
        String eventTitle,
        String ticketTypeName,
        String seatDescription,
        String ticketNumber,
        BufferedImage qrCodeImage,
        String primaryColorHex) {
}
