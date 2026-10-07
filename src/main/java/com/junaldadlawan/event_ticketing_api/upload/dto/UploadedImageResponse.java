package com.junaldadlawan.event_ticketing_api.upload.dto;

import java.io.Serializable;

/**
 * Result of an image upload: where the image is now served from (absolute URL,
 * ready to be used as e.g. a ticket template's {@code backgroundImageUrl}), its
 * detected content type and size in bytes.
 */
public record UploadedImageResponse(String url, String contentType, long size) implements Serializable {
}
