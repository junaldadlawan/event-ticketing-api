# Dispute Feature (Phase 12) — Proof of Testing

**Date:** 2026-09-16
**Branch:** `feat/phase12-disputes-moderation`
**Based on:** `testing/dispute-test-plan.md`'s 4 scenarios, `openapi.yaml`'s
`/disputes` paths and `Dispute`/`DisputeCreate`/`DisputeUpdate` schemas
(BR-ADMIN-003), `docs/event-ticketing-api-erd.md`'s note that `Dispute` gets
`updated_by` "only where an admin actively edits it after creation", and the
new `dispute/` module (`Dispute` entity, `DisputeRepository`,
`DisputeService`/`Impl`, `DisputeController`), migration
`V20__add_disputes_table.sql`, the new `NotificationType.DISPUTE_RESOLVED`,
and the new `SecurityConfig` matcher for `/api/v1/disputes/**`.

This was a from-scratch implementation in this session — no prior test
coverage existed. Full three-tier coverage was added alongside the
production code, in the same pass.

**Command:** `mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.dispute.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result:** BUILD SUCCESS — 52 tests run, 0 failures, 0 errors.

```
[INFO] Running com.junaldadlawan.event_ticketing_api.dispute.controller.DisputeControllerTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.388 s
[INFO] Running com.junaldadlawan.event_ticketing_api.dispute.DisputeIntegrationTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.787 s
[INFO] Running com.junaldadlawan.event_ticketing_api.dispute.service.DisputeServiceImplTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.382 s
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

## Test files added

