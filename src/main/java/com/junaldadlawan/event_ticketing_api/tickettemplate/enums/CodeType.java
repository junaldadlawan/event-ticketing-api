package com.junaldadlawan.event_ticketing_api.tickettemplate.enums;

/**
 * The kind of scannable code a ticket template places on the ticket. Each type
 * has a fixed shape (width : height), so a placement only needs a width: a QR
 * code is square, a barcode is 3:1.
 */
public enum CodeType {

    /** A QR code's minimum is a physical size (0.75 in), not a share of the ticket. */
    QR(1.0, 0.75, 0.0),
    BARCODE(3.0, 0.0, 35.0),
    /** No code is printed on the ticket; a NONE template carries no placement values. */
    NONE(1.0, 0.0, 0.0);

    /** Pixels per inch the ticket's pixel size is defined in (a 3 in ticket is 900 px). */
    public static final int PX_PER_INCH = 300;

    private final double aspect;
    private final double minWidthInches;
    private final double minWidthPercent;

    CodeType(double aspect, double minWidthInches, double minWidthPercent) {
        this.aspect = aspect;
        this.minWidthInches = minWidthInches;
        this.minWidthPercent = minWidthPercent;
    }

    /** Width divided by height. */
    public double aspect() {
        return aspect;
    }

    /**
     * Smallest width, as a % of the ticket's width, at which the code still scans
     * when the ticket is rendered. A type with a physical minimum (QR: 0.75 in)
     * converts it using the ticket's width in px; the others use a fixed share
     * (the barcode symbol is far denser than a QR code).
     */
    public double minWidthPercent(int ticketWidthPx) {
        if (minWidthInches > 0) {
            return Math.min(100.0, minWidthInches * PX_PER_INCH / ticketWidthPx * 100.0);
        }
        return minWidthPercent;
    }

    /** Smallest physical width in inches, or 0 when the minimum is a % of the ticket instead. */
    public double minWidthInches() {
        return minWidthInches;
    }
}
