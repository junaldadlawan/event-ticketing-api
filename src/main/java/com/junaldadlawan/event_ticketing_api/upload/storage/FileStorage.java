package com.junaldadlawan.event_ticketing_api.upload.storage;

import org.springframework.core.io.Resource;

/**
 * Where uploaded bytes live, by object key (e.g. {@code users/<uuid>.png}). {@link LocalFileStorage} on local
 * disk by default, {@link R2FileStorage} (Cloudflare R2) when {@code app.storage.type=r2}. Keys are built and
 * checked by {@code ImageStorageService}; implementations never see user input directly.
 */
public interface FileStorage {

    void put(String key, byte[] bytes, String contentType);

    /** The stored bytes, or null when there is no such object. */
    Resource get(String key);

    boolean exists(String key);

    /** Removes the object; a missing one is not an error. */
    void delete(String key);
}
