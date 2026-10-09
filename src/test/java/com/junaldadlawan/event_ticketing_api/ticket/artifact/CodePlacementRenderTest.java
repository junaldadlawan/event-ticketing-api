package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the organizer-chosen code placement (type, position, width, rotation)
 * is really where the code ends up on the rendered ticket - and still scans
 * there. Each test crops the rendered image to the expected area (worked out
 * here by hand from the percentages, not by the code under test) and decodes
 * only that crop, so a code drawn somewhere else fails the test even though ZXing
 * could find it on the whole page.
 */
class CodePlacementRenderTest {

    private static final int PNG_WIDTH = 900;
    private static final int PNG_HEIGHT = 380;
    private static final float PDF_WIDTH = 612f;
    private static final float PDF_HEIGHT = 792f;
    private static final float PDF_DPI = 144f;
    private static final float PDF_SCALE = PDF_DPI / 72f;

    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();
    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();
    private final PngTicketRenderer pngRenderer = new PngTicketRenderer();
    private final PdfTicketRenderer pdfRenderer = new PdfTicketRenderer();

    /**
     * A realistic credential (ticket id, version, signature) - FIXED, not random: ZXing itself misses the odd
     * random payload, which would make these position tests flaky without saying anything about the renderer.
     */
    private String credential() {
        return "11111111-2222-3333-4444-555555555555:1.AbC123-_xyzSIGNATUREvalue-0123456789abcdefghij";
    }

    private TicketArtifactFields fields(String payload, CodePlacement placement) {
        BufferedImage code = placement != null && placement.type() == CodeType.BARCODE
                ? barcodeGenerator.generate(payload)
                : qrCodeGenerator.generate(payload);
        return new TicketArtifactFields("Concert Night", "General", "General Admission", "ABC-123456",
                code, "#112233", placement);
    }

