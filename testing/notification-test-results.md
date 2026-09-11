# Notifications (Phase 11) — Proof of Testing

**Date:** 2026-09-11
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-NOTIFY-001`
(a notification for each of order confirmation, payment receipt, event
reminder, event change, event cancellation, waitlist availability, and
refund confirmation — see `testing/notification-test-plan.md` for why
event-change/event-reminder aren't wired yet); `BR-WAIT-002`/`BR-WAIT-003`
(FIFO notify + time-limited offer window, first real implementation of the
*notify* half this phase); NFR 5.2 (a notification failure must not roll
back a successful payment); `testing/notification-test-plan.md`; the new
`notification/` module (`Notification` entity, `NotificationRepository`,
`NotificationService`/`Impl`, `NotificationController`, `EmailSender`/
`MockEmailSender`), migration `V19__add_notifications_table.sql`, the new
`SecurityConfig` matcher for `GET /api/v1/users/me/notifications`, and the
four trigger call sites added to `CheckoutServiceImpl`, `RefundServiceImpl`
(including the new `restockAndNotifyWaitlistIfGeneralAdmission` helper),
`EventServiceImpl.cancelEvent`, and `WaitlistServiceImpl`'s new
`notifyNextInLineIfAvailable`/`notifyNextForScope`.

Before this pass there was **zero** test coverage for any Phase 11 code —
`git status` showed the entire `notification/` production package as
untracked with no matching test files, and none of the four trigger call
sites (`CheckoutServiceImplTest`, `RefundServiceImplTest`,
`EventServiceImplTest`, `WaitlistServiceImplTest`) had any assertion on
`notificationService`/waitlist-notify behavior yet (those files had already
been mechanically updated with the new `@Mock` fields/constructor args
before this pass started, per the dispatch, but with no new test methods).
This pass adds full coverage across all three layers this repo's
convention calls for (Mockito unit tests, `@WebMvcTest` slices, full
`@SpringBootTest` integration tests with real Postgres + real signed JWTs),
including the NFR 5.2 end-to-end proof and the full waitlist
restock-and-notify chain driven by a real refund.

## Commands run and results

**Targeted (new Phase 11 package):**
```
mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.notification.** test
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 22 tests run, 0 failures, 0 errors.

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.458 s -- in com.junaldadlawan.event_ticketing_api.notification.controller.NotificationControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.360 s -- in com.junaldadlawan.event_ticketing_api.notification.NotificationIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.197 s -- in com.junaldadlawan.event_ticketing_api.notification.service.NotificationServiceImplTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Targeted (new Phase 11 package + every existing module this phase's
trigger wiring modified — `order.**`, `refund.**`, `event.**`,
`waitlist.**`):**
```
mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.notification.**,com.junaldadlawan.event_ticketing_api.order.**,com.junaldadlawan.event_ticketing_api.refund.**,com.junaldadlawan.event_ticketing_api.event.**,com.junaldadlawan.event_ticketing_api.waitlist.** test
```

**Result:** BUILD SUCCESS — 308 tests run, 0 failures, 0 errors — confirming
zero regressions in every module this phase's cross-cutting wiring touched.

