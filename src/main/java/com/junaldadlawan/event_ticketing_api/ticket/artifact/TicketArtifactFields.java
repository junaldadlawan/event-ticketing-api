package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import java.awt.image.BufferedImage;

/**
 * The dynamic content drawn into a rendered ticket artifact (Phase 6b
 * confirmed decisions #2/#5) — package-private plumbing shared by {@link
 * PngTicketRenderer} and {@link PdfTicketRenderer}.
 * <p>
 * {@code codeImage} is the QR code or barcode to draw ({@code null} when the template prints no code);
 * {@code codePlacement} is the organizer-chosen position from the template, or {@code null} for the
 * renderer's default. {@code design} and {@code values} are set together when the template uses the
 * ticket designer (own canvas size, background, text fields, or no code): the ticket is then drawn
 * by {@link TicketCanvasRenderer} instead of the built-in layout, which the first six components
 * still describe.
 */
record TicketArtifactFields(
        String eventTitle,
        String ticketTypeName,
        String seatDescription,
        String ticketNumber,
        BufferedImage codeImage,
        String primaryColorHex,
        CodePlacement codePlacement,
        TicketDesign design,
        TicketValues values) {

    /** The built-in layout: no designer content. */
    TicketArtifactFields(String eventTitle, String ticketTypeName, String seatDescription, String ticketNumber,
                         BufferedImage codeImage, String primaryColorHex, CodePlacement codePlacement) {
        this(eventTitle, ticketTypeName, seatDescription, ticketNumber, codeImage, primaryColorHex, codePlacement,
                null, null);
    }
}
