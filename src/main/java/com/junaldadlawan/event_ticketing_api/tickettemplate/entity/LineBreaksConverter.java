package com.junaldadlawan.event_ticketing_api.tickettemplate.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a text field's line-break positions as one short column ({@code "3,7"}).
 * A nested collection inside an embeddable that is itself an element collection
 * is unreliable in Hibernate, and ten small integers don't need a table.
 */
@Converter
public class LineBreaksConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (Integer position : attribute) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(position);
        }
        return joined.toString();
    }

    @Override
    public List<Integer> convertToEntityAttribute(String column) {
        List<Integer> positions = new ArrayList<>();
        if (column == null || column.isBlank()) {
            return positions;
        }
        for (String part : column.split(",")) {
            positions.add(Integer.valueOf(part.trim()));
        }
        return positions;
    }
}
