package com.junaldadlawan.event_ticketing_api.upload.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded images (ticket template backgrounds) on local disk and hands
 * them back by name.
 * <p>
 * What is trusted: nothing the client sends about the file. The type is decided
 * from the file's own first bytes (PNG, JPEG, GIF or WebP only - never SVG, which
 * can carry script), the browser-supplied filename and content type are ignored,
 * and the stored name is a random UUID plus the extension for the detected type,
 * so nothing a user types ever reaches the file system path. PNG/JPEG/GIF are
 * also decoded far enough to read their dimensions, which rejects truncated or
 * disguised files and oversized "decompression bomb" images.
 * <p>
 * Local disk is fine for one instance and for local development; on ECS Fargate
 * the container disk is lost on every deployment, so production needs a
 * durable store (S3) behind this same class.
 */
@Service
public class ImageStorageService {

    /** The only names {@link #load} will serve: our own random name + a known extension. */
    private static final Pattern STORED_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|gif|webp)");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    /** Longest side, and total pixels, an image may have. */
    private static final int MAX_SIDE_PX = 8_000;
    private static final long MAX_PIXELS = 40_000_000L;

    private final Path directory;
    private final long maxBytes;

    public ImageStorageService(@Value("${app.upload.dir:uploads}") String directory,
                               @Value("${app.upload.max-bytes:5242880}") long maxBytes) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("An image file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Image must be " + (maxBytes >= 1024 * 1024 ? (maxBytes / (1024 * 1024)) + " MB" : maxBytes + " bytes") + " or smaller");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded image", e);
        }
        String extension = detectExtension(bytes);
        if (extension == null) {
            throw new BadRequestException("Only PNG, JPEG, WebP or GIF images are accepted");
        }
        if (!extension.equals("webp")) {
            requireDecodableWithinLimits(bytes);
        }

        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(name);
            // Write to a temp name first so a half-written file is never served.
            Path temp = directory.resolve(name + ".part");
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store the uploaded image", e);
        }
        return new StoredImage(name, CONTENT_TYPE_BY_EXTENSION.get(extension), bytes.length);
    }

    public LoadedImage load(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            throw new ResourceNotFoundException("Image not found");
        }
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Image not found");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1);
        return new LoadedImage(new FileSystemResource(file), CONTENT_TYPE_BY_EXTENSION.get(extension));
    }

    /** Whether {@code name} has the shape of a name this service generates (random UUID + a known extension). */
    public static boolean isStoredName(String name) {
        return name != null && STORED_NAME.matcher(name).matches();
    }

    /** Whether a stored file with this name exists. */
    public boolean exists(String name) {
        if (!isStoredName(name)) {
            return false;
        }
        Path file = directory.resolve(name).normalize();
        return file.startsWith(directory) && Files.isRegularFile(file);
    }

    /** Removes a stored file; a name that is not ours or a file that is already gone is ignored. */
    public void delete(String name) {
        if (!isStoredName(name)) {
            return;
        }
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete the stored image", e);
        }
    }

    /** Magic-byte sniffing: the extension for the real type, or null if it is not a supported image. */
    static String detectExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 6) {
            String head = new String(b, 0, 6, StandardCharsets.ISO_8859_1);
            if (head.equals("GIF87a") || head.equals("GIF89a")) {
                return "gif";
            }
        }
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
            return "webp";
        }
        return null;
    }

    private void requireDecodableWithinLimits(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("The file is not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SIDE_PX || height > MAX_SIDE_PX
                        || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("Image dimensions are too large (max " + MAX_SIDE_PX + " px per side)");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("The file is not a valid image");
        }
    }

    public record StoredImage(String name, String contentType, long size) {
    }

    public record LoadedImage(Resource resource, String contentType) {
    }
}
