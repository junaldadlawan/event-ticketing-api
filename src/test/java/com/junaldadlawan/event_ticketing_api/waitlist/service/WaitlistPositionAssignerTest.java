package com.junaldadlawan.event_ticketing_api.waitlist.service;

import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import com.junaldadlawan.event_ticketing_api.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link WaitlistPositionAssigner} — the single
 * count-then-insert attempt that {@code WaitlistServiceImpl.saveWithRetriedPosition}
 * loops over. Proves the position-counting math (current count + 1) for
 * both the ticket-type-specific and event-general branches, and that a
 * {@code DataIntegrityViolationException} on the insert propagates
 * uncaught (rather than being swallowed here) — per this class's javadoc,
 * catching it inside the same {@code REQUIRES_NEW} method and returning
 * normally would throw {@code UnexpectedRollbackException} instead, since
 * Hibernate marks the transaction rollback-only the instant the flush
 * fails. The retry-on-exception behavior itself is exercised in {@code
 * WaitlistServiceImplTest} against a mocked assigner.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistPositionAssignerTest {

    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;

    private WaitlistPositionAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new WaitlistPositionAssigner(waitlistEntryRepository);
    }

    @Test
    void assign_specificTicketType_positionIsCurrentCountPlusOne() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(waitlistEntryRepository.countByEventIdAndTicketTypeId(eventId, ticketTypeId)).thenReturn(4L);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistEntry result = assigner.assign(eventId, ticketTypeId, userId);

        assertThat(result.getPosition()).isEqualTo(5);
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(result.getUserId()).isEqualTo(userId);
        // Scoped to the ticket-type-specific counting method only.
        verify(waitlistEntryRepository, never()).countByEventIdAndTicketTypeIdIsNull(any());
    }

    @Test
    void assign_eventGeneral_positionIsCurrentCountPlusOne() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(waitlistEntryRepository.countByEventIdAndTicketTypeIdIsNull(eventId)).thenReturn(2L);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistEntry result = assigner.assign(eventId, null, userId);

        assertThat(result.getPosition()).isEqualTo(3);
        assertThat(result.getTicketTypeId()).isNull();
        verify(waitlistEntryRepository, never()).countByEventIdAndTicketTypeId(any(), any());
    }

    @Test
    void assign_firstEverEntryInScope_positionOne() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(waitlistEntryRepository.countByEventIdAndTicketTypeId(eventId, ticketTypeId)).thenReturn(0L);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        WaitlistEntry result = assigner.assign(eventId, ticketTypeId, userId);

        assertThat(result.getPosition()).isEqualTo(1);
    }

    @Test
    void assign_insertLosesPositionRace_dataIntegrityViolation_propagatesUncaught() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(waitlistEntryRepository.countByEventIdAndTicketTypeId(eventId, ticketTypeId)).thenReturn(0L);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate position"));

        assertThatThrownBy(() -> assigner.assign(eventId, ticketTypeId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assign_savesEntryWithExpectedFields() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(waitlistEntryRepository.countByEventIdAndTicketTypeId(eventId, ticketTypeId)).thenReturn(0L);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        assigner.assign(eventId, ticketTypeId, userId);

        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(waitlistEntryRepository).saveAndFlush(captor.capture());
        WaitlistEntry saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPosition()).isEqualTo(1);
        assertThat(saved.getNotifiedAt()).isNull();
        assertThat(saved.getOfferExpiresAt()).isNull();
    }
}
