# Event Hardening (Phase 3) — Proof of Testing

**Date:** 2026-09-10
**Branch:** `feat/phase3-event-hardening`
**Based on:** `git diff main` for this branch (`Event` entity, `EventRequest`/
`EventUpdateRequest`/`EventResponse`/`EventVenueSnapshot` DTOs,
`EventService`/`EventServiceImpl`, `EventController`, `EventRepository`,
migration `V8__harden_events_table.sql`, `SecurityConfig` simplification,
`OrganizationAccessGuard` cleanup); `openapi.yaml`'s `Event`/`EventCreate`/
`EventUpdate` schemas (~1760-1818) and `/events`, `/events/{eventId}`,
`/events/{eventId}/publish`, `/events/{eventId}/cancel` paths (~430-555);
`BR-EVENT-001/002`, `BR-TICKET-001-005`, `BR-AUTH-004` in
`docs/event-ticketing-api-business-rules.md`; `UC-EVENT-01/02/03` in
`docs/event-ticketing-api-use-cases.md`. `developer` implemented this phase;
`code-reviewer` found 2 HIGH + 2 MEDIUM findings, all fixed and verified in a
follow-up pass. Before this test pass, the ONLY test coverage for the event
module at all was a rewritten `EventOrganizationAccessIntegrationTest`
(8 tests) — zero unit tests and zero controller-slice tests existed.

**Command:** `mvnw.cmd test` (Postgres already running locally via
`docker compose up -d`, container `postgresql`)
**Result:** BUILD SUCCESS — 198 tests run, 0 failures, 0 errors (89 of
which are new/changed for this pass — 55 service-layer, 25 controller-slice,
1 new integration scenario added to the existing 8; the remaining 109 are
the pre-existing `user`/`organization`/`venue`/smoke suites, run unchanged
to confirm no regression).

## Test files added / changed

- `src/test/java/.../event/service/EventServiceImplTest.java` — **new**,
  55 tests (Mockito, no Spring context; mirrors `VenueServiceImplTest`)
