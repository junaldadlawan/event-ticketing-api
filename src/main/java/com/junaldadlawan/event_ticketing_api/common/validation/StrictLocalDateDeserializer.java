package com.junaldadlawan.event_ticketing_api.common.validation;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Reads a date only from an ISO {@code yyyy-MM-dd} string. The stock reader also turns a bare number into a day count
 * since 1970 (so {@code 12345} became a birth date) and accepts a {@code [year, month, day]} array; neither is wanted.
 */
public class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        String text = parser.getString();
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return (LocalDate) context.handleWeirdStringValue(LocalDate.class, text, "must be a date in the format yyyy-MM-dd");
        }
    }
}
