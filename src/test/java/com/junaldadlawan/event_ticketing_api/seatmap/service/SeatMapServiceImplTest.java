package com.junaldadlawan.event_ticketing_api.seatmap.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.enums.SeatStatus;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatMapRepository;
import com.junaldadlawan.event_ticketing_api.seatmap.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link SeatMapServiceImpl} (no Spring context),
 * mirroring {@code TicketTypeServiceImplTest}'s style. Covers the
 * draft-visibility gating and 404-when-no-seatmap behavior described in the
 * task; see {@code testing/ticket-type-seatmap-test-results.md} for the full
 * scenario map.
 */
@ExtendWith(MockitoExtension.class)
class SeatMapServiceImplTest {

    @Mock
    private SeatMapRepository seatMapRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private SeatMapServiceImpl service;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new SeatMapServiceImpl(seatMapRepository, seatRepository, eventRepository, accessGuard);
        orgId = UUID.randomUUID();
    }

    private Event event(UUID id, UUID organizationId, EventStatus status) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert Night")
                .description("desc")
                .category("music")
                .status(status)
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private SeatMap seatMap(UUID id, UUID eventId) {
        return SeatMap.builder().id(id).eventId(eventId).build();
    }

    private Seat seat(UUID seatMapId, String section, String row, String seatNumber, SeatStatus status) {
        return Seat.builder()
                .id(UUID.randomUUID())
                .seatMapId(seatMapId)
                .section(section)
                .row(row)
                .seatNumber(seatNumber)
                .status(status)
                .build();
    }

    // ---- getSeatMap() ----

    @Test
    void getSeatMap_publishedEvent_seatMapExists_succeedsWithoutTouchingAccessGuard() {
        UUID eventId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));

        SeatMap result = service.getSeatMap(eventId);

        assertThat(result.getId()).isEqualTo(seatMapId);
        verifyNoInteractions(accessGuard);
    }

    @Test
    void getSeatMap_noSeatMapExists_throwsResourceNotFoundWithExpectedMessage() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.PUBLISHED)));
        when(seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSeatMap(eventId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Event has no seat map");
    }

    @Test
    void getSeatMap_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSeatMap(eventId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accessGuard);
        verifyNoInteractions(seatMapRepository);
    }

    @Test
    void getSeatMap_draftEvent_stranger_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getSeatMap(eventId)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(seatMapRepository);
    }

    @Test
    void getSeatMap_draftEvent_ownerOfEventsOrg_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);
        when(accessGuard.hasRole(ownerId, orgId, OrganizationRole.OWNER)).thenReturn(true);
        when(seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));

        SeatMap result = service.getSeatMap(eventId);

        assertThat(result.getId()).isEqualTo(seatMapId);
    }

    @Test
    void getSeatMap_draftEvent_admin_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID seatMapId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(true);
        when(seatMapRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(seatMap(seatMapId, eventId)));

        service.getSeatMap(eventId);

        verify(accessGuard, never()).currentUserId();
    }

    /** Same cross-org regression class as the ticket-type module. */
    @Test
    void getSeatMap_draftEvent_ownerOfDifferentOrganization_throwsForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID otherOrgOwnerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId, EventStatus.DRAFT)));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOwnerId);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOwnerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.getSeatMap(eventId)).isInstanceOf(ForbiddenException.class);
        verify(accessGuard).hasRole(otherOrgOwnerId, orgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOwnerId, otherOrgId, OrganizationRole.OWNER);
    }

    // ---- getSeats() ----

    @Test
    void getSeats_returnsSeatsWithStatus() {
        UUID seatMapId = UUID.randomUUID();
        Seat availableSeat = seat(seatMapId, "A", "1", "1", SeatStatus.AVAILABLE);
        Seat heldSeat = seat(seatMapId, "A", "1", "2", SeatStatus.HELD);
        when(seatRepository.findBySeatMapId(seatMapId)).thenReturn(List.of(availableSeat, heldSeat));

        List<Seat> result = service.getSeats(seatMapId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Seat::getStatus).containsExactlyInAnyOrder(SeatStatus.AVAILABLE, SeatStatus.HELD);
    }

    @Test
    void getSeats_noSeats_returnsEmptyList() {
        UUID seatMapId = UUID.randomUUID();
        when(seatRepository.findBySeatMapId(seatMapId)).thenReturn(List.of());

        assertThat(service.getSeats(seatMapId)).isEmpty();
    }
}
