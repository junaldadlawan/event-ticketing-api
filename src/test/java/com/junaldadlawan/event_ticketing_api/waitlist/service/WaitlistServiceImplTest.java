package com.junaldadlawan.event_ticketing_api.waitlist.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.notification.enums.NotificationType;
import com.junaldadlawan.event_ticketing_api.notification.service.NotificationService;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import com.junaldadlawan.event_ticketing_api.tickettype.repository.TicketTypeRepository;
import com.junaldadlawan.event_ticketing_api.waitlist.dto.WaitlistEntryResponse;
import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import com.junaldadlawan.event_ticketing_api.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link WaitlistServiceImpl} (no Spring context) —
 * mirrors {@code TicketTransferServiceImplTest}'s style. Covers the
 * sold-out gate (BR-WAIT-001, both the ticket-type-specific and
 * event-general branches, including the "event has zero ticket types"
 * edge case), the duplicate-join 409, and {@code saveWithRetriedPosition}'s
 * retry-until-success / exhausted-retries behavior against a mocked {@link
 * WaitlistPositionAssigner} (the actual count+1 math and the {@code
 * DataIntegrityViolationException} handling live in {@code
 * WaitlistPositionAssignerTest} instead, since that logic was extracted
 * into its own {@code REQUIRES_NEW} bean — see that class's javadoc for
 * why a same-transaction retry doesn't work on Postgres). Notify-on-
 * inventory-freed (BR-WAIT-002/003) is covered separately in {@code
 * RefundServiceImplTest} (the trigger call site) and {@code
 * WaitlistServiceImplTest}'s own {@code notifyNextInLineIfAvailable} tests
 * below.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceImplTest {

    @Mock
    private WaitlistEntryRepository waitlistEntryRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private WaitlistPositionAssigner positionAssigner;
    @Mock
    private NotificationService notificationService;

    private WaitlistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WaitlistServiceImpl(waitlistEntryRepository, eventRepository, ticketTypeRepository, accessGuard, positionAssigner, notificationService);
    }

    private Event event(UUID id) {
        return Event.builder().id(id).organizationId(UUID.randomUUID()).title("t").description("d")
                .category("music").ticketPrefix("ABC").build();
    }

    private TicketType ticketType(UUID id, UUID eventId, int quantityAvailable) {
        return TicketType.builder().id(id).eventId(eventId).name("GA").quantityAvailable(quantityAvailable).build();
    }

    private WaitlistEntry entry(UUID eventId, UUID ticketTypeId, UUID userId, int position) {
        return WaitlistEntry.builder().id(UUID.randomUUID()).eventId(eventId).ticketTypeId(ticketTypeId)
                .userId(userId).position(position).build();
    }

    // ---- join(): specific ticketTypeId branch ----

    @Test
    void join_unknownEvent_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(eventId, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ticketTypeRepository, waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_specificTicketType_unknownTicketType_throwsResourceNotFound() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_specificTicketType_belongsToDifferentEvent_throwsBadRequest() {
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, otherEventId, 0)));

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_specificTicketType_stillHasAvailableQuantity_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 1)));

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("This ticket type is not currently sold out");
        verifyNoInteractions(waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_specificTicketType_soldOut_zeroQuantity_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId)).thenReturn(false);
        when(positionAssigner.assign(eventId, ticketTypeId, callerId))
                .thenReturn(entry(eventId, ticketTypeId, callerId, 1));

        WaitlistEntryResponse result = service.join(eventId, ticketTypeId);

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.ticketTypeId()).isEqualTo(ticketTypeId);
        assertThat(result.userId()).isEqualTo(callerId);
        assertThat(result.position()).isEqualTo(1);
        assertThat(result.notifiedAt()).isNull();
        assertThat(result.offerExpiresAt()).isNull();
    }

    // ---- join(): event-general branch (ticketTypeId == null) ----

    @Test
    void join_eventGeneral_noTicketTypesAtAll_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.join(eventId, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("This event is not currently sold out");
        verifyNoInteractions(waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_eventGeneral_oneTicketTypeStillAvailable_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        List<TicketType> types = List.of(
                ticketType(UUID.randomUUID(), eventId, 0),
                ticketType(UUID.randomUUID(), eventId, 5)); // this one is NOT sold out
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(types);

        assertThatThrownBy(() -> service.join(eventId, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("This event is not currently sold out");
        verifyNoInteractions(waitlistEntryRepository, accessGuard, positionAssigner);
    }

    @Test
    void join_eventGeneral_allTicketTypesSoldOut_succeeds() {
        UUID eventId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        List<TicketType> types = List.of(
                ticketType(UUID.randomUUID(), eventId, 0),
                ticketType(UUID.randomUUID(), eventId, -0)); // both sold out (<=0)
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId)).thenReturn(types);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdIsNullAndUserId(eventId, callerId)).thenReturn(false);
        when(positionAssigner.assign(eq(eventId), isNull(), eq(callerId)))
                .thenReturn(entry(eventId, null, callerId, 3));

        WaitlistEntryResponse result = service.join(eventId, null);

        assertThat(result.ticketTypeId()).isNull();
        assertThat(result.position()).isEqualTo(3);
        // Delegated with a null ticketTypeId argument - the event-general scope.
        verify(positionAssigner).assign(eq(eventId), isNull(), eq(callerId));
    }

    // ---- join(): duplicate-join gate (BR-WAIT-001) ----

    @Test
    void join_specificTicketType_alreadyOnWaitlist_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("You are already on this waitlist");
        verifyNoInteractions(positionAssigner);
    }

    @Test
    void join_eventGeneral_alreadyOnWaitlist_throwsConflict() {
        UUID eventId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId))
                .thenReturn(List.of(ticketType(UUID.randomUUID(), eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdIsNullAndUserId(eventId, callerId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(eventId, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("You are already on this waitlist");
        verifyNoInteractions(positionAssigner);
    }

    // ---- join(): position-assignment delegation scoped independently per (eventId, ticketTypeId) ----

    @Test
    void join_delegatesToPositionAssigner_withExactEventTicketTypeAndCallerArguments() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId)).thenReturn(false);
        when(positionAssigner.assign(eventId, ticketTypeId, callerId))
                .thenReturn(entry(eventId, ticketTypeId, callerId, 5));

        service.join(eventId, ticketTypeId);

        ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> ticketTypeIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(positionAssigner).assign(eventIdCaptor.capture(), ticketTypeIdCaptor.capture(), userIdCaptor.capture());
        assertThat(eventIdCaptor.getValue()).isEqualTo(eventId);
        assertThat(ticketTypeIdCaptor.getValue()).isEqualTo(ticketTypeId);
        assertThat(userIdCaptor.getValue()).isEqualTo(callerId);
    }

    @Test
    void join_twoDifferentTicketTypesInSameEvent_eachDelegatesToPositionAssignerWithItsOwnTicketTypeId() {
        UUID eventId = UUID.randomUUID();
        UUID gaTicketTypeId = UUID.randomUUID();
        UUID vipTicketTypeId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(gaTicketTypeId))
                .thenReturn(Optional.of(ticketType(gaTicketTypeId, eventId, 0)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(vipTicketTypeId))
                .thenReturn(Optional.of(ticketType(vipTicketTypeId, eventId, 0)));
        when(positionAssigner.assign(eventId, gaTicketTypeId, userA))
                .thenReturn(entry(eventId, gaTicketTypeId, userA, 1));
        when(positionAssigner.assign(eventId, vipTicketTypeId, userB))
                .thenReturn(entry(eventId, vipTicketTypeId, userB, 1));

        when(accessGuard.currentUserId()).thenReturn(userA);
        WaitlistEntryResponse gaResult = service.join(eventId, gaTicketTypeId);

        when(accessGuard.currentUserId()).thenReturn(userB);
        WaitlistEntryResponse vipResult = service.join(eventId, vipTicketTypeId);

        assertThat(gaResult.position()).isEqualTo(1);
        assertThat(vipResult.position()).isEqualTo(1);
        verify(positionAssigner).assign(eventId, gaTicketTypeId, userA);
        verify(positionAssigner).assign(eventId, vipTicketTypeId, userB);
    }

    // ---- join(): saveWithRetriedPosition's retry loop against a lost position race ----

    @Test
    void join_positionAssignerLosesRaceOnce_retriesAndSucceedsOnSecondAttempt() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId)).thenReturn(false);
        // First attempt loses the position race (DataIntegrityViolationException, as WaitlistPositionAssigner.assign
        // would throw on Postgres); second succeeds.
        when(positionAssigner.assign(eventId, ticketTypeId, callerId))
                .thenThrow(new DataIntegrityViolationException("duplicate position"))
                .thenReturn(entry(eventId, ticketTypeId, callerId, 5));

        WaitlistEntryResponse result = service.join(eventId, ticketTypeId);

        assertThat(result.position()).isEqualTo(5);
        // Proves the loop actually retries (calls assign again) rather than giving up after the first lost race.
        verify(positionAssigner, times(2)).assign(eventId, ticketTypeId, callerId);
    }

    @Test
    void join_positionAssignerLosesRaceEveryAttempt_throwsIllegalStateException_afterMaxRetries() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId)).thenReturn(false);
        when(positionAssigner.assign(eventId, ticketTypeId, callerId))
                .thenThrow(new DataIntegrityViolationException("duplicate position"));

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to assign a waitlist position after 10 attempts");
        // 10 attempts total, no fewer, no more (proves it actually retries rather than giving up after one try).
        verify(positionAssigner, times(10)).assign(eventId, ticketTypeId, callerId);
    }

    /**
     * Code-reviewer HIGH: {@code isAlreadyOnWaitlist} and the eventual
     * insert are two separate steps - two concurrent join attempts from
     * the SAME user can both pass the initial check before either commits,
     * and both land in the retry loop. Without distinguishing this from a
     * genuine lost-position-race, the loop would burn all 10 attempts on a
     * doomed duplicate-join insert and surface an unhandled 500
     * ({@code IllegalStateException}) instead of the 409 a duplicate join
     * should produce. Proves the fix: re-checking {@code
     * isAlreadyOnWaitlist} inside the catch (now returning {@code true},
     * simulating that the other concurrent request's insert has since
     * committed) short-circuits straight to {@code ConflictException}
     * instead of retrying.
     */
    @Test
    void join_positionAssignerFails_concurrentDuplicateJoinFromSameUserSinceCommitted_throwsConflict_doesNotExhaustRetries() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId)));
        when(ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId))
                .thenReturn(Optional.of(ticketType(ticketTypeId, eventId, 0)));
        when(accessGuard.currentUserId()).thenReturn(callerId);
        // Initial check (before the race): not yet on the waitlist.
        // Re-check (inside the catch, after losing the race): the
        // concurrent duplicate request's insert has since committed.
        when(waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, callerId))
                .thenReturn(false, true);
        when(positionAssigner.assign(eventId, ticketTypeId, callerId))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.join(eventId, ticketTypeId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already on this waitlist");
        // Exactly one doomed attempt, not all 10 - the re-check short-circuited it.
        verify(positionAssigner, times(1)).assign(eventId, ticketTypeId, callerId);
    }

    // ---- listMyEntries() ----

    @Test
    void listMyEntries_delegatesToCallerScopedRepositoryQuery_andMapsToResponses() {
        UUID callerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WaitlistEntry storedEntry = entry(eventId, null, callerId, 1);
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(callerId)).thenReturn(List.of(storedEntry));

        List<WaitlistEntryResponse> result = service.listMyEntries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(storedEntry.getId());
        assertThat(result.get(0).userId()).isEqualTo(callerId);
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(waitlistEntryRepository).findByUserIdOrderByCreatedAtAsc(captor.capture());
        assertThat(captor.getValue()).isEqualTo(callerId);
    }

    @Test
    void listMyEntries_noEntries_returnsEmptyList() {
        UUID callerId = UUID.randomUUID();
        when(accessGuard.currentUserId()).thenReturn(callerId);
        when(waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(callerId)).thenReturn(List.of());

        assertThat(service.listMyEntries()).isEmpty();
    }

    // ---- notifyNextInLineIfAvailable() / notifyNextForScope() (Phase 11, BR-WAIT-002/003) ----

    @Test
    void notifyNextInLineIfAvailable_ticketTypeScopedWaiterExists_notifiedAndOffered() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID waiterId = UUID.randomUUID();
        WaitlistEntry specificWaiter = entry(eventId, ticketTypeId, waiterId, 1);
        when(waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eq(eventId), eq(ticketTypeId), any()))
                .thenReturn(List.of(specificWaiter));
        when(waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any()))
                .thenReturn(List.of());

        service.notifyNextInLineIfAvailable(eventId, ticketTypeId);

        assertThat(specificWaiter.getNotifiedAt()).isNotNull();
        assertThat(specificWaiter.getOfferExpiresAt()).isNotNull();
        verify(waitlistEntryRepository).save(specificWaiter);
        verify(notificationService).notify(waiterId, NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", specificWaiter.getId());
    }

    @Test
    void notifyNextInLineIfAvailable_eventGeneralWaiterExists_alsoNotifiedIndependently() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID generalWaiterId = UUID.randomUUID();
        WaitlistEntry generalWaiter = entry(eventId, null, generalWaiterId, 1);
        when(waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eq(eventId), eq(ticketTypeId), any()))
                .thenReturn(List.of());
        when(waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any()))
                .thenReturn(List.of(generalWaiter));

        service.notifyNextInLineIfAvailable(eventId, ticketTypeId);

        assertThat(generalWaiter.getNotifiedAt()).isNotNull();
        verify(waitlistEntryRepository).save(generalWaiter);
        verify(notificationService).notify(generalWaiterId, NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", generalWaiter.getId());
    }

    /**
     * Restocking one ticket type always ends the event's overall
     * "generally sold out" state - both the ticket-type-scoped AND the
     * event-general waiter (if either exists) are notified from the SAME
     * restock, as two distinct people, two distinct notifications.
     */
    @Test
    void notifyNextInLineIfAvailable_bothScopesHaveWaiters_bothNotifiedIndependently_asDifferentPeople() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID specificWaiterId = UUID.randomUUID();
        UUID generalWaiterId = UUID.randomUUID();
        WaitlistEntry specificWaiter = entry(eventId, ticketTypeId, specificWaiterId, 1);
        WaitlistEntry generalWaiter = entry(eventId, null, generalWaiterId, 1);
        when(waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eq(eventId), eq(ticketTypeId), any()))
                .thenReturn(List.of(specificWaiter));
        when(waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any()))
                .thenReturn(List.of(generalWaiter));

        service.notifyNextInLineIfAvailable(eventId, ticketTypeId);

        verify(notificationService).notify(specificWaiterId, NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", specificWaiter.getId());
        verify(notificationService).notify(generalWaiterId, NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", generalWaiter.getId());
        verify(notificationService, times(2)).notify(any(), eq(NotificationType.WAITLIST_AVAILABILITY), eq("WaitlistEntry"), any());
    }

    @Test
    void notifyNextInLineIfAvailable_noOneWaitingInEitherScope_isANoOp() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eq(eventId), eq(ticketTypeId), any()))
                .thenReturn(List.of());
        when(waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any()))
                .thenReturn(List.of());

        service.notifyNextInLineIfAvailable(eventId, ticketTypeId);

        verify(waitlistEntryRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    /**
     * NFR 5.2-equivalent for the waitlist trigger: never throws, even if the
     * underlying repository blows up - this is called from the middle of a
     * refund transaction (see {@code RefundServiceImpl.restockAndNotifyWaitlistIfGeneralAdmission}),
     * which must not fail because of this.
     * <p>
     * Regression test: each scope is now caught INDEPENDENTLY (fixed after
     * a qa-tester finding on the original shared-try/catch version, which
     * meant an exception on the ticket-type-scoped scope's save silently
     * skipped the unrelated event-general waiter too). A failure notifying
     * the ticket-type-specific waiter must not prevent the event-general
     * waiter from still being offered their spot - they're two different
     * people.
     */
    @Test
    void notifyNextInLineIfAvailable_specificScopeSaveThrows_swallowed_eventGeneralScopeStillAttempted() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        WaitlistEntry specificWaiter = entry(eventId, ticketTypeId, UUID.randomUUID(), 1);
        WaitlistEntry generalWaiter = entry(eventId, null, UUID.randomUUID(), 1);
        when(waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eq(eventId), eq(ticketTypeId), any()))
                .thenReturn(List.of(specificWaiter));
        when(waitlistEntryRepository.save(specificWaiter)).thenThrow(new RuntimeException("connection lost"));
        when(waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any()))
                .thenReturn(List.of(generalWaiter));
        when(waitlistEntryRepository.save(generalWaiter)).thenReturn(generalWaiter);

        assertThatCode(() -> service.notifyNextInLineIfAvailable(eventId, ticketTypeId)).doesNotThrowAnyException();

        verify(waitlistEntryRepository).findNextNotNotifiedEventGeneralForUpdate(eq(eventId), any());
        verify(waitlistEntryRepository).save(generalWaiter);
        verify(notificationService).notify(eq(generalWaiter.getUserId()), eq(NotificationType.WAITLIST_AVAILABILITY), eq("WaitlistEntry"), eq(generalWaiter.getId()));
        // The specific-scope waiter never got a saved notifiedAt (its save threw) and was never notified.
        verify(notificationService, never()).notify(eq(specificWaiter.getUserId()), any(), any(), any());
    }
}
