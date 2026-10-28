package com.junaldadlawan.event_ticketing_api.seatmap.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.layout.entity.SeatLayout;
import com.junaldadlawan.event_ticketing_api.layout.repository.SeatLayoutRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout.SeatType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Copies seats from one of the organization's seat layouts into an event's seat map, as a snapshot: the seats carry everything the buyers' plan needs (position, row, number,
 * type, the section's colour and kind), so editing the layout later never changes an event that already copied it. BLOCKED seats are not copied.
 * Two steps so the ticket type's quantity (the seats copied) is known before it is saved: {@link #plan} checks and counts, {@link #apply} saves.
 */
@Component
@RequiredArgsConstructor
public class LayoutSeatCopier {

    /** What {@link #apply} will save: the sections taken and the layout it came from. */
    public record Plan(UUID layoutId, VenueLayout layout, int layoutVersion, List<VenueLayout.Section> sections, int seatCount) {
    }

    private final SeatLayoutRepository seatLayoutRepository;
    private final EventRepository eventRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatRepository seatRepository;
    private final ObjectMapper objectMapper;

    /**
     * Chooses the seats of {@code sectionId}, or of every section when {@code wholeLayout}, from the organization layout {@code layoutId}; 400 when
     * the layout cannot supply them, 409 when taken or when the event already copied seats from another layout.
     */
    public Plan plan(Event event, UUID layoutId, UUID sectionId, boolean wholeLayout) {
        SeatLayout seatLayout = seatLayoutRepository.findByIdAndDeletedAtIsNull(layoutId)
                .filter(found -> found.getOrganizationId().equals(event.getOrganizationId()))
                .orElseThrow(() -> new BadRequestException("The event's organization has no seat layout " + layoutId));
        VenueLayout layout = objectMapper.readValue(seatLayout.getLayout(), VenueLayout.class);
        List<VenueLayout.Section> sections = wholeLayout ? layout.sections()
                : layout.sections().stream().filter(section -> section.id().equals(sectionId)).toList();
        if (sections.isEmpty()) {
            throw new BadRequestException(wholeLayout ? "The seat layout has no sections" : "The seat layout has no section " + sectionId);
        }
        int seatCount = sections.stream().mapToInt(section -> sellable(section).size()).sum();
        if (seatCount == 0) {
            throw new BadRequestException("The chosen section(s) have no seats for sale (blocked seats are not copied)");
        }

        seatMapRepository.findByEventIdAndDeletedAtIsNull(event.getId()).ifPresent(seatMap -> {
            if (seatMap.getLayoutVersion() != null && !layoutId.equals(event.getLayoutId())) {
                throw new ConflictException("This event already copied seats from another seat layout (" + event.getLayoutId()
                        + "), so seats from a second layout cannot be mixed in");
            }
            if (seatMap.getLayoutVersion() != null && seatMap.getLayoutVersion() != seatLayout.getVersion()) {
                throw new ConflictException("The seat layout was changed after this event copied seats from it (version " + seatMap.getLayoutVersion()
                        + ", now " + seatLayout.getVersion() + "), so more seats cannot be added without mixing two plans");
            }
            Set<UUID> ids = sections.stream().map(VenueLayout.Section::id).collect(Collectors.toSet());
            if (seatRepository.existsBySeatMapIdAndLayoutSectionIdIn(seatMap.getId(), ids)) {
                throw new ConflictException("A section of the seat layout already has a ticket type in this event");
            }
        });
        return new Plan(layoutId, layout, seatLayout.getVersion(), sections, seatCount);
    }

    /** Saves the planned seats under {@code ticketTypeId} in the event's seat map (made on first use) and records the layout on the event. */
    public void apply(Plan plan, Event event, UUID ticketTypeId) {
        event.setLayoutId(plan.layoutId());
        eventRepository.save(event);
        UUID eventId = event.getId();
        SeatMap seatMap = seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId).orElseGet(() -> SeatMap.builder().eventId(eventId).build());
        if (seatMap.getLayoutVersion() == null) {
            seatMap.setLayoutVersion(plan.layoutVersion());
            seatMap.setLayoutWidth(plan.layout().width());
            seatMap.setLayoutHeight(plan.layout().height());
            seatMap.setLayoutShapes(objectMapper.writeValueAsString(plan.layout().shapes()));
        }
        seatMap = seatMapRepository.save(seatMap);

        List<Seat> seats = new ArrayList<>();
        for (VenueLayout.Section section : plan.sections()) {
            double radians = Math.toRadians(section.rotation());
            for (VenueLayout.Row row : section.rows()) {
                for (VenueLayout.Seat seat : row.seats()) {
                    if (seat.type() == SeatType.BLOCKED) {
                        continue;
                    }
                    double localX = row.offset() + seat.x();
                    seats.add(Seat.builder()
                            .seatMapId(seatMap.getId())
                            .section(section.name())
                            .row(row.label())
                            .seatNumber(seat.number())
                            .layoutSectionId(section.id())
                            .ticketTypeId(ticketTypeId)
                            .x(round(section.x() + localX * Math.cos(radians) - seat.y() * Math.sin(radians)))
                            .y(round(section.y() + localX * Math.sin(radians) + seat.y() * Math.cos(radians)))
                            .seatType(seat.type())
                            .sectionColor(section.color())
                            .sectionKind(section.kind())
                            .build());
                }
            }
        }
        seatRepository.saveAll(seats);
    }

    private static List<VenueLayout.Seat> sellable(VenueLayout.Section section) {
        return section.rows().stream().flatMap(row -> row.seats().stream()).filter(seat -> seat.type() != SeatType.BLOCKED).toList();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
