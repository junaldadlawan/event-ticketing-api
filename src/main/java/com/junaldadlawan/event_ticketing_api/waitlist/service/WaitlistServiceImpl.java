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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Phase 9 (BR-WAIT-001): join/position tracking only - see {@code
 * WaitlistEntry}'s javadoc for why the notify-on-inventory-freed trigger
 * (BR-WAIT-002/003) is out of scope for this dispatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private static final int MAX_POSITION_RETRY_ATTEMPTS = 10;

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrganizationAccessGuard accessGuard;
    private final WaitlistPositionAssigner positionAssigner;
    private final NotificationService notificationService;

    @Value("${app.waitlist.offer-window-hours:24}")
    private int offerWindowHours;

    /**
     * Deliberately NOT {@code @Transactional} (code-reviewer HIGH): this
     * method's reads (event/ticket-type lookup, the duplicate-join check)
     * have no atomicity requirement linking them to each other or to the
     * eventual insert - the insert's own correctness is independently
     * guaranteed by {@link WaitlistPositionAssigner#assign}'s {@code
     * REQUIRES_NEW} transaction plus this class's retry loop. Wrapping this
     * whole method in an outer transaction would hold ITS OWN physical
     * connection checked out from the pool for the method's entire
     * duration while ALSO needing a second, separate connection for every
     * {@code REQUIRES_NEW} call inside the retry loop (Spring suspends,
     * not releases, the outer transaction's resources around each inner
     * one) - under concurrent load, that's two connections held
     * simultaneously per in-flight join, which can stall or deadlock a
     * shared connection pool sized for one-per-request.
     */
    @Override
    public WaitlistEntryResponse join(UUID eventId, UUID ticketTypeId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));

        if (ticketTypeId != null) {
            TicketType ticketType = ticketTypeRepository.findByIdAndDeletedAtIsNull(ticketTypeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found"));
            if (!ticketType.getEventId().equals(eventId)) {
                throw new BadRequestException("Ticket type " + ticketTypeId + " does not belong to event " + eventId);
            }
            if (ticketType.getQuantityAvailable() > 0) {
                throw new ConflictException("This ticket type is not currently sold out");
            }
        } else {
            requireEventGenerallySoldOut(eventId);
        }

        UUID callerId = accessGuard.currentUserId();
        if (isAlreadyOnWaitlist(eventId, ticketTypeId, callerId)) {
            throw new ConflictException("You are already on this waitlist");
        }

        return WaitlistEntryResponse.from(saveWithRetriedPosition(eventId, ticketTypeId, callerId));
    }

    @Override
    public List<WaitlistEntryResponse> listMyEntries() {
        UUID callerId = accessGuard.currentUserId();
        return waitlistEntryRepository.findByUserIdOrderByCreatedAtAsc(callerId).stream()
                .map(WaitlistEntryResponse::from)
                .toList();
    }

    /**
     * BR-WAIT-001, event-general join (no {@code ticketTypeId}): the event
     * is only "sold out" once every one of its own ticket types is. An
     * event with no ticket types at all has nothing to wait for.
     */
    private void requireEventGenerallySoldOut(UUID eventId) {
        List<TicketType> ticketTypes = ticketTypeRepository.findByEventIdAndDeletedAtIsNull(eventId);
        boolean allSoldOut = !ticketTypes.isEmpty() && ticketTypes.stream().allMatch(t -> t.getQuantityAvailable() <= 0);
        if (!allSoldOut) {
            throw new ConflictException("This event is not currently sold out");
        }
    }

    private boolean isAlreadyOnWaitlist(UUID eventId, UUID ticketTypeId, UUID userId) {
        return ticketTypeId != null
                ? waitlistEntryRepository.existsByEventIdAndTicketTypeIdAndUserId(eventId, ticketTypeId, userId)
                : waitlistEntryRepository.existsByEventIdAndTicketTypeIdIsNullAndUserId(eventId, userId);
    }

    /**
     * BR-WAIT-002 (FIFO position): position is computed as "current count +
     * 1" with no existing row to lock beforehand, so two concurrent joins
     * for the same (eventId, ticketTypeId) scope could compute the same
     * position. V17's partial unique indexes on position are the actual
     * guard - the loser's insert fails and retries against a freshly
     * recounted position, same idiom as {@code ResaleListingServiceImpl.create}'s
     * active-listing race handling.
     * <p>
     * Each attempt is delegated to {@link WaitlistPositionAssigner#assign}
     * (a separate {@code REQUIRES_NEW} bean) and the catch lives HERE, at
     * the call site, rather than inside that method - see its javadoc for
     * why: on Postgres, a same-transaction retry after catching {@link
     * DataIntegrityViolationException} never actually succeeds (the DB
     * transaction is poisoned regardless of what Java catches), and even
     * catching-and-returning-normally from inside the {@code REQUIRES_NEW}
     * method itself throws {@code UnexpectedRollbackException} (Hibernate
     * marks that transaction rollback-only the instant the flush fails).
     * Both failure modes were found via a genuine concurrent test during
     * Phase 9 QA - see {@code testing/waitlist-test-results.md}'s Findings
     * section. Catching here means every retry is a brand-new method
     * invocation, a brand-new {@code REQUIRES_NEW} transaction, and a
     * brand-new persistence context.
     * <p>
     * Code-reviewer HIGH: the initial {@code isAlreadyOnWaitlist} check in
     * {@link #join} and this insert are two separate steps, not one atomic
     * operation - two concurrent join attempts from the SAME user (a
     * double-click, or a client-retried POST) can both pass that check
     * before either commits, and then both land in THIS loop. The DB's
     * duplicate-join unique indexes (V17, keyed on user/event/ticketType,
     * not on position) reject the second one's insert too, every single
     * retry, indistinguishably from a genuine lost-position-race
     * {@code DataIntegrityViolationException} - left unhandled, that
     * doomed loop would burn all {@value #MAX_POSITION_RETRY_ATTEMPTS}
     * attempts and then surface an unhandled 500, not the 409 a duplicate
     * join should produce. Re-checking {@code isAlreadyOnWaitlist} inside
     * the catch (in a fresh read, now that the other side's insert has
     * definitely either committed or lost the race itself) distinguishes
     * the two cases correctly: a real duplicate now sees its own row and
     * gets a proper 409; a genuine position-race retry sees nothing and
     * loops again.
     */
    private WaitlistEntry saveWithRetriedPosition(UUID eventId, UUID ticketTypeId, UUID userId) {
        for (int attempt = 0; attempt < MAX_POSITION_RETRY_ATTEMPTS; attempt++) {
            try {
                return positionAssigner.assign(eventId, ticketTypeId, userId);
            } catch (DataIntegrityViolationException e) {
                if (isAlreadyOnWaitlist(eventId, ticketTypeId, userId)) {
                    throw new ConflictException("You are already on this waitlist");
                }
                // Genuinely lost the position race - loop retries with a
                // freshly recounted position in a brand-new transaction.
            }
        }
        throw new IllegalStateException("Unable to assign a waitlist position after " + MAX_POSITION_RETRY_ATTEMPTS + " attempts");
    }

    @Override
    public void notifyNextInLineIfAvailable(UUID eventId, UUID ticketTypeId) {
        // Each scope is caught independently (not one try/catch around both
        // calls) - a failure notifying the ticket-type-specific waiter must
        // not prevent the event-general waiter from still being offered
        // their spot, and vice versa. These are two unrelated people.
        safelyNotifyNextForScope(eventId, ticketTypeId);
        // Restocking one specific ticket type always ends the event's
        // overall "generally sold out" state (requireEventGenerallySoldOut
        // requires EVERY ticket type to be sold out) - so an event-general
        // waiter may now also be owed an offer.
        safelyNotifyNextForScope(eventId, null);
    }

    private void safelyNotifyNextForScope(UUID eventId, UUID ticketTypeId) {
        try {
            notifyNextForScope(eventId, ticketTypeId);
        } catch (RuntimeException e) {
            log.error("Failed to notify next waitlisted user for event {} ticketType {}", eventId, ticketTypeId, e);
        }
    }

    private void notifyNextForScope(UUID eventId, UUID ticketTypeId) {
        List<WaitlistEntry> matches = ticketTypeId != null
                ? waitlistEntryRepository.findNextNotNotifiedForTicketTypeForUpdate(eventId, ticketTypeId, Pageable.ofSize(1))
                : waitlistEntryRepository.findNextNotNotifiedEventGeneralForUpdate(eventId, Pageable.ofSize(1));
        matches.stream().findFirst().ifPresent(entry -> {
            Instant now = Instant.now();
            entry.setNotifiedAt(now);
            entry.setOfferExpiresAt(now.plus(offerWindowHours, ChronoUnit.HOURS));
            waitlistEntryRepository.save(entry);
            notificationService.notify(entry.getUserId(), NotificationType.WAITLIST_AVAILABILITY, "WaitlistEntry", entry.getId());
        });
    }
}
