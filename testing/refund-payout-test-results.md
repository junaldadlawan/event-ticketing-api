# Refunds & Payouts (Phase 8) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/phase8-Refunds-&-Payouts`
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-PAY-001`–`006`
(no raw card data, organizer/admin-only refund initiation, refund-policy
gating, the three refund rule types, mandatory cancellation-triggered
refunds, payout tracking); `docs/event-ticketing-api-use-cases.md`
`UC-EVENT-07` (set an event's refund policy) and `UC-EVENT-08` (issue a
refund); `testing/refund-test-plan.md` and `testing/payout-test-plan.md`;
the new `refundpolicy/` module (`RefundPolicy` entity, repository,
`RefundPolicyService`/`Impl`, `EventRefundPolicyController`), the new
`refund/` module (`Refund` entity, repository, `RefundService`/`Impl`,
`OrderRefundController`), the new `payout/` module (`Payout` entity,
repository, `PayoutService`/`Impl`, `OrganizationPayoutController`),
migration `V16__add_refund_and_payout_tables.sql`, the new
`PaymentGatewayClient.refund`/`MockPaymentGatewayClient.refund` methods, the
new `TicketRepository.existsByEventIdAndOwnerId`, the new `SecurityConfig`
matcher for `GET /api/v1/events/*/refund-policy`, and `EventServiceImpl
.cancelEvent`'s new `RefundService.refundAllForEventCancellation` call.

Before this pass there was **zero** test coverage for any Phase 8 code —
`git status` showed the entire `refundpolicy/`, `refund/`, and `payout/`
production packages as untracked with no matching test files (the one
exception, `EventServiceImplTest`, had already been updated in this branch
before this pass started to mock the new `RefundService` dependency and add
`cancelEvent_success_triggersRefundAllForEventCancellation` — verified
compiling/passing as-is, not modified further here). This pass adds full
coverage across all three layers this repo's convention calls for (Mockito
unit tests, `@WebMvcTest` slices, full `@SpringBootTest` integration tests
with real Postgres + real JWTs) and proves the two headline correctness
properties the dispatch called out: (1) a real refund end-to-end against
real `Order`/`Payment`/`Ticket` rows, verified straight from Postgres, and
(2) cancelling a real event with a real paid order on it — via the real
`POST /events/{eventId}/cancel` HTTP endpoint, never calling any refund
endpoint directly — correctly refunds it, proving the `cancelEvent` →
`RefundService` wiring fires end-to-end.

## Commands run and results

**Targeted (all three new Phase 8 packages):**
```
mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.refundpolicy.**,com.junaldadlawan.event_ticketing_api.refund.**,com.junaldadlawan.event_ticketing_api.payout.** test
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 102 tests run, 0 failures, 0 errors.

```
[INFO] Running com.junaldadlawan.event_ticketing_api.payout.controller.OrganizationPayoutControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.583 s -- in com.junaldadlawan.event_ticketing_api.payout.controller.OrganizationPayoutControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.payout.service.PayoutServiceImplTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.292 s -- in com.junaldadlawan.event_ticketing_api.payout.service.PayoutServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refund.controller.OrderRefundControllerTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.781 s -- in com.junaldadlawan.event_ticketing_api.refund.controller.OrderRefundControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refund.EventCancellationRefundIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.384 s -- in com.junaldadlawan.event_ticketing_api.refund.EventCancellationRefundIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refund.RefundIntegrationTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.252 s -- in com.junaldadlawan.event_ticketing_api.refund.RefundIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refund.service.RefundServiceImplTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.526 s -- in com.junaldadlawan.event_ticketing_api.refund.service.RefundServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refundpolicy.controller.EventRefundPolicyControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.543 s -- in com.junaldadlawan.event_ticketing_api.refundpolicy.controller.EventRefundPolicyControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refundpolicy.RefundPolicyIntegrationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.587 s -- in com.junaldadlawan.event_ticketing_api.refundpolicy.RefundPolicyIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.refundpolicy.service.RefundPolicyServiceImplTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in com.junaldadlawan.event_ticketing_api.refundpolicy.service.RefundPolicyServiceImplTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Full suite:** `mvnw.cmd test`

**Baseline confirmed before this pass:** a clean `mvnw.cmd test` run against
the pre-existing (pre-Phase-8-test) codebase reported **717** tests, 0
failures/errors — the dispatch's own ballpark estimate ("around 718") was
essentially exact.

**Result after this pass:** BUILD SUCCESS — **819** tests run, 0 failures, 0
errors. Delta: **+102** (exactly the new Phase 8 package total above; every
new test is additive, nothing pre-existing was modified except
`EventServiceImplTest`, which had already been updated before this pass
started per the dispatch). Re-ran the full suite three times in a row
(once immediately after the targeted run, once as the "official" run, once
after fixing a cleanup-robustness issue described in Findings below) — all
three came back 819/819, 0 failures/errors, confirming zero flakiness
introduced by this pass.

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.253 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 819, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  38.643 s
[INFO] Finished at: 2026-09-11T18:30:13+08:00
[INFO] ------------------------------------------------------------------------
```

## Test files added

- `src/test/java/.../refundpolicy/service/RefundPolicyServiceImplTest.java`
  — **new**, 14 tests (Mockito, no Spring context). `get`'s synthesized
  `NO_REFUNDS` default and its wider visibility (organizer/owner, admin, OR
  a buyer with any ticket on the event), `update`'s owner/organizer/admin
  authorization matrix, upsert semantics, and the
  `DataIntegrityViolationException` race-retry path.
- `src/test/java/.../refund/service/RefundServiceImplTest.java` — **new**,
  28 tests (Mockito). The most important file in this pass: BR-PAY-002's
  organizer/admin-only gate (including the buyer-self-refund-attempt case
  explicitly), BR-PAY-003's three policy branches (`NO_REFUNDS`/no-row,
  `REFUNDABLE_UNTIL_N_DAYS` within/past window, `CUSTOM` always-allowed),
  the remaining-balance math (including a prior-partial-refund-then-another
  scenario reaching the full total), the full-refund-marks-tickets-REFUNDED
  vs. partial-refund-leaves-tickets-untouched split, the
  gateway-failure-still-creates-a-FAILED-refund-row behavior, and
  `refundAllForEventCancellation`'s policy-bypass + best-effort
  skip-on-failure + skip-already-refunded-or-cancelled-orders behavior.
- `src/test/java/.../payout/service/PayoutServiceImplTest.java` — **new**,
  6 tests (Mockito). Unknown-organization 404, owner/organizer/admin/
  stranger/cross-org authorization matrix, paginated mapping.
- `src/test/java/.../refundpolicy/controller/EventRefundPolicyControllerTest.java`
  — **new**, 7 tests (`@WebMvcTest` slice).
- `src/test/java/.../refund/controller/OrderRefundControllerTest.java` —
  **new**, 12 tests (`@WebMvcTest` slice) — including the "gateway declined
  still returns 201 with a FAILED body" status-mapping case (openapi.yaml:
  a refund attempt is "initiated", not guaranteed to succeed, so a declined
  gateway reversal is not itself an HTTP error).
- `src/test/java/.../payout/controller/OrganizationPayoutControllerTest.java`
  — **new**, 4 tests (`@WebMvcTest` slice) — paginated response shape,
  empty-result shape, 404/403 mapping.
- `src/test/java/.../refundpolicy/RefundPolicyIntegrationTest.java` —
  **new**, 13 tests (full `@SpringBootTest`, real filter chain, real signed
  JWTs, real Postgres). The full visibility matrix including the
  anonymous-401-vs-stranger-403 distinction on the new `SecurityConfig`
  matcher, and the buyer-with-a-ticket-but-no-org-role case.
- `src/test/java/.../refund/RefundIntegrationTest.java` — **new**, 14 tests
  (full `@SpringBootTest`, real filter chain, real Postgres). A real refund
  end-to-end (real `Order`/`Payment`/`Ticket` rows, real HTTP call, `orders`/
  `tickets`/`refunds` table state verified directly against Postgres
  afterward) for both full and partial refunds, the gateway-decline path,
  the full BR-PAY-002/003 authorization and policy-gate matrices, and the
  amount-validation (currency mismatch / exceeds remaining balance) cases.
- `src/test/java/.../refund/EventCancellationRefundIntegrationTest.java` —
  **new**, 4 tests (full `@SpringBootTest`, real filter chain, real
  Postgres). The headline BR-PAY-005 proof: cancelling a real event via the
  real `POST /events/{eventId}/cancel` endpoint (never touching any refund
  endpoint directly) with a real paid order on it, confirming
  `refunds`/`orders`/`tickets` end up correctly refunded even with **no**
  refund policy configured (and even with an explicit `NO_REFUNDS` policy
  configured) — proving the policy-bypass is real, not just documented in a
  comment — plus the best-effort multi-order and
  already-refunded-orders-are-skipped behaviors.

## Scenario → test mapping

### `refundpolicy/service/RefundPolicyServiceImplTest` (Mockito, 14 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown event → 404 | `.get_unknownEvent_throwsResourceNotFound` | Pass |
| No policy row yet → synthesized `NO_REFUNDS` default | `.get_noPolicyRowYet_returnsSynthesizedNoRefundsDefault` | Pass |
| **Admin bypasses role AND ticket-ownership lookup entirely** | `.get_admin_bypassesRoleAndTicketLookup` | Pass |
| **Buyer with a ticket on the event (no org role at all) is permitted** | `.get_buyerWithOrderOnEvent_isPermitted` | Pass |
| Stranger with no role and no ticket → 403 | `.get_strangerWithNoRoleAndNoTicket_throwsForbidden` | Pass |
| Existing policy row → mapped fields | `.get_existingPolicyRow_mapsFields` | Pass |
| Unknown event → 404 (update) | `.update_unknownEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.update_stranger_throwsForbidden` | Pass |
| Cross-org owner → 403 | `.update_crossOrgOwner_throwsForbidden` | Pass |
| Admin bypasses org-role lookup entirely | `.update_admin_bypassesOrgRoleCheck_withNoCurrentUserIdOrHasRoleCallAtAll` | Pass |
| Organizer succeeds | `.update_organizer_succeeds` | Pass |
| No existing row → creates a new policy for the event | `.update_noExistingRow_createsNewPolicyForTheEvent` | Pass |
| Existing row → upserts in place, clears omitted `customTerms` | `.update_existingRow_upsertsInPlace_ratherThanCreatingASecondRow` | Pass |
| **Concurrent first-insert race (`DataIntegrityViolationException`) → retries against the winner row** | `.update_firstInsertRaceLostAtDbLevel_dataIntegrityViolation_retriesAgainstWinner` | Pass |

### `refund/service/RefundServiceImplTest` (Mockito, 28 tests — the most important suite)

| Scenario | Test | Result |
|---|---|---|
| Unknown order → 404 | `.createRefund_unknownOrder_throwsResourceNotFound` | Pass |
| **BR-PAY-002: the order's own buyer attempting a self-refund → 403 (deliberately not organizer/admin)** | `.createRefund_buyerAttemptsSelfRefund_throwsForbidden_notOrganizerOrAdmin` | Pass |
| Roleless stranger → 403 | `.createRefund_roselessStranger_throwsForbidden` | Pass |
| Admin bypasses org-role check | `.createRefund_admin_bypassesOrgRoleCheck` | Pass |
| Order already fully refunded (remaining ≤ 0) → 409 | `.createRefund_orderAlreadyFullyRefunded_throwsConflict` | Pass |
| Explicit amount currency mismatch → 400 | `.createRefund_explicitAmountCurrencyMismatch_throwsBadRequest` | Pass |
| Explicit amount exceeds remaining balance → 400 | `.createRefund_explicitAmountExceedsRemainingBalance_throwsBadRequest` | Pass |
| Explicit amount exactly at remaining balance → succeeds | `.createRefund_explicitAmountExactlyAtRemainingBalance_succeeds` | Pass |
| No refund policy row at all → 409 | `.createRefund_noRefundPolicyRowAtAll_throwsConflict` | Pass |
| `NO_REFUNDS` policy → 409 | `.createRefund_noRefundsPolicy_throwsConflict` | Pass |
| `REFUNDABLE_UNTIL_N_DAYS`, within window → succeeds | `.createRefund_refundableUntilNDays_withinWindow_succeeds` | Pass |
| `REFUNDABLE_UNTIL_N_DAYS`, past window → 409 | `.createRefund_refundableUntilNDays_pastWindow_throwsConflict` | Pass |
| `CUSTOM` policy → always allowed | `.createRefund_customPolicy_alwaysAllowed` | Pass |
| Omitted `amount` → refunds the full remaining balance | `.createRefund_omittedAmount_refundsFullRemainingBalance` | Pass |
| **Full refund → `Order.status=REFUNDED`, every one of the order's tickets marked `REFUNDED`** | `.createRefund_fullRefund_transitionsOrderToRefunded_andMarksAllTicketsRefunded` | Pass |
| **Partial refund → `Order.status=PARTIALLY_REFUNDED`, tickets untouched (`ticketRepository.findByOrderId`/`save` never called)** | `.createRefund_partialRefund_transitionsOrderToPartiallyRefunded_leavesTicketsUntouched` | Pass |
| **Prior partial refund (300 of 1000) + a second refund of the remaining 700 → cumulative reaches the total, tickets marked REFUNDED** | `.createRefund_priorPartialRefund_thenAnotherRefundReachingFullTotal_marksTicketsRefunded` | Pass |
| **Gateway decline → a `FAILED` `Refund` row is still created; `Order`/`Ticket` state untouched; `sumCompletedAmountByOrderId` only called once (the post-save recompute is skipped)** | `.createRefund_gatewayFailure_stillCreatesFailedRefundRow_doesNotTouchOrderOrTickets` | Pass |
| Missing `Payment` row for the order → 404 | `.createRefund_missingPaymentRow_throwsResourceNotFound` | Pass |
| `listRefunds`: unknown order → 404 | `.listRefunds_unknownOrder_throwsResourceNotFound` | Pass |
| `listRefunds`: owning buyer permitted | `.listRefunds_owningBuyer_isPermitted` | Pass |
| `listRefunds`: admin permitted | `.listRefunds_admin_isPermitted` | Pass |
| `listRefunds`: event organizer permitted | `.listRefunds_eventOrganizer_isPermitted` | Pass |
| `listRefunds`: roleless stranger with no order tie → 403 | `.listRefunds_roselessStrangerWithNoOrgTie_throwsForbidden` | Pass |
| **`refundAllForEventCancellation` bypasses the refund policy entirely (`verifyNoInteractions(refundPolicyRepository)`)** | `.refundAllForEventCancellation_bypassesRefundPolicyEntirely` | Pass |
| Orders already `REFUNDED`/`CANCELLED` are skipped, not re-refunded | `.refundAllForEventCancellation_skipsOrdersAlreadyRefundedOrCancelled` | Pass |
| **Best-effort: one order's missing-Payment failure doesn't stop the healthy order from being refunded** | `.refundAllForEventCancellation_oneOrdersFailureDoesNotStopTheRest_bestEffort` | Pass |
| An order with no remaining balance is skipped | `.refundAllForEventCancellation_orderWithNoRemainingBalance_isSkipped` | Pass |

### `refundpolicy/controller/EventRefundPolicyControllerTest` (`@WebMvcTest`, 7 tests)

Request validation (missing `ruleType`), 200/403/404 status mapping for
`get`/`update` — mirrors this repo's slice-test convention (security
enforcement, including this endpoint's non-public GET, left to the
integration test).

### `refund/controller/OrderRefundControllerTest` (`@WebMvcTest`, 12 tests)

Request validation (missing/blank `reason`, invalid `amount.currency`
format), 201/400/403/404/409 status mapping for `create`, and 200/403/404
for `list` — including the deliberate **201-with-a-FAILED-body** case for a
gateway-declined refund (a refund is "initiated" per openapi.yaml, not an
HTTP error, even when the gateway reversal itself fails).

### `payout/controller/OrganizationPayoutControllerTest` (`@WebMvcTest`, 4 tests)

Paginated response shape (`content`/`totalElements`), empty-result shape,
404/403 status mapping — mirrors `EventResaleListingControllerTest`'s
paginated-listing slice-test style.

### `refundpolicy/RefundPolicyIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 13 tests)

| Scenario | Test | Result |
|---|---|---|
| **`GET`, no token at all → 401 (proves this endpoint is genuinely NOT public, unlike resale-policy's GET — the new `SecurityConfig` matcher actually works)** | `.get_noTokenAtAll_returns401` | **Pass** |
| **`GET`, authenticated roleless stranger with no ticket → 403 (the anonymous-401-vs-stranger-403 distinction)** | `.get_roselessStrangerWithNoTicketAndNoOrgRole_returns403` | **Pass** |
| **Buyer with a ticket on the event, no org role at all → 200, `NO_REFUNDS` default** | `.get_buyerWithTicketOnEvent_noOrgRoleAtAll_returns200_withNoRefundsDefault` | **Pass** |
| Organizer → 200 | `.get_organizer_returns200` | Pass |
| Admin, no org membership at all → 200 | `.get_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |
| Unknown event → 404 | `.get_unknownEvent_returns404` | Pass |
| Owner → 200, sets `REFUNDABLE_UNTIL_N_DAYS`; round-trip GET reflects it | `.update_owner_returns200_setsRefundableUntilNDays_roundTripGetReflectsIt` | Pass |
| Organizer → 200, sets `CUSTOM` terms | `.update_organizer_returns200_setsCustomTerms` | Pass |
| Admin, no org membership → 200 | `.update_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |
| Roleless stranger → 403 | `.update_roselessStranger_returns403` | Pass |
| Cross-org owner → 403 | `.update_crossOrgOwner_returns403` | Pass |
| `PATCH`, no token → 401 | `.update_noToken_returns401` | Pass |
| Unknown event → 404 (update) | `.update_unknownEvent_returns404` | Pass |

### `refund/RefundIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 14 tests)

| Scenario | Test | Result |
|---|---|---|
| **Owner, full refund → 201; `orders.status=REFUNDED` and `tickets.status=REFUNDED` verified fresh from Postgres** | `.create_owner_fullRefund_returns201_marksOrderRefundedAndTicketsRefundedInPostgres` | **Pass** |
| **Organizer, partial refund (400 of 1000) → 201; `orders.status=PARTIALLY_REFUNDED`, ticket stays `VALID`, verified from Postgres** | `.create_organizer_partialRefund_returns201_marksOrderPartiallyRefunded_ticketsStayValid` | **Pass** |
| **Gateway-declining `gatewayRef` → 201 with a `FAILED` body; `orders`/`tickets` state unchanged in Postgres** | `.create_gatewayDeclinedRefund_returns201WithFailedStatus_orderAndTicketsUntouched` | **Pass** |
| Buyer attempts self-refund (BR-PAY-002) → 403 | `.create_buyerAttemptsSelfRefund_returns403` | Pass |
| Roleless stranger → 403 | `.create_roselessStranger_returns403` | Pass |
| No token → 401 | `.create_noToken_returns401` | Pass |
| No refund policy configured (BR-PAY-003) → 409 | `.create_noRefundPolicyConfigured_returns409` | Pass |
| Amount exceeds remaining balance → 400 | `.create_amountExceedsRemainingBalance_returns400` | Pass |
| Amount currency mismatch → 400 | `.create_amountCurrencyMismatch_returns400` | Pass |
| Unknown order → 404 | `.create_unknownOrder_returns404` | Pass |
| Owning buyer can list their order's refunds | `.list_owningBuyer_returns200_withRefunds` | Pass |
| Organizer can list an order's refunds | `.list_organizer_returns200` | Pass |
| Roleless stranger → 403 | `.list_roselessStranger_returns403` | Pass |
| Unknown order → 404 | `.list_unknownOrder_returns404` | Pass |

### `refund/EventCancellationRefundIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 4 tests) — BR-PAY-005 headline proof

| Scenario | Test | Result |
|---|---|---|
| **Cancelling a real event with a real paid order and NO refund policy configured → the direct-refund endpoint would 409 here, but cancellation refunds it anyway (event → `CANCELLED`, refund → `COMPLETED` with `reason="Event cancelled"`, order → `REFUNDED`, ticket → `REFUNDED`, all read fresh from Postgres)** | `.cancelEvent_withNoRefundPolicyConfigured_stillRefundsEveryTicketHolder` | **Pass** |
| **Same, but with an explicit `NO_REFUNDS` policy configured — cancellation still bypasses it and refunds** | `.cancelEvent_withNoRefundsPolicyExplicitlyConfigured_stillBypassesItAndRefunds` | **Pass** |
| **Best-effort: two orders on the same event, one with no `Payment` row (data-inconsistent) → the healthy order is still refunded; the broken order's failure is silently swallowed, left `PAID`/`VALID`, no `Refund` row** | `.cancelEvent_multipleOrders_oneWithNoPaymentRow_bestEffortStillRefundsTheOther` | **Pass** |
| An already-`REFUNDED` order is skipped, not double-refunded | `.cancelEvent_orderAlreadyRefunded_isSkipped_noDuplicateRefundIssued` | Pass |

## The end-to-end refund result (most important assertion for `refund/`)

`RefundIntegrationTest.create_owner_fullRefund_returns201_marksOrderRefundedAndTicketsRefundedInPostgres`
proves, against real Postgres in one HTTP call, that an organizer-initiated
refund genuinely reaches all the way through the stack: a real `Order`
(`PAID`, total 1000), a real `Payment` (`COMPLETED`, a deterministic
non-failing `gatewayRef`), and a real `Ticket` (`VALID`) are seeded
directly; a single `POST /orders/{orderId}/refunds` call with no explicit
`amount` (full-balance path) is made; and the test then reads the **same
rows back from Postgres** (not the response body) to confirm `orders.status
= REFUNDED` and `tickets.status = REFUNDED`. The companion gateway-decline
test in the same file proves the opposite: a `gatewayRef` containing
`"fail"` still produces a `201` with a real `FAILED` `refunds` row, but
leaves `orders`/`tickets` completely untouched — exactly matching
`RefundServiceImpl`'s documented "a declined gateway reversal is a recorded
fact, not an exception" contract.

## The event-cancellation-triggers-refunds result (most important assertion for BR-PAY-005)

`EventCancellationRefundIntegrationTest` is the one property a purely mocked
unit test cannot prove — `EventServiceImplTest
.cancelEvent_success_triggersRefundAllForEventCancellation` (pre-existing,
not modified by this pass) only proves `refundService
.refundAllForEventCancellation(eventId, ownerId)` was *called* with the
right arguments via Mockito. This pass's integration test instead calls the
real `POST /events/{eventId}/cancel` HTTP endpoint — never touching
`/orders/{orderId}/refunds` directly — against a real event with a real
paid order, and reads `events`/`refunds`/`orders`/`tickets` straight from
Postgres afterward to confirm the whole chain actually fired: `EventServiceImpl
.cancelEvent` → `RefundService.refundAllForEventCancellation` → `issueRefund`
→ a real gateway call → real row updates. It additionally proves the
policy-bypass claim behaviorally (not just from a code comment) by running
the same scenario twice — once with no `RefundPolicy` row at all, once with
an explicit `NO_REFUNDS` row — both still result in a `COMPLETED` refund,
which the direct-refund endpoint would have rejected with a `409` in either
case (see `RefundIntegrationTest.create_noRefundPolicyConfigured_returns409`
for that contrasting behavior on the same policy data).

## Findings surfaced by writing these tests

**No production bugs were found.** Every business-rule behavior these tests
assert — the policy-gate branching for all three `RefundRuleType` values,
the remaining-balance math including the prior-partial-then-full scenario,
the full-vs-partial refund ticket-state split, the gateway-failure-still-
records-a-row contract, BR-PAY-002's organizer/admin-only gate, BR-PAY-005's
policy-bypass and best-effort semantics, and the new non-public
`SecurityConfig` matcher for the refund-policy GET — matched the production
code exactly on the first passing run.

**One test-authoring bug in this pass's own first draft, caught and fixed
before finalizing** (not a production issue, but worth recording per this
repo's rule about surfacing real gaps found while writing tests): the first
draft of `EventCancellationRefundIntegrationTest` and three of
`RefundIntegrationTest`'s tests chained `mockMvc.perform(...).andExpect(status()...)`
directly, with the `trackAnyRefundsForOrder(...)` cleanup call placed
*after* that chain. Because a `.andExpect(...)` failure throws immediately
— skipping any code after it in the same test method — a wrong status-code
expectation (the `POST /events/{eventId}/cancel` endpoint actually returns
**202 Accepted**, not `200 OK`, as this pass's own first draft incorrectly
assumed) caused the HTTP call to complete and genuinely write `Refund` rows
to Postgres, then threw before those rows were ever added to the cleanup
list — orphaning 3 rows in the shared dev database (caught by this pass's
own before/after row-count check, not by chance). Fixed two ways: (1) the
status-code assumption itself, and (2) restructured every test that creates
a real `Refund` row via HTTP to capture the `MvcResult` first via
`.andReturn()`, call `trackAnyRefundsForOrder(...)` immediately, and *then*
assert on the response — so a future wrong assertion can no longer orphan a
row. The 3 orphaned rows from the initial failing run were identified
(`reason = 'Event cancelled'`, `created_at` matching that run's timestamp)
and deleted manually before re-verifying full cleanup; see the verification
below.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'refund_policies' t, count(*) from refund_policies
   union all select 'refunds', count(*) from refunds
   union all select 'payouts', count(*) from payouts
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
 refund_policies       |     0
 refunds               |     0
 payouts               |     0
 tickets               |     0
 orders                |     0
 payments              |     0
 ticket_types          |     3
 events                |     4
 organizations         |     1
 organization_members  |     1
 users                 |    11
```

All three brand-new Phase 8 tables (`refund_policies`, `refunds`,
`payouts`) are `0` — full cleanup confirmed, no leftover rows from this
pass's tests at all. `tickets`/`orders`/`payments` (tables this pass's
tests also write to directly, alongside Phase 5b/6a) are likewise `0`.
`ticket_types`=3/`events`=4/`organizations`=1/`organization_members`=1/
`users`=11 are the same pre-existing residue already documented in the
Phase 7 results doc (`testing/transfer-resale-test-results.md`), predating
this session and unmodified by this pass — verified identical before and
after this pass's targeted 102-test run and the full 819-test suite run
(re-checked three times across the session, all three runs came back with
this exact same steady-state count).

## Concurrency-sensitive rules: not applicable to this phase's own new code

Nothing new in Phase 8 introduces a fresh race condition of its own — the
cumulative-refund-never-exceeds-total invariant is enforced by a single
transactional read-then-write within `issueRefund` (no new
`findByIdForUpdate`/pessimistic-lock primitive was added for this phase,
unlike Phase 7's ticket/listing locks), and `refundAllForEventCancellation`
runs synchronously within `cancelEvent`'s own transaction rather than as a
separate concurrent job. No genuinely concurrent test was added for this
reason; if a future phase adds concurrent refund-initiation support (e.g.
two organizers racing to refund the same order), that would need its own
dedicated `ExecutorService`-based test at that time.

**Superseded by the addendum below** — a `code-reviewer` pass found this
paragraph's premise wrong: the read-then-write in `issueRefund` was in fact
exactly as racy as Phase 7's, just without the lock Phase 7 had. Left
un-edited above (rather than deleted) so the "why" in the addendum makes
sense against what was originally believed.

## Post-dispatch addendum (2026-09-11): code-reviewer found 2 CRITICAL + 1 HIGH + 1 MEDIUM, all fixed

A `code-reviewer` pass over this dispatch's files (run after this results
doc was first written) found two CRITICAL issues, one HIGH, and one MEDIUM,
all fixed and covered by new regression tests:

- **CRITICAL — no locking allowed a double-refund race.** This doc's own
  "not applicable to this phase" paragraph above was the exact premise the
  reviewer disproved: `RefundServiceImpl.createRefund` and
  `refundAllForEventCancellation` both computed the remaining refundable
  balance via `RefundRepository.sumCompletedAmountByOrderId` and only later
  wrote a new `Refund` row + updated `Order.status`, with no lock on the
  `Order` row in between. Two admins calling `POST /orders/{orderId}/refunds`
  concurrently (or one admin racing a concurrent event cancellation) could
  both read "remaining = full total" before either committed, both pass
  validation, and both successfully refund the same order - overpaying it
  by 2x. Fixed by adding `OrderRepository.findByIdForUpdate` (mirroring
  Phase 7's `TicketRepository`/`ResaleListingRepository` pattern exactly)
  and routing every read-then-write in `issueRefund`'s callers through it
  instead of a plain `findById`.
- **CRITICAL — a full refund stripped a ticket from whoever legitimately
  holds it now, not just the original buyer.** `issueRefund` marked every
  ticket returned by `TicketRepository.findByOrderId(order.getId())` as
  `REFUNDED` on a full refund - but `Ticket.orderId` never changes on a
  Phase 7 transfer/resale, only `ownerId` does. So: buyer A purchases,
  transfers the ticket to B via a legitimate direct transfer or resale, and
  the *original* order is later refunded (manually, or automatically via
  event cancellation) - B's validly-held, unrelated-to-the-refund ticket
  was silently invalidated, with B paid nothing and never having sold
  anything back. Fixed by only marking a ticket `REFUNDED` if its current
  `ownerId` still equals the order's own `buyerId`; a ticket that's since
  been transferred/resold away is left untouched. New test:
  `RefundServiceImplTest.createRefund_fullRefund_ticketAlreadyTransferredToSomeoneElse_leavesThatTicketAlone`.
- **HIGH — `refundAllForEventCancellation`'s best-effort catch swallowed
  every `RuntimeException` with zero visibility.** The empty `catch
  (RuntimeException e) {}` block caught genuine bugs (e.g. a missing
  `Payment` row) exactly the same way it caught nothing-went-wrong, with no
  log line, no way for the cancelling organizer or an admin to ever learn
  some ticket holders weren't refunded after their event was cancelled.
  Fixed by adding an `@Slf4j` error log (order id + event id + the
  exception) in the catch block - still best-effort (doesn't re-throw, so
  the sweep continues), but no longer invisible. Confirmed via the existing
  `refundAllForEventCancellation_oneOrdersFailureDoesNotStopTheRest_bestEffort`
  test's log output during this pass's re-run.
- **MEDIUM — a `$0` "refund" was accepted.** `MoneyDto.amount` is
  `@PositiveOrZero` (correct for its other reuses, e.g. a free ticket
  type's price), but a `0`-amount `RefundCreateRequest` passed straight
  through to `issueRefund`, calling the gateway for `$0`, persisting a
  meaningless `COMPLETED` `Refund` row, and flipping `Order.status` to
  `PARTIALLY_REFUNDED` for nothing. Fixed with an explicit `amount() <= 0`
  check in `createRefund` (not a global tightening of the shared
  `MoneyDto`). New test: `RefundServiceImplTest.createRefund_zeroExplicitAmount_throwsBadRequest`.

**Verification:** `mvnw.cmd compile`/`test-compile` clean after every fix.
Targeted Phase 8 + `EventServiceImplTest` run: 194 tests, 0 failures/errors
(the one `ERROR`-level log line in that run's output is the HIGH fix's own
new logging, expected from the best-effort test, not a failure). Full
suite: **823 tests, 0 failures, 0 errors** (819 baseline + 4 new regression
tests from this addendum).
