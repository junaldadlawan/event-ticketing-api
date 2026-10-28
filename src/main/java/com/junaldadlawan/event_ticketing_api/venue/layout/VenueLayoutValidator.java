package com.junaldadlawan.event_ticketing_api.venue.layout;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.Row;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.Seat;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.Section;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.SeatType;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.Shape;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.ShapeType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checks a layout an editor saves and returns it cleaned (names trimmed, colours upper-case, omitted optional numbers and seat types filled in).
 * Every rejection is a 400 whose message says what is wrong and where, like {@code sections[2].rows[0].seats[5]: x must be ...}.
 */
@Component
public class VenueLayoutValidator {

    public static final int MAX_SECTIONS = 50;
    public static final int MAX_SEATS = 5000;
    public static final int MAX_SHAPES = 200;
    public static final double MAX_PLAN_SIZE = 100_000;
    static final int MAX_SECTION_NAME = 100;   // seats.section is VARCHAR(100)
    static final int MAX_LABEL = 20;           // seats.row and seats.seat_number are VARCHAR(20)
    static final int MAX_TEXT = 200;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final double EDGE_TOLERANCE = 0.01;   // the rounding of a seat turned with its section

    public VenueLayout validate(VenueLayout layout) {
        if (layout == null) {
            throw bad("The layout is required");
        }
        double width = planSize(layout.width(), "width");
        double height = planSize(layout.height(), "height");
        if (layout.sections() == null) {
            throw bad("sections is required (it may be empty)");
        }
        if (layout.sections().size() > MAX_SECTIONS) {
            throw bad("A layout can have at most " + MAX_SECTIONS + " sections (got " + layout.sections().size() + ")");
        }
        long seatCount = 0;
        for (Section section : layout.sections()) {
            if (section != null && section.rows() != null) {
                for (Row row : section.rows()) {
                    seatCount += row == null || row.seats() == null ? 0 : row.seats().size();
                }
            }
        }
        if (seatCount > MAX_SEATS) {
            throw bad("A layout can have at most " + MAX_SEATS + " seats (got " + seatCount + ")");
        }

        List<Section> sections = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < layout.sections().size(); i++) {
            sections.add(section(layout.sections().get(i), "sections[" + i + "]", width, height, ids, names));
        }

