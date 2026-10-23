package com.junaldadlawan.event_ticketing_api.scannerapp.dto;

import com.junaldadlawan.event_ticketing_api.scannerapp.entity.ScannerAppRelease;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded scanner app version. {@code downloadUrl} is a path on this API ({@code /api/v1/scanner-app/{id}/download}): the app prepends its
 * API base URL and sends its usual Authorization header. {@code sha256} lets the device verify the file.
 */
public record ScannerAppResponse(
        UUID id,
        String versionName,
        int versionCode,
        long size,
        String sha256,
        Instant uploadedAt,
        String uploadedBy,
        String releaseNotes,
        String downloadUrl) implements Serializable {

    public static ScannerAppResponse from(ScannerAppRelease release) {
        return new ScannerAppResponse(release.getId(), release.getVersionName(), release.getVersionCode(), release.getSizeBytes(),
                release.getSha256(), release.getCreatedAt(), release.getCreatedBy(), release.getReleaseNotes(),
                "/api/v1/scanner-app/" + release.getId() + "/download");
    }
}
