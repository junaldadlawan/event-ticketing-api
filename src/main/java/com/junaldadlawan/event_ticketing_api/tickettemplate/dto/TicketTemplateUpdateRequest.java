package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate}
 * partial update (owning organization's owner/organizer, or admin). Matches
 * openapi.yaml's {@code TicketTemplateUpdate} schema, which only lists the
 * branding fields as updatable ({@code format}/{@code ticketTypeId} are
 * create-time-only). {@code null} on any field means "leave unchanged" —
 * mirrors {@code TicketTypeUpdateRequest}/{@code VenueUpdateRequest}.
 */
public record TicketTemplateUpdateRequest(
        @URL
        @Size(max = 2_048)
        String logoUrl,

        @URL
        @Size(max = 2_048)
        String backgroundImageUrl,

        @NoHtml
        @Size(max = 20)
        String primaryColor) implements Serializable {
}
