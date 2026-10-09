package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.pdf417.encoder.Dimensions;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Encodes a ticket's raw credential as a PDF417 barcode, the wide (about 3:1)
 * 2D barcode used on boarding passes - the {@code BARCODE} option of a ticket
 * template. PDF417 rather than a linear barcode (Code 128) because the
 * credential is ~80 characters: a linear barcode for that many characters
 * would need to be wider than the ticket itself to scan, while PDF417 stacks
 * the data in rows and fits a 3:1 box.
 */
@Component
public class BarcodeGenerator {

    /** Fixed 3:1 canvas, matching {@link com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType#BARCODE}. */
    private static final int WIDTH_PX = 1200;
    private static final int HEIGHT_PX = 400;

    /**
     * The encoder returns the symbol at the nearest whole-module size, which is rarely exactly 3:1. The
     * ticket draws a barcode in a fixed 3:1 box, so make the image that shape here (nearest-neighbour: the
     * bars stay crisp) rather than leaving the renderers to distort it.
     */
    private static BufferedImage toThreeToOne(BufferedImage symbol) {
        int width = symbol.getWidth();
        int height = Math.max(1, Math.round(width / 3f));
        BufferedImage shaped = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = shaped.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(symbol, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return shaped;
    }

    public BufferedImage generate(String payload) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, 2);
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.PDF417_DIMENSIONS, new Dimensions(4, 8, 8, 90));
            BitMatrix matrix = new PDF417Writer().encode(payload, BarcodeFormat.PDF_417, WIDTH_PX, HEIGHT_PX, hints);
            return toThreeToOne(MatrixToImageWriter.toBufferedImage(matrix));
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to generate ticket barcode", e);
        }
    }
}
