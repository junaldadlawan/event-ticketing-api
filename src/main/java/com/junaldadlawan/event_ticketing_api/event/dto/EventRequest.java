package com.junaldadlawan.event_ticketing_api.event.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.common.validation.ValidEndTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.event.entity.Event}
 * (create). Deliberate deviation from openapi.yaml's {@code EventCreate}
 * schema: the settled contract has no {@code organization_id} field, but a
 * caller can hold owner/organizer roles in more than one organization
 * (Phase 1), so the API can't infer which organization the event belongs
 * to. {@code organizationId} is added here as a required field so the
 * service can validate the caller against that *specific* organization
 * (confirmed decision, not a re-litigated design choice).
 * <p>
 * {@code ticketPrefix} is intentionally absent — it is never
 * client-supplied, the server generates and reserves it at creation time
 * (BR-TICKET-003/004).
 */
@ValidEndTime
public record EventRequest(
        @NotNull
        UUID organizationId,

        @NotBlank
        @Size(max = 200)
        @NoHtml
        String title,

        @NotBlank
        @Size(max = 2_000)
        @NoHtml
        String description,

        @NotBlank
        @Size(max = 100)
        @NoHtml String category,

        UUID venueId,

        @NotNull
        @Future
        Instant startAt,

        @NotNull
        Instant endAt,

        @NotBlank
        String timezone,

        @Valid
        List<@NotBlank @URL String> images)
        implements Serializable {
}
