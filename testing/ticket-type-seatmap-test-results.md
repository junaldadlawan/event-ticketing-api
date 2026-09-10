# Ticket Types & Inventory (Phase 4) — Proof of Testing

**Date:** 2026-09-10
**Branch:** `feat/phase4-ticket-types`
**Based on:** `git diff main` for this branch (`common/entity/Money.java`;
new `tickettype/` module — `TicketType` entity, `TicketTypeKind` enum,
`MoneyDto`/`TicketTypeCreateRequest`/`TicketTypeUpdateRequest`/`TicketTypeResponse`
DTOs, `TicketTypeRepository`, `TicketTypeService`/`Impl`,
`EventTicketTypeController`/`TicketTypeController`; new `seatmap/` module —
`SeatMap`/`Seat` entities, `SeatStatus` enum, `SeatMapResponse`/`SeatResponse`
DTOs, `SeatMapRepository`/`SeatRepository`, `SeatMapService`/`Impl`,
`SeatMapController`; migration `V9__add_ticket_types_and_seatmaps.sql`;
`SecurityConfig` additions for `/api/v1/ticket-types/**` and
`POST /api/v1/events/*/ticket-types`); `openapi.yaml`'s `TicketType`/
`TicketTypeCreate`/`TicketTypeUpdate`/`SeatMap`/`Seat`/`Money` schemas
(~1623-1629, ~1833-1896) and the `/events/{eventId}/ticket-types`,
`/ticket-types/{ticketTypeId}`, `/events/{eventId}/seatmap` paths
(~556-637); `BR-EVENT-003`, `BR-INV-001/002`, `BR-AUTH-004` in
`docs/event-ticketing-api-business-rules.md`; `UC-EVENT-04` in
`docs/event-ticketing-api-use-cases.md`. `developer` implemented this
phase; `code-reviewer` signed off with NO CRITICAL/HIGH findings (a couple
LOW/informational notes only — a `Money.amount` int32-vs-int64 openapi
nuance, not chased here). Before this test pass there was ZERO automated
coverage for the `tickettype`/`seatmap` modules — all prior verification
was manual/curl or a throwaway MockMvc test the developer deleted after
use.

