# Waitlist (Phase 9) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/Phase9-Waitlist`
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-WAIT-001`
(join-only-when-sold-out; `BR-WAIT-002`/`BR-WAIT-003` are out of scope, see
below); `docs/event-ticketing-api-use-cases.md` `UC-ATTND-07` (join a
waitlist); `testing/waitlist-test-plan.md`; the new `waitlist/` module
(`WaitlistEntry` entity, `WaitlistEntryRepository`,
`WaitlistService`/`Impl`, `WaitlistController`), migration
`V17__add_waitlist_entries_table.sql`, and the new `SecurityConfig`
matcher for `GET /api/v1/users/me/waitlist-entries`.

Before this pass there was **zero** test coverage for any Phase 9 code —
`git status` showed the entire `waitlist/` production package as untracked
with no matching test files. This pass adds full coverage across all
three layers this repo's convention calls for (Mockito unit tests,
`@WebMvcTest` slices, a full `@SpringBootTest` with real Postgres + real
JWTs including a genuine `ExecutorService` concurrency race), and along
the way found and fixed a real production concurrency bug in the
position-assignment retry loop (see Findings).

## Commands run and results

**Targeted (new Phase 9 package):**
```
mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.waitlist.** test
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 44 tests run, 0 failures, 0 errors.

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.833 s -- in com.junaldadlawan.event_ticketing_api.waitlist.controller.WaitlistControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.152 s -- in com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistPositionAssignerTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.202 s -- in com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistServiceImplTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.418 s -- in com.junaldadlawan.event_ticketing_api.waitlist.WaitlistIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

The concurrency test
(`WaitlistIntegrationTest.join_concurrentJoinsForSameScope_neverCollideOnPosition_allPositionsSequentialAndUnique`)
was additionally re-run 3 times in isolation to check for flakiness — all
3 passed (see Findings for why this test mattered).

**Full suite:** `mvnw.cmd test`

**Result:** BUILD SUCCESS — 865 tests run, 0 failures, 0 errors. The
pre-dispatch baseline (confirmed by running the full suite once before
writing any Phase 9 tests) was **821 tests**, not the 823 the dispatch
guessed — 44 new tests this pass, delta of +44, exactly matching the
targeted Phase 9 package's count. No regressions.

```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.926 s -- in com.junaldadlawan.event_ticketing_api.waitlist.WaitlistIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 865, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  36.263 s
[INFO] Finished at: 2026-09-11T20:01:11+08:00
[INFO] ------------------------------------------------------------------------
```

## Test files added/changed

- `src/test/java/.../waitlist/service/WaitlistServiceImplTest.java` —
  **new**, 16 tests (Mockito, no Spring context). The sold-out gate for
  both branches (specific ticket type, event-general including the
  zero-ticket-types edge case), the duplicate-join 409, delegation to
  `WaitlistPositionAssigner` with the exact scoped arguments, and the
  retry-until-success / exhausted-retries behavior of
  `saveWithRetriedPosition` against a mocked assigner.
- `src/test/java/.../waitlist/service/WaitlistPositionAssignerTest.java`
  — **new**, 5 tests (Mockito). The count+1 position math for both
  branches, and proof that a `DataIntegrityViolationException` from the
  insert propagates UNCAUGHT rather than being swallowed inside the
  `REQUIRES_NEW` method (see Findings for why this is load-bearing).
- `src/test/java/.../waitlist/controller/WaitlistControllerTest.java` —
  **new**, 9 tests (`@WebMvcTest` slice). Status-code mapping for both
  endpoints, the optional-request-body handling (`{}`, an omitted
  `ticketTypeId`, and a fully omitted body all still work), and proof
  that `GET /users/me/waitlist-entries` returns a plain JSON array (no
  `content`/pagination envelope).
- `src/test/java/.../waitlist/WaitlistIntegrationTest.java` — **new**, 14
  tests (full `@SpringBootTest`, real filter chain, real signed JWTs,
  real Postgres). The sold-out gate against real `TicketType` rows, the
  real sequential position sequence across multiple joiners, the
  `SecurityConfig` matcher's visibility split (anonymous 401 /
  authenticated-non-admin 200 / own-entries-only), and a genuine
  `ExecutorService` concurrency race.

**Production files touched (the bug fix, not scope creep — see
Findings):**

- `src/main/java/.../waitlist/service/WaitlistPositionAssigner.java` —
  **new** file, extracted from `WaitlistServiceImpl`.
