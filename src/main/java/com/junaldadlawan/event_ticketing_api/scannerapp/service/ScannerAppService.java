package com.junaldadlawan.event_ticketing_api.scannerapp.service;

import com.junaldadlawan.event_ticketing_api.scannerapp.dto.ScannerAppResponse;
import com.junaldadlawan.event_ticketing_api.scannerapp.entity.ScannerAppRelease;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Distribution of the scanner app (Android APK): admins upload versions, check-in staff (and admins) fetch the latest. */
public interface ScannerAppService {

    /** Admin only. Stores the APK and records the version. 400 for anything that is not an APK or is too large, 409 for a version code already uploaded. */
    ScannerAppResponse upload(MultipartFile file, String versionName, Integer versionCode, String releaseNotes);

    /** Admin only. Every live upload, newest first. */
    List<ScannerAppResponse> list();

    /** Admin only. Soft-deletes the row and removes the stored file. 404 if there is no such upload. */
    void delete(UUID id);

    /** An admin, or a user who is CHECK_IN_STAFF in some organization (else 403). 404 when nothing was uploaded yet. */
    ScannerAppResponse latest();

    /** Same access as {@link #latest()}. The APK to stream, with its record. */
    Download download(UUID id);

    record Download(ScannerAppRelease release, Resource file) {
    }
}
