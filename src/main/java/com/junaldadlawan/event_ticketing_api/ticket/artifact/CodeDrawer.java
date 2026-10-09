package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Draws a QR code / barcode image into a {@link Graphics2D} at a resolved {@link CodePlacement.Box}
 * - rotated about the box centre, with a white quiet zone behind it - shared by the legacy layout
 * and the template designer's canvas. The box is in the graphics' own pixel space, so callers that
 * scale their drawing (see {@link TicketCanvasRenderer}) pass a box resolved for the output size and a
 * graphics with no scaling transform: a scanner needs crisp module edges, never an upscaled blur.
 */
final class CodeDrawer {

    /** How much bigger a turned code is drawn before being averaged down. */
    private static final int SUPERSAMPLE = 4;
    /** Quiet zone around the code, as a fraction of its width. */
    private static final double QUIET_ZONE = 0.04;

    private CodeDrawer() {
    }

    static void draw(Graphics2D g, BufferedImage code, CodePlacement.Box box) {
        if (box.rotationDegrees() % 90 != 0) {
            drawTurned(g, code, box);
            return;
        }
        AffineTransform original = g.getTransform();
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            g.rotate(Math.toRadians(box.rotationDegrees()), box.centerX(), box.centerY());
            // Crisp module edges are what scanners need.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int x = (int) Math.round(box.centerX() - box.width() / 2);
            int y = (int) Math.round(box.centerY() - box.height() / 2);
            int width = (int) Math.round(box.width());
            int height = (int) Math.round(box.height());
            int pad = (int) Math.round(width * QUIET_ZONE);
            g.setColor(Color.WHITE);
            g.fillRect(x - pad, y - pad, width + 2 * pad, height + 2 * pad);
            g.drawImage(code, x, y, width, height, null);
        } finally {
            g.setTransform(original);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    interpolation == null ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : interpolation);
        }
    }

    /**
     * A code turned by a non-right angle. Turning a crisp bitmap directly leaves jagged module edges
     * that scanners struggle with, so it is turned at {@value #SUPERSAMPLE}x size (modules still crisp
     * blocks) and then averaged down, which gives clean anti-aliased edges. The white quiet zone is
     * turned with it; everything outside it stays transparent so the ticket behind is untouched.
     */
    private static void drawTurned(Graphics2D g, BufferedImage code, CodePlacement.Box box) {
        double radians = Math.toRadians(box.rotationDegrees());
        double cos = Math.abs(Math.cos(radians));
        double sin = Math.abs(Math.sin(radians));
        double pad = box.width() * QUIET_ZONE;
        double fullWidth = box.width() + 2 * pad;
        double fullHeight = box.height() + 2 * pad;
        int boundsWidth = (int) Math.ceil(fullWidth * cos + fullHeight * sin) + 2;
        int boundsHeight = (int) Math.ceil(fullWidth * sin + fullHeight * cos) + 2;

        BufferedImage large = new BufferedImage(boundsWidth * SUPERSAMPLE, boundsHeight * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = large.createGraphics();
        try {
            lg.scale(SUPERSAMPLE, SUPERSAMPLE);
            lg.rotate(radians, boundsWidth / 2.0, boundsHeight / 2.0);
            lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            lg.setColor(Color.WHITE);
            lg.fill(new Rectangle2D.Double((boundsWidth - fullWidth) / 2, (boundsHeight - fullHeight) / 2, fullWidth, fullHeight));
            lg.drawImage(code, (int) Math.round((boundsWidth - box.width()) / 2), (int) Math.round((boundsHeight - box.height()) / 2),
                    (int) Math.round(box.width()), (int) Math.round(box.height()), null);
        } finally {
            lg.dispose();
        }
        Image small = large.getScaledInstance(boundsWidth, boundsHeight, Image.SCALE_AREA_AVERAGING);
        g.drawImage(small, (int) Math.round(box.centerX() - boundsWidth / 2.0), (int) Math.round(box.centerY() - boundsHeight / 2.0), null);
    }
}
