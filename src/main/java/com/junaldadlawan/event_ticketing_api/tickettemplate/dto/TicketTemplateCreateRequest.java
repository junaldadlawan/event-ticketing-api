package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate}
 * creation. Matches openapi.yaml's {@code TicketTemplateCreate} schema:
 * {@code format} required, {@code ticketTypeId}/branding fields optional. A
 * null {@code ticketTypeId} means the template applies to the whole event.
 */
public record TicketTemplateCreateRequest(
        UUID ticketTypeId,

        @NotNull
        TicketTemplateFormat format,

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