**Command (targeted):**
`mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.tickettype.**,com.junaldadlawan.event_ticketing_api.seatmap.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result (targeted):** BUILD SUCCESS — 85 tests run, 0 failures, 0 errors.

**Command (full suite):** `mvnw.cmd test`
**Result (full suite):** BUILD SUCCESS — 283 tests run, 0 failures, 0
errors (85 of which are new for this phase; the remaining 198 are the
pre-existing `event`/`organization`/`user`/`venue`/smoke suites, run
unchanged to confirm no regression — matches the 198 baseline recorded in
`testing/event-hardening-test-results.md`).

## Test files added

- `src/test/java/.../tickettype/service/TicketTypeServiceImplTest.java` —
  **new**, 38 tests (Mockito, no Spring context; mirrors
  `EventServiceImplTest`)
- `src/test/java/.../tickettype/controller/EventTicketTypeControllerTest.java` —
  **new**, 12 tests (`@WebMvcTest` slice on the `POST`/`GET` "list" controller,
  security filters disabled, `JwtAuthenticationFilter` mocked per this
  repo's `@WebMvcTest` convention)
- `src/test/java/.../tickettype/controller/TicketTypeControllerTest.java` —
  **new**, 9 tests (`@WebMvcTest` slice on the `GET`/`PATCH`
  single-resource controller)
- `src/test/java/.../tickettype/TicketTypeAccessIntegrationTest.java` —
  **new**, 8 tests (full `@SpringBootTest`, real security filter chain,
  real signed JWTs, real Postgres)
- `src/test/java/.../seatmap/service/SeatMapServiceImplTest.java` —
  **new**, 9 tests (Mockito, no Spring context)
- `src/test/java/.../seatmap/controller/SeatMapControllerTest.java` —
  **new**, 4 tests (`@WebMvcTest` slice)
- `src/test/java/.../seatmap/SeatMapAccessIntegrationTest.java` —
  **new**, 5 tests (full `@SpringBootTest`, real security filter chain,
  real Postgres; inserts `SeatMap`/`Seat` rows directly via the
  repositories in setup, since no creation endpoint exists in this
  phase's scope)

No production code was changed to make these tests pass — Phase 4 was
already implemented and reviewed before this testing pass; this work is
purely verification.

## Scenario → test mapping

### `TicketTypeServiceImpl.create` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Owner of the event's org can create | `.create_owner_succeeds` | Pass |
| Organizer of the event's org can create | `.create_organizer_succeeds` | Pass |
| **Admin bypass (BR-AUTH-004) — org-role check never even touched** | `.create_adminWithNoOrgRole_bypassesOrgRoleCheck` (asserts `currentUserId()`/`hasRole` never called) | Pass |
| Nonexistent event → 404, before touching the access guard | `.create_nonExistentEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.create_stranger_throwsForbidden` | Pass |
| **Owner/organizer of a *different* org → 403 (not just a roleless stranger — the Phase 3 regression class)** | `.create_ownerOfDifferentOrganization_throwsForbidden` (verifies the guard is asked about the target org, never the caller's own org) | Pass |
| `saleEndAt` before `saleStartAt` → 400 | `.create_saleEndAtBeforeSaleStartAt_throwsBadRequest` | Pass |
| `saleEndAt` equal to `saleStartAt` → 400 | `.create_saleEndAtEqualsSaleStartAt_throwsBadRequest` | Pass |
| `quantityAvailable` initialized to `quantityTotal` | `.create_quantityAvailableInitializedToQuantityTotal` | Pass |
| `maxPerOrder` omitted → defaults to 10 | `.create_maxPerOrderOmitted_defaultsToTen` | Pass |
| `maxPerOrder` provided → uses provided value | `.create_maxPerOrderProvided_usesProvidedValue` | Pass |

### `TicketTypeServiceImpl.list` / `.get` (service layer — draft-visibility gating)

| Scenario | Test | Result |
|---|---|---|
| Non-draft event: no auth needed, guard never touched | `.list_publishedEvent_succeedsWithoutTouchingAccessGuard`, `.get_publishedEvent_succeedsWithoutTouchingAccessGuard` | Pass |
| Draft event, non-privileged caller → 403 | `.list_draftEvent_stranger_throwsForbidden`, `.get_draftEvent_stranger_throwsForbidden` | Pass |
| Draft event, owning org's owner → succeeds | `.list_draftEvent_ownerOfEventsOrg_succeeds`, `.get_draftEvent_ownerOfEventsOrg_succeeds` | Pass |
| Draft event, admin → succeeds | `.list_draftEvent_admin_succeeds`, `.get_draftEvent_admin_succeeds` | Pass |
| **Draft event, owner of a *different* org → 403** | `.list_draftEvent_ownerOfDifferentOrganization_throwsForbidden`, `.get_draftEvent_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Unknown event/ticket type → 404 | `.list_unknownEvent_throwsResourceNotFound`, `.get_unknownTicketType_throwsResourceNotFound` | Pass |

