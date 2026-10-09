package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;
import java.util.List;

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
        String primaryColor,

        // Where the scannable code sits (see TicketTemplate#codeType). Type, x, y and width go
        // together; the type-specific minimum width is checked in the service.
        CodeType codeType,

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double codeX,

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double codeY,

        @DecimalMin("1.0")
        @DecimalMax("100.0")
        Double codeWidth,

        @Min(0)
        @Max(359)
        Integer codeRotation,

        // The ticket canvas: its own size in px (not tied to the background image). Null = 900 x 380.
        @Min(100)
        @Max(5000)
        Integer ticketWidth,

        @Min(100)
        @Max(5000)
        Integer ticketHeight,

        /** "#RRGGBB" fill under everything; "" clears it (white). */
        @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "must be a #RRGGBB colour")
        String backgroundColor,

        /** Null = COVER. CUSTOM needs the four rectangle values below; any other fit must not have them. */
        BackgroundFit backgroundFit,

        /** CUSTOM fit: the image's rectangle in % of the ticket (x/y may be negative, width/height above 100). */
        @DecimalMin("-500.0")
        @DecimalMax("500.0")
        Double backgroundX,

        @DecimalMin("-500.0")
        @DecimalMax("500.0")
        Double backgroundY,

        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("500.0")
        Double backgroundWidth,

        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("500.0")
        Double backgroundHeight,

        /** In drawing order (later on top), at most 30. Update: null = unchanged, [] = none. */
        @Size(max = 30)
        List<@Valid TicketTextFieldDto> textFields) implements Serializable {
}
