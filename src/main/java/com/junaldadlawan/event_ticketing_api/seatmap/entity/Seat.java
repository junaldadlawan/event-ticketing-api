package com.junaldadlawan.event_ticketing_api.seatmap.entity;

import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * "Transactional/status-bearing" tier per the ERD's audit-column policy
 * (status changes over time, no soft delete, no created_by/updated_by) —
 * deliberately does NOT extend {@code Auditable}, carrying only its own
 * {@code createdAt}/{@code updatedAt}, same idiom as
 * {@code OrganizationMember} choosing a leaner base than the full-audit tier.
 */
@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "seat_map_id", nullable = false)
    private UUID seatMapId;

    @Column(name = "section", nullable = false, length = 100)
    private String section;

    @Column(name = "row", nullable = false, length = 20)
    private String row;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    // Seats copied from a venue's seat layout (see LayoutSeatCopier); all null on seats that were not.
    /** The ticket type this seat is sold under; a reserved-seating buyer can only pick seats of the ticket type they add to the cart. */
    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    /** The id of the layout section the seat came from. */
    @Column(name = "layout_section_id")
    private UUID layoutSectionId;

    /** Where the seat is on the plan (the plan's own units, the section's position and turn already applied). */
    @Column(name = "x")
    private Double x;

    @Column(name = "y")
    private Double y;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", length = 20)
    private VenueLayout.SeatType seatType;

    @Column(name = "section_color", length = 7)
    private String sectionColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", length = 20)
    private VenueLayout.SectionKind sectionKind;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
