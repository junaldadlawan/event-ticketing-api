package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders the {@code format=digital} ticket artifact (Phase 6b confirmed
 * decisions #2/#3) as a fixed-size PNG: event title, ticket type name,
 * seat-or-"General Admission", the ticket number, and the QR code — plus a
 * {@code primaryColor}-based accent bar if the resolved template has one.
 * Intentionally minimal/functional per {@code requirements.md} §4.10, not
 * visually polished.
 */
@Component
public class PngTicketRenderer {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 380;
    private static final int QR_SIZE = 260;
    private static final Color DEFAULT_ACCENT = new Color(0x2B, 0x3A, 0x67);

    public byte[] render(TicketArtifactFields fields) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            Color accent = resolveColor(fields.primaryColorHex());
            g.setColor(accent);
            g.fillRect(0, 0, WIDTH, 70);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString(fields.eventTitle(), 24, 46);

            g.setColor(Color.BLACK);
            int textX = 24;
            int y = 130;
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g.drawString("Ticket type: " + fields.ticketTypeName(), textX, y);
            y += 36;
            g.drawString(fields.seatDescription(), textX, y);
            y += 40;
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.drawString("Ticket #" + fields.ticketNumber(), textX, y);

            // Nearest-neighbor only for the QR rescale: bilinear/bicubic
            // smoothing would blend black/white module edges into gray,
            // which is worse for any barcode reader (phone camera or
            // otherwise) than the crisp edges nearest-neighbor preserves.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(fields.qrCodeImage(), WIDTH - QR_SIZE - 24, HEIGHT - QR_SIZE - 24, QR_SIZE, QR_SIZE, null);
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode ticket artifact PNG", e);
        }
    }

    private Color resolveColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return DEFAULT_ACCENT;
        }
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return DEFAULT_ACCENT;
        }
    }
}
