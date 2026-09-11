package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
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

    public byte[] render(TicketArtifactFields fields) {
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

                PDImageXObject qrImage = LosslessFactory.createFromImage(document, fields.qrCodeImage());
                float qrSize = 220f;
                contentStream.drawImage(qrImage, (width - qrSize) / 2, 60, qrSize, qrSize);
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
