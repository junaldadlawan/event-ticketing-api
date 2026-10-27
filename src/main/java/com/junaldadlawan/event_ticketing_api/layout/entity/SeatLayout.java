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

/** An organization's seat layout; events pick one to copy reserved seats from. */
@Entity
@Table(name = "seat_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatLayout extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Unique in the organization, ignoring case. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

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
