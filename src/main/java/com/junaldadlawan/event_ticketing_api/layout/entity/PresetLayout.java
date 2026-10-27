package com.junaldadlawan.event_ticketing_api.layout.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

/** A seat layout an admin offers every organizer: read-only to them, copied into an organization as an independent {@link SeatLayout}. */
@Entity
@Table(name = "layout_presets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresetLayout extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    /** Unique among presets, ignoring case. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Only active presets are listed to organizers. */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "width", nullable = false)
    private double width;

    @Column(name = "height", nullable = false)
    private double height;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    /** 1 when created, one more on every replace. */
    @Column(name = "version", nullable = false)
    private int version;

    /** The whole design as JSON (a {@code VenueLayout}). */
    @Column(name = "layout", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String layout;
}