### `TicketTypeServiceImpl.update` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Owner succeeds | `.update_owner_succeeds` | Pass |
| Unknown ticket type → 404 | `.update_unknownTicketType_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.update_stranger_throwsForbidden` | Pass |
| **Owner of a *different* org → 403** | `.update_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Admin, no org role → succeeds | `.update_admin_succeedsWithNoOrgRole` | Pass |
| Partial update: name/price/maxPerOrder each change independently | `.update_nameOnly_changesOnlyName`, `.update_priceOnly_changesOnlyPrice`, `.update_maxPerOrderOnly_changesOnlyMaxPerOrder` | Pass |
| Blank name → 400 | `.update_blankName_throwsBadRequest` | Pass |
| All fields omitted → unchanged | `.update_allFieldsOmitted_leavesTicketTypeUnchanged` | Pass |
| **`quantityTotal` present → `quantityAvailable` resyncs to the new value** | `.update_quantityTotalPresent_resyncsQuantityAvailable` | Pass |
| **`quantityTotal` omitted → `quantityAvailable` left untouched (the edge case code-reviewer specifically checked)** | `.update_quantityTotalOmitted_leavesQuantityAvailableUntouched` | Pass |
| Sale-window re-checked against merged state: only `saleEndAt` moved before the *existing* `saleStartAt` → 400 | `.update_saleEndAtOnly_movedBeforeExistingSaleStartAt_throwsBadRequest` | Pass |
| Sale-window re-checked against merged state: only `saleStartAt` moved after the *existing* `saleEndAt` → 400 | `.update_saleStartAtOnly_movedAfterExistingSaleEndAt_throwsBadRequest` | Pass |
| Both sale-window fields changed to a consistent new window → succeeds | `.update_bothSaleWindowFields_consistentNewWindow_succeeds` | Pass |

### `EventTicketTypeController` (slice — `POST`/`GET list`)

| Scenario | Test | Result |
|---|---|---|
| Create → 201, `quantityAvailable`/`maxPerOrder` echoed in body | `.create_validRequest_returns201` | Pass |
| Missing required field (`name`) → 400, not 401/500 (GlobalExceptionHandler fix from Phase 1 still holds) | `.create_missingRequiredField_returns400` | Pass |
| Blank name → 400 | `.create_blankName_returns400` | Pass |
| **Missing `quantityTotal` → 400 (the confirmed deliberate openapi deviation — BR-EVENT-003 requires it even though openapi's `TicketTypeCreate.required` doesn't list it)** | `.create_missingQuantityTotal_returns400` | Pass |
| Missing sale window → 400 | `.create_missingSaleWindow_returns400` | Pass |
| Invalid (lowercase) currency code → 400 | `.create_invalidCurrencyCode_returns400` | Pass |
| Service 404/403/400 map through correctly | `.create_nonExistentEvent_returns404`, `.create_unauthorizedCaller_returns403`, `.create_saleWindowInvalid_returns400` | Pass |
| List → 200 | `.list_existingEvent_returns200` | Pass |
| List unknown event → 404; list draft unauthorized → 403 | `.list_unknownEvent_returns404`, `.list_draftEventUnauthorizedCaller_returns403` | Pass |

### `TicketTypeController` (slice — `GET`/`PATCH` single resource)

| Scenario | Test | Result |
|---|---|---|
| Get → 200 | `.get_existingTicketType_returns200` | Pass |
| Get unknown → 404; get draft unauthorized → 403 | `.get_unknownTicketType_returns404`, `.get_draftEventUnauthorizedCaller_returns403` | Pass |
| Update → 200; blank name → 400; negative `quantityTotal` (`@Positive`) → 400 | `.update_validRequest_returns200`, `.update_blankName_returns400`, `.update_negativeQuantityTotal_returns400` | Pass |
| Sale-window invalid → 400; cross-org → 403; unknown → 404 | `.update_saleWindowInvalid_returns400`, `.update_crossOrgCaller_returns403`, `.update_unknownTicketType_returns404` | Pass |

### `SeatMapServiceImpl` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Non-draft event, seat map exists: no auth needed, guard never touched | `.getSeatMap_publishedEvent_seatMapExists_succeedsWithoutTouchingAccessGuard` | Pass |
| **No seat map exists → 404 with "Event has no seat map"** | `.getSeatMap_noSeatMapExists_throwsResourceNotFoundWithExpectedMessage` | Pass |
| Unknown event → 404, seat-map repository never even queried | `.getSeatMap_unknownEvent_throwsResourceNotFound` | Pass |
| Draft event, non-privileged caller → 403 | `.getSeatMap_draftEvent_stranger_throwsForbidden` | Pass |
| Draft event, owning org's owner → succeeds | `.getSeatMap_draftEvent_ownerOfEventsOrg_succeeds` | Pass |
| Draft event, admin → succeeds | `.getSeatMap_draftEvent_admin_succeeds` | Pass |
| **Draft event, owner of a *different* org → 403** | `.getSeatMap_draftEvent_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Seats returned with correct status | `.getSeats_returnsSeatsWithStatus` | Pass |
| No seats → empty list | `.getSeats_noSeats_returnsEmptyList` | Pass |

### `SeatMapController` (slice)

| Scenario | Test | Result |
|---|---|---|
| Seat map exists → 200, seats embedded with `status` | `.get_seatMapExists_returns200WithSeats` | Pass |
| No seat map → 404 with "Event has no seat map" detail | `.get_noSeatMap_returns404WithExpectedMessage` | Pass |
| Unknown event → 404 | `.get_unknownEvent_returns404` | Pass |
| Draft event unauthorized → 403 | `.get_draftEventUnauthorizedCaller_returns403` | Pass |

### `TicketTypeAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres)

