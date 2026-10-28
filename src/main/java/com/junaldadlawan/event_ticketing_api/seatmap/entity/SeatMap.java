package com.junaldadlawan.event_ticketing_api.seatmap.entity;

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

import java.util.UUID;

/**
 * "Managed resource" tier per the ERD's audit-column policy — full audit
 * set, same as {@code Event}/{@code Venue}/{@code TicketType}. Event-owned
 * (the ERD settles the {@code event_id} FK direction, superseding a looser
 * note elsewhere about a reusable venue-level seat map): effectively
 * one-per-event.
 */
@Entity
@Table(name = "seat_maps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatMap extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    // The venue layout the seats were copied from, kept as a snapshot for the buyers' plan (null when the seats were not copied from a layout).
    @Column(name = "layout_version")
    private Integer layoutVersion;

    @Column(name = "layout_width")
    private Double layoutWidth;

    @Column(name = "layout_height")
    private Double layoutHeight;

    /** The layout's shapes (stage, labels, walls) as JSON. */
    @Column(name = "layout_shapes")
    private String layoutShapes;
}