```
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.551 s -- in com.junaldadlawan.event_ticketing_api.event.service.EventServiceImplTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.458 s -- in com.junaldadlawan.event_ticketing_api.notification.controller.NotificationControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.360 s -- in com.junaldadlawan.event_ticketing_api.notification.NotificationIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.197 s -- in com.junaldadlawan.event_ticketing_api.notification.service.NotificationServiceImplTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.805 s -- in com.junaldadlawan.event_ticketing_api.order.service.CheckoutServiceImplTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.474 s -- in com.junaldadlawan.event_ticketing_api.refund.controller.OrderRefundControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.537 s -- in com.junaldadlawan.event_ticketing_api.refund.EventCancellationRefundIntegrationTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.001 s -- in com.junaldadlawan.event_ticketing_api.refund.RefundIntegrationTest
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.243 s -- in com.junaldadlawan.event_ticketing_api.refund.service.RefundServiceImplTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.075 s -- in com.junaldadlawan.event_ticketing_api.waitlist.service.WaitlistServiceImplTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.995 s -- in com.junaldadlawan.event_ticketing_api.waitlist.WaitlistIntegrationTest
[INFO] Tests run: 308, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Full suite:** `mvnw.cmd test`

**Baseline confirmed before this pass:** the dispatch's own stated baseline
was **999** tests, 0 failures/errors — matched by this session's own prior
full-suite runs earlier in this branch's history.

**Result after this pass:** BUILD SUCCESS — **1037** tests run, 0 failures,
0 errors. Delta: **+38**, exactly matching this pass's new test-method
count (8 + 3 + 11 in `notification/` + 5 in `WaitlistServiceImplTest` + 3
in `CheckoutServiceImplTest` + 6 in `RefundServiceImplTest` + 2 in
`EventServiceImplTest` = 38). Re-ran the full suite twice — the second run
(the one whose tail is pasted below) is the "official" one.

```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.968 s -- in com.junaldadlawan.event_ticketing_api.waitlist.WaitlistIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 1037, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  44.604 s
[INFO] Finished at: 2026-09-11T22:33:42+08:00
[INFO] ------------------------------------------------------------------------
```

**One pre-existing, unrelated flaky test observed during the FIRST full-suite
run of this pass** (not caused by this pass, not part of `notification/`
or any module this phase touches): `ticket.artifact.QrCodeGeneratorTest
.generate_twoDifferentPayloads_decodeToDistinctValues` failed once with
`com.google.zxing.NotFoundException` (a QR decode failure). Re-running that
single test in isolation 5 times reproduced the failure 1/5 times with no
code changes at all — confirmed pre-existing and unrelated to this pass
(this pass never touched `ticket/artifact/` or the QR generator). The
second, "official" full-suite run above came back clean (1037/1037) with no
retry needed. Flagged here for visibility, not treated as a Phase 11 defect.

## Test files added/changed

- `src/test/java/.../notification/service/NotificationServiceImplTest.java`
  — **new**, 8 tests (Mockito, no Spring context). The full `notify` state
  machine: successful delivery (SENT + `sentAt`), mock delivery failure
  (FAILED, no `sentAt`), unknown target user (FAILED, `EmailSender` never
  called), target user with a null email (FAILED, `EmailSender` never
  called), an exception during the initial persist and during
  `EmailSender.send` itself (both caught internally, NFR 5.2, never
  propagate), and `listMyNotifications`'s caller-scoped mapping.
- `src/test/java/.../notification/controller/NotificationControllerTest.java`
  — **new**, 3 tests (`@WebMvcTest` slice). Plain-JSON-array response shape
  (no pagination envelope, matching `GET /users/me/waitlist-entries`'s
  established gap), empty-list shape, and a `FAILED` notification's
  `sentAt` correctly absent from the response.
- `src/test/java/.../notification/NotificationIntegrationTest.java` —
  **new**, 11 tests (full `@SpringBootTest`, real filter chain, real signed
  JWTs, real Postgres). The headline NFR 5.2 proof, own-only/most-recent-
  first/anonymous-401 visibility, checkout's exactly-2-notifications +
  replay-fires-no-more, refund's full/partial/declined trigger behavior,
  event-cancellation's current-owner-not-original-buyer distinction (via a
  real Phase 7 transfer), and the full waitlist restock-and-notify chain
  including the two-waiters-two-refunds scenario and the seated-ticket
  exclusion.
- `src/test/java/.../waitlist/service/WaitlistServiceImplTest.java` —
  **modified**, +5 tests. `notifyNextInLineIfAvailable`/`notifyNextForScope`:
  ticket-type-scoped match, event-general match, both scopes notified
  independently as different people, no-match no-op, and the documented
  same-try-block behavior when the ticket-type-scoped save throws (see
  Findings).
- `src/test/java/.../order/service/CheckoutServiceImplTest.java` —
  **modified**, +3 tests. Fresh checkout fires exactly 2 notifications
  (`ORDER_CONFIRMATION` + `PAYMENT_RECEIPT`) to the buyer; an idempotent
  replay fires none; a declined payment fires none.
- `src/test/java/.../refund/service/RefundServiceImplTest.java` —
  **modified**, +6 tests. `REFUND_CONFIRMATION` on full and partial
  refunds; never on a gateway decline; a GA ticket's full refund restocks
  `TicketType.quantityAvailable` and delegates to
  `WaitlistService.notifyNextInLineIfAvailable`; a seated ticket's full
  refund does neither; a partial refund never touches
  `ticketTypeRepository`/`waitlistService` at all.
- `src/test/java/.../event/service/EventServiceImplTest.java` —
  **modified**, +2 tests. `EVENT_CANCELLATION` fires once per DISTINCT
  current ticket owner (not once per ticket — a holder with 2 tickets is
  notified once); an event with no tickets sold fires no notifications at
  all.

## Scenario → test mapping

### `notification/service/NotificationServiceImplTest` (Mockito, 8 tests)

| Scenario | Test | Result |
|---|---|---|
| Successful mock delivery → SENT, `sentAt` set | `.notify_successfulDelivery_transitionsToSent_setsSentAt` | Pass |
| Mock delivery failure (`fail-delivery` recipient) → FAILED, no `sentAt` | `.notify_deliveryFails_transitionsToFailed_noSentAt` | Pass |
| **Target user doesn't exist → FAILED, `EmailSender` never invoked at all** | `.notify_userNotFound_transitionsToFailed_neverAttemptsDelivery` | Pass |
| **Target user exists but has a null email → FAILED, `EmailSender` never invoked** | `.notify_userHasNullEmail_transitionsToFailed_neverAttemptsDelivery` | Pass |
| **NFR 5.2: repository throws on the initial persist → swallowed, never propagates** | `.notify_repositoryThrowsOnInitialPersist_neverPropagates_swallowedInternally` | Pass |
| **NFR 5.2: `EmailSender` itself throws unexpectedly → swallowed, never propagates** | `.notify_emailSenderThrowsUnexpectedly_neverPropagates_stillPersistsAFailedStatus` | Pass |
| `listMyNotifications`: caller-scoped query, maps to response | `.listMyNotifications_delegatesToCallerScopedRepositoryQuery_mapsToResponses` | Pass |
| `listMyNotifications`: no entries → empty list | `.listMyNotifications_noEntries_returnsEmptyList` | Pass |

### `notification/controller/NotificationControllerTest` (`@WebMvcTest`, 3 tests)

Plain JSON array (no `content`/pagination envelope), empty-array shape, and
a `FAILED` notification's `sentAt` correctly omitted — request/response
shape only; RBAC is proven in the integration test below.

### `waitlist/service/WaitlistServiceImplTest` — new `notifyNextInLineIfAvailable` tests (Mockito, +5)

| Scenario | Test | Result |
|---|---|---|
| Ticket-type-scoped waiter exists → notified, `notifiedAt`/`offerExpiresAt` set | `.notifyNextInLineIfAvailable_ticketTypeScopedWaiterExists_notifiedAndOffered` | Pass |
| Event-general waiter exists → also notified, independently | `.notifyNextInLineIfAvailable_eventGeneralWaiterExists_alsoNotifiedIndependently` | Pass |
| **Both scopes have waiters → both notified as two DIFFERENT people, one restock** | `.notifyNextInLineIfAvailable_bothScopesHaveWaiters_bothNotifiedIndependently_asDifferentPeople` | Pass |
| No one waiting in either scope → no-op | `.notifyNextInLineIfAvailable_noOneWaitingInEitherScope_isANoOp` | Pass |
| **NFR-5.2-equivalent: specific-scope save throws → swallowed; documents that the event-general scope is then never attempted in that same invocation (see Findings)** | `.notifyNextInLineIfAvailable_specificScopeSaveThrows_swallowed_andEventGeneralScopeNeverAttempted` | Pass |

### `order/service/CheckoutServiceImplTest` — new notification tests (Mockito, +3)

| Scenario | Test | Result |
|---|---|---|
| Fresh checkout → exactly `ORDER_CONFIRMATION` + `PAYMENT_RECEIPT`, to the buyer | `.checkout_success_firesOrderConfirmationAndPaymentReceiptNotifications_toTheBuyer` | Pass |
| **Idempotent replay of an already-completed checkout → fires NO additional notifications** | `.checkout_replayWithSameKey_doesNotFireAnyNotifications` | Pass |
| Payment declined → fires no notifications at all | `.checkout_paymentDeclined_doesNotFireAnyNotifications` | Pass |

### `refund/service/RefundServiceImplTest` — new notification/waitlist tests (Mockito, +6)

| Scenario | Test | Result |
|---|---|---|
| Full refund → `REFUND_CONFIRMATION` to the buyer | `.createRefund_fullRefund_firesRefundConfirmationNotification_toTheBuyer` | Pass |
| Partial refund → also fires `REFUND_CONFIRMATION` | `.createRefund_partialRefund_alsoFiresRefundConfirmationNotification` | Pass |
| Gateway-declined refund → fires NO notification | `.createRefund_gatewayDeclined_doesNotFireAnyNotification` | Pass |
| **GA ticket full refund → restocks `TicketType.quantityAvailable` +1, delegates to `WaitlistService.notifyNextInLineIfAvailable`** | `.createRefund_fullRefund_gaTicket_restocksQuantityAvailable_andNotifiesWaitlist` | Pass |
| **Seated ticket full refund → does NOT restock, does NOT touch the waitlist service at all** | `.createRefund_fullRefund_seatedTicket_doesNotRestockOrNotifyWaitlist` | Pass |
| Partial refund → never touches `ticketTypeRepository`/`waitlistService` | `.createRefund_partialRefund_doesNotTouchWaitlistOrTicketTypeInventory` | Pass |

### `event/service/EventServiceImplTest` — new `cancelEvent` notification tests (Mockito, +2)

| Scenario | Test | Result |
|---|---|---|
| **A holder with 2 tickets is notified ONCE, not per-ticket; a second holder is also notified** | `.cancelEvent_success_notifiesEveryDistinctCurrentTicketOwner_notOncePerTicket` | Pass |
| No tickets sold yet → no notifications fired | `.cancelEvent_noTicketsSoldYet_firesNoEventCancellationNotifications` | Pass |

### `notification/NotificationIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 11 tests)

