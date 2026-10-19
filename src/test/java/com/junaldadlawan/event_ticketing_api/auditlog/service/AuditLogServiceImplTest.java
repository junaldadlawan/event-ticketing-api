package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import com.junaldadlawan.event_ticketing_api.auditlog.repository.AuditLogEntryRepository;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link AuditLogServiceImpl} (no Spring context) -
 * covers BR-NFR-005: {@code record} never throws even when the repository
 * does (mirrors {@code NotificationServiceImpl.notify}'s own test coverage),
 * {@code list}'s admin-only gate, and the optional {@code actorId} filter.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogEntryRepository auditLogEntryRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditLogServiceImpl(auditLogEntryRepository, accessGuard, eventRepository, userRepository);
    }

    @Test
    void record_savesEntryWithGivenFields() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        service.record(actorId, "refund.issued", "Refund", targetId);

        verify(auditLogEntryRepository).saveAndFlush(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo("refund.issued");
        assertThat(saved.getTargetType()).isEqualTo("Refund");
        assertThat(saved.getTargetId()).isEqualTo(targetId);
    }

    @Test
    void record_repositoryThrows_doesNotPropagate() {
        doThrow(new RuntimeException("db down")).when(auditLogEntryRepository).saveAndFlush(any());

        service.record(UUID.randomUUID(), "event.cancelled", "Event", UUID.randomUUID());

        // No exception thrown out of record() - NFR 5.2-equivalent guarantee.
    }

    @Test
    void list_admin_noFilter_returnsFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLogEntry> page = new PageImpl<>(List.of(AuditLogEntry.builder().id(UUID.randomUUID()).build()));
        when(auditLogEntryRepository.findAll(pageable)).thenReturn(page);

        Page<AuditLogEntry> result = service.list(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(auditLogEntryRepository, never()).findByActorId(any(), any());
    }

    @Test
    void list_admin_withActorIdFilter_returnsFindByActorId() {
        UUID actorId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLogEntry> page = new PageImpl<>(List.of(AuditLogEntry.builder().id(UUID.randomUUID()).actorId(actorId).build()));
        when(auditLogEntryRepository.findByActorId(actorId, pageable)).thenReturn(page);

        Page<AuditLogEntry> result = service.list(actorId, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(auditLogEntryRepository, never()).findAll(pageable);
    }

    @Test
    void list_nonAdmin_throwsForbidden() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- BR-EVENT-006: per-event activity history ----

    @Test
    void record_withEventId_savesTheEvent_andAllowsNoActorForTheSystem() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        UUID eventId = UUID.randomUUID();

        service.record(null, "event.completed", "Event", eventId, eventId);

        verify(auditLogEntryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getActorId()).isNull();
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    void listForEvent_unknownEvent_throwsNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForEvent(eventId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForEvent_aCallerWhoDoesNotManageTheEvent_isForbidden_andReadsNothing() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(Event.builder().id(eventId).organizationId(orgId).build()));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(strangerId);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(strangerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.listForEvent(eventId, PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class);
        verify(auditLogEntryRepository, never()).findByEventIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listForEvent_organizer_seesTheEntries_withTheActorsNames_andNoNameForTheSystem() {
        UUID eventId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(Event.builder().id(eventId).organizationId(orgId).build()));
        when(accessGuard.isAdmin()).thenReturn(false);
        when(accessGuard.currentUserId()).thenReturn(organizerId);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.OWNER)).thenReturn(false);
        when(accessGuard.hasRole(organizerId, orgId, OrganizationRole.ORGANIZER)).thenReturn(true);
        AuditLogEntry byOrganizer = AuditLogEntry.builder().actorId(organizerId).action("ticket_type.created").eventId(eventId).build();
        AuditLogEntry bySystem = AuditLogEntry.builder().action("event.completed").eventId(eventId).build();
        when(auditLogEntryRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable))
                .thenReturn(new PageImpl<>(List.of(bySystem, byOrganizer), pageable, 2));
        when(userRepository.findAllById(anyIterable()))
                .thenReturn(List.of(User.builder().id(organizerId).firstName("Jordan").lastName("Cruz").build()));

        var page = service.listForEvent(eventId, pageable);

        assertThat(page.getContent()).extracting("action").containsExactly("event.completed", "ticket_type.created");
        assertThat(page.getContent()).extracting("actorName").containsExactly(null, "Jordan Cruz");
        verify(userRepository).findAllById(Set.of(organizerId));
    }
}