| Scenario | Test | Result |
|---|---|---|
| Golden path: stranger-403 and **cross-org-owner-403** on create → owner creates (`quantityAvailable == quantityTotal`, `maxPerOrder` defaults to 10) → owner GET/list ok on draft → stranger/anonymous/**cross-org-owner** all 403 on draft GET/list → **cross-org-owner-403 on PATCH** → owner PATCH ok → publish → anonymous GET/list now succeed with **no** `Authorization` header at all | `goldenPath_createDraftVisibility_publishMakesPublic` | Pass |
| Nonexistent event → 404 on create | `create_nonExistentEvent_returns404` | Pass |
| `saleEndAt` before `saleStartAt` → 400 on create | `create_saleEndAtBeforeSaleStartAt_returns400` | Pass |
| **Organizer and admin (no org role, BR-AUTH-004) can also create** | `create_organizerAndAdmin_alsoSucceed` | Pass |
| **All six updatable fields (name/price/quantityTotal/saleStartAt/saleEndAt/maxPerOrder) apply independently over HTTP, including `quantityAvailable` resyncing on `quantityTotal` update and staying put on a subsequent PATCH that omits it** | `update_sixFieldsIndividually_eachAppliesIndependently` | Pass |
| Blank name on update → 400 | `update_blankName_returns400` | Pass |
| Sale-window re-check against merged state (only `saleEndAt` moved) → 400, over HTTP | `update_saleEndAtOnly_beforeExistingSaleStartAt_returns400` | Pass |
| No token → 401 on create | `noToken_cannotCreateTicketType` | Pass |

### `SeatMapAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres)

| Scenario | Test | Result |
|---|---|---|
| No seat map → 404 with "Event has no seat map" detail, over HTTP | `get_noSeatMap_returns404WithExpectedMessage` | Pass |
| Seat map exists (inserted directly via repositories) → 200 with 3 seats, each status (AVAILABLE/HELD/SOLD) present | `get_seatMapExists_returns200WithSeatsAndStatus` | Pass |
| **Draft event: anonymous/stranger/cross-org-owner all 403; owning org's owner/admin 200 — same gating as ticket types** | `get_draftEvent_gatedTheSameWayAsTicketTypes` | Pass |
| Published event: anonymous GET succeeds with no `Authorization` header at all | `get_publishedEvent_anonymousSucceedsWithNoAuthorizationHeader` | Pass |
| Nonexistent event → 404 | `get_nonExistentEvent_returns404` | Pass |

## Findings surfaced by writing these tests

No CRITICAL/HIGH/MEDIUM-severity bugs were found while writing this suite —
consistent with `code-reviewer`'s clean sign-off. Points worth recording
precisely, since they were the specific things the task called out as
necessary-but-not-sufficient to skip:

1. **The `quantityAvailable` resync-only-when-`quantityTotal`-present edge
   case is real and correctly implemented.**
   `TicketTypeServiceImplTest.update_quantityTotalOmitted_leavesQuantityAvailableUntouched`
   constructs a ticket type whose `quantityAvailable` (40) has already
   diverged from `quantityTotal` (100) — simulating a state a future Phase-5
   decrement would produce — and confirms a PATCH that omits `quantityTotal`
   entirely leaves `quantityAvailable` exactly as it was, not silently reset
   to `quantityTotal`. The companion test
   (`update_quantityTotalPresent_resyncsQuantityAvailable`) confirms the
   opposite case actually resyncs. Both are also proven over real HTTP end
   to end in `TicketTypeAccessIntegrationTest.update_sixFieldsIndividually_eachAppliesIndependently`.
2. **Sale-window validation on `update` is genuinely re-checked against the
   *merged final state*, not just the two incoming fields in isolation.**
   `update_saleEndAtOnly_movedBeforeExistingSaleStartAt_throwsBadRequest`
   and `update_saleStartAtOnly_movedAfterExistingSaleEndAt_throwsBadRequest`
   each change only ONE of the two sale-window fields to a value that's
   individually plausible but invalid once merged with the *persisted*
   other field — and the service correctly rejects both. This is the
   specific edge case a naive "validate whatever two fields you got"
   implementation would miss.
3. **Cross-org isolation is proven on every one of the four
   ticket-type-module surfaces (create/list/get/update)** using a caller who
   *is* OWNER of a real, different, persisted organization — not just a
   roleless stranger — both at the unit level (mock-interaction assertions
   confirming the guard is asked about the *target* org, never the caller's
   own) and at the full-stack level
   (`TicketTypeAccessIntegrationTest.goldenPath_createDraftVisibility_publishMakesPublic`).
   The seat-map module carries the identical draft-visibility gating and is
   regression-guarded the same way
   (`SeatMapAccessIntegrationTest.get_draftEvent_gatedTheSameWayAsTicketTypes`).
4. **The BR-EVENT-003 deliberate openapi deviation (`quantityTotal`/
   `saleStartAt`/`saleEndAt` required on create, unlike openapi's literal
   `required: [name, kind, price]`) is enforced exactly as documented in the
   DTO's Javadoc** —
   `EventTicketTypeControllerTest.create_missingQuantityTotal_returns400`
   confirms omitting it 400s even though `openapi.yaml` alone wouldn't
   require it.
5. **`GET /events/{eventId}/seatmap` returns exactly the openapi-specified
   404 message ("Event has no seat map") when the event exists but has no
   seat map**, distinguishing it from the generic "Event {id} not found"
   404 for a nonexistent event — both are asserted separately
   (`SeatMapServiceImplTest.getSeatMap_noSeatMapExists_throwsResourceNotFoundWithExpectedMessage`
   / `.getSeatMap_unknownEvent_throwsResourceNotFound`, and again over HTTP
   in `SeatMapAccessIntegrationTest`).
6. **`kind` is genuinely not updatable** — confirmed indirectly:
   `TicketTypeUpdateRequest` has no `kind` field at all (compile-time
   guarantee, not just an untested assumption), so no test can even attempt
   to PATCH it; this is noted rather than asserted as a runtime check since
   there's nothing to exercise.

No test-writing workarounds were needed for known gaps — the two
concurrency-dependent scenarios from `testing/ticket-type-test-plan.md`
(BR-INV-005/006, "never sold the same seat" / "GA quantity never goes
negative") and the "reserved-seating ticket type paired with an
organizer-created seat map" scenario are explicitly left unchecked in that
plan rather than faked, because the checkout/hold module and the seat-map
*creation* endpoint don't exist yet — there is nothing in this phase's code
to actually exercise for those three scenarios.

## Full run output (tail, full suite)

```
[INFO] Running com.junaldadlawan.event_ticketing_api.seatmap.controller.SeatMapControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.283 s -- in com.junaldadlawan.event_ticketing_api.seatmap.controller.SeatMapControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.seatmap.SeatMapAccessIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.408 s -- in com.junaldadlawan.event_ticketing_api.seatmap.SeatMapAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.seatmap.service.SeatMapServiceImplTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.117 s -- in com.junaldadlawan.event_ticketing_api.seatmap.service.SeatMapServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.tickettype.controller.EventTicketTypeControllerTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.337 s -- in com.junaldadlawan.event_ticketing_api.tickettype.controller.EventTicketTypeControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.tickettype.controller.TicketTypeControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.228 s -- in com.junaldadlawan.event_ticketing_api.tickettype.controller.TicketTypeControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeServiceImplTest
[INFO] Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.096 s -- in com.junaldadlawan.event_ticketing_api.tickettype.service.TicketTypeServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.tickettype.TicketTypeAccessIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.622 s -- in com.junaldadlawan.event_ticketing_api.tickettype.TicketTypeAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.292 s -- in com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.118 s -- in com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.298 s -- in com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.276 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.215 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.304 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 283, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  14.970 s
[INFO] Finished at: 2026-09-10T15:14:59+08:00
[INFO] ------------------------------------------------------------------------
```

## Database cleanup verification

`TicketTypeAccessIntegrationTest`/`SeatMapAccessIntegrationTest` hit the
real local Postgres (`compose.yml`'s `postgresql` container, db
`event_ticketing`) — they persist real `Organization`/`OrganizationMember`/
`Event`/`TicketType`/`SeatMap`/`Seat` rows and delete every row they create
in `@AfterEach`. Verified before and after this full test run that the
suite leaves no net rows behind in any of the tables touched by this
phase's new modules:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM organizations) AS orgs,
          (SELECT count(*) FROM organization_members) AS members,
          (SELECT count(*) FROM organization_member_roles) AS roles,
          (SELECT count(*) FROM venues) AS venues,
          (SELECT count(*) FROM events) AS events,
          (SELECT count(*) FROM ticket_types) AS ticket_types,
          (SELECT count(*) FROM seat_maps) AS seat_maps,
          (SELECT count(*) FROM seats) AS seats;"

 orgs | members | roles | venues | events | ticket_types | seat_maps | seats
------+---------+-------+--------+--------+--------------+-----------+-------
    0 |       0 |     1 |      0 |      3 |            0 |         0 |     0
```

`ticket_types`/`seat_maps`/`seats` are all `0` — fully clean, no leaks from
this phase's new tests. `orgs`/`members`/`venues` are all `0` and `events`
is `3`, `organization_member_roles` is `1` — byte-for-byte identical to the
baseline already documented as pre-existing (predating this session, from
earlier manual/curl testing) in `testing/event-hardening-test-results.md`,
confirming this run didn't touch or leak anything in those tables either.
