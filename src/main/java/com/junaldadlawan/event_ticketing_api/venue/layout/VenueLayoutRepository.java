package com.junaldadlawan.event_ticketing_api.venue.layout;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The saved layout of a venue (one row per venue, the whole design as JSON). */
@Repository
@RequiredArgsConstructor
public class VenueLayoutRepository {

    /** A saved layout and its version. */
    public record Stored(int version, VenueLayout layout) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Optional<Stored> find(UUID venueId) {
        List<Stored> rows = jdbc.query("select version, layout::text from venue_layouts where venue_id = ?",
                (rs, i) -> new Stored(rs.getInt(1), objectMapper.readValue(rs.getString(2), VenueLayout.class)), venueId);
        return rows.stream().findFirst();
    }

    /** Replaces the layout and adds one to the version in one atomic statement (the first save is version 1); returns the new version. */
    public int save(UUID venueId, VenueLayout layout, String updatedBy) {
        return jdbc.queryForObject("insert into venue_layouts (venue_id, version, layout, updated_at, updated_by) values (?, 1, ?::jsonb, now(), ?)"
                        + " on conflict (venue_id) do update set version = venue_layouts.version + 1, layout = excluded.layout,"
                        + " updated_at = excluded.updated_at, updated_by = excluded.updated_by returning version",
                Integer.class, venueId, objectMapper.writeValueAsString(layout), updatedBy);
    }
}
