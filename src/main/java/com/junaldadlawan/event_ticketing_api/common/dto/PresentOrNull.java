package com.junaldadlawan.event_ticketing_api.common.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.Optional;

/**
 * For a PATCH field where JSON null means "clear it": a left-out field stays null (unchanged), {@code null} becomes
 * {@code Optional.empty()}, a value {@code Optional.of(value)}. Jackson's own Optional handling would turn a left-out
 * field into empty too. Use on an {@code Optional<T>} record component via {@code @JsonDeserialize(using = ...)}.
 */
public abstract class PresentOrNull<T> extends ValueDeserializer<Optional<T>> {

    private final Class<T> type;

    protected PresentOrNull(Class<T> type) {
        this.type = type;
    }

    @Override
    public Optional<T> deserialize(JsonParser p, DeserializationContext ctxt) {
        return Optional.of(ctxt.readValue(p, type));
    }

    @Override
    public Optional<T> getNullValue(DeserializationContext ctxt) {
        return Optional.empty();
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return null;
    }

    public static class OfString extends PresentOrNull<String> {
        public OfString() {
            super(String.class);
        }
    }

    public static class OfInteger extends PresentOrNull<Integer> {
        public OfInteger() {
            super(Integer.class);
        }
    }
}
