package com.junaldadlawan.event_ticketing_api.upload.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageServiceTest {

    @TempDir
    Path directory;

    private ImageStorageService service;

    @BeforeEach
    void setUp() {
        service = new ImageStorageService(directory.toString(), 5 * 1024 * 1024);
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private MockMultipartFile file(String originalName, String claimedType, byte[] bytes) {
        return new MockMultipartFile("file", originalName, claimedType, bytes);
    }

    // ---- accepted types ----

    @Test
    void store_png_isSavedUnderARandomNameWithTheRealType() throws Exception {
        byte[] bytes = image("png");

        ImageStorageService.StoredImage stored = service.store(file("my holiday photo.png", "image/png", bytes));

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.size()).isEqualTo(bytes.length);
        assertThat(stored.name()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.name()).doesNotContain("holiday");
        assertThat(Files.readAllBytes(directory.resolve(stored.name()))).isEqualTo(bytes);
    }

    @Test
    void store_jpeg_andGif_areAccepted() throws Exception {
        assertThat(service.store(file("a.jpg", "image/jpeg", image("jpg"))).name()).endsWith(".jpg");
        assertThat(service.store(file("a.gif", "image/gif", image("gif"))).name()).endsWith(".gif");
    }

    @Test
    void store_webp_isAcceptedByItsHeader() {
        byte[] webp = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.ISO_8859_1), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.ISO_8859_1), 0, webp, 8, 4);

        ImageStorageService.StoredImage stored = service.store(file("a.webp", "image/webp", webp));

        assertThat(stored.contentType()).isEqualTo("image/webp");
        assertThat(stored.name()).endsWith(".webp");
    }

    @Test
    void store_twice_neverReusesAName() throws Exception {
        byte[] bytes = image("png");

        assertThat(service.store(file("a.png", "image/png", bytes)).name())
                .isNotEqualTo(service.store(file("a.png", "image/png", bytes)).name());
    }

    // ---- what is NOT trusted / not accepted ----

    @Test
    void store_theFilesOwnBytesDecideTheType_notTheNameOrContentType() throws Exception {
        // a PNG uploaded claiming to be a .jpg / text/html is stored and served as the PNG it really is
        ImageStorageService.StoredImage stored = service.store(file("evil.html", "text/html", image("png")));

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.name()).endsWith(".png");
    }

    @Test
    void store_aTextFileCalledPng_isRejected() {
        assertThatThrownBy(() -> service.store(file("a.png", "image/png", "just some text".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_svg_isRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.store(file("a.svg", "image/svg+xml", svg))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_aTruncatedPng_isRejected() throws Exception {
        byte[] bytes = image("png");
        byte[] truncated = java.util.Arrays.copyOf(bytes, 20);

        assertThatThrownBy(() -> service.store(file("a.png", "image/png", truncated))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_emptyFile_isRejected() {
        assertThatThrownBy(() -> service.store(file("a.png", "image/png", new byte[0]))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.store(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_overTheSizeLimit_isRejected() throws Exception {
        ImageStorageService small = new ImageStorageService(directory.toString(), 10);

        assertThatThrownBy(() -> small.store(file("a.png", "image/png", image("png"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("smaller");
    }

    @Test
    void store_nothingIsLeftOnDiskWhenRejected() {
        try {
            service.store(file("a.png", "image/png", "nope".getBytes(StandardCharsets.UTF_8)));
        } catch (BadRequestException expected) {
            // fine
        }

        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void detectExtension_recognisesOnlyTheFourTypes() {
        assertThat(ImageStorageService.detectExtension(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("jpg");
        assertThat(ImageStorageService.detectExtension("GIF89a....".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo("gif");
        assertThat(ImageStorageService.detectExtension("%PDF-1.7 ....".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(ImageStorageService.detectExtension("MZ".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(ImageStorageService.detectExtension(new byte[0])).isNull();
    }

    // ---- load ----

    @Test
    void load_returnsWhatWasStored() throws Exception {
        byte[] bytes = image("png");
        ImageStorageService.StoredImage stored = service.store(file("a.png", "image/png", bytes));

        ImageStorageService.LoadedImage loaded = service.load(stored.name());

        assertThat(loaded.contentType()).isEqualTo("image/png");
        assertThat(loaded.resource().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void load_unknownName_isNotFound() {
        assertThatThrownBy(() -> service.load(UUID.randomUUID() + ".png")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void load_anythingThatIsNotOneOfOurNames_isNotFound_evenIfTheFileExists() throws Exception {
        Files.writeString(directory.resolve("secret.txt"), "secret");
        Path parent = directory.getParent();
        Files.writeString(parent.resolve("outside.png"), "x");

        for (String name : new String[] {"secret.txt", "../outside.png", "..%2Foutside.png", "a/b.png", "", "x.png",
                UUID.randomUUID() + ".svg", UUID.randomUUID() + ".png.part"}) {
            assertThatThrownBy(() -> service.load(name)).isInstanceOf(ResourceNotFoundException.class);
        }
        assertThatThrownBy(() -> service.load(null)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- isStoredName / exists / delete (used to clean up replaced profile pictures) ----

    @Test
    void isStoredName_acceptsOnlyAGeneratedShape() {
        assertThat(ImageStorageService.isStoredName(UUID.randomUUID() + ".png")).isTrue();
        assertThat(ImageStorageService.isStoredName(UUID.randomUUID() + ".webp")).isTrue();
        for (String name : new String[] {null, "", "x.png", "../a.png", UUID.randomUUID() + ".svg", UUID.randomUUID() + ".png.part"}) {
            assertThat(ImageStorageService.isStoredName(name)).isFalse();
        }
    }

    @Test
    void exists_isTrueOnlyForAStoredFile() throws Exception {
        String name = service.store(file("a.png", "image/png", image("png"))).name();

        assertThat(service.exists(name)).isTrue();
        assertThat(service.exists(UUID.randomUUID() + ".png")).isFalse();
        assertThat(service.exists("../" + name)).isFalse();
        assertThat(service.exists(null)).isFalse();
    }

    @Test
    void delete_removesTheFile_andIgnoresUnknownNamesAndMissingFiles() throws Exception {
        String name = service.store(file("a.png", "image/png", image("png"))).name();
        Files.writeString(directory.resolve("keep.txt"), "keep");

        service.delete(name);
        service.delete(name);
        service.delete("keep.txt");
        service.delete("../keep.txt");
        service.delete(null);

        assertThat(service.exists(name)).isFalse();
        assertThat(Files.exists(directory.resolve("keep.txt"))).isTrue();
    }
}
