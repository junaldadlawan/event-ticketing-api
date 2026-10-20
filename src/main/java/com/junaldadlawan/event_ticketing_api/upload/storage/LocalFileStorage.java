package com.junaldadlawan.event_ticketing_api.upload.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Files under {@code app.upload.dir}, laid out like the R2 bucket ({@code <dir>/users/<uuid>.png}, ...).
 * The default backend: fine for local development and a single instance, but an ECS container's disk is
 * wiped on every deploy, so production uses {@link R2FileStorage}.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path directory;

    public LocalFileStorage(@Value("${app.upload.dir:uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        if (target == null) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            // Write to a temp name first so a half-written file is never served.
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store the uploaded file", e);
        }
    }

    @Override
    public Resource get(String key) {
        Path file = resolve(key);
        return file != null && Files.isRegularFile(file) ? new FileSystemResource(file) : null;
    }

    @Override
    public boolean exists(String key) {
        Path file = resolve(key);
        return file != null && Files.isRegularFile(file);
    }

    @Override
    public void delete(String key) {
        Path file = resolve(key);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete the stored file", e);
        }
    }

    /** The file for {@code key}, or null if it would land outside the upload folder. */
    private Path resolve(String key) {
        Path file = directory.resolve(key).normalize();
        return file.startsWith(directory) && !file.equals(directory) ? file : null;
    }
}
