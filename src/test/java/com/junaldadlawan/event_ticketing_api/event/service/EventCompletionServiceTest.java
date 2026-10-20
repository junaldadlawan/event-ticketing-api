package com.junaldadlawan.event_ticketing_api.event.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.event.cache.PublicEventCacheEvictionListener;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** BR-EVENT-005: the completion rules and the sweep, without a database. */
@ExtendWith(MockitoExtension.class)
class EventCompletionServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PublicEventCacheEvictionListener cacheEviction;

    @Mock
    private com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogService auditLogService;

    private EventCompletionService service;

    @BeforeEach
    void setUp() {
        service = new EventCompletionService(eventRepository, cacheEviction, auditLogService);
    }

    private static Event event(EventStatus status, Instant endAt) {
        return Event.builder().id(UUID.randomUUID()).status(status).endAt(endAt).build();
    }

    private static Instant past() {
        return Instant.now().minus(1, ChronoUnit.MINUTES);
    }

    private static Instant future() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    @Test
    void hasEnded_whenCompleted_orPastItsEndTime() {
        assertThat(EventCompletionService.hasEnded(event(EventStatus.COMPLETED, future()))).isTrue();
        assertThat(EventCompletionService.hasEnded(event(EventStatus.PUBLISHED, past()))).isTrue();
        assertThat(EventCompletionService.hasEnded(event(EventStatus.PUBLISHED, future()))).isFalse();
    }

    @Test
    void requireNotEnded_throws409WithTheMessage() {
        assertThatThrownBy(() -> EventCompletionService.requireNotEnded(event(EventStatus.ON_SALE, past())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Event has ended");
        assertThatCode(() -> EventCompletionService.requireNotEnded(event(EventStatus.ON_SALE, future())))
                .doesNotThrowAnyException();
    }

    @Test
    void completeIfEnded_completesALiveEndedEvent_andClearsTheCache() {
        Event event = event(EventStatus.SOLD_OUT, past());
        when(eventRepository.markCompletedIfEnded(eq(event.getId()), anyCollection(), eq(EventStatus.COMPLETED), any()))
                .thenReturn(1);

        assertThat(service.completeIfEnded(event).getStatus()).isEqualTo(EventStatus.COMPLETED);
        verify(cacheEviction).onChange(event.getId());
        // in the event's activity history, with no actor: the system did it (BR-EVENT-006)
        verify(auditLogService).record(null, "event.completed", "Event", event.getId(), event.getId());
    }

    @Test
    void completeIfEnded_leavesUpcomingDraftAndCancelledEventsAlone() {
        for (Event event : List.of(event(EventStatus.PUBLISHED, future()), event(EventStatus.DRAFT, past()),
                event(EventStatus.CANCELLED, past()), event(EventStatus.SUSPENDED, past()))) {
            EventStatus before = event.getStatus();
            assertThat(service.completeIfEnded(event).getStatus()).isEqualTo(before);
        }
        verifyNoInteractions(eventRepository, cacheEviction);
    }

    @Test
    void completeIfEnded_whenAnotherCallerCompletedItFirst_doesNotRecordItAgain() {
        Event event = event(EventStatus.PUBLISHED, past());
        when(eventRepository.markCompletedIfEnded(eq(event.getId()), anyCollection(), eq(EventStatus.COMPLETED), any()))
                .thenReturn(0);

        assertThat(service.completeIfEnded(event).getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(cacheEviction, never()).onChange(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void completeEndedEvents_completesEveryEndedLiveEvent_andCountsOnlyTheOnesItChanged() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(eventRepository.findEndedIds(anyCollection(), any())).thenReturn(List.of(first, second));
        when(eventRepository.markCompletedIfEnded(eq(first), anyCollection(), eq(EventStatus.COMPLETED), any())).thenReturn(1);
        when(eventRepository.markCompletedIfEnded(eq(second), anyCollection(), eq(EventStatus.COMPLETED), any())).thenReturn(0);

        assertThat(service.completeEndedEvents()).isEqualTo(1);
        verify(cacheEviction).onChange(first);
        verify(cacheEviction, never()).onChange(second);
    }

    @Test
    void sweep_survivesADatabaseError_soTheNextRunStillHappens() {
        when(eventRepository.findEndedIds(anyCollection(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(service::sweep).doesNotThrowAnyException();
    }
}