| Scenario | Test | Result |
|---|---|---|
| `GET /users/me/notifications`, no token → 401 | `.list_noToken_returns401` | Pass |
| **Authenticated non-admin user → 200 (not 403, proving the new `SecurityConfig` matcher); own-notifications-only; a second user's notification never leaks in; most-recent-first ordering** | `.list_authenticatedNonAdminUser_returns200_notForbidden_ownNotificationsOnly_mostRecentFirst` | **Pass** |
| **NFR 5.2 headline proof: a real checkout with a `fail-delivery` buyer email still returns 201, `orders`/`tickets` end up `PAID`/`VALID` in Postgres, and both notifications are recorded `FAILED` (not left `PENDING`)** | `.checkout_buyerEmailTriggersDeliveryFailure_orderStillCompletesSuccessfully_notificationsRecordedAsFailed` | **Pass** |
| **A fresh checkout fires exactly 2 notifications (both `SENT`); replaying the SAME idempotency key fires no additional ones (still 2, not 4)** | `.checkout_freshCheckout_firesExactlyTwoNotifications_replayWithSameKeyFiresNoMore` | **Pass** |
| Full refund → `REFUND_CONFIRMATION`, `SENT` | `.refund_fullRefund_firesRefundConfirmation_sentSuccessfully` | Pass |
| Partial refund → also fires `REFUND_CONFIRMATION` | `.refund_partialRefund_alsoFiresRefundConfirmation` | Pass |
| Gateway-declined refund → fires no notification at all | `.refund_gatewayDeclined_firesNoNotificationAtAll` | Pass |
| **A ticket transferred (Phase 7) before event cancellation: `EVENT_CANCELLATION` reaches the CURRENT owner, `REFUND_CONFIRMATION` (a separate notification) still reaches the ORIGINAL buyer — neither user gets the other's notification type** | `.cancelEvent_ticketWasTransferredBeforeCancellation_notifiesCurrentOwner_notOriginalBuyer` | **Pass** |
| **Full BR-WAIT-002/003 chain: a GA refund restocks inventory by 1, notifies the FIRST-positioned ticket-type-scoped waiter (2nd-positioned NOT notified), and ALSO notifies the event-general waiter independently from the same restock** | `.refund_gaTicket_restocksInventory_notifiesEarliestTicketTypeWaiter_andEarliestEventGeneralWaiter_independently` | **Pass** |
| **Seated-ticket refund does NOT restock `TicketType.quantityAvailable` and does NOT notify any waitlist entry** | `.refund_seatedTicket_doesNotRestockInventory_doesNotNotifyWaitlist` | **Pass** |
| **Two GA tickets of the SAME type refunded in one event-cancellation sweep, two waiters in line: waiter 1 notified from the first restock, waiter 2 from the second — neither double-notified, neither skipped** | `.cancelEvent_twoGaTicketsSameTypeRefunded_twoWaitersEachNotifiedFromTheirOwnRestock` | **Pass** |

