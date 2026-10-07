package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * What the template designer chose for a ticket, resolved for drawing: the canvas size in pixels,
 * its background (fill colour, optional already-loaded image and how to fit it), the text fields in
 * drawing order, and whether a code is printed at all ({@code printCode=false} for {@code NONE}).
 * {@code backgroundImage} is {@code null} when there is none or it could not be loaded - the ticket
 * then simply renders without it. {@code customRect} is only used for {@link BackgroundFit#CUSTOM}.
 * Package-private plumbing shared by {@link PngTicketRenderer} and {@link PdfTicketRenderer}.
 */
record TicketDesign(
        int width,
        int height,
        String backgroundColorHex,
        BufferedImage backgroundImage,
        BackgroundFit backgroundFit,
        Rect customRect,
        List<TicketTextField> textFields,
        boolean printCode) {

    static final int DEFAULT_WIDTH = 900;
    static final int DEFAULT_HEIGHT = 380;

    /** The background image's rectangle in % of the ticket's width/height (may be negative or above 100). */
    record Rect(double x, double y, double width, double height) {
    }
}
