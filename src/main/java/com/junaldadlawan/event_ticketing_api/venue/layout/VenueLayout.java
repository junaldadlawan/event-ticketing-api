package com.junaldadlawan.event_ticketing_api.venue.layout;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * A venue's seat layout: the whole design the editor saves (and the shape stored as JSON). Coordinates are in the plan's own units, {@code (0, 0)} at
 * the top-left, {@code x} to the right and {@code y} down. A section sits at {@code (x, y)} turned by {@code rotation} degrees; a seat's
 * {@code (x, y)} are inside the section, and its row's {@code offset} moves the whole row along the section's x axis. The numbers are wrappers so a
 * missing one is told apart from zero (the validator answers 400).
 */
public record VenueLayout(Double width, Double height, List<Section> sections, List<Shape> shapes) implements Serializable {

    /** Every seat of every section, blocked ones included. */
    public int seatCount() {
        return sections.stream().flatMap(section -> section.rows().stream()).mapToInt(row -> row.seats().size()).sum();
    }

    public enum SectionKind { VIP, STANDARD }

    /** SEAT and WHEELCHAIR are for sale; BLOCKED is not (it is never copied into an event). */
    public enum SeatType { SEAT, WHEELCHAIR, BLOCKED }

    public enum ShapeType { STAGE, LABEL, WALL }

    public record Section(UUID id, String name, SectionKind kind, String color, Double x, Double y, Double rotation, List<Row> rows) implements Serializable {
    }

    public record Row(String label, Double offset, List<Seat> seats) implements Serializable {
    }

    public record Seat(String number, Double x, Double y, SeatType type) implements Serializable {
    }

    public record Shape(ShapeType type, String text, Double x, Double y, Double width, Double height, Double rotation) implements Serializable {
    }
}