        List<Shape> inputShapes = layout.shapes() == null ? List.of() : layout.shapes();
        if (inputShapes.size() > MAX_SHAPES) {
            throw bad("A layout can have at most " + MAX_SHAPES + " shapes (got " + inputShapes.size() + ")");
        }
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < inputShapes.size(); i++) {
            shapes.add(shape(inputShapes.get(i), "shapes[" + i + "]", width, height));
        }
        return new VenueLayout(width, height, sections, shapes);
    }

    private Section section(Section section, String at, double width, double height, Set<UUID> ids, Set<String> names) {
        if (section == null) {
            throw bad(at + " must not be null");
        }
        if (section.id() == null) {
            throw bad(at + ": id is required");
        }
        if (!ids.add(section.id())) {
            throw bad(at + ": the section id " + section.id() + " is used twice");
        }
        String name = text(section.name(), at + ": name", MAX_SECTION_NAME, true);
        if (!names.add(name.toLowerCase(Locale.ROOT))) {
            throw bad(at + ": the section name \"" + name + "\" is used twice (section names must be unique)");
        }
        if (section.kind() == null) {
            throw bad(at + ": kind is required (VIP or STANDARD)");
        }
        if (section.color() == null || !COLOR.matcher(section.color()).matches()) {
            throw bad(at + ": color must be a #RRGGBB colour");
        }
        double x = coordinate(section.x(), at + ": x", 0, width, "the plan width");
        double y = coordinate(section.y(), at + ": y", 0, height, "the plan height");
        double rotation = rotation(section.rotation(), at + ": rotation");
        if (section.rows() == null) {
            throw bad(at + ": rows is required (it may be empty)");
        }
        double radians = Math.toRadians(rotation);
        List<Row> rows = new ArrayList<>();
        Set<String> seatKeys = new HashSet<>();
        for (int r = 0; r < section.rows().size(); r++) {
            Row row = section.rows().get(r);
            String rowAt = at + ".rows[" + r + "]";
            if (row == null) {
                throw bad(rowAt + " must not be null");
            }
            String label = text(row.label(), rowAt + ": label", MAX_LABEL, true);
            double offset = row.offset() == null ? 0 : finite(row.offset(), rowAt + ": offset");
            if (row.seats() == null) {
                throw bad(rowAt + ": seats is required (it may be empty)");
            }
            List<Seat> seats = new ArrayList<>();
            for (int s = 0; s < row.seats().size(); s++) {
                Seat seat = row.seats().get(s);
                String seatAt = rowAt + ".seats[" + s + "]";
                if (seat == null) {
                    throw bad(seatAt + " must not be null");
                }
                String number = text(seat.number(), seatAt + ": number", MAX_LABEL, true);
                if (!seatKeys.add(label.toLowerCase(Locale.ROOT) + "\u0000" + number.toLowerCase(Locale.ROOT))) {
                    throw bad(seatAt + ": row \"" + label + "\" seat \"" + number + "\" is used twice in the section \"" + name + "\"");
                }
                double seatX = finite(seat.x(), seatAt + ": x");
                double seatY = finite(seat.y(), seatAt + ": y");
                // where the seat ends up on the plan: its row's offset, then the section's turn about its own corner, then the section's position
                double localX = offset + seatX;
                double absX = x + localX * Math.cos(radians) - seatY * Math.sin(radians);
                double absY = y + localX * Math.sin(radians) + seatY * Math.cos(radians);
                if (absX < -EDGE_TOLERANCE || absX > width + EDGE_TOLERANCE) {
                    throw bad(seatAt + ": x puts the seat outside the plan (it must stay between 0 and the plan width " + width + ")");
                }
                if (absY < -EDGE_TOLERANCE || absY > height + EDGE_TOLERANCE) {
                    throw bad(seatAt + ": y puts the seat outside the plan (it must stay between 0 and the plan height " + height + ")");
                }
                seats.add(new Seat(number, seatX, seatY, seat.type() == null ? SeatType.SEAT : seat.type()));
            }
            rows.add(new Row(label, offset, seats));
        }
        return new Section(section.id(), name, section.kind(), section.color().toUpperCase(Locale.ROOT), x, y, rotation, rows);
    }

    private Shape shape(Shape shape, String at, double width, double height) {
        if (shape == null) {
            throw bad(at + " must not be null");
        }
        if (shape.type() == null) {
            throw bad(at + ": type is required (STAGE, LABEL or WALL)");
        }
        String text = shape.text() == null || shape.text().isBlank() ? null : text(shape.text(), at + ": text", MAX_TEXT, false);
        if (shape.type() == ShapeType.LABEL && text == null) {
            throw bad(at + ": text is required for a LABEL");
        }
        double x = coordinate(shape.x(), at + ": x", 0, width, "the plan width");
        double y = coordinate(shape.y(), at + ": y", 0, height, "the plan height");
        if (shape.type() != ShapeType.LABEL && (shape.width() == null || shape.height() == null)) {
            throw bad(at + ": width and height are required for a " + shape.type());
        }
        double w = shape.width() == null ? 0 : finite(shape.width(), at + ": width");
        double h = shape.height() == null ? 0 : finite(shape.height(), at + ": height");
        if (w < 0 || h < 0) {
            throw bad(at + ": width and height must not be negative");
        }
        if (x + w > width + EDGE_TOLERANCE || y + h > height + EDGE_TOLERANCE) {
            throw bad(at + ": the shape reaches outside the plan (x + width must stay within " + width + " and y + height within " + height + ")");
        }
        return new Shape(shape.type(), text, x, y, w, h, rotation(shape.rotation(), at + ": rotation"));
    }

    private double planSize(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value < 1 || value > MAX_PLAN_SIZE) {
            throw bad(field + " is required and must be between 1 and " + (long) MAX_PLAN_SIZE);
        }
        return value;
    }

    private double coordinate(Double value, String field, double min, double max, String limit) {
        double v = finite(value, field);
        if (v < min || v > max) {
            throw bad(field + " must be between " + min + " and " + limit + " (" + max + ")");
        }
        return v;
    }

    private double rotation(Double value, String field) {
        if (value == null) {
            return 0;
        }
        double v = finite(value, field);
        if (v < -360 || v > 360) {
            throw bad(field + " must be between -360 and 360 degrees");
        }
        return v;
    }

    private double finite(Double value, String field) {
        if (value == null || !Double.isFinite(value)) {
            throw bad(field + " is required and must be a number");
        }
        return value;
    }

    private String text(String value, String field, int max, boolean required) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw bad(field + " must not be blank");
            }
            return trimmed;
        }
        if (trimmed.length() > max) {
            throw bad(field + " must be at most " + max + " characters");
        }
        if (trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) {
            throw bad(field + " must not contain HTML");
        }
        return trimmed;
    }

    private static BadRequestException bad(String message) {
        return new BadRequestException(message);
    }
}
