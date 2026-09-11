package com.junaldadlawan.event_ticketing_api.waitlist.service;

import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import com.junaldadlawan.event_ticketing_api.waitlist.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Isolated transactional-boundary helper for a single count-then-insert
 * position-assignment attempt, deliberately kept as a separate Spring bean
 * (not inlined into {@code WaitlistServiceImpl}) so its {@code
 * REQUIRES_NEW} method actually gets proxied - Spring AOP self-invocation
 * wouldn't apply propagation semantics if this lived on the same class that
 * calls it in a loop. Mirrors {@code CheckoutIdempotencyKeyManager}/{@code
 * ResalePurchaseIdempotencyKeyManager}'s "separate REQUIRES_NEW bean"
 * structure, but NOT their internal catch-and-continue pattern - see below
 * for why that doesn't work here.
 * <p>
 * {@code REQUIRES_NEW} is necessary but not sufficient by itself. Two
 * failed approaches, both found by a genuine concurrent {@code
 * ExecutorService} test against real Postgres during Phase 9 QA (see
 * {@code testing/waitlist-test-results.md}'s Findings section):
 * <ol>
 *   <li>Retrying the insert in the SAME transaction after catching {@link
 *   DataIntegrityViolationException}: on Postgres, once one statement in a
 *   transaction fails, the WHOLE transaction is marked aborted at the
 *   database level - every subsequent statement fails with "current
 *   transaction is aborted", even though the Java-level exception was
 *   already caught.</li>
 *   <li>Moving each attempt into its own {@code REQUIRES_NEW} transaction
 *   but still catching {@link DataIntegrityViolationException} INSIDE that
 *   same transactional method and returning normally: Hibernate marks its
 *   own transaction rollback-only the moment the flush fails (independent
 *   of whether the Java exception is caught), so when the method returns
 *   normally afterward, Spring's transaction interceptor tries to commit
 *   and throws {@code UnexpectedRollbackException} instead.</li>
 * </ol>
 * The fix that actually works: this method does NOT catch the exception at
 * all - it lets {@link DataIntegrityViolationException} propagate straight
 * out of the {@code REQUIRES_NEW} boundary, so Spring's transaction
 * interceptor rolls back this one poisoned attempt cleanly (the normal,
 * supported "exception thrown from a transactional method" path). The
 * catch-and-retry then happens one level up, in {@code
 * WaitlistServiceImpl.saveWithRetriedPosition}, wrapping the CALL to this
 * method rather than living inside it - each retry is therefore a brand
 * new method invocation, a brand new {@code REQUIRES_NEW} transaction, and
 * a brand new (unpoisoned) persistence context.
 */
@Component
@RequiredArgsConstructor
public class WaitlistPositionAssigner {

    private final WaitlistEntryRepository waitlistEntryRepository;

    /**
     * One attempt: recount the (eventId, ticketTypeId) scope and insert at
     * count+1. Throws {@link DataIntegrityViolationException} (uncaught) if
     * this attempt lost the position race to a concurrent insert - the DB's
     * partial unique index on position is the actual backstop; the caller
     * is responsible for catching this and retrying with a freshly
     * recounted position in a new call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WaitlistEntry assign(UUID eventId, UUID ticketTypeId, UUID userId) {
        long currentCount = ticketTypeId != null
                ? waitlistEntryRepository.countByEventIdAndTicketTypeId(eventId, ticketTypeId)
                : waitlistEntryRepository.countByEventIdAndTicketTypeIdIsNull(eventId);
        WaitlistEntry entry = WaitlistEntry.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .userId(userId)
                .position((int) currentCount + 1)
                .build();
        return waitlistEntryRepository.saveAndFlush(entry);
    }
}