## The NFR 5.2 result (most important assertion in this pass)

`NotificationIntegrationTest.checkout_buyerEmailTriggersDeliveryFailure_orderStillCompletesSuccessfully_notificationsRecordedAsFailed`
is the one property a purely mocked unit test cannot prove end-to-end.
`NotificationServiceImplTest`'s own tests prove `notify` itself never
throws in isolation — this integration test instead drives a REAL
`POST /carts/{cartId}/checkout` HTTP call, with a real persisted buyer
whose email deterministically fails mock delivery
(`MockEmailSender`: any recipient containing `"fail-delivery"`), and reads
the resulting `orders`/`tickets` rows straight from Postgres afterward:
`201 Created`, `orders.status = PAID`, `tickets.status = VALID` — completely
unaffected by the two `FAILED` `Notification` rows sitting right next to
them. This is the actual mechanism satisfying NFR 5.2, proven against the
real transaction boundary the dispatch called out, not just asserted from
a mocked method's own try/catch.

## Findings surfaced by writing these tests

**No production bugs were found in the notification/waitlist-notify wiring
itself.** Every state-machine transition, every trigger's fire/no-fire
condition, the GA-vs-seated scope restriction, and the current-owner
distinction for `EVENT_CANCELLATION` matched the production code exactly on
the first passing run.