- `src/main/java/.../waitlist/service/WaitlistServiceImpl.java` —
  **modified**, `saveWithRetriedPosition` now delegates each attempt to
  `WaitlistPositionAssigner` and catches at the call site instead of
  inline.

## Scenario → test mapping

### `waitlist/service/WaitlistServiceImplTest` (Mockito, 16 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown event → 404 | `.join_unknownEvent_throwsResourceNotFound` | Pass |
| Specific ticket type: unknown ticket type → 404 | `.join_specificTicketType_unknownTicketType_throwsResourceNotFound` | Pass |
| Specific ticket type: belongs to a different event → 400 | `.join_specificTicketType_belongsToDifferentEvent_throwsBadRequest` | Pass |
| Specific ticket type: still has availability → 409 | `.join_specificTicketType_stillHasAvailableQuantity_throwsConflict` | Pass |
| Specific ticket type: `quantityAvailable=0` → succeeds | `.join_specificTicketType_soldOut_zeroQuantity_succeeds` | Pass |
| **Event-general: zero ticket types at all → 409 ("nothing to be sold out of")** | `.join_eventGeneral_noTicketTypesAtAll_throwsConflict` | Pass |
| Event-general: one of several ticket types still available → 409 | `.join_eventGeneral_oneTicketTypeStillAvailable_throwsConflict` | Pass |
| Event-general: every ticket type sold out → succeeds | `.join_eventGeneral_allTicketTypesSoldOut_succeeds` | Pass |
| Duplicate join, specific ticket type → 409 | `.join_specificTicketType_alreadyOnWaitlist_throwsConflict` | Pass |
| Duplicate join, event-general → 409 | `.join_eventGeneral_alreadyOnWaitlist_throwsConflict` | Pass |
| Delegates to `WaitlistPositionAssigner` with exact (eventId, ticketTypeId, callerId) | `.join_delegatesToPositionAssigner_withExactEventTicketTypeAndCallerArguments` | Pass |
| **Two different ticket types in the same event each delegate independently (scoping proof)** | `.join_twoDifferentTicketTypesInSameEvent_eachDelegatesToPositionAssignerWithItsOwnTicketTypeId` | Pass |
| **Position-assigner loses the race once → retries → succeeds** | `.join_positionAssignerLosesRaceOnce_retriesAndSucceedsOnSecondAttempt` | Pass |
| **Position-assigner loses the race every attempt → `IllegalStateException` after exactly 10 attempts** | `.join_positionAssignerLosesRaceEveryAttempt_throwsIllegalStateException_afterMaxRetries` | Pass |
| `listMyEntries`: delegates to caller-scoped query, maps to response | `.listMyEntries_delegatesToCallerScopedRepositoryQuery_andMapsToResponses` | Pass |
| `listMyEntries`: no entries → empty list | `.listMyEntries_noEntries_returnsEmptyList` | Pass |

### `waitlist/service/WaitlistPositionAssignerTest` (Mockito, 5 tests)

| Scenario | Test | Result |
|---|---|---|
| Specific ticket type: position = current count + 1 | `.assign_specificTicketType_positionIsCurrentCountPlusOne` | Pass |
| Event-general: position = current count + 1 | `.assign_eventGeneral_positionIsCurrentCountPlusOne` | Pass |
| First-ever entry in a scope → position 1 | `.assign_firstEverEntryInScope_positionOne` | Pass |
| **`DataIntegrityViolationException` from the insert propagates uncaught (not swallowed)** | `.assign_insertLosesPositionRace_dataIntegrityViolation_propagatesUncaught` | Pass |
| Saved entry has the expected fields, `notifiedAt`/`offerExpiresAt` null | `.assign_savesEntryWithExpectedFields` | Pass |

### `waitlist/controller/WaitlistControllerTest` (`@WebMvcTest`, 9 tests)

