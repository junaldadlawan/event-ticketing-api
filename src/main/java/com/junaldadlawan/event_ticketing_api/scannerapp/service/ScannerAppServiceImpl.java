package com.junaldadlawan.event_ticketing_api.scannerapp.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.scannerapp.dto.ScannerAppResponse;
import com.junaldadlawan.event_ticketing_api.scannerapp.entity.ScannerAppRelease;
import com.junaldadlawan.event_ticketing_api.scannerapp.repository.ScannerAppReleaseRepository;
import com.junaldadlawan.event_ticketing_api.upload.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * What is trusted: nothing the client sends about the file. It is accepted only if it is a ZIP that holds an {@code AndroidManifest.xml} (an APK is
 * a ZIP; only the central directory is read, nothing is unpacked), the stored name is a random UUID, and the version fields are checked here.
 * The APK itself lives in {@link FileStorage} (R2 in production, so it survives a redeploy), the history in {@code scanner_app_releases}.
 */
@Service
public class ScannerAppServiceImpl implements ScannerAppService {

    static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";
    private static final Pattern VERSION_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,49}");
    private static final int MAX_NOTES = 2000;

    private final ScannerAppReleaseRepository repository;
    private final FileStorage storage;
    private final OrganizationAccessGuard accessGuard;
    private final long maxBytes;

    public ScannerAppServiceImpl(ScannerAppReleaseRepository repository, FileStorage storage, OrganizationAccessGuard accessGuard,
                                 @Value("${app.scanner-app.max-bytes:104857600}") long maxBytes) {
        this.repository = repository;
        this.storage = storage;
        this.accessGuard = accessGuard;
        this.maxBytes = maxBytes;
    }

    @Override
    @Transactional
    public ScannerAppResponse upload(MultipartFile file, String versionName, Integer versionCode, String releaseNotes) {
        requireMayPublish();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("An APK file is required");
        }
        String name = versionName == null ? "" : versionName.trim();
        if (!VERSION_NAME.matcher(name).matches()) {
            throw new BadRequestException("versionName must be 1 to 50 letters, digits or . _ + - (for example 1.4.2)");
        }
        if (versionCode == null || versionCode < 1) {
            throw new BadRequestException("versionCode must be a whole number of 1 or more");
        }
        String notes = releaseNotes == null || releaseNotes.isBlank() ? null : releaseNotes.trim();
        if (notes != null && (notes.length() > MAX_NOTES || notes.contains("<"))) {
            throw new BadRequestException("releaseNotes must be plain text of at most " + MAX_NOTES + " characters");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.valueOf(413), "The APK is too large (" + (maxBytes / (1024 * 1024)) + " MB maximum)");
        }
        if (repository.existsByVersionCodeAndDeletedAtIsNull(versionCode)) {
            throw new ConflictException("Version code " + versionCode + " was already uploaded");
        }

        byte[] bytes = readVerifiedApk(file);
        String key = "scanner-app/" + UUID.randomUUID() + ".apk";
        storage.put(key, bytes, APK_CONTENT_TYPE);
        try {
            ScannerAppRelease saved = repository.saveAndFlush(ScannerAppRelease.builder()
                    .versionName(name)
                    .versionCode(versionCode)
                    .releaseNotes(notes)
                    .storageKey(key)
                    .sizeBytes(bytes.length)
                    .sha256(sha256(bytes))
                    .build());
            BusinessAuditLogger.record("scanner_app.uploaded", "ScannerAppRelease", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS,
                    "version=" + name + " code=" + versionCode + " bytes=" + bytes.length);
            return ScannerAppResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            storage.delete(key);   // a concurrent upload of the same version code won the race
            throw new ConflictException("Version code " + versionCode + " was already uploaded");
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScannerAppResponse> list() {
        requireMayPublish();
        return repository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream().map(ScannerAppResponse::from).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        requireMayPublish();
        ScannerAppRelease release = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner app upload " + id + " not found"));
        // a holder of SCANNER_APP removes only their own uploads; an admin any
        if (!accessGuard.isAdmin() && !accessGuard.currentUserId().toString().equals(release.getCreatedBy())) {
            throw new ForbiddenException("Only an admin or the person who uploaded this version may remove it");
        }
        release.markDeleted();
        repository.save(release);
        storage.delete(release.getStorageKey());
        BusinessAuditLogger.record("scanner_app.deleted", "ScannerAppRelease", id, BusinessAuditLogger.Outcome.SUCCESS,
                "version=" + release.getVersionName() + " code=" + release.getVersionCode());
    }

    @Override
    @Transactional(readOnly = true)
    public ScannerAppResponse latest() {
        requireMayFetch();
        return repository.findFirstByDeletedAtIsNullOrderByVersionCodeDescCreatedAtDesc()
                .map(ScannerAppResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No scanner app has been uploaded yet"));
    }

    @Override
    @Transactional(readOnly = true)
    public Download download(UUID id) {
        requireMayFetch();
        ScannerAppRelease release = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scanner app upload " + id + " not found"));
        Resource file = storage.get(release.getStorageKey());
        if (file == null) {
            throw new ResourceNotFoundException("The file of scanner app upload " + id + " is no longer stored");
        }
        return new Download(release, file);
    }

    /** An admin, or a member who holds SCANNER_APP in some organization (the app is shared by every organization). */
    private void requireMayPublish() {
        if (accessGuard.isAdmin()) {
            return;
        }
        if (!accessGuard.holdsScannerAppPermissionAnywhere(accessGuard.currentUserId())) {
            throw new ForbiddenException("Only an admin or a member with the SCANNER_APP permission may publish the scanner app");
        }
    }

    /** An admin, someone who is CHECK_IN_STAFF in at least one organization, or a SCANNER_APP holder. */
    private void requireMayFetch() {
        if (accessGuard.isAdmin()) {
            return;
        }
        UUID caller = accessGuard.currentUserId();
        if (!accessGuard.isCheckInStaffAnywhere(caller) && !accessGuard.holdsScannerAppPermissionAnywhere(caller)) {
            throw new ForbiddenException("Only admins and check-in staff may get the scanner app");
        }
    }

    /** The upload's bytes, once it is shown to be a ZIP with an AndroidManifest.xml (an APK). Only the entry list is read, nothing is unpacked. */
    private byte[] readVerifiedApk(MultipartFile file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("scanner-app-", ".apk");
            file.transferTo(temp);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                if (zip.getEntry("AndroidManifest.xml") == null) {
                    throw new BadRequestException("The file is not an Android APK (no AndroidManifest.xml inside)");
                }
            } catch (java.util.zip.ZipException e) {
                throw new BadRequestException("The file is not an Android APK");
            }
            return Files.readAllBytes(temp);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded APK", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // a leftover temp file is harmless
                }
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
