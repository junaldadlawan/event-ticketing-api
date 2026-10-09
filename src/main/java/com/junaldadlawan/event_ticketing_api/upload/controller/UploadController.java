package com.junaldadlawan.event_ticketing_api.upload.controller;

import com.junaldadlawan.event_ticketing_api.upload.dto.UploadedImageResponse;
import com.junaldadlawan.event_ticketing_api.upload.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

/**
 * Image upload for the ticket template designer: {@code POST /api/v1/uploads}
 * (any signed-in user) stores one PNG/JPEG/WebP/GIF of up to 5 MB and returns
 * its absolute URL; {@code GET /api/v1/uploads/files/{name}} serves it publicly
 * (the image ends up on tickets, which buyers and scanners render without any
 * special access).
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final String FILES_PATH = "/api/v1/uploads/files/";

    private final ImageStorageService imageStorageService;

    /** Optional fixed public origin (e.g. https://api.example.com) when the app sits behind a proxy or load balancer. */
    @Value("${app.upload.public-base-url:}")
    private String publicBaseUrl;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImageResponse upload(@RequestPart("file") MultipartFile file) {
        ImageStorageService.StoredImage stored = imageStorageService.store(file);
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : publicBaseUrl.replaceAll("/+$", "");
        return new UploadedImageResponse(base + FILES_PATH + stored.name(), stored.contentType(), stored.size());
    }

    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> serve(@PathVariable String name) {
        ImageStorageService.LoadedImage image = imageStorageService.load(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                // Names are random and never reused, so the bytes behind one never change.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(image.resource());
    }
}