- `src/test/java/.../dispute/service/DisputeServiceImplTest.java` — 23 tests
  (Mockito, no Spring context; mirrors `RefundServiceImplTest`'s style).
  The orderId/ticketId-presence guard, buyer-or-admin / ticket-owner-or-admin
  raise authorization, admin-only `list`/`update`, raiser-or-admin `get`
  visibility, the terminal-state (`RESOLVED`/`DISMISSED`) update guard, and
  the `DISPUTE_RESOLVED` notification trigger (fires on `RESOLVED` and
  `DISMISSED`, not on `INVESTIGATING`).
- `src/test/java/.../dispute/controller/DisputeControllerTest.java` — 15
  tests (`@WebMvcTest` slice, security filters disabled). Request validation
  (missing/blank `reason`, `resolution` over 1000 chars) and
  200/201/400/403/404/409 status mapping.
- `src/test/java/.../dispute/DisputeIntegrationTest.java` — 14 tests (full
  `@SpringBootTest`, real filter chain, real signed JWTs, real Postgres).
  Real `Order`/`Ticket` rows seeded directly; disputes raised/resolved via
  real HTTP calls; persisted `disputes`/`notifications` rows verified
  straight from Postgres, not just the response body.

## Scenario → test mapping

| `dispute-test-plan.md` scenario | Covered by | Result |
|---|---|---|
| A user can raise a dispute against an order or ticket | `DisputeServiceImplTest.create_orderBuyer_succeeds`, `.create_ticketOwner_succeeds`, `DisputeIntegrationTest.create_orderBuyer_returns201`, `.create_ticketOwner_returns201` | Pass |
| Admin can view and resolve disputes | `DisputeServiceImplTest.get_admin_succeedsEvenThoughNotRaiser`, `.update_toResolved_setsResolutionAndNotifiesRaiser`, `DisputeIntegrationTest.update_admin_resolvesDispute_notifiesRaiser` | Pass |
| Non-admin cannot update a dispute's status/resolution (403) | `DisputeServiceImplTest.update_nonAdmin_throwsForbidden`, `DisputeControllerTest.update_nonAdmin_returns403`, `DisputeIntegrationTest.update_nonAdmin_returns403` | Pass |
| A user can view disputes they raised | `DisputeServiceImplTest.get_raiser_succeeds`, `DisputeIntegrationTest.get_raiser_returns200` | Pass |

## Additional coverage beyond the test plan's 4 scenarios

- **At-least-one-of orderId/ticketId guard (400)** —
  `DisputeServiceImplTest.create_neitherOrderNorTicket_throwsBadRequest`,
  `DisputeControllerTest.create_neitherOrderNorTicket_returns400`,
  `DisputeIntegrationTest.create_neitherOrderNorTicket_returns400`.
- **Non-buyer/non-owner raise attempt (403)** —
  `.create_orderNonBuyer_throwsForbidden`,
  `.create_ticketNonOwnerNonAdmin_throwsForbidden`,
  `DisputeIntegrationTest.create_orderNonBuyer_returns403`.
- **Admin can raise a dispute on someone else's behalf** —
  `DisputeServiceImplTest.create_orderAdmin_succeedsEvenThoughNotBuyer`,
  `DisputeIntegrationTest.create_admin_canRaiseAgainstAnyoneElsesOrder`.
- **Stranger cannot view someone else's dispute (403)** —
  `.get_stranger_throwsForbidden`, `DisputeIntegrationTest.get_stranger_returns403`.
- **Terminal-state guard: already-`RESOLVED`/`DISMISSED` → 409** —
  `DisputeServiceImplTest.update_alreadyResolved_throwsConflict`,
  `.update_alreadyDismissed_throwsConflict`,
  `DisputeControllerTest.update_alreadyResolved_returns409`,
  `DisputeIntegrationTest.update_alreadyResolved_returns409`.
- **Partial update semantics** (status-only, resolution-only, both) —
  `.update_toInvestigating_doesNotNotify`, `.update_resolutionOnly_leavesStatusUnchanged`.
- **Unknown order/ticket/dispute → 404** — one test per resource per layer.
- **`GET /disputes?status=` optional filter** —
  `.list_admin_withStatusFilter_delegatesToFindByStatus`,
  `.list_admin_noStatusFilter_delegatesToFindAll`.
- **No token → 401** — `DisputeIntegrationTest.create_noToken_returns401`,
  `.update_noToken_returns401` (exercises the real new `SecurityConfig`
  matcher for `/api/v1/disputes/**`).

## Findings surfaced by writing these tests

No production bugs were found — this was a from-scratch implementation
written and tested in the same pass, so the usual "existing code vs. new
tests" mismatch category doesn't apply. Two things worth recording:

1. **Mockito strict-stubbing caught an important test-authoring mistake
   early**, not a production one: `DisputeServiceImpl.list`/`.update` both
   call `accessGuard.requireAdmin()` (a `void` method), not
   `accessGuard.isAdmin()`. The first draft of several tests stubbed
   `isAdmin()` to simulate the admin/non-admin split, which Mockito's
   strict-stubbing mode correctly flagged as `UnnecessaryStubbing` (that
   stub was never consulted, since the production code never calls
   `isAdmin()` on those two paths) — fixed by switching the "admin" case to
   rely on the mock's default no-op `void` behavior and the "non-admin"
   case to `doThrow(...).when(accessGuard).requireAdmin()`.
2. **`Order.status` is `nullable = false`** — the integration test's
   `persistOrder` helper initially omitted it, causing an immediate
   `ConstraintViolationException` on save. Fixed by setting
   `OrderStatus.PAID` explicitly (matching a real checkout-produced order).

## Database cleanup verification

`DisputeIntegrationTest` persists real `Order`/`Ticket`/`Dispute`/
`Notification` rows against the shared local Postgres instance. Each test
tracks every row it creates and deletes them in `@AfterEach`.

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'disputes' t, count(*) from disputes
   union all select 'orders', count(*) from orders
   union all select 'tickets', count(*) from tickets;"

    t      | count
-----------+-------
 disputes  |     0
 orders    |     0
 tickets   |     0
```

Fully clean — no leftover rows from this pass's tests.