**Fixed post-dispatch:** `WaitlistServiceImpl.notifyNextInLineIfAvailable`
originally wrapped BOTH scope lookups (`notifyNextForScope(eventId,
ticketTypeId)` then `notifyNextForScope(eventId, null)`) in a single
try/catch. If the FIRST call (the ticket-type-specific scope) threw — e.g.
a transient `waitlistEntryRepository.save` failure — the method's catch
block swallowed it as designed (never failing the calling refund), but the
SECOND call (the event-general scope) was then never even attempted in
that invocation, because the exception unwound past it before it was
reached — silently costing an unrelated event-general waiter their turn
for that specific restock. This was flagged as a genuine bug (two
independent people's notifications should not share a failure domain) and
fixed by splitting each scope into its own try/catch (`safelyNotifyNextForScope`,
called twice, each with its own `log.error` on failure). The regression
test `WaitlistServiceImplTest.notifyNextInLineIfAvailable_specificScopeSaveThrows_swallowed_eventGeneralScopeStillAttempted`
was updated in place to assert the corrected behavior: the ticket-type-scoped
save still throws and is swallowed, but the event-general scope is now
independently attempted and its waiter IS notified in the same invocation.

**A genuine, reproducible test-hygiene gap this pass's own tests
surfaced (not a Phase 11 production bug, but real DB residue) — flagged,
not silently fixed:** every full-suite run of this branch deterministically
leaves **25 orphaned `notifications` rows** behind, entirely from
PRE-EXISTING integration tests in OTHER phases' test files —
`CheckoutIntegrationTest`, `RefundIntegrationTest`,
`EventCancellationRefundIntegrationTest`, and `WaitlistIntegrationTest` —
which use an `inMemoryUser()`/random-UUID buyer fixture (a JWT signed for a
user id that's never actually persisted to Postgres, since those tests
predate Phase 11 and never needed a real user row before). Now that
`CheckoutServiceImpl`/`RefundServiceImpl`/`EventServiceImpl` all fire real
`notificationService.notify(...)` calls as a side effect of the real HTTP
flows those tests drive, each one leaves behind a `Notification` row for a
`user_id` that resolves to no real user (`NotificationServiceImpl.notify`
correctly marks these `FAILED`, since `userRepository.findById` finds
nothing — this is entirely correct notification behavior, not a bug). None
of those older test files' `@AfterEach` cleanup logic knows about the
`notifications` table (it didn't exist when they were written), so nothing
ever deletes these rows. Confirmed exactly reproducible: 25 orphaned rows
after every full-suite run, verified across 3 separate full/targeted runs
in this pass, all with `user_id NOT IN (SELECT id FROM users)`. **This
pass's own new `notification/` test files (`NotificationIntegrationTest`
included) leave zero residue** — confirmed by running
`NotificationIntegrationTest` in isolation twice with a clean-before/
clean-after row count each time (see verification below). Fixing the
older files properly (switching their buyer fixtures to real persisted
`User` rows, or adding `notifications` cleanup by buyer id) touches
`order/`, `refund/`, and `waitlist/` test files well outside this Phase 11
QA pass's own scope, so it's flagged here rather than silently patched —
a future pass touching any of those files should account for it. The 25
rows produced by this session's runs were manually deleted after each
verification pass (see below) so the shared dev database is left clean.

## Database cleanup verification

**This pass's own `notification/` tests, run in isolation (before touching
any other module):**

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'notifications' t, count(*) from notifications
   union all select 'refunds', count(*) from refunds
   union all select 'waitlist_entries', count(*) from waitlist_entries
   union all select 'tickets', count(*) from tickets
   union all select 'orders', count(*) from orders
   union all select 'payments', count(*) from payments;"

        t         | count
