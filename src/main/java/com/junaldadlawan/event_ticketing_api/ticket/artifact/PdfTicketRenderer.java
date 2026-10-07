package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders the {@code format=physical} ticket artifact (Phase 6b confirmed
 * decisions #2/#3) as a single-page, print-ready PDF via PDFBox: the same
 * fields as {@link PngTicketRenderer} (event title, ticket type name,
 * seat-or-"General Admission", ticket number, QR code), plus a {@code
 * primaryColor}-based accent bar if the resolved template has one.
 */
@Component
public class PdfTicketRenderer {

    private static final Color DEFAULT_ACCENT = new Color(0x2B, 0x3A, 0x67);

    /** Output of the ticket designer is drawn at 2x so print stays sharp (the page itself is 1 px = 1 pt). */
    private static final double DESIGNED_SCALE = 2.0;

    private final TicketCanvasRenderer canvasRenderer = new TicketCanvasRenderer();

    public byte[] render(TicketArtifactFields fields) {
        if (fields.design() != null) {
            return renderDesigned(fields);
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDRectangle mediaBox = page.getMediaBox();
            float width = mediaBox.getWidth();
            float height = mediaBox.getHeight();

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                Color accent = resolveColor(fields.primaryColorHex());
                contentStream.setNonStrokingColor(accent);
                contentStream.addRect(0, height - 80, width, 80);
                contentStream.fill();

                contentStream.setNonStrokingColor(Color.WHITE);
                drawLine(contentStream, helveticaBold(), 20, height - 50, fields.eventTitle(), 18);

                contentStream.setNonStrokingColor(Color.BLACK);
                float y = height - 130;
                y = drawLine(contentStream, helveticaBold(), 20, y, "Ticket type: " + fields.ticketTypeName(), 14);
                y -= 10;
                y = drawLine(contentStream, helvetica(), 20, y, fields.seatDescription(), 14);
                y -= 10;
                drawLine(contentStream, helveticaBold(), 20, y, "Ticket #" + fields.ticketNumber(), 16);

                PDImageXObject codeImage = LosslessFactory.createFromImage(document, fields.codeImage());
                if (fields.codePlacement() == null) {
                    float qrSize = 220f;
                    contentStream.drawImage(codeImage, (width - qrSize) / 2, 60, qrSize, qrSize);
                } else {
                    drawPlacedCode(contentStream, codeImage, fields.codePlacement(), width, height);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render ticket artifact PDF", e);
        }
    }

    /**
     * Organizer-chosen position from the ticket template, measured from the top-left (PDF user
     * space starts at the bottom-left): rotated clockwise about the box centre, kept fully on the
     * page, with a white quiet zone behind it whatever is drawn there.
     */
    private void drawPlacedCode(PDPageContentStream contentStream, PDImageXObject image, CodePlacement placement,
                                float pageWidth, float pageHeight) throws IOException {
        CodePlacement.Box box = placement.resolve(pageWidth, pageHeight);
        float width = (float) box.width();
        float height = (float) box.height();
        float pad = width * 0.04f;
        contentStream.saveGraphicsState();
        contentStream.transform(Matrix.getRotateInstance(-Math.toRadians(box.rotationDegrees()),
                (float) box.centerX(), (float) (pageHeight - box.centerY())));
        contentStream.setNonStrokingColor(Color.WHITE);
        contentStream.addRect(-width / 2 - pad, -height / 2 - pad, width + 2 * pad, height + 2 * pad);
        contentStream.fill();
        contentStream.drawImage(image, -width / 2, -height / 2, width, height);
        contentStream.restoreGraphicsState();
    }

    /**
     * A ticket laid out in the designer: the same canvas image the PNG uses (drawn by one engine so the
     * two cannot drift apart), on a page of the ticket's own size. The text is therefore part of the
     * picture, not selectable.
     */
    private byte[] renderDesigned(TicketArtifactFields fields) {
        TicketDesign design = fields.design();
        BufferedImage image = canvasRenderer.render(fields, DESIGNED_SCALE);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(design.width(), design.height()));
            document.addPage(page);
            PDImageXObject canvas = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(canvas, 0, 0, design.width(), design.height());
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render ticket artifact PDF", e);
        }
    }

    private float drawLine(PDPageContentStream contentStream, PDType1Font font, float x, float y, String text, float fontSize) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
        return y - (fontSize + 6);
    }

    private PDType1Font helvetica() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private PDType1Font helveticaBold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
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