    private String decode(BufferedImage image) throws NotFoundException {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE, BarcodeFormat.PDF_417));
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels))), hints).getText();
    }

    private BufferedImage crop(BufferedImage image, int x, int y, int width, int height, int margin) {
        int left = Math.max(0, x - margin);
        int top = Math.max(0, y - margin);
        int right = Math.min(image.getWidth(), x + width + margin);
        int bottom = Math.min(image.getHeight(), y + height + margin);
        return image.getSubimage(left, top, right - left, bottom - top);
    }

    private BufferedImage png(TicketArtifactFields fields) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(pngRenderer.render(fields)));
    }

    private BufferedImage pdfPage(TicketArtifactFields fields) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfRenderer.render(fields))) {
            return new PDFRenderer(document).renderImageWithDPI(0, PDF_DPI);
        }
    }

    // ---- CodePlacement.resolve ----

    @Test
    void resolve_aRotatedQr_keepsItsSquareAndStaysOnTheTicket() {
        CodePlacement.Box box = new CodePlacement(CodeType.QR, 5, 30, 28, 45).resolve(900, 380);

        double bounds = box.width() * Math.sqrt(2);
        assertThat(box.width()).isEqualTo(box.height());
        assertThat(box.rotationDegrees()).isEqualTo(45.0);
        assertThat(box.centerY() - bounds / 2).isGreaterThanOrEqualTo(-1e-9);
        assertThat(box.centerY() + bounds / 2).isLessThanOrEqualTo(380.0 + 1e-9);
        assertThat(box.centerX() - bounds / 2).isGreaterThanOrEqualTo(-1e-9);
    }

    @Test
    void resolve_qr_convertsPercentagesToACentredSquare() {
        CodePlacement.Box box = new CodePlacement(CodeType.QR, 10, 30, 25, 0).resolve(900, 380);

        assertThat(box.width()).isEqualTo(225.0);
        assertThat(box.height()).isEqualTo(225.0);
        assertThat(box.centerX()).isEqualTo(90.0 + 112.5);
        assertThat(box.centerY()).isEqualTo(114.0 + 112.5);
        assertThat(box.rotationDegrees()).isZero();
    }

    @Test
    void resolve_barcode_isThreeToOne() {
        CodePlacement.Box box = new CodePlacement(CodeType.BARCODE, 10, 10, 45, 0).resolve(900, 380);

        assertThat(box.width()).isEqualTo(405.0);
        assertThat(box.height()).isEqualTo(135.0);
    }

    @Test
    void resolve_clampsASquareThatWouldRunOffTheBottom() {
        // 25% of 900 = 225 tall; y=95% of 380 would end far below the ticket.
        CodePlacement.Box box = new CodePlacement(CodeType.QR, 10, 95, 25, 0).resolve(900, 380);

        assertThat(box.centerY() + box.height() / 2).isLessThanOrEqualTo(380.0 + 1e-9);
        assertThat(box.centerY() - box.height() / 2).isEqualTo(380.0 - 225.0);
    }

    @Test
    void resolve_clampsTheRightEdge() {
        CodePlacement.Box box = new CodePlacement(CodeType.QR, 95, 0, 25, 0).resolve(900, 380);

        assertThat(box.centerX() + box.width() / 2).isLessThanOrEqualTo(900.0 + 1e-9);
    }

    @Test
    void resolve_aSquareTallerThanTheTicket_isShrunkToFit() {
        CodePlacement.Box box = new CodePlacement(CodeType.QR, 0, 0, 80, 0).resolve(900, 380);

        assertThat(box.width()).isEqualTo(380.0);
        assertThat(box.height()).isEqualTo(380.0);
    }

    @Test
    void resolve_aRotatedBarcode_staysFullyOnTheTicket() {
        // 3:1 barcode 50% wide = 450x150; turned 90 degrees it is 150 wide x 450 tall > 380, so it shrinks.
        CodePlacement.Box box = new CodePlacement(CodeType.BARCODE, 40, 10, 50, 90).resolve(900, 380);

        double boundsHeight = box.width(); // at 90 degrees the box's width runs vertically
        double boundsWidth = box.height();
        assertThat(boundsHeight).isLessThanOrEqualTo(380.0 + 1e-9);
        assertThat(box.centerY() - boundsHeight / 2).isGreaterThanOrEqualTo(-1e-9);
        assertThat(box.centerY() + boundsHeight / 2).isLessThanOrEqualTo(380.0 + 1e-9);
        assertThat(box.centerX() - boundsWidth / 2).isGreaterThanOrEqualTo(-1e-9);
        assertThat(box.width() / box.height()).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void resolve_normalisesTheRotation() {
        assertThat(new CodePlacement(CodeType.BARCODE, 10, 10, 40, 450).resolve(900, 380).rotationDegrees())
                .isEqualTo(90.0);
    }

    // ---- PNG ----

    @Test
    void png_qr_isDrawnAtThePlacementAndScans() throws Exception {
        String payload = credential();
        // top-left (5%, 30%) = (45, 114); side 28% of 900 = 252 (fits the 380 height)
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.QR, 5, 30, 28, 0)));

        assertThat(decode(crop(image, 45, 114, 252, 252, 14))).isEqualTo(payload);
    }

    @Test
    void png_qr_leavesTheDefaultCornerFree() throws Exception {
        BufferedImage image = png(fields(credential(), new CodePlacement(CodeType.QR, 5, 30, 28, 0)));

        // the default placement is the bottom-right 260px square
        BufferedImage defaultCorner = crop(image, PNG_WIDTH - 260 - 24, PNG_HEIGHT - 260 - 24, 260, 260, 0);

        assertThatThrownBy(() -> decode(defaultCorner)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void png_withoutPlacement_keepsTheDefaultBottomRightQr() throws Exception {
        String payload = credential();
        BufferedImage image = png(fields(payload, null));

        assertThat(decode(crop(image, PNG_WIDTH - 260 - 24, PNG_HEIGHT - 260 - 24, 260, 260, 14))).isEqualTo(payload);
    }

    @Test
    void png_qrPlacedTooLow_isClampedOntoTheTicketAndStillScans() throws Exception {
        String payload = credential();
        // side 270px; y=99% would run off the ticket, so it sits flush with the bottom: y = 380 - 270 = 110
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.QR, 60, 99, 30, 0)));

        assertThat(decode(crop(image, 540, 110, 270, 270, 14))).isEqualTo(payload);
    }

    @Test
    void png_barcode_isDrawnAtThePlacementAndScans() throws Exception {
        String payload = credential();
        // top-left (5%, 20%) = (45, 76); 45% wide = 405 x 135 (3:1)
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.BARCODE, 5, 20, 45, 0)));

        assertThat(decode(crop(image, 45, 76, 405, 135, 14))).isEqualTo(payload);
    }

    @Test
    void png_barcode_atItsMinimumWidth_stillScans() throws Exception {
        String payload = credential();
        double minimum = CodeType.BARCODE.minWidthPercent(PNG_WIDTH);
        int width = (int) Math.round(PNG_WIDTH * minimum / 100.0);
        int height = Math.round(width / 3f);
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.BARCODE, 5, 20, minimum, 0)));

        assertThat(decode(crop(image, 45, 76, width, height, 14))).isEqualTo(payload);
    }

    @Test
    void png_qr_atItsMinimumWidth_stillScans() throws Exception {
        String payload = credential();
        double minimum = CodeType.QR.minWidthPercent(PNG_WIDTH);
        int side = (int) Math.round(PNG_WIDTH * minimum / 100.0);
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.QR, 5, 20, minimum, 0)));

        assertThat(decode(crop(image, 45, 76, side, side, 14))).isEqualTo(payload);
    }

    @Test
    void png_barcodeRotatedNinetyDegrees_isDrawnVerticallyAndScans() throws Exception {
        String payload = credential();
        // 30% of 900 = 270 wide x 90 tall unrotated; top-left (10%, 5%) -> centre (90+135, 19+45) = (225, 64).
        // Turned 90 degrees it is 90 wide x 270 tall; the centre is moved down to keep it on the ticket
        // only if needed: 64 - 135 < 0, so centre y = 135. Region: x 180..270, y 0..270.
        BufferedImage image = png(fields(payload, new CodePlacement(CodeType.BARCODE, 10, 5, 30, 90)));

        assertThat(decode(crop(image, 180, 0, 90, 270, 14))).isEqualTo(payload);
    }

    @Test
    void png_qrRotated_isTurnedAboutItsCentreAndScans() throws Exception {
        // A fixed credential, so this can never flake on an unlucky random payload (ZXing itself misses the
        // odd one); the angle sweep over random payloads lives in the renderer, not here.
        String payload = "11111111-2222-3333-4444-555555555555:1.AbC123-_xyzSIGNATUREvalue-0123456789abcdefghij";
        // 28% of 900 = 252px square at (5%, 30%) = (45, 114); centre (171, 240). Turned 45 degrees its
        // bounding box is 252*sqrt(2) = 356px: wider than the room below the centre, so the centre moves
        // up to keep it on the ticket (y: 240 -> 202). Crop the whole bounding box.
        for (int degrees : new int[] {45, 90, 135, 225, 300}) {
            BufferedImage image = png(fields(payload, new CodePlacement(CodeType.QR, 5, 30, 28, degrees)));

            assertThat(decode(crop(image, 0, 0, 400, PNG_HEIGHT, 0)))
                    .as("QR rotated %d degrees", degrees).isEqualTo(payload);
        }
    }

    @Test
    void png_qrRotatedNinety_leavesTheDefaultCornerFree() throws Exception {
        BufferedImage image = png(fields(credential(), new CodePlacement(CodeType.QR, 5, 30, 28, 90)));

        BufferedImage defaultCorner = crop(image, PNG_WIDTH - 260 - 24, PNG_HEIGHT - 260 - 24, 260, 260, 0);

        assertThatThrownBy(() -> decode(defaultCorner)).isInstanceOf(NotFoundException.class);
    }

    // ---- PDF ----

    @Test
    void pdf_qr_isDrawnAtThePlacementAndScans() throws Exception {
        String payload = credential();
        BufferedImage page = pdfPage(fields(payload, new CodePlacement(CodeType.QR, 10, 30, 35, 0)));
        int x = Math.round(PDF_WIDTH * 0.10f * PDF_SCALE);
        int y = Math.round(PDF_HEIGHT * 0.30f * PDF_SCALE);
        int side = Math.round(PDF_WIDTH * 0.35f * PDF_SCALE);

        assertThat(decode(crop(page, x, y, side, side, 24))).isEqualTo(payload);
    }

    @Test
    void pdf_qr_leavesTheDefaultAreaFree() throws Exception {
        BufferedImage page = pdfPage(fields(credential(), new CodePlacement(CodeType.QR, 10, 30, 35, 0)));
        // default: 220pt square, centred horizontally, 60pt up from the bottom
        int x = Math.round((PDF_WIDTH - 220f) / 2 * PDF_SCALE);
        int y = Math.round((PDF_HEIGHT - 60f - 220f) * PDF_SCALE);
        int side = Math.round(220f * PDF_SCALE);
        BufferedImage defaultArea = crop(page, x, y, side, side, 0);

        assertThatThrownBy(() -> decode(defaultArea)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void pdf_withoutPlacement_keepsTheDefaultCentredQr() throws Exception {
        String payload = credential();
        BufferedImage page = pdfPage(fields(payload, null));
        int x = Math.round((PDF_WIDTH - 220f) / 2 * PDF_SCALE);
        int y = Math.round((PDF_HEIGHT - 60f - 220f) * PDF_SCALE);
        int side = Math.round(220f * PDF_SCALE);

        assertThat(decode(crop(page, x, y, side, side, 24))).isEqualTo(payload);
    }

    @Test
    void pdf_barcode_isDrawnAtThePlacementAndScans() throws Exception {
        String payload = credential();
        BufferedImage page = pdfPage(fields(payload, new CodePlacement(CodeType.BARCODE, 10, 30, 60, 0)));
        int x = Math.round(PDF_WIDTH * 0.10f * PDF_SCALE);
        int y = Math.round(PDF_HEIGHT * 0.30f * PDF_SCALE);
        int width = Math.round(PDF_WIDTH * 0.60f * PDF_SCALE);
        int height = Math.round(width / 3f);

        assertThat(decode(crop(page, x, y, width, height, 24))).isEqualTo(payload);
    }

    @Test
    void pdf_qrRotated_scans() throws Exception {
        String payload = "11111111-2222-3333-4444-555555555555:1.AbC123-_xyzSIGNATUREvalue-0123456789abcdefghij";
        // 35% of 612 = 214.2pt square, top-left (10%, 30%): centre (61.2+107.1, 237.6+107.1) = (168.3, 344.7).
        // Turned 30 degrees its bounding box is 214.2*(cos30+sin30) = 292.6pt, well inside the page.
        BufferedImage page = pdfPage(fields(payload, new CodePlacement(CodeType.QR, 10, 30, 35, 30)));
        int left = Math.round((168.3f - 146.3f) * PDF_SCALE);
        int top = Math.round((344.7f - 146.3f) * PDF_SCALE);
        int side = Math.round(292.6f * PDF_SCALE);

        assertThat(decode(crop(page, left, top, side, side, 24))).isEqualTo(payload);
    }

    @Test
    void pdf_barcodeRotatedNinetyDegrees_scans() throws Exception {
        String payload = credential();
        // 60% of 612 = 367.2pt wide x 122.4pt tall, top-left (10%, 30%) -> centre (61.2+183.6, 237.6+61.2)
        // = (244.8, 298.8); turned 90 degrees it covers x 183.6..306, y 115.2..482.4 (all on the page).
        BufferedImage page = pdfPage(fields(payload, new CodePlacement(CodeType.BARCODE, 10, 30, 60, 90)));
        int x = Math.round(183.6f * PDF_SCALE);
        int y = Math.round(115.2f * PDF_SCALE);
        int width = Math.round(122.4f * PDF_SCALE);
        int height = Math.round(367.2f * PDF_SCALE);

        assertThat(decode(crop(page, x, y, width, height, 24))).isEqualTo(payload);
    }
}
