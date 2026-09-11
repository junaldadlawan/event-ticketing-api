package com.junaldadlawan.event_ticketing_api.ticket.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link TicketAccessGuard} — the BR-CART-004-
 * equivalent ticket-visibility rule (owning buyer, event's organizer/owner,
 * or admin), factored out of {@code TicketServiceImpl} in Phase 6b so
 * {@code TicketArtifactServiceImpl} can reuse the exact same check.
 */
@ExtendWith(MockitoExtension.class)
class TicketAccessGuardTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private TicketAccessGuard guard;

    private UUID orgId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        guard = new TicketAccessGuard(eventRepository, accessGuard);
        orgId = UUID.randomUUID();
        eventId = UUID.randomUUID();
    }

    private Event event(UUID id, UUID organizationId) {
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return Event.builder()
                .id(id)
                .organizationId(organizationId)
                .title("Concert Night")
                .description("desc")
                .category("music")
                .ticketPrefix("ABC")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("UTC")
                .build();
    }

    private Ticket ticket(UUID ownerId) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .eventId(eventId)
                .ticketTypeId(UUID.randomUUID())
                .seatId(null)
                .ownerId(ownerId)
                .ticketNumber("ABC-A2B3C4")
                .credential("irrelevant-for-this-test")
                .status(TicketStatus.VALID)
                .build();
    }

    @Test
    void owningBuyer_succeeds_withoutTouchingEventRepository() {
        UUID ownerId = UUID.randomUUID();
        Ticket ticket = ticket(ownerId);
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(ownerId);

        assertThatCode(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).doesNotThrowAnyException();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void admin_succeeds_withoutTouchingCurrentUserIdOrEventRepository() {
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(true);

        assertThatCode(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).doesNotThrowAnyException();
        verify(accessGuard, never()).currentUserId();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void eventOrganizer_succeeds() {
        UUID organizerId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);

        assertThatCode(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).doesNotThrowAnyException();
    }

    @Test
    void eventOwner_succeeds() {
        UUID orgOwnerId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(orgOwnerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(orgOwnerId, orgId, OrganizationRole.OWNER)).thenReturn(true);

        assertThatCode(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).doesNotThrowAnyException();
    }

    @Test
    void stranger_throwsForbidden() {
        UUID strangerId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).isInstanceOf(ForbiddenException.class);
    }

    /**
     * Key regression class (mirrors {@code TicketTypeServiceImplTest}): an
     * owner/organizer of a DIFFERENT organization must be forbidden too, not
     * just a roleless stranger.
     */
    @Test
    void organizerOfDifferentOrganization_throwsForbidden() {
        UUID otherOrgOrganizerId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(otherOrgOrganizerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, orgId)));
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(otherOrgOrganizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).isInstanceOf(ForbiddenException.class);
        verify(accessGuard, never()).hasRole(otherOrgOrganizerId, otherOrgId, OrganizationRole.OWNER);
        verify(accessGuard, never()).hasRole(otherOrgOrganizerId, otherOrgId, OrganizationRole.ORGANIZER);
    }

    @Test
    void nonOwningCaller_eventNoLongerExists_throwsResourceNotFound() {
        UUID callerId = UUID.randomUUID();
        Ticket ticket = ticket(UUID.randomUUID());
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireOwnerBuyerOrOrganizerOrAdmin(ticket)).isInstanceOf(ResourceNotFoundException.class);
    }
}