- `src/test/java/.../event/controller/EventControllerTest.java` — **new**,
  25 tests (`@WebMvcTest` slice, security filters disabled, `JwtAuthenticationFilter`
  mocked per this repo's `@WebMvcTest` convention; mirrors `VenueControllerTest`)
- `src/test/java/.../event/EventOrganizationAccessIntegrationTest.java` —
  **extended**, +1 test (`crossOrgOwner_forbiddenOnDraftGet_andOnDelete`),
  8 → 9 tests total (full `@SpringBootTest`, real security filter chain,
  real signed JWTs, real Postgres)

No production code was changed to make these tests pass — Phase 3 was
already implemented and reviewed (including the follow-up fix pass) before
this testing pass; this work is purely verification, plus filling the
unit/slice coverage gap the task called out.

## Scenario → test mapping

### `createEvent` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Owner of the target org can create | `EventServiceImplTest.createEvent_owner_succeeds` | Pass |
| Organizer of the target org can create | `.createEvent_organizer_succeeds` | Pass |
| **Admin with no org role at all can create (just-fixed HIGH, BR-AUTH-004)** | `.createEvent_adminWithNoOrgRole_bypassesOrgRoleCheck` (asserts `accessGuard.currentUserId()`/`hasRole` never called) | Pass |
| Nonexistent org → 404, fails before touching the access guard | `.createEvent_nonExistentOrganization_throwsResourceNotFound` | Pass |
| Non-approved org → 403, regardless of caller | `.createEvent_organizationNotApproved_throwsForbidden_regardlessOfCaller` | Pass |
| Non-approved org rejects even an admin caller | `.createEvent_organizationNotApproved_rejectsEvenAdmin` | Pass |
| Roleless stranger → 403 | `.createEvent_nonOwnerNonOrganizer_throwsForbidden` | Pass |
| **Owner/organizer of a *different* org → 403 (not just a roleless stranger)** | `.createEvent_ownerOfDifferentOrganization_throwsForbidden` (verifies the guard is asked about the *target* org, never the caller's own org) | Pass |
| Venue from a different org → 400 | `.createEvent_venueFromDifferentOrganization_throwsBadRequest` | Pass |
| Nonexistent venue → 404 | `.createEvent_nonExistentVenue_throwsResourceNotFound` | Pass |
| Venue from the same org → succeeds | `.createEvent_venueFromSameOrganization_succeeds` | Pass |
| Ticket prefix is server-generated, unique-checked via `existsByTicketPrefix`, retried on collision | `.createEvent_ticketPrefixCollision_retriesUntilUnique` | Pass |
| Exhausted retry budget → `IllegalStateException` | `.createEvent_allCandidatesCollide_throwsIllegalState` | Pass |

### `getEvent` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Non-draft event: no auth needed, guard never touched | `.getEvent_publishedStatus_succeedsWithoutTouchingAccessGuard` | Pass |
| Draft event, non-privileged caller → 403 | `.getEvent_draftStatus_nonPrivilegedCaller_throwsForbidden` | Pass |
| Draft event, owning org's owner → succeeds | `.getEvent_draftStatus_ownerOfEventsOrg_succeeds` | Pass |
| Draft event, admin → succeeds | `.getEvent_draftStatus_admin_succeeds` | Pass |
| **Draft event, owner of a *different* org → 403** | `.getEvent_draftStatus_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Unknown event → 404 | `.getEvent_unknownId_throwsResourceNotFound` | Pass |

### `updateEvent` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Partial update: title/description/category/images each change independently | `.updateEvent_titleOnly_changesOnlyTitle`, `.updateEvent_descriptionOnly_changesOnlyDescription`, `.updateEvent_categoryOnly_changesOnlyCategory`, `.updateEvent_imagesOnly_changesOnlyImages` | Pass |
| All fields omitted → unchanged | `.updateEvent_allFieldsOmitted_leavesEventUnchanged` | Pass |
| Blank title/description/category → 400 each | `.updateEvent_blankTitle_throwsBadRequest`, `.updateEvent_blankDescription_throwsBadRequest`, `.updateEvent_blankCategory_throwsBadRequest` | Pass |
| Roleless stranger → 403 | `.updateEvent_nonOwnerNonOrganizer_throwsForbidden` | Pass |
| **Owner of a *different* org → 403, proving the auth check always resolves from the persisted event's own org (there is no org field on `EventUpdateRequest` to influence it)** | `.updateEvent_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Admin, no org role → succeeds | `.updateEvent_admin_succeedsWithNoOrgRole` | Pass |
| Unknown event → 404 | `.updateEvent_unknownEvent_throwsResourceNotFound` | Pass |

### `publishEvent` / `cancelEvent` (service layer)

| Scenario | Test | Result |
|---|---|---|
| DRAFT + org approved → PUBLISHED | `.publishEvent_draftAndOrgApproved_succeeds` | Pass |
| Org not approved → 409 | `.publishEvent_organizationNotApproved_throwsConflict` | Pass |
| Non-DRAFT status (PUBLISHED/ON_SALE/SOLD_OUT/CANCELLED/COMPLETED) → 409 | `.publishEvent_nonDraftStatus_throwsConflict` (`@ParameterizedTest`, 5 cases) | Pass |
| **Owner of a different org → 403** | `.publishEvent_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Cancellable statuses (DRAFT/PUBLISHED/ON_SALE/SOLD_OUT) → CANCELLED | `.cancelEvent_cancellableStatus_succeeds` (`@ParameterizedTest`, 4 cases) | Pass |
| Non-cancellable statuses (CANCELLED/COMPLETED) → 409 | `.cancelEvent_nonCancellableStatus_throwsConflict` (`@ParameterizedTest`, 2 cases) | Pass |
| **Owner of a different org → 403** | `.cancelEvent_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Admin, no org role → succeeds | `.cancelEvent_admin_succeedsWithNoOrgRole` | Pass |

### `delete` (service layer) — proving the second core fix (previously no per-event check at all)

| Scenario | Test | Result |
|---|---|---|
| Owner succeeds, event soft-deleted | `.delete_owner_succeeds` | Pass |
| **Owner of a *different* org → 403** | `.delete_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| Roleless stranger → 403 | `.delete_nonOwnerNonOrganizer_throwsForbidden` | Pass |
| Admin, no org role → succeeds | `.delete_admin_succeedsWithNoOrgRole` | Pass |
| Unknown event → 404 | `.delete_unknownEvent_throwsResourceNotFound` | Pass |

### `resolveVenue` (service layer)

| Scenario | Test | Result |
|---|---|---|
| Null venueId → null, no repo call | `.resolveVenue_nullVenueId_returnsNull` | Pass |
| Existing venue → returned | `.resolveVenue_existingVenue_returnsVenue` | Pass |
| Unknown venue → null | `.resolveVenue_unknownVenue_returnsNull` | Pass |

### `EventController` (slice — request/response shape, status codes)

| Scenario | Test | Result |
|---|---|---|
| Create → 201, body includes generated `ticketPrefix`/status | `EventControllerTest.create_validRequest_returns201` | Pass |
| Missing required field (`title`) → 400, not 401/500 | `.create_missingRequiredField_returns400` | Pass |
| Blank title → 400 | `.create_blankTitle_returns400` | Pass |
| Missing `organizationId` → 400 | `.create_missingOrganizationId_returns400` | Pass |
| `startAt` not in the future → 400 | `.create_startAtNotInFuture_returns400` | Pass |
| Service 404/403/400 map through correctly | `.create_organizationNotFound_returns404`, `.create_organizationNotApproved_returns403`, `.create_venueFromDifferentOrganization_returns400` | Pass |
| Get → 200, embeds venue snapshot | `.get_existingEvent_returns200` | Pass |
| Get unknown → 404; get draft unauthorized → 403 | `.get_unknownEvent_returns404`, `.get_draftEvent_unauthorizedCaller_returns403` | Pass |
| Update → 200; blank title → 400; invalid image URL (`@URL`) → 400 | `.update_validRequest_returns200`, `.update_blankTitle_returns400`, `.update_invalidImageUrl_returns400` | Pass |
| Update cross-org → 403; unknown → 404 | `.update_crossOrgCaller_returns403`, `.update_unknownEvent_returns404` | Pass |
| Publish → 200; already-published → 409; cross-org → 403 | `.publish_validRequest_returns200`, `.publish_alreadyPublished_returns409`, `.publish_crossOrgCaller_returns403` | Pass |
| **Cancel → 202 (not 200)**; already-cancelled → 409; cross-org → 403 | `.cancel_validRequest_returns202`, `.cancel_alreadyCancelled_returns409`, `.cancel_crossOrgCaller_returns403` | Pass |
| Delete → 204; cross-org → 403; unknown → 404 | `.delete_validRequest_returns204`, `.delete_crossOrgCaller_returns403`, `.delete_unknownEvent_returns404` | Pass |

### `EventOrganizationAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres)

| Scenario | Test | Result |
|---|---|---|
| Golden path: stranger-403 on create → owner creates (generated prefix, venue snapshot, DRAFT) → owner GET/PATCH ok → stranger/anonymous-403 on draft GET → **cross-org-owner-403 on PATCH** → publish (200) → public GET with **no** `Authorization` header (200) → **cross-org-owner-403 on cancel** → republish-409 → cancel (202) → re-cancel-409 → publish-after-cancel-409 | `goldenPath_createPublishCancel_crossOrgRejected` | Pass |
| **Cross-org-owner-403 on draft GET and on DELETE** (the two mutation surfaces the golden path didn't cover — DELETE previously had NO per-event auth check at all) | `crossOrgOwner_forbiddenOnDraftGet_andOnDelete` (new) | Pass |
| Venue from a different org → 400 on create | `create_venueFromDifferentOrganization_returns400` | Pass |
| Org not approved → 403 on create | `create_organizationNotApproved_returns403` | Pass |
| Nonexistent org → 404 on create | `create_nonExistentOrganization_returns404` | Pass |
| **Admin, no org role → create succeeds (BR-AUTH-004)** | `create_adminWithNoOrgRole_succeeds` | Pass |
| Admin, no org role → update/cancel succeed | `admin_canUpdateAndCancelEventWithNoOrgRole` | Pass |
| Blank title on update → 400 | `update_blankTitle_returns400` | Pass |
| No token → 401 on create | `noToken_cannotCreateEvent` | Pass |

## Findings surfaced by writing these tests

No new CRITICAL/HIGH/MEDIUM-severity bugs were found while writing this
suite — consistent with `code-reviewer`'s sign-off after the follow-up fix
pass. Points worth recording precisely, since the task called them out as
the things to actually prove rather than trust:

1. **The BR-AUTH-004 admin bypass on `createEvent` is real, not just
   claimed.** `EventServiceImplTest.createEvent_adminWithNoOrgRole_bypassesOrgRoleCheck`
   asserts `accessGuard.currentUserId()`/`hasRole(...)` are **never** called
   when the caller is an admin — the bypass genuinely short-circuits before
   any org-role lookup, matching the same pattern already used by
   update/publish/cancel/delete. Confirmed end-to-end too via
   `EventOrganizationAccessIntegrationTest.create_adminWithNoOrgRole_succeeds`,
   which persists a real admin user with zero `OrganizationMember` rows.
2. **The org-approval gate is genuinely independent of the admin bypass.**
   `createEvent_organizationNotApproved_rejectsEvenAdmin` confirms an admin
   caller still gets 403 for a non-`APPROVED` org — the bypass only ever
   skips the *role* check, never the org's own approval status. This
   matters because it would be easy to accidentally short-circuit both
   checks together.
3. **Cross-org isolation is proven for every one of the five mutation
   surfaces (create/update/publish/cancel/delete) plus draft-GET, using a
   caller who *is* OWNER of a real, different, persisted organization — not
   just a roleless stranger.** This is the specific regression the task
   flagged as necessary-but-not-sufficient to skip: a stranger-only test
   would not have caught the original "any org owner/organizer, not just
   this event's own" gap, because a stranger was already rejected by the
   old coarse check too. The unit tests additionally verify the mock
   interaction directly (`verify(accessGuard).hasRole(callerId, event's
   orgId, ...)`, `verify(accessGuard, never()).hasRole(callerId, other
   orgId, ...)`), proving the check is resolved from the *persisted*
   entity's `organizationId`, never anything client-influenced (there's no
   org field on `EventUpdateRequest` at all, and `DELETE`/`publish`/`cancel`
   take no body).
4. **`DELETE` previously had no per-event authorization check at all — now
   it does, and it's regression-guarded at both layers**: unit
   (`EventServiceImplTest.delete_ownerOfDifferentOrganization_throwsForbidden`)
   and end-to-end
   (`EventOrganizationAccessIntegrationTest.crossOrgOwner_forbiddenOnDraftGet_andOnDelete`,
   which additionally confirms the event's own owner can still delete it →
   204, so the fix didn't overcorrect into "nobody can delete").
5. **Cancel really does return 202, not 200** — confirmed at both the slice
   level (`EventControllerTest.cancel_validRequest_returns202`) and the
   full-stack level (`EventOrganizationAccessIntegrationTest`'s golden
   path). Easy to get wrong since every other mutation in this module
   returns 200/201/204.
6. **The ticket-prefix uniqueness retry loop actually loops.**
   `createEvent_ticketPrefixCollision_retriesUntilUnique` forces two
   simulated `existsByTicketPrefix` collisions before a free slot and
   confirms the service retries (3 calls) rather than saving a colliding
   prefix; `createEvent_allCandidatesCollide_throwsIllegalState` confirms
   the `MAX_TICKET_PREFIX_ATTEMPTS` (50) budget is honored and the service
   fails loudly (not silently, and not with a DB constraint violation)
   rather than looping forever.

No test-writing workarounds were needed for known gaps — this phase's own
migration/entity/DTO changes are internally consistent with the settled
behavior described in the task, and nothing forced weakening an assertion.

## Full run output (tail)

```
[INFO] Running com.junaldadlawan.event_ticketing_api.event.controller.EventControllerTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.100 s -- in com.junaldadlawan.event_ticketing_api.event.controller.EventControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.event.EventOrganizationAccessIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.964 s -- in com.junaldadlawan.event_ticketing_api.event.EventOrganizationAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.event.service.EventServiceImplTest
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.464 s -- in com.junaldadlawan.event_ticketing_api.event.service.EventServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.EventTicketingApiApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.670 s -- in com.junaldadlawan.event_ticketing_api.EventTicketingApiApplicationTests
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.557 s -- in com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.261 s -- in com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.103 s -- in com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.449 s -- in com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.165 s -- in com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.355 s -- in com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.359 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.257 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.318 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 198, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  14.249 s
[INFO] Finished at: 2026-09-10T14:07:50+08:00
[INFO] ------------------------------------------------------------------------
```

## Database cleanup verification

`EventOrganizationAccessIntegrationTest` hits the real local Postgres
(`compose.yml`'s `postgresql` container, db `event_ticketing`) — it persists
real `Organization`/`OrganizationMember`/`Venue`/`Event` rows via the HTTP
API (`OrganizationRepository`/`OrganizationMemberRepository`/
`VenueRepository`/`EventRepository` for setup and teardown). Each test
tracks every org/member/venue/event row it creates and deletes them in
`@AfterEach`. Verified before and after this test run that the suite leaves
no net rows behind:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM organizations) AS orgs,
          (SELECT count(*) FROM organization_members) AS members,
          (SELECT count(*) FROM organization_member_roles) AS roles,
          (SELECT count(*) FROM venues) AS venues,
          (SELECT count(*) FROM events) AS events;"

 orgs | members | roles | venues | events
------+---------+-------+--------+--------
    0 |       0 |     1 |      0 |      3
```

`organizations`/`organization_members`/`venues` are all `0` — fully clean.
Re-ran `EventOrganizationAccessIntegrationTest` alone and re-checked: the
counts are byte-for-byte identical before and after (`organization_member_roles`
stayed at `1`, `events` stayed at `3`), confirming these are pre-existing
rows from earlier manual/curl testing sessions (same pattern noted as
pre-existing in `organization-test-results.md`/`venue-test-results.md`), not
leaked by this test run. The `1` orphaned `organization_member_roles` row
(no matching `organization_members` or `organizations` row) predates this
session and is unrelated to any of the tests added here — it was not
created or touched by this run, so it's noted for visibility but is not
this phase's bug to fix.
