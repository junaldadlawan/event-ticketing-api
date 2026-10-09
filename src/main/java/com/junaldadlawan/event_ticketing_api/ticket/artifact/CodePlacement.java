package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;

/**
 * Where the organizer wants the scannable code on the ticket, as set on the
 * ticket template: {@code xPercent}/{@code yPercent} is the top-left corner of
 * the UNROTATED box as a % of the ticket's width/height, {@code widthPercent}
 * its width as a % of the ticket's width (the height follows from the type's
 * shape: QR square, barcode 3:1), {@code rotationDegrees} a clockwise turn about
 * the box's centre. Package-private plumbing shared by {@link PngTicketRenderer}
 * and {@link PdfTicketRenderer}; a {@code null} placement means "use the
 * renderer's built-in default".
 * <p>
 * The geometry mirrors the template designer's, so a position that looks right
 * there lands in the same place here.
 */
record CodePlacement(CodeType type, double xPercent, double yPercent, double widthPercent, int rotationDegrees) {

    /**
     * The code's box on a canvas, in the canvas's own units: its centre, its
     * UNROTATED size and the rotation to apply about that centre.
     */
    record Box(double centerX, double centerY, double width, double height, double rotationDegrees) {
    }

    /**
     * Converts the percentages to canvas units and keeps the whole (rotated) code
     * on the canvas: a code too big to fit at its rotation is scaled down about
     * its centre, then the centre is moved just far enough to be inside. The
     * height of a ticket isn't known when a template is saved, so a position
     * that fits in width may not fit in height.
     */
    Box resolve(double canvasWidth, double canvasHeight) {
        double rotation = ((rotationDegrees % 360) + 360) % 360;
        double width = Math.min(canvasWidth * widthPercent / 100.0, canvasWidth);
        double height = width / type.aspect();
        double centerX = canvasWidth * xPercent / 100.0 + width / 2;
        double centerY = canvasHeight * yPercent / 100.0 + height / 2;

        double radians = Math.toRadians(rotation);
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        double boundsWidth = width * cos + height * sin;
        double boundsHeight = width * sin + height * cos;
        double fit = Math.min(1.0, Math.min(canvasWidth / boundsWidth, canvasHeight / boundsHeight));
        width *= fit;
        height *= fit;
        boundsWidth *= fit;
        boundsHeight *= fit;

        centerX = clamp(centerX, boundsWidth / 2, canvasWidth - boundsWidth / 2);
        centerY = clamp(centerY, boundsHeight / 2, canvasHeight - boundsHeight / 2);
        return new Box(centerX, centerY, width, height, rotation);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
