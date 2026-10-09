package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a ticket laid out in the template designer. One engine for both formats: the PNG is its
 * output, the PDF embeds it - so the two can never drift apart.
 * <p>
 * Drawing order, always: the canvas filled with the background colour (white if none); the
 * background image (clipped to the ticket); the text fields in list order (later on top); the code
 * last, over everything. Sizes are percentages of the ticket, so the same design renders at any
 * {@code scale} (the PDF asks for 2x for print sharpness).
 */
final class TicketCanvasRenderer {

    private static final String FONT_FAMILY = "SansSerif";
    private static final double LINE_HEIGHT = 1.15;
    /** Fractional metrics, independent of any scaling, so text measures identically at every scale. */
    private static final FontRenderContext MEASURE = new FontRenderContext(null, true, true);
    /** Keeps a 5000 x 5000 ticket (or a large scale) from needing hundreds of MB of pixels. */
    private static final double MAX_PIXELS = 36_000_000d;
    private static final double DEFAULT_QR_MARGIN_PX = 24;
    /** Today's default QR: 260 px on a 380 px high ticket. */
    private static final double DEFAULT_QR_SIDE_OF_HEIGHT = 260d / 380d;

    BufferedImage render(TicketArtifactFields fields, double requestedScale) {
        TicketDesign design = fields.design();
        double scale = Math.min(requestedScale, Math.sqrt(MAX_PIXELS / ((double) design.width() * design.height())));
        int pixelWidth = (int) Math.max(1, Math.round(design.width() * scale));
        int pixelHeight = (int) Math.max(1, Math.round(design.height() * scale));

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setColor(parseColor(design.backgroundColorHex(), Color.WHITE));
            g.fillRect(0, 0, pixelWidth, pixelHeight);

            // Everything below is laid out in ticket pixels and scaled to the output size.
            g.scale(scale, scale);
            g.setClip(0, 0, design.width(), design.height());
            drawBackgroundImage(g, design);
            for (TicketTextField field : design.textFields()) {
                drawTextField(g, design, field, fields.values());
            }

            // The code goes last, at output pixel size and with no scaling transform: scanners need crisp edges.
            g.setTransform(new AffineTransform());
            g.setClip(null);
            if (design.printCode() && fields.codeImage() != null) {
                CodePlacement placement = fields.codePlacement() != null
                        ? fields.codePlacement()
                        : defaultCodePlacement(design.width(), design.height());
                CodeDrawer.draw(g, fields.codeImage(), placement.resolve(pixelWidth, pixelHeight));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** A QR at the bottom-right with a 24 px margin, the way the built-in layout places it. */
    static CodePlacement defaultCodePlacement(int width, int height) {
        double side = height * DEFAULT_QR_SIDE_OF_HEIGHT;
        return new CodePlacement(CodeType.QR,
                (width - DEFAULT_QR_MARGIN_PX - side) / width * 100.0,
                (height - DEFAULT_QR_MARGIN_PX - side) / height * 100.0,
                side / width * 100.0,
                0);
    }

    // ---- background ----

    private void drawBackgroundImage(Graphics2D g, TicketDesign design) {
        BufferedImage image = design.backgroundImage();
        if (image == null) {
            return;
        }
        double ticketWidth = design.width();
        double ticketHeight = design.height();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double x;
        double y;
        double width;
        double height;
        BackgroundFit fit = design.backgroundFit() == null ? BackgroundFit.COVER : design.backgroundFit();
        switch (fit) {
            case CONTAIN -> {
                double scale = Math.min(ticketWidth / imageWidth, ticketHeight / imageHeight);
                width = imageWidth * scale;
                height = imageHeight * scale;
                x = (ticketWidth - width) / 2;
                y = (ticketHeight - height) / 2;
            }
            case STRETCH -> {
                x = 0;
                y = 0;
                width = ticketWidth;
                height = ticketHeight;
            }
            case CUSTOM -> {
                TicketDesign.Rect rect = design.customRect();
                x = rect.x() / 100.0 * ticketWidth;
                y = rect.y() / 100.0 * ticketHeight;
                width = rect.width() / 100.0 * ticketWidth;
                height = rect.height() / 100.0 * ticketHeight;
            }
            default -> { // COVER: fill the ticket, crop the overflow equally on both sides
                double scale = Math.max(ticketWidth / imageWidth, ticketHeight / imageHeight);
                width = imageWidth * scale;
                height = imageHeight * scale;
                x = (ticketWidth - width) / 2;
                y = (ticketHeight - height) / 2;
            }
        }
        AffineTransform placement = new AffineTransform();
        placement.translate(x, y);
        placement.scale(width / imageWidth, height / imageHeight);
        g.drawImage(image, placement, null);
    }

    // ---- text ----

    private void drawTextField(Graphics2D g, TicketDesign design, TicketTextField field, TicketValues values) {
        String value = valueOf(field, values);
        if (value.isEmpty()) {
            return;
        }
        double fontSize = field.getFontSize() / 100.0 * design.height();
        Font font = new Font(FONT_FAMILY, field.isBold() ? Font.BOLD : Font.PLAIN, 1).deriveFont((float) fontSize);
        double lineHeight = fontSize * LINE_HEIGHT;
        double anchorX = field.getX() / 100.0 * design.width();
        double centerY = field.getY() / 100.0 * design.height();
        TextAlign align = field.getAlign();

        List<String> lines;
        double boxWidth;
        if (field.getKey().isDynamic()) {
            // The box is reserved space; the value fills it from the aligned edge and may grow past it.
            lines = List.of(value);
            if (field.getSampleText() != null) {
                boxWidth = width(font, field.getSampleText());
            } else if (field.getSampleLength() != null) {
                boxWidth = field.getSampleLength() * width(font, "X");
            } else {
                boxWidth = width(font, value);
            }
        } else {
            lines = splitLines(value, field.getLineBreaks());
            boxWidth = 0;
            for (String line : lines) {
                boxWidth = Math.max(boxWidth, width(font, line));
            }
        }
        double boxLeft = switch (align) {
            case LEFT -> anchorX;
            case CENTER -> anchorX - boxWidth / 2;
            case RIGHT -> anchorX - boxWidth;
        };
        double boxHeight = lines.size() * lineHeight;
        double boxTop = centerY - boxHeight / 2;

        Graphics2D text = (Graphics2D) g.create();
        try {
            text.setFont(font);
            text.setColor(parseColor(field.getColor(), Color.BLACK));
            // Rotation turns the whole block about the centre of its box, after it has been placed by its anchor.
            text.rotate(Math.toRadians(field.getRotation()), boxLeft + boxWidth / 2, centerY);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                double lineWidth = width(font, line);
                double lineLeft = switch (align) {
                    case LEFT -> boxLeft;
                    case CENTER -> boxLeft + (boxWidth - lineWidth) / 2;
                    case RIGHT -> boxLeft + boxWidth - lineWidth;
                };
                LineMetrics metrics = font.getLineMetrics(line, MEASURE);
                double baseline = boxTop + i * lineHeight
                        + (lineHeight - (metrics.getAscent() + metrics.getDescent())) / 2 + metrics.getAscent();
                text.drawString(line, (float) lineLeft, (float) baseline);
            }
        } finally {
            text.dispose();
        }
    }

    private static String valueOf(TicketTextField field, TicketValues values) {
        String value = switch (field.getKey()) {
            case TICKET_TYPE -> values.ticketType();
            case SECTION -> values.section();
            case ROW -> values.row();
            case SEAT -> values.seat();
            case TICKET_NUMBER -> values.ticketNumber();
            case ATTENDEE_NAME -> values.attendeeName();
            case EVENT_NAME -> values.eventName();
            case EVENT_DATE -> values.eventDate();
            case EVENT_TIME -> values.eventTime();
            case VENUE -> values.venue();
            case CUSTOM -> field.getText();
        };
        return value == null ? "" : value;
    }

    /**
     * Splits at the given character positions (ascending); a position at or past the end of the value
     * is ignored, and every line is trimmed. No positions = one line.
     */
    static List<String> splitLines(String value, List<Integer> lineBreaks) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        if (lineBreaks != null) {
            for (int position : lineBreaks) {
                if (position <= start || position >= value.length()) {
                    continue;
                }
                lines.add(value.substring(start, position).trim());
                start = position;
            }
        }
        lines.add(value.substring(start).trim());
        return lines;
    }

    private static double width(Font font, String text) {
        return font.getStringBounds(text, MEASURE).getWidth();
    }

    private static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