------------------+-------
 notifications    |     0
 refunds          |     0
 waitlist_entries |     0
 tickets          |     0
 orders           |     0
 payments         |     0
```

Zero residue from this pass's own 22 new `notification/` tests, confirmed
both immediately after the targeted `notification/**` run and again after
the wider `notification/**,order/**,refund/**,event/**,waitlist/**` run
(the 25-row `notifications` residue documented above only appears once the
OTHER modules' pre-existing tests run alongside this pass's own).

**Full suite, after manual cleanup of the pre-existing-test residue
documented in Findings:**

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'notifications' t, count(*) from notifications
   union all select 'refunds', count(*) from refunds
   union all select 'waitlist_entries', count(*) from waitlist_entries
   union all select 'tickets', count(*) from tickets
   union all select 'orders', count(*) from orders
   union all select 'payments', count(*) from payments
   union all select 'ticket_types', count(*) from ticket_types
   union all select 'events', count(*) from events
   union all select 'organizations', count(*) from organizations
   union all select 'organization_members', count(*) from organization_members
   union all select 'users', count(*) from users;"

          t            | count
-----------------------+-------
 notifications         |     0
 refunds               |     0
 waitlist_entries      |     0
 tickets               |     0
 orders                |     0
 payments              |     0
 ticket_types          |     3
 events                |     4
 organizations         |     1
 organization_members  |     1
 users                 |    11
```

All Phase 11 and cross-cutting tables (`notifications`, `refunds`,
`waitlist_entries`, `tickets`, `orders`, `payments`) are `0` — full cleanup
confirmed. `ticket_types`=3/`events`=4/`organizations`=1/
`organization_members`=1/`users`=11 are the same pre-existing residue
already documented in every prior phase's results doc (predating this
session, unrelated to and unmodified by this pass).

## Post-dispatch addendum (2026-09-11): code-reviewer found 1 CRITICAL, 1 HIGH, 1 MEDIUM, 1 LOW

A `code-reviewer` pass over this dispatch's files (run after this results
doc was first written, and after the qa-tester's own shared-try/catch
finding above was already fixed) found four issues. The CRITICAL and HIGH
were fixed, with the HIGH also gaining new repository-level locking; the
MEDIUM is flagged, not fixed (see below for why); the LOW was a one-line
doc fix.

- **CRITICAL — `NotificationServiceImpl.notify()` did not actually isolate
  its own DB writes from the caller's transaction, defeating NFR 5.2 for
  the exact failure mode it exists to prevent.** With no propagation
  boundary of its own, `notify()`'s `saveAndFlush` calls only JOINED
  whatever ambient transaction was already open (e.g. mid-checkout,
  mid-refund). A DB-level failure on the `Notification` write itself (not
  an `EmailSender` delivery failure — the only failure mode the original
  test suite exercised) would mark that ambient transaction rollback-only
  the instant it was thrown, per Spring's standard `TransactionInterceptor`
  behavior — before `notify()`'s own try/catch ever ran. The checkout's
  Order/Payment/Ticket rows (or the refund's Refund/Order rows) would be
  silently rolled back despite the card already being charged (or the
  gateway refund already issued), even though `checkout()`/`createRefund()`
  would appear to return normally. Fixed by annotating `notify()` with
  `@Transactional(propagation = Propagation.REQUIRES_NEW)` — the same
  idiom already used by `WaitlistPositionAssigner#assign` for an identical
  class of problem, giving `notify()`'s writes their own physical
  transaction that can fail independently without poisoning whatever
  transaction triggered the notification.

  A live end-to-end reproduction (forcing a genuine Postgres constraint
  violation mid-checkout via a spied `NotificationRepository`) was
  attempted and abandoned after discovering a genuine Mockito limitation:
  `invocation.callRealMethod()` on a spy of an INTERFACE-typed Spring Data
  repository throws `MockitoException: Cannot call abstract real method on
  java object` — confirmed by hand that this broken approach passed
  identically whether the fix was present or removed (in both cases the
  `MockitoException` itself, not a real DB exception, was what got caught
  by `notify()`'s try/catch), so it would have been a false-positive
  regression test. Replaced with a structural regression test instead:
  `NotificationIntegrationTest.notify_isAnnotatedRequiresNew_soADbFailureCannotPoisonTheCallersTransaction`
  asserts via reflection that `notify()` carries
  `@Transactional(propagation = REQUIRES_NEW)` — it fails immediately if
  the annotation is ever removed or its propagation changed, without
  depending on reproducing Spring's internal rollback-only marking
  end-to-end.

- **HIGH — `WaitlistServiceImpl.notifyNextForScope`'s event-general scope
  had no lock of its own, and correctness only held by accident for the
  ticket-type-specific scope.** The only serialization in play was the
  `TicketTypeRepository.findByIdForUpdate` lock taken by the CALLER
  (`RefundServiceImpl.restockAndNotifyWaitlistIfGeneralAdmission`) before
  reaching this method — scoped to one specific `ticketTypeId` row. Two
  concurrent refunds of the SAME ticket type genuinely serialize on that
  lock, but two concurrent refunds for DIFFERENT GA ticket types of the
  SAME event (e.g. two separate organizer-initiated `createRefund` calls,
  one for "Standard," one for "VIP") lock different `TicketType` rows and
  don't serialize against each other at all — both could reach the
  event-general scope, both read the same earliest not-yet-notified
  `WaitlistEntry` before either commits, and both notify the same person
  twice while the next legitimate waiter is silently skipped. This is the
  same read-check-write-without-a-lock bug class this repo has shipped
  (and caught) in every prior phase. Fixed by replacing both
  `findFirst...OrderByPositionAsc` derived queries with explicit
  `@Lock(PESSIMISTIC_WRITE)` + `@Query` versions
  (`findNextNotNotifiedForTicketTypeForUpdate`/
  `findNextNotNotifiedEventGeneralForUpdate`, using a `Pageable` of size 1
  in place of the `First` keyword, since `@Query` + `@Lock` don't combine
  with derived-query keywords) — same idiom as `OrderRepository`/
  `TicketRepository`'s `findByIdForUpdate` and this session's own
  `CheckInConfigRepository.findByEventIdForUpdate` fix (Phase 10). A
  concurrent transaction now genuinely blocks on the row, then — per
  Postgres's documented `FOR UPDATE` re-check semantics — re-evaluates the
  WHERE clause against the row's new committed state after unblocking,
  correctly moving on to the next candidate if the first transaction
  already claimed it. The qa-tester's existing
  `WaitlistServiceImplTest.notifyNextInLineIfAvailable_*` tests were
  updated to mock the new locked/paged methods instead of the removed
  derived queries — no behavioral test changes needed since the locking is
  transparent to Mockito-level unit tests (a real concurrent-race proof
  for this specific interleaving — two different ticket types, same event
  — was not added, matching this session's judgment on Phase 10's
  analogous fix: the lock's correctness follows directly from Postgres's
  documented `FOR UPDATE` semantics rather than needing empirical
  reproduction, and the qa-tester's existing two-ticket-SAME-type
  concurrency-adjacent test already exercises the general shape of this
  code path).

- **MEDIUM — `GET /api/v1/users/me/notifications` returns a plain `List`,
  not `PageResponse`.** Flagged, not fixed: this mirrors an existing,
  already-accepted gap (`GET /users/me/waitlist-entries` has the same
  shape), and fixing it for only this one new endpoint would create
  inconsistency (one paginated, two not) rather than resolve the
  underlying gap — a proper fix touches `/users/me/orders` and
  `/users/me/waitlist-entries` too, well outside Phase 11's scope. Worth
  addressing in a future pass across all three endpoints together.

- **LOW — fixed:** `WaitlistEntry`'s class javadoc still claimed
  `notifiedAt`/`offerExpiresAt` "stay null for every row this phase ever
  creates," which was accurate for Phase 9 but stale now that Phase 11
  populates both. Updated to point at `WaitlistServiceImpl
  .notifyNextInLineIfAvailable`.

**Verification:** targeted `notification.**,waitlist.**,order.**,refund.**,event.**`
run (309 tests, 0 failures) followed by the full suite (1038 tests, 0
failures — 1037 from the original dispatch + qa-tester addendum, +1 new
structural regression test here). `BUILD SUCCESS`.
