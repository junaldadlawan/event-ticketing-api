package com.junaldadlawan.event_ticketing_api.tickettemplate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.junaldadlawan.event_ticketing_api.common.validation.NoHtml;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/**
 * One text field of a ticket template, in a create/update request and in the response.
 * <p>
 * {@code x}/{@code y}: % of the ticket's width/height (x is the anchor edge chosen by
 * {@code align}, y the vertical centre of the whole text block); {@code fontSize}: % of the
 * ticket's height. Which of the optional members apply depends on the key (the rules that
 * need the key - e.g. {@code text} only for CUSTOM - are checked in
 * {@code TicketTemplateServiceImpl}): {@code sampleLength}/{@code sampleText} for dynamic
 * keys, {@code text} for CUSTOM, {@code lineBreaks} for static keys and CUSTOM.
 * <p>
 * Members that do not apply to a field are left out of the response JSON instead of being sent
 * as null - on this DTO only, on purpose: other responses rely on explicit nulls (e.g.
 * {@code ticketTypeId: null} = "all ticket types"). {@code rotation} (0 when never rotated) and
 * {@code bold} are always present. Requests are unaffected: a missing member and a null one both
 * mean "not set".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketTextFieldDto(
        @NotNull
        TextFieldKey key,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double x,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double y,

        @NotNull
        @DecimalMin("2.0")
        @DecimalMax("25.0")
        Double fontSize,

        @NotNull
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a #RRGGBB colour")
        String color,

        /** Null = false. */
        Boolean bold,

        @NotNull
        TextAlign align,

        /** Degrees clockwise, null = 0. */
        @Min(0)
        @Max(359)
        Integer rotation,

        @Min(1)
        @Max(30)
        Integer sampleLength,

        @Size(min = 1, max = 30)
        @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line")
        @NoHtml
        String sampleText,

        @Size(min = 1, max = 60)
        @Pattern(regexp = "[^\\r\\n]*", message = "must be a single line")
        @NoHtml
        String text,

        @Size(max = 10)
        List<@Min(1) Integer> lineBreaks) implements Serializable {

    public static TicketTextFieldDto from(TicketTextField field) {
        return new TicketTextFieldDto(
                field.getKey(),
                field.getX(),
                field.getY(),
                field.getFontSize(),
                field.getColor(),
                field.isBold(),
                field.getAlign(),
                field.getRotation(), // primitive: 0 when the field was never rotated
                field.getSampleLength(),
                field.getSampleText(),
                field.getText(),
                field.getLineBreaks() == null || field.getLineBreaks().isEmpty() ? null : List.copyOf(field.getLineBreaks()));
    }
}
