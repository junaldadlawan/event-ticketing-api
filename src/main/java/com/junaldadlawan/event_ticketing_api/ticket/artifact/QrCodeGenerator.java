package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Thin wrapper around ZXing (Phase 6b confirmed decision #4) — encodes a
 * ticket's raw credential (see {@code TicketArtifactServiceImpl}) into a
 * scannable QR code image, embedded into the rendered artifact.
 */
@Component
public class QrCodeGenerator {

    private static final int DEFAULT_SIZE_PX = 300;

    public BufferedImage generate(String payload, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix bitMatrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to generate ticket QR code", e);
        }
    }

    public BufferedImage generate(String payload) {
        return generate(payload, DEFAULT_SIZE_PX);
    }
}
