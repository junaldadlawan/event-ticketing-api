package com.junaldadlawan.event_ticketing_api.ticket.artifact;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Pixel-level tests for the ticket designer's drawing engine ({@link TicketCanvasRenderer}) and for the
 * PNG / PDF renderers that expose it. Positions are checked by measuring where ink actually lands, with a
 * small tolerance for glyph side bearings - never by calling the renderer's own geometry.
 */
class TicketCanvasRendererTest {

    private static final String CREDENTIAL = "11111111-2222-3333-4444-555555555555:1.AbC123-_xyzSIGNATUREvalue-0123456789abcdefghij";
    private static final Color BLUE = new Color(0, 0, 255);
    private static final Color GREEN = new Color(0, 255, 0);

    private final TicketCanvasRenderer renderer = new TicketCanvasRenderer();
    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();
    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    // ---- builders ----

    private TicketValues values() {
        return new TicketValues("VIP", "A", "3", "12", "ABC-123456", "Jamie Cruz",
                "Concert Night", "Wed, Dec 23, 2026", "8:30 PM", "Main Hall");
    }

    private TicketDesign design(int width, int height, String backgroundColor, BufferedImage image, BackgroundFit fit,
                                TicketDesign.Rect rect, List<TicketTextField> fields, boolean printCode) {
        return new TicketDesign(width, height, backgroundColor, image, fit, rect, fields, printCode);
    }

    private TicketArtifactFields fields(TicketDesign design, CodePlacement placement) {
        BufferedImage code = !design.printCode() ? null
                : placement != null && placement.type() == CodeType.BARCODE
                ? barcodeGenerator.generate(CREDENTIAL)
                : qrCodeGenerator.generate(CREDENTIAL);
        return new TicketArtifactFields("Concert Night", "VIP", "General Admission", "ABC-123456", code, null,
                placement, design, values());
    }

    private TicketTextField text(TextFieldKey key, double x, double y, double fontSize, String color, boolean bold,
                                 TextAlign align, int rotation, Integer sampleLength, String sampleText, String text,
                                 List<Integer> lineBreaks) {
        return TicketTextField.builder().key(key).x(x).y(y).fontSize(fontSize).color(color).bold(bold).align(align)
                .rotation(rotation).sampleLength(sampleLength).sampleText(sampleText).text(text).lineBreaks(lineBreaks)
                .build();
    }

    private TicketTextField custom(String value, double x, double y, double fontSize, String color, TextAlign align) {
        return text(TextFieldKey.CUSTOM, x, y, fontSize, color, true, align, 0, null, null, value, null);
    }

    private BufferedImage halves() {
        // 100 x 50: left half blue, right half green
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(BLUE);
        g.fillRect(0, 0, 50, 50);
        g.setColor(GREEN);
        g.fillRect(50, 0, 50, 50);
        g.dispose();
        return image;
    }

    private BufferedImage render(TicketArtifactFields fields) {
        return renderer.render(fields, 1.0);
    }

    // ---- measuring ----

