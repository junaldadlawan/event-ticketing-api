package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.common.dto.PresentOrNull;
import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Optional;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.event.entity.Event}
 * (partial update). Matches openapi.yaml's {@code EventUpdate} schema
 * exactly: only title/description/category/images and the gate time are mutable after
 * creation — venue, dates, timezone, and ticketPrefix are create-time-only
 * / immutable. {@code null} on any field means "leave unchanged"; a
 * present-but-blank {@code title} is rejected (mirrors
 * {@code VenueUpdateRequest}'s blank-name handling).
 */
public record EventUpdateRequest(
        @Size(max = 200) @NoHtml String title,
        @Size(max = 10_000) @NoHtml String description,
        @Size(max = 100) @NoHtml String category,
        @Valid List<@NotBlank @URL String> images,
        /** BR-TICKET-GATE-001 gate time in minutes. Left out (null) = unchanged; JSON null (empty) = clear it. */
        @JsonDeserialize(using = PresentOrNull.OfInteger.class)
        Optional<@Min(0) @Max(1440) Integer> gateOpensMinutesBefore
) {

    /** An update without a gate time change. */
    public EventUpdateRequest(String title, String description, String category, List<String> images) {
        this(title, description, category, images, null);
    }
}