| Scenario | Test | Result |
|---|---|---|
| Specific ticket type → 201 | `.join_specificTicketType_returns201` | Pass |
| **Omitted `ticketTypeId` (empty JSON body `{}`) → 201, `null` passed through** | `.join_omittedTicketTypeId_stillReturns201_andPassesNullThrough` | Pass |
| **Fully omitted request body → 201** | `.join_omittedBodyEntirely_stillReturns201` | Pass |
| Unknown event → 404 | `.join_unknownEvent_returns404` | Pass |
| Ticket type not belonging to event → 400 | `.join_ticketTypeNotBelongingToEvent_returns400` | Pass |
| Not sold out → 409 | `.join_notSoldOut_returns409` | Pass |
| Already on waitlist → 409 | `.join_alreadyOnWaitlist_returns409` | Pass |
| **`listMyEntries` → 200, plain JSON array, no pagination envelope** | `.listMyEntries_returns200_asPlainJsonArray_notPaginationWrapper` | Pass |
| `listMyEntries`: no entries → 200, empty array | `.listMyEntries_noEntries_returns200_withEmptyArray` | Pass |

### `waitlist/WaitlistIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 14 tests)

| Scenario | Test | Result |
|---|---|---|
| Specific ticket type sold out → 201, position 1, real DB row | `.join_specificTicketType_soldOut_returns201_withPositionOne` | Pass |
| Specific ticket type NOT sold out → 409 | `.join_specificTicketType_notSoldOut_returns409` | Pass |
| Event-general, all real ticket types sold out → 201 | `.join_eventGeneral_allTicketTypesSoldOut_returns201` | Pass |
| Event-general, one real ticket type still available → 409 | `.join_eventGeneral_oneTicketTypeStillAvailable_returns409` | Pass |
| Event-general, zero ticket types → 409 | `.join_eventGeneral_noTicketTypesAtAll_returns409` | Pass |
| Unknown event → 404 | `.join_unknownEvent_returns404` | Pass |
| No token → 401 | `.join_noToken_returns401` | Pass |
| Same user joins twice → second attempt 409 | `.join_sameUserTwice_secondAttemptReturns409` | Pass |
| **Position sequence: 3 real users join the same real waitlist in turn → positions 1, 2, 3** | `.join_positionSequence_multipleUsersJoinSameWaitlist_getSequentialPositions` | Pass |
| **Scoped independently: user A on GA-specific waitlist and user B on VIP-specific waitlist (same event) both get position 1** | `.join_positionScopedIndependently_specificTicketTypeVsEventGeneral_bothStartAtOne` | Pass |
| `GET /users/me/waitlist-entries`, no token → 401 | `.listMyEntries_noToken_returns401` | Pass |
| **`GET /users/me/waitlist-entries`, authenticated non-admin user → 200, not 403 (proves the new `SecurityConfig` matcher)** | `.listMyEntries_authenticatedNonAdminUser_returns200_notForbidden` | Pass |
| **Returns only the caller's own entries, oldest-joined-first; a second user's entry never leaks in** | `.listMyEntries_returnsOnlyCallersOwnEntries_oldestJoinedFirst` | Pass |
| **THE headline test: 8 real threads racing to join the SAME (event, ticketType) scope via a real `ExecutorService` + `CountDownLatch` against real Postgres → all 8 succeed (201), and the 8 resulting positions are exactly `{1,2,...,8}` with no duplicates and no gaps** | `.join_concurrentJoinsForSameScope_neverCollideOnPosition_allPositionsSequentialAndUnique` | **Pass** (3/3 on repeat isolated runs) |

## The concurrency headline result

`WaitlistIntegrationTest.join_concurrentJoinsForSameScope_neverCollideOnPosition_allPositionsSequentialAndUnique`
starts 8 real HTTP threads (real JWTs, real Postgres, `CountDownLatch`-synchronized
start, same idiom as `CheckoutIntegrationTest.concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment`)
all calling `POST /events/{eventId}/waitlist` for the identical
(event, ticketType) scope at the same instant. It asserts:

