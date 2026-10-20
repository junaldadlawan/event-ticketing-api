package com.junaldadlawan.event_ticketing_api.auditlog.service;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * BR-EVENT-006: builds the {@code details} of an audit entry - which fields an update changed ({@code changes}), or the
 * name of the item it is about. Field names are the update request's own components, i.e. the API's JSON names.
 * Values are kept small and safe: Money as {@code {amount, currency}}, dates as ISO strings, lists as their size, text
 * capped at {@value #MAX_TEXT} characters, and a field whose name looks secret is never recorded.
 */
public final class AuditDetails {

    static final int MAX_TEXT = 500;
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|secret|token|credential|cvc|cvv|card|account|security).*");

    private AuditDetails() {
    }

    /** The current values of {@code fieldsOf}'s components on {@code source} (an entity or a record), normalized now. */
    public static Map<String, Object> snapshot(Object source, Class<? extends Record> fieldsOf) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (RecordComponent component : fieldsOf.getRecordComponents()) {
            String field = component.getName();
            if (SECRET.matcher(field).matches()) {
                continue;
            }
            Method reader = reader(source.getClass(), field);
            if (reader != null) {
                values.put(field, normalize(read(reader, source)));
            }
        }
        return values;
    }

    /** {@code {"changes": [{field, from, to}, ...]}} for the fields that differ, or null when nothing changed. */
    public static Map<String, Object> changes(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            Object from = before.get(entry.getKey());
            if (!Objects.equals(from, entry.getValue())) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("field", entry.getKey());
                change.put("from", from);
                change.put("to", entry.getValue());
                changes.add(change);
            }
        }
        return changes.isEmpty() ? null : Map.of("changes", changes);
    }

    /** {@code {"name": name}} - so a deleted item can still be named. */
    public static Map<String, Object> named(String name) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", capped(name));
        return details;
    }

    /** {@code {"reason": reason}} (capped), or null when none was given. */
    public static Map<String, Object> reason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", capped(reason));
        return details;
    }

    static Object normalize(Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return capped(text);
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof BigDecimal decimal) {   // 10 and 10.00 are the same value, and stay a JSON number
            return new BigDecimal(decimal.stripTrailingZeros().toPlainString());
        }
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof UUID || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        Method amount = reader(value.getClass(), "amount");
        Method currency = reader(value.getClass(), "currency");
        if (amount != null && currency != null) {   // Money / MoneyDto
            Map<String, Object> money = new LinkedHashMap<>();
            money.put("amount", normalize(read(amount, value)));
            money.put("currency", normalize(read(currency, value)));
            return money;
        }
        return capped(value.toString());
    }

    private static String capped(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    /** {@code field()} (record) or {@code getField()} / {@code isField()} (bean); null when the type has none. */
    private static Method reader(Class<?> type, String field) {
        String capitalized = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (String name : List.of(field, "get" + capitalized, "is" + capitalized)) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next naming style
            }
        }
        return null;
    }

    private static Object read(Method reader, Object source) {
        try {
            return reader.invoke(source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + reader.getName() + " for the audit log", e);
        }
    }
}
