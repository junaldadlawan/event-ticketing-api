package com.junaldadlawan.event_ticketing_api.ticket.artifact;

/**
 * The bytes + MIME type of a freshly rendered ticket artifact (Phase 6b
 * confirmed decision #1 — never persisted, always regenerated on demand).
 */
public record RenderedTicketArtifact(byte[] content, String contentType, String filename) {
}