- **Every one of the 8 requests succeeds with 201** (unlike a checkout
  race where exactly one winner is expected, a waitlist join has no
  "only one can win" semantic — everyone who's genuinely eligible gets a
  position, they just can't collide on WHICH position).
- **The 8 persisted rows' positions are exactly `{1, 2, 3, 4, 5, 6, 7, 8}`**
  — no duplicate position, no gap, read directly from Postgres.

This test is what actually found the bug described below — the very
first version of this test failed deterministically (not flaky; failed
every single time) against the original implementation.

## Findings surfaced by writing these tests (production bug found and fixed)

**The bug:** `WaitlistServiceImpl.saveWithRetriedPosition`'s original
implementation counted the current scope, built a new `WaitlistEntry`,
called `waitlistEntryRepository.saveAndFlush(entry)`, and caught
`DataIntegrityViolationException` to retry — all inside the SAME
`@Transactional` method (`join`) and the SAME database transaction. This
looks correct in isolation and passed every sequential/mocked test, but
the concurrency test above failed deterministically with:

```
ERROR: current transaction is aborted, commands ignored until end of transaction block
```

**Root cause:** on Postgres, once any single statement inside a
transaction errors (here: the `uq_waitlist_entries_specific_position`
partial unique index violation from a lost race), Postgres marks the
*entire transaction* as aborted — not just that one statement. Every
subsequent statement on that same transaction fails with "current
transaction is aborted", **regardless of whether the Java-level exception
was already caught**. Catching `DataIntegrityViolationException` in Java
code does nothing to un-abort the underlying database transaction, so the
retry loop's second `saveAndFlush` attempt was guaranteed to fail every
single time past the first lost race — the retry loop looked like it
retried, but never actually could succeed under a real race.

**First fix attempt (also failed, different error):** moving the
count+insert into a separate `@Component` bean annotated
`@Transactional(propagation = Propagation.REQUIRES_NEW)` — mirroring the
established `CheckoutIdempotencyKeyManager`/`ResalePurchaseIdempotencyKeyManager`
pattern already in this codebase — but *still* catching
`DataIntegrityViolationException` inside that same `REQUIRES_NEW` method
and returning `Optional.empty()` normally. This also failed the
concurrency test, this time with:

```
org.springframework.transaction.UnexpectedRollbackException:
Transaction silently rolled back because it has been marked as rollback-only
```

This is a separate, subtler gotcha: Hibernate marks its own transaction
rollback-only the instant a flush fails, independent of whether the
surrounding Java code catches the translated exception. Returning
normally from a method whose transaction Hibernate has already flagged
rollback-only makes Spring's transaction interceptor discover the flag at
commit time and throw `UnexpectedRollbackException` instead of silently
succeeding.

**The actual fix:** `WaitlistPositionAssigner.assign` (the `REQUIRES_NEW`
method) does NOT catch `DataIntegrityViolationException` at all — it lets
it propagate straight out of the transactional boundary, which is
Spring's normal, fully-supported "exception thrown from a `@Transactional`
method" path and rolls back cleanly. The catch-and-retry loop lives one
level up, in `WaitlistServiceImpl.saveWithRetriedPosition`, wrapping the
*call* to `positionAssigner.assign(...)` rather than living inside it.
Each retry is therefore a brand-new method invocation → a brand-new
`REQUIRES_NEW` physical transaction → a brand-new, unpoisoned persistence
context. Verified: the concurrency test above now passes consistently
(4 total runs: 1 as part of the targeted suite, 3 additional isolated
re-runs), and the Postgres logs during the test run show real constraint
violations happening and being genuinely retried (not just a lucky
non-race), e.g.:

```
WARN ... org.hibernate.orm.jdbc.error : ERROR: duplicate key value violates unique constraint "uq_waitlist_entries_specific_position"
```
— followed by the test still passing, confirming the retry-after-a-real-lost-race
path is genuinely exercised, not just the never-collided happy path.

This is exactly the class of bug the dispatch's own framing called out
("Phase 7 and Phase 8 review passes both found real concurrency bugs that
sequential tests didn't catch") — a sequential/mocked test of the retry
loop (which this pass also has, in `WaitlistServiceImplTest`) proves the
loop's *logic* is correct in isolation, but only a genuine concurrent
test against real Postgres proves the *transactional plumbing* underneath
it actually works.

**Fix scope:** entirely within Phase 9's own files — one new class
(`WaitlistPositionAssigner`) and one modified method
(`WaitlistServiceImpl.saveWithRetriedPosition`), both in the `waitlist/`
module. No other production files were touched.

## Scenarios not covered (out of scope per the dispatch)

Two `waitlist-test-plan.md` scenarios remain genuinely unbuilt and are
left unchecked there, per the dispatch's explicit scope note:

- **"Waitlisted users are notified in the order they joined"** (BR-WAIT-002's
  notify half) — `notifiedAt` stays `null` for every row this phase ever
  creates; nothing in the codebase populates it. This depends on Phase 11
  (Notifications), which doesn't exist yet — there's no delivery mechanism
  to trigger a notification through.
- **"A notified user has a limited time window to purchase before it
  passes to the next person"** (BR-WAIT-003) — same reason;
  `offerExpiresAt` stays `null` for every row this phase ever creates.

Writing a test asserting either of these would either assert a
permanently-null field (proving nothing) or require building the actual
notification/offer-expiry trigger myself, which is out of scope for a
QA pass per this dispatch's explicit instruction.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'waitlist_entries' t, count(*) from waitlist_entries
   union all select 'ticket_types', count(*) from ticket_types
   union all select 'events', count(*) from events
   union all select 'organizations', count(*) from organizations
   union all select 'organization_members', count(*) from organization_members
   union all select 'users', count(*) from users;"

          t            | count
-----------------------+-------
 waitlist_entries      |     0
 ticket_types          |     3
 events                |     4
 organizations         |     1
 organization_members  |     1
 users                 |    11
```

`waitlist_entries` (the only brand-new Phase 9 table) is `0` — full
cleanup confirmed, no leftover rows from this pass's tests, including the
8-thread concurrency test (its `@AfterEach` sweeps every row scoped to
that test's `eventId`, not just individually-tracked ids, since the
concurrent joiners' rows aren't captured synchronously in the test body).
`ticket_types`=3/`events`=4/`organizations`=1/`organization_members`=1/
`users`=11 are the same pre-existing residue already documented in the
Phase 7/8 results docs (predating this session, unrelated to and
unmodified by this pass) — confirmed identical before and after this
pass's targeted 44-test run and the full 865-test suite run.

## Post-dispatch addendum (2026-09-11): code-reviewer found 2 HIGH, both fixed

A `code-reviewer` pass over this dispatch's files (run after this results
doc was first written) found two HIGH issues, both fixed and covered by a
new regression test:

- **HIGH — a same-user concurrent double-join leaked an unhandled 500
  instead of the intended 409.** `isAlreadyOnWaitlist` (the initial
  duplicate-join check in `join()`) and the eventual insert are two
  separate steps, not one atomic operation. Two concurrent join attempts
  from the SAME user (a double-click, or a client-retried POST) could both
  pass that check before either commits, then both land in
  `saveWithRetriedPosition`'s retry loop. The DB's duplicate-join unique
  indexes (V17, keyed on user/event/ticketType, not on `position`) reject
  the losing insert too, every single retry - `catch
  (DataIntegrityViolationException e)` couldn't distinguish "genuinely lost
  the position race" (retryable) from "duplicate user join" (never
  retryable), so it burned all 10 attempts and then threw an unhandled
  `IllegalStateException` (no handler in `GlobalExceptionHandler`) instead
  of the `ConflictException` a duplicate join should produce. This pass's
  own concurrency test used 8 *different* users racing for the same scope,
  so it never exercised the same-user race and didn't catch this. Fixed by
  re-checking `isAlreadyOnWaitlist` inside the catch, before retrying - by
  the time a `DataIntegrityViolationException` is caught, the conflicting
  side (whichever it was) has already committed, so a fresh read now
  correctly distinguishes the two cases. New test:
  `WaitlistServiceImplTest.join_positionAssignerFails_concurrentDuplicateJoinFromSameUserSinceCommitted_throwsConflict_doesNotExhaustRetries`.
- **HIGH — `WaitlistServiceImpl.join()` being `@Transactional` while
  calling a `REQUIRES_NEW` bean in a loop could stall or deadlock the
  connection pool under load.** Spring's transaction manager holds a
  physical connection checked out for an `@Transactional` method's entire
  duration; each `REQUIRES_NEW` call inside `saveWithRetriedPosition`'s
  loop *suspends* (doesn't release) that connection and acquires a
  *second* one for its own transaction. With `join()` itself
  `@Transactional`, every in-flight join could hold two connections
  simultaneously from the same pool - at high concurrency, every
  connection could end up checked out as an "outer" one while every thread
  waits for an "inner" one that can never free up. This pass's 8-thread
  test had margin below the default pool size (10) and didn't reliably
  trip it, so it went uncaught. Fixed by removing `@Transactional` from
  `join()` entirely - its reads have no atomicity requirement linking them
  to each other or to the eventual insert (that insert's own correctness
  is independently guaranteed by `WaitlistPositionAssigner`'s own
  transaction plus the retry loop), so there was never a real need for an
  outer transaction to begin with.

**Verification:** `mvnw.cmd compile`/`test-compile` clean after both
fixes. Targeted waitlist package re-run clean (the same 8-thread
concurrency test still passes, confirming the fix didn't regress the
different-user race proof). Full suite: **868 tests, 0 failures, 0
errors**.
