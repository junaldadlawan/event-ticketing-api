# Analytics (Phase 14) — Proof of Testing

**Date:** 2026-09-17
**Branch:** `feat/phase14-Analytics` (based on `main` post-Phase-13-merge, PR #27)
**Based on:** `testing/analytics-test-plan.md`, `BR-ANALYTICS-001`/`002` — the
new `analytics/` module (`AnalyticsService`/`Impl`,
`EventAnalyticsController`, `PlatformAnalyticsController`) and new
aggregate query methods added to `TicketRepository`, `OrderRepository`,
`TicketTypeRepository`, `OrganizationRepository`, and `EventRepository`.
No new entity/migration — pure aggregation over existing data, per the
roadmap's own framing.

This is the final phase on the roadmap. Both endpoints were already fully
spec'd in `openapi.yaml` (unlike Phase 12's moderation half) — a
straightforward implementation pass, full three-tier coverage
(`AnalyticsServiceImplTest`/`EventAnalyticsControllerTest`/
`PlatformAnalyticsControllerTest`/`AnalyticsIntegrationTest`).

**Command:** `mvnw.cmd test` (full suite; Postgres already running locally,
container `postgresql`)
**Result:** BUILD SUCCESS — 1194 tests run, 0 failures, 0 errors (20 new
analytics tests; base was 1174 on this branch before Phase 14).

```
[INFO] Running com.junaldadlawan.event_ticketing_api.analytics.AnalyticsIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.421 s
[INFO] Running com.junaldadlawan.event_ticketing_api.analytics.controller.EventAnalyticsControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.028 s
[INFO] Running com.junaldadlawan.event_ticketing_api.analytics.controller.PlatformAnalyticsControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.651 s
[INFO] Running com.junaldadlawan.event_ticketing_api.analytics.service.AnalyticsServiceImplTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.562 s
```

## Scenario → test mapping

| Test plan scenario | Covered by | Result |
|---|---|---|
| Organizer can view sales analytics for their own event only | `AnalyticsIntegrationTest.getEventAnalytics_owningOrganizer_numbersMatchSeededData` | ✅ Pass |
| Organizer cannot view analytics for another organizer's event (403) | `AnalyticsIntegrationTest.getEventAnalytics_crossOrgOrganizer_returns403` | ✅ Pass |
| Admin can view platform-wide analytics | `AnalyticsIntegrationTest.getPlatformAnalytics_admin_returns200` | ✅ Pass |
| Analytics numbers match actual seeded order/ticket data | `AnalyticsIntegrationTest.getEventAnalytics_owningOrganizer_numbersMatchSeededData` (asserts `ticketsSold`, `revenue`, `remainingInventory`, and `salesOverTime` against 2 real persisted `Ticket` rows + 1 real `Order` row + a `TicketType` with known `quantityAvailable`) | ✅ Pass |

## Judgment calls (not explicitly specified, decided during implementation)

- **Event analytics admits admin, not just the owning organizer** —
  openapi.yaml's original summary said only "(owning organizer)", unlike
  every sibling owner/organizer-gated endpoint elsewhere in the same spec.
  Read as a spec omission, not a deliberate restriction (BR-AUTH-004's
  general admin-bypass rule + zero precedent anywhere else in this
  codebase for excluding admin from an owner/organizer-gated resource).
  `openapi.yaml`'s summary text was corrected to match.
- **`tickets_sold`** = every `Ticket` row ever issued for the event
  (`TicketRepository.countByEventId`), not just currently-`VALID` ones —
  a later transfer/refund doesn't undo that a sale happened.
- **`revenue`/`total_gmv`/`sales_over_time`** are gross (before refunds),
  matching the glossary's own `total_gmv` definition ("before
  fees/refunds") — no refund-rate metric was named in the spec, so none
  was added.
- **`remaining_inventory`** = sum of `quantityAvailable` across the
  event's non-deleted `TicketType` rows.
- **`active_organizers`** = `Organization` rows with `status = APPROVED`
  and not soft-deleted (i.e. not `PENDING`/`REJECTED`/`SUSPENDED`/removed).
- **`event_volume`** = total non-deleted `Event` rows platform-wide, all
  statuses — read "volume" as total throughput, not just currently-live
  events.
- **Currency resolution** — this schema has no platform-currency setting
  and no multi-currency reconciliation precedent anywhere in this
  codebase. Both endpoints pick a real currency from an actual order when
  at least one exists, falling back to `"USD"` otherwise.
- **`sales_over_time` unions, not intersects,** the per-day ticket-count
  and per-day revenue queries (covered by
  `AnalyticsServiceImplTest.getEventAnalytics_salesOverTime_mergesTicketAndRevenueDays`)
  — a day present in only one of the two result sets still appears, with
  `0` on the missing side, rather than being silently dropped.

## Nothing left out

Both endpoints from `testing/analytics-test-plan.md` are fully covered.
This completes the roadmap — Phase 14 was the last phase in
`docs/event-ticketing-api-roadmap.md`.
