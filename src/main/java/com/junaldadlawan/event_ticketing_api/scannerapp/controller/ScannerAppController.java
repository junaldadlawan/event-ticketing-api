package com.junaldadlawan.event_ticketing_api.scannerapp.controller;

import com.junaldadlawan.event_ticketing_api.scannerapp.dto.ScannerAppResponse;
import com.junaldadlawan.event_ticketing_api.scannerapp.service.ScannerAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The scanner app (Android APK). Publishing ({@code /scanner-app/uploads}, kept also at its old {@code /admin/scanner-app} address) is for admins and
 * members holding the SCANNER_APP permission; {@code latest} and {@code download} for admins, check-in staff and those holders. All checked in the service.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScannerAppController {

    /** The media type Android uses for an APK. */
    public static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");

    private final ScannerAppService scannerAppService;

    @PostMapping(value = {"/scanner-app/uploads", "/admin/scanner-app"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScannerAppResponse upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam("versionName") String versionName,
                                     @RequestParam("versionCode") Integer versionCode,
                                     @RequestParam(value = "releaseNotes", required = false) String releaseNotes) {
        return scannerAppService.upload(file, versionName, versionCode, releaseNotes);
    }

    @GetMapping({"/scanner-app/uploads", "/admin/scanner-app"})
    public List<ScannerAppResponse> list() {
        return scannerAppService.list();
    }

    @DeleteMapping({"/scanner-app/uploads/{id}", "/admin/scanner-app/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        scannerAppService.delete(id);
    }

    @GetMapping("/scanner-app/latest")
    public ScannerAppResponse latest() {
        return scannerAppService.latest();
    }

    @GetMapping("/scanner-app/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        ScannerAppService.Download download = scannerAppService.download(id);
        return ResponseEntity.ok()
                .contentType(APK)
                .contentLength(download.release().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("event-scanner-" + download.release().getVersionName() + ".apk").build().toString())
                .header("X-Content-SHA256", download.release().getSha256())
                .body(download.file());
    }
}