    private boolean isInk(int rgb, int background) {
        Color a = new Color(rgb);
        Color b = new Color(background);
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue()) > 150;
    }

    /** Bounding box of every pixel that differs clearly from the canvas background, or null if there is none. */
    private Rectangle inkBounds(BufferedImage image, int background) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isInk(image.getRGB(x, y), background)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private int count(BufferedImage image, Color color) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                if (Math.abs(c.getRed() - color.getRed()) + Math.abs(c.getGreen() - color.getGreen())
                        + Math.abs(c.getBlue() - color.getBlue()) < 60) {
                    n++;
                }
            }
        }
        return n;
    }

    private void assertPixel(BufferedImage image, int x, int y, Color expected) {
        Color actual = new Color(image.getRGB(x, y));
        assertThat(Math.abs(actual.getRed() - expected.getRed()) + Math.abs(actual.getGreen() - expected.getGreen())
                + Math.abs(actual.getBlue() - expected.getBlue()))
                .as("pixel (%d,%d) was %s, expected %s", x, y, actual, expected).isLessThan(40);
    }

    private String decode(BufferedImage image) throws NotFoundException {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE, BarcodeFormat.PDF_417));
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels))), hints).getText();
    }

    // ---- canvas ----

    @Test
    void canvas_hasTheDesignedSize_andIsWhiteWithoutABackgroundColour() {
        BufferedImage image = render(fields(design(600, 200, null, null, null, null, List.of(), false), null));

        assertThat(image.getWidth()).isEqualTo(600);
        assertThat(image.getHeight()).isEqualTo(200);
        assertPixel(image, 0, 0, Color.WHITE);
        assertPixel(image, 599, 199, Color.WHITE);
    }

    @Test
    void canvas_isFilledWithTheBackgroundColour() {
        BufferedImage image = render(fields(design(300, 150, "#FF0000", null, null, null, List.of(), false), null));

        assertPixel(image, 10, 10, Color.RED);
        assertPixel(image, 299, 149, Color.RED);
    }

    @Test
    void canvas_scaleMakesTheImageBiggerWithTheSameLayout() {
        TicketArtifactFields f = fields(design(300, 150, "#FF0000", null, null, null, List.of(), false), null);

        BufferedImage twice = renderer.render(f, 2.0);

        assertThat(twice.getWidth()).isEqualTo(600);
        assertThat(twice.getHeight()).isEqualTo(300);
    }

    @Test
    void canvas_aHugeTicketAtHighScaleStaysWithinThePixelBudget() {
        TicketArtifactFields f = fields(design(5000, 5000, null, null, null, null, List.of(), false), null);

        BufferedImage image = renderer.render(f, 2.0);

        assertThat((long) image.getWidth() * image.getHeight()).isLessThanOrEqualTo(36_000_000L);
        assertThat(image.getWidth()).isEqualTo(image.getHeight());
    }

    // ---- background image: every fit ----

    @Test
    void background_cover_fillsTheTicketAndCropsTheCentre() {
        // 100x50 image on a 400x400 ticket: scaled 8x to 800x400, centre-cropped: the seam lands mid-ticket
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.COVER, null, List.of(), false), null));

        assertPixel(image, 100, 200, BLUE);
        assertPixel(image, 300, 200, GREEN);
        assertPixel(image, 100, 5, BLUE);
        assertPixel(image, 300, 395, GREEN);
    }

    @Test
    void background_noFit_behavesAsCover() {
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), null, null, List.of(), false), null));

        assertPixel(image, 100, 5, BLUE);
        assertPixel(image, 300, 395, GREEN);
    }

    @Test
    void background_contain_fitsInsideAndShowsTheFillColourAround() {
        // scaled 4x to 400x200, centred vertically: bands of fill colour above and below
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.CONTAIN, null, List.of(), false), null));

        assertPixel(image, 100, 200, BLUE);
        assertPixel(image, 300, 200, GREEN);
        assertPixel(image, 200, 10, Color.YELLOW);
        assertPixel(image, 200, 390, Color.YELLOW);
    }

    @Test
    void background_stretch_isExactlyTheTicketSize() {
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.STRETCH, null, List.of(), false), null));

        assertPixel(image, 100, 5, BLUE);
        assertPixel(image, 100, 395, BLUE);
        assertPixel(image, 300, 5, GREEN);
        assertPixel(image, 300, 395, GREEN);
    }

    @Test
    void background_custom_isPlacedInItsRectangleAndClipped() {
        // a 400x400 rectangle starting at the ticket's centre: only its top-left quarter is on the ticket
        TicketDesign.Rect rect = new TicketDesign.Rect(50, 50, 100, 100);
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.CUSTOM, rect, List.of(), false), null));

        assertPixel(image, 100, 100, Color.YELLOW);
        assertPixel(image, 250, 250, BLUE);
        assertPixel(image, 390, 390, BLUE);
    }

    @Test
    void background_custom_anImagePartlyOffTheTicketIsClippedNotShrunk() {
        // x = -50%: the image spans -200..200, so its blue half is off the ticket and its green half shows
        TicketDesign.Rect rect = new TicketDesign.Rect(-50, 0, 100, 100);
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.CUSTOM, rect, List.of(), false), null));

        assertPixel(image, 100, 100, GREEN);
        assertPixel(image, 300, 100, Color.YELLOW);
    }

    @Test
    void background_custom_aRectangleBiggerThanTheTicketIsClippedToIt() {
        // 200% x 200% starting at (-50%, -50%): the ticket only shows the middle of the image
        TicketDesign.Rect rect = new TicketDesign.Rect(-50, -50, 200, 200);
        BufferedImage image = render(fields(design(400, 400, "#FFFF00", halves(), BackgroundFit.CUSTOM, rect, List.of(), false), null));

        assertThat(image.getWidth()).isEqualTo(400);
        assertPixel(image, 100, 200, BLUE);
        assertPixel(image, 300, 200, GREEN);
    }

    // ---- text: alignment, y centre, size ----

    @Test
    void text_leftAlign_startsAtTheAnchor() {
        BufferedImage image = render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHHHH", 10, 50, 10, "#000000", TextAlign.LEFT)), false), null));

        Rectangle ink = inkBounds(image, Color.WHITE.getRGB());
        assertThat(ink.x).isCloseTo(60, within(4));
        // y is the vertical centre of the block
        assertThat(ink.y + ink.height / 2.0).isCloseTo(100.0, within(5.0));
    }

    @Test
    void text_centerAlign_isCentredOnTheAnchor() {
        BufferedImage image = render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHHHH", 50, 50, 10, "#000000", TextAlign.CENTER)), false), null));

        Rectangle ink = inkBounds(image, Color.WHITE.getRGB());
        assertThat(ink.x + ink.width / 2.0).isCloseTo(300.0, within(5.0));
    }

    @Test
    void text_rightAlign_endsAtTheAnchor() {
        BufferedImage image = render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHHHH", 90, 50, 10, "#000000", TextAlign.RIGHT)), false), null));

        Rectangle ink = inkBounds(image, Color.WHITE.getRGB());
        assertThat(ink.x + ink.width).isCloseTo(540, within(4));
    }

    @Test
    void text_fontSizeIsAPercentageOfTheTicketHeight() {
        Rectangle small = inkBounds(render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHH", 10, 50, 5, "#000000", TextAlign.LEFT)), false), null)), Color.WHITE.getRGB());
        Rectangle big = inkBounds(render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHH", 10, 50, 10, "#000000", TextAlign.LEFT)), false), null)), Color.WHITE.getRGB());

        assertThat(big.height / (double) small.height).isCloseTo(2.0, within(0.2));
        assertThat(big.width / (double) small.width).isCloseTo(2.0, within(0.2));
    }

    @Test
    void text_usesTheFieldColour() {
        BufferedImage image = render(fields(design(600, 200, null, null, null, null,
                List.of(custom("HHHH", 10, 50, 20, "#FF0000", TextAlign.LEFT)), false), null));

        assertThat(count(image, Color.RED)).isGreaterThan(50);
        assertThat(count(image, Color.BLACK)).isZero();
    }

    // ---- text: line breaks ----

    @Test
    void text_lineBreaks_splitStaticTextIntoStackedLines() {
        TicketTextField oneLine = custom("HHHHHHHH", 10, 50, 8, "#000000", TextAlign.LEFT);
        TicketTextField twoLines = text(TextFieldKey.CUSTOM, 10, 50, 8, "#000000", true, TextAlign.LEFT, 0, null, null,
                "HHHHHHHH", List.of(4));

        Rectangle single = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(oneLine), false), null)), Color.WHITE.getRGB());
        Rectangle stacked = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(twoLines), false), null)), Color.WHITE.getRGB());

        assertThat(stacked.width / (double) single.width).isCloseTo(0.5, within(0.08));
        assertThat(stacked.height).isGreaterThan((int) (single.height * 1.8));
        // the block stays vertically centred on y
        assertThat(stacked.y + stacked.height / 2.0).isCloseTo(150.0, within(6.0));
    }

    @Test
    void text_lineBreakPastTheEnd_isIgnored() {
        TicketTextField oneLine = custom("HHHH", 10, 50, 8, "#000000", TextAlign.LEFT);
        TicketTextField ignored = text(TextFieldKey.CUSTOM, 10, 50, 8, "#000000", true, TextAlign.LEFT, 0, null, null,
                "HHHH", List.of(9));

        Rectangle a = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(oneLine), false), null)), Color.WHITE.getRGB());
        Rectangle b = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(ignored), false), null)), Color.WHITE.getRGB());

        assertThat(b).isEqualTo(a);
    }

    @Test
    void splitLines_splitsAtThePositions_trimsEachLine_andIgnoresOutOfRangeOnes() {
        assertThat(TicketCanvasRenderer.splitLines("Hello World", List.of(5))).containsExactly("Hello", "World");
        assertThat(TicketCanvasRenderer.splitLines("ab cd ef", List.of(3, 6))).containsExactly("ab", "cd", "ef");
        assertThat(TicketCanvasRenderer.splitLines("abc", List.of(3, 10))).containsExactly("abc");
        assertThat(TicketCanvasRenderer.splitLines("abc", null)).containsExactly("abc");
        assertThat(TicketCanvasRenderer.splitLines("abc", List.of())).containsExactly("abc");
    }

    @Test
    void text_multiLineAlignment_followsTheField() {
        // two lines of different length; right aligned: both lines end at the anchor
        TicketTextField field = text(TextFieldKey.CUSTOM, 90, 50, 8, "#000000", true, TextAlign.RIGHT, 0, null, null,
                "HHHHHHHH", List.of(2));
        BufferedImage image = render(fields(design(600, 300, null, null, null, null, List.of(field), false), null));

        Rectangle ink = inkBounds(image, Color.WHITE.getRGB());
        assertThat(ink.x + ink.width).isCloseTo(540, within(4));
    }

    // ---- text: dynamic fields reserve a box and fill it from the aligned edge ----

    private Rectangle dynamicInk(TextAlign align, double x, Integer sampleLength, String sampleText, String value) {
        // TICKET_TYPE prints values().ticketType(); override through a TicketValues with the wanted value
        TicketValues v = new TicketValues(value, "A", "3", "12", "N", "Name", "E", "D", "T", "V");
        TicketTextField field = text(TextFieldKey.TICKET_TYPE, x, 50, 10, "#000000", true, align, 0, sampleLength, sampleText, null, null);
        TicketArtifactFields f = new TicketArtifactFields("t", "t", "s", "n", null, null, null,
                design(800, 200, null, null, null, null, List.of(field), false), v);
        return inkBounds(render(f), Color.WHITE.getRGB());
    }

    private double xWidth() {
        // the width of one "X" at the test font size, measured from a rendered X
        TicketValues v = new TicketValues("X", "A", "3", "12", "N", "Name", "E", "D", "T", "V");
        TicketTextField field = text(TextFieldKey.TICKET_TYPE, 10, 50, 10, "#000000", true, TextAlign.LEFT, 0, null, null, null, null);
        TicketArtifactFields f = new TicketArtifactFields("t", "t", "s", "n", null, null, null,
                design(800, 200, null, null, null, null, List.of(field), false), v);
        return inkBounds(render(f), Color.WHITE.getRGB()).width;
    }

    @Test
    void dynamic_left_aShorterValueStartsAtTheBoxLeftEdge() {
        Rectangle ink = dynamicInk(TextAlign.LEFT, 50, 8, null, "XX");

        assertThat(ink.x).isCloseTo(400, within(4));
    }

    @Test
    void dynamic_center_aShorterValueIsCentredInTheBox() {
        Rectangle ink = dynamicInk(TextAlign.CENTER, 50, 8, null, "XX");

        assertThat(ink.x + ink.width / 2.0).isCloseTo(400.0, within(5.0));
    }

    @Test
    void dynamic_right_aShorterValueEndsAtTheBoxRightEdge() {
        Rectangle ink = dynamicInk(TextAlign.RIGHT, 50, 8, null, "XX");

        assertThat(ink.x + ink.width).isCloseTo(400, within(5));
    }

    @Test
    void dynamic_left_aLongerValueKeepsGrowingToTheRight_neverTruncated() {
        double box = 3 * xWidth();
        Rectangle ink = dynamicInk(TextAlign.LEFT, 10, 3, null, "XXXXXXXX");

        assertThat(ink.x).isCloseTo(80, within(4));
        assertThat(ink.width).isGreaterThan((int) (box * 2));
    }

    @Test
    void dynamic_right_aLongerValueKeepsGrowingToTheLeft() {
        double box = 3 * xWidth();
        Rectangle ink = dynamicInk(TextAlign.RIGHT, 50, 3, null, "XXXXXXXX");

        assertThat(ink.x + ink.width).isCloseTo(400, within(5));
        assertThat(ink.x).isLessThan((int) (400 - box * 2));
    }

    @Test
    void dynamic_center_aLongerValueGrowsBothWays() {
        double box = 3 * xWidth();
        Rectangle ink = dynamicInk(TextAlign.CENTER, 50, 3, null, "XXXXXXXX");

        assertThat(ink.x + ink.width / 2.0).isCloseTo(400.0, within(5.0));
        assertThat(ink.width).isGreaterThan((int) (box * 2));
    }

    @Test
    void dynamic_sampleText_setsTheReservedWidth() {
        // the box is as wide as the sample "XXXXXXXXXX" (10 X); a right-aligned "X" ends at its right edge,
        // which is at the anchor - while the same value in a 1-X box would also end at the anchor but the
        // box left edge differs; so check CENTER: a short value in a wide sample box stays centred on the anchor
        Rectangle ink = dynamicInk(TextAlign.CENTER, 50, null, "XXXXXXXXXX", "X");

        assertThat(ink.x + ink.width / 2.0).isCloseTo(400.0, within(5.0));
    }

    @Test
    void dynamic_valuesAreThoseOfTheTicket() {
        // the SECTION / ROW / SEAT / TICKET_NUMBER / ATTENDEE_NAME / TICKET_TYPE keys each draw something
        for (TextFieldKey key : List.of(TextFieldKey.TICKET_TYPE, TextFieldKey.SECTION, TextFieldKey.ROW, TextFieldKey.SEAT,
                TextFieldKey.TICKET_NUMBER, TextFieldKey.ATTENDEE_NAME, TextFieldKey.EVENT_NAME, TextFieldKey.EVENT_DATE,
                TextFieldKey.EVENT_TIME, TextFieldKey.VENUE)) {
            TicketTextField field = text(key, 10, 50, 10, "#000000", true, TextAlign.LEFT, 0, null, null, null, null);

            BufferedImage image = render(fields(design(800, 200, null, null, null, null, List.of(field), false), null));

            assertThat(inkBounds(image, Color.WHITE.getRGB())).as("%s should print something", key).isNotNull();
        }
    }

    @Test
    void emptyValues_printNothing() {
        TicketValues blank = new TicketValues("", "", "", "", "", "", "", "", "", "");
        TicketTextField field = text(TextFieldKey.VENUE, 10, 50, 10, "#000000", true, TextAlign.LEFT, 0, null, null, null, null);
        TicketArtifactFields f = new TicketArtifactFields("t", "t", "s", "n", null, null, null,
                design(800, 200, null, null, null, null, List.of(field), false), blank);

        assertThat(inkBounds(render(f), Color.WHITE.getRGB())).isNull();
    }

    // ---- text: rotation ----

    @Test
    void text_rotation_turnsTheWholeBlockAboutTheCentreOfItsBox() {
        TicketTextField flat = text(TextFieldKey.CUSTOM, 30, 50, 8, "#000000", true, TextAlign.LEFT, 0, null, null, "HHHHHH", null);
        TicketTextField turned = text(TextFieldKey.CUSTOM, 30, 50, 8, "#000000", true, TextAlign.LEFT, 90, null, null, "HHHHHH", null);

        Rectangle before = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(flat), false), null)), Color.WHITE.getRGB());
        Rectangle after = inkBounds(render(fields(design(600, 300, null, null, null, null, List.of(turned), false), null)), Color.WHITE.getRGB());

        assertThat(after.height).isGreaterThan(after.width * 2);
        assertThat(after.width).isCloseTo(before.height, within(4));
        assertThat(after.height).isCloseTo(before.width, within(4));
        // same centre
        assertThat(after.x + after.width / 2.0).isCloseTo(before.x + before.width / 2.0, within(4.0));
        assertThat(after.y + after.height / 2.0).isCloseTo(before.y + before.height / 2.0, within(4.0));
    }

    @Test
    void text_rotation_ofADynamicFieldTurnsAboutItsReservedBox() {
        TicketValues v = new TicketValues("XX", "A", "3", "12", "N", "Name", "E", "D", "T", "V");
        TicketTextField flat = text(TextFieldKey.TICKET_TYPE, 50, 50, 8, "#000000", true, TextAlign.RIGHT, 0, 10, null, null, null);
        TicketTextField turned = text(TextFieldKey.TICKET_TYPE, 50, 50, 8, "#000000", true, TextAlign.RIGHT, 180, 10, null, null, null);

        Rectangle before = inkBounds(render(new TicketArtifactFields("t", "t", "s", "n", null, null, null,
                design(800, 300, null, null, null, null, List.of(flat), false), v)), Color.WHITE.getRGB());
        Rectangle after = inkBounds(render(new TicketArtifactFields("t", "t", "s", "n", null, null, null,
                design(800, 300, null, null, null, null, List.of(turned), false), v)), Color.WHITE.getRGB());

        // the value sits at the right edge of a 10-X box; a half turn about the box centre moves it to the LEFT edge
        double boxCentre = 400 - (10 * xWidth()) / 2.0;
        assertThat(before.x + before.width / 2.0).isGreaterThan(boxCentre);
        assertThat(after.x + after.width / 2.0).isLessThan(boxCentre);
    }

    // ---- order: later text on top, the code last ----

    @Test
    void text_laterFieldsAreDrawnOnTopOfEarlierOnes() {
        TicketTextField below = custom("HHHH", 10, 50, 25, "#FF0000", TextAlign.LEFT);
        TicketTextField above = custom("HHHH", 10, 50, 25, "#0000FF", TextAlign.LEFT);

        BufferedImage image = render(fields(design(600, 200, null, null, null, null, List.of(below, above), false), null));

        assertThat(count(image, BLUE)).isGreaterThan(200);
        assertThat(count(image, Color.RED)).isLessThan(count(image, BLUE) / 10);
    }

    @Test
    void text_reversedOrderReversesWhoIsOnTop() {
        TicketTextField red = custom("HHHH", 10, 50, 25, "#FF0000", TextAlign.LEFT);
        TicketTextField blue = custom("HHHH", 10, 50, 25, "#0000FF", TextAlign.LEFT);

        BufferedImage image = render(fields(design(600, 200, null, null, null, null, List.of(blue, red), false), null));

        assertThat(count(image, Color.RED)).isGreaterThan(200);
        assertThat(count(image, BLUE)).isLessThan(count(image, Color.RED) / 10);
    }

    @Test
    void code_isDrawnLast_overTheText() {
        TicketTextField band = custom("HHHHHHHHHHHH", 0, 20, 25, "#000000", TextAlign.LEFT);
        CodePlacement placement = new CodePlacement(CodeType.QR, 60, 10, 30, 0);
        BufferedImage withoutCode = render(fields(design(600, 300, null, null, null, null, List.of(band), false), null));
        BufferedImage withCode = render(fields(design(600, 300, null, null, null, null, List.of(band), true), placement));

        // the code is 180 px square at (360, 30); its quiet zone is a 7 px white frame around it
        int pad = (int) Math.round(180 * 0.04);
        int inkOutsideTheCodeButInsideItsQuietZone = 0;
        int inkWithoutCodeThere = 0;
        for (int y = 30 - pad; y < 30 + 180 + pad; y++) {
            for (int x = 360 - pad; x < 360 + 180 + pad; x++) {
                boolean insideCode = x >= 360 && x < 540 && y >= 30 && y < 210;
                if (insideCode) {
                    continue;
                }
                if (isInk(withCode.getRGB(x, y), Color.WHITE.getRGB())) {
                    inkOutsideTheCodeButInsideItsQuietZone++;
                }
                if (isInk(withoutCode.getRGB(x, y), Color.WHITE.getRGB())) {
                    inkWithoutCodeThere++;
                }
            }
        }
        assertThat(inkWithoutCodeThere).as("the text really runs under the code's frame").isGreaterThan(20);
        assertThat(inkOutsideTheCodeButInsideItsQuietZone).as("the code's white frame covers the text").isZero();
    }

    // ---- the code ----

    @Test
    void code_withNoPlacement_isTheDefaultQrBottomRight_andScans() throws Exception {
        BufferedImage image = render(fields(design(900, 380, null, null, null, null, List.of(), true), null));

        // 260 px square, 24 px from the bottom-right corner
        BufferedImage region = image.getSubimage(900 - 24 - 260 - 14, 380 - 24 - 260 - 14, 260 + 28, 260 + 28 - 0);
        assertThat(decode(region)).isEqualTo(CREDENTIAL);
    }

    @Test
    void code_noneDrawsNoCodeAtAll() {
        BufferedImage image = render(fields(design(600, 300, "#FFFF00", null, null, null, List.of(), false), null));

        assertThat(inkBounds(image, Color.YELLOW.getRGB())).isNull();
        assertThatThrownBy(() -> decode(image)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void code_qrPlacement_isHonouredInADesignedTicket() throws Exception {
        // 700 x 300 ticket, QR 30% of the width = 210 px at (50%, 10%) = (350, 30)
        BufferedImage image = render(fields(design(700, 300, "#EEEEEE", null, null, null, List.of(), true),
                new CodePlacement(CodeType.QR, 50, 10, 30, 0)));

        assertThat(decode(image.getSubimage(336, 16, 238, 238))).isEqualTo(CREDENTIAL);
    }

    @Test
    void code_barcodePlacement_scansInADesignedTicket() throws Exception {
        // 40% of 800 = 320 x 107, at (5%, 40%) = (40, 120)
        BufferedImage image = render(fields(design(800, 300, null, null, null, null, List.of(), true),
                new CodePlacement(CodeType.BARCODE, 5, 40, 40, 0)));

        assertThat(decode(image.getSubimage(26, 106, 348, 135))).isEqualTo(CREDENTIAL);
    }

    @Test
    void code_atTwiceTheScale_stillScans() throws Exception {
        TicketArtifactFields f = fields(design(700, 300, null, null, null, null, List.of(), true),
                new CodePlacement(CodeType.QR, 50, 10, 30, 0));

        BufferedImage image = renderer.render(f, 2.0);

        assertThat(decode(image.getSubimage(2 * 336, 2 * 16, 2 * 238, 2 * 238))).isEqualTo(CREDENTIAL);
    }

    @Test
    void code_backgroundImageDoesNotBreakScanning() throws Exception {
        BufferedImage image = render(fields(design(700, 300, "#202020", halves(), BackgroundFit.COVER, null, List.of(), true),
                new CodePlacement(CodeType.QR, 50, 10, 30, 0)));

        assertThat(decode(image.getSubimage(336, 16, 238, 238))).isEqualTo(CREDENTIAL);
    }

    // ---- PNG / PDF through the real renderers ----

    @Test
    void png_ofADesignedTicket_isTheDesignedSize() throws Exception {
        TicketArtifactFields f = fields(design(500, 250, "#00FF00", null, null, null, List.of(), false), null);

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(new PngTicketRenderer().render(f)));

        assertThat(png.getWidth()).isEqualTo(500);
        assertThat(png.getHeight()).isEqualTo(250);
        assertPixel(png, 10, 10, GREEN);
    }

    @Test
    void png_withoutDesignerContent_isStillTheBuiltInLayout() throws Exception {
        TicketArtifactFields f = new TicketArtifactFields("Concert Night", "VIP", "General Admission", "ABC-123456",
                qrCodeGenerator.generate(CREDENTIAL), "#112233", null);

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(new PngTicketRenderer().render(f)));

        assertThat(png.getWidth()).isEqualTo(900);
        assertThat(png.getHeight()).isEqualTo(380);
        assertPixel(png, 10, 10, Color.decode("#112233"));
    }

    @Test
    void pdf_ofADesignedTicket_isAPageOfTheTicketsSize_andMatchesThePng() throws Exception {
        TicketTextField text = custom("HHHHHH", 10, 50, 12, "#000000", TextAlign.LEFT);
        TicketArtifactFields f = fields(design(600, 240, "#FFEEAA", null, null, null, List.of(text), true),
                new CodePlacement(CodeType.QR, 60, 10, 30, 0));

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(new PngTicketRenderer().render(f)));
        BufferedImage page;
        try (PDDocument document = Loader.loadPDF(new PdfTicketRenderer().render(f))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(600f);
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(240f);
            page = new PDFRenderer(document).renderImageWithDPI(0, 72f);
        }

        assertThat(page.getWidth()).isEqualTo(600);
        assertThat(page.getHeight()).isEqualTo(240);
        int background = Color.decode("#FFEEAA").getRGB();
        Rectangle pngInk = inkBounds(png.getSubimage(0, 0, 340, 240), background);
        Rectangle pdfInk = inkBounds(page.getSubimage(0, 0, 340, 240), background);
        assertThat(pdfInk.x).isCloseTo(pngInk.x, within(3));
        assertThat(pdfInk.y).isCloseTo(pngInk.y, within(3));
        assertThat(pdfInk.width).isCloseTo(pngInk.width, within(4));
        assertThat(pdfInk.height).isCloseTo(pngInk.height, within(4));
    }

    @Test
    void pdf_ofADesignedTicket_keepsTheCodeScannable() throws Exception {
        TicketArtifactFields f = fields(design(700, 300, null, null, null, null, List.of(), true),
                new CodePlacement(CodeType.QR, 50, 10, 30, 0));

        BufferedImage page;
        try (PDDocument document = Loader.loadPDF(new PdfTicketRenderer().render(f))) {
            page = new PDFRenderer(document).renderImageWithDPI(0, 144f);
        }

        assertThat(decode(page.getSubimage(2 * 336, 2 * 16, 2 * 238, 2 * 238))).isEqualTo(CREDENTIAL);
    }
}
