# Venue Feature — Proof of Testing

**Date:** 2026-09-10
**Based on:** [venue-test-plan.md](venue-test-plan.md), `openapi.yaml`'s
`Venues` paths (`/organizations/{orgId}/venues`, `/venues/{venueId}`, ~lines
367-428) and schemas (`Venue`/`VenueCreate`/`VenueUpdate`, ~lines 1728-1758).
No `BR-VENUE-*`/`UC-VENUE-*` docs exist for this module, so openapi.yaml is
the authoritative behavior spec. `developer` implemented the module and
`code-reviewer` signed off with no CRITICAL/HIGH findings (two MEDIUM items,
both pre-existing/already-fixed, not blocking — see the reviewer's notes).
All verification prior to this pass was manual/curl-based; this is the first
automated-test pass for the module.
**Command:** `mvnw.cmd test` (Postgres already running locally via
`docker compose`, container `postgresql`)
**Result:** BUILD SUCCESS — 115 tests run, 0 failures, 0 errors (37 of which
are new for this feature; the remaining 78 are the pre-existing
`user`/`organization`/`event` suites, run unchanged to confirm no
regression).

## Test files added

- `src/test/java/.../venue/service/VenueServiceImplTest.java` — 19 tests
  (Mockito, no Spring context; mirrors `OrganizationServiceImplTest`)
- `src/test/java/.../venue/controller/OrganizationVenueControllerTest.java`
  — 7 tests (`@WebMvcTest` slice, security filters disabled; mirrors
  `OrganizationControllerTest`)
- `src/test/java/.../venue/controller/VenueControllerTest.java` — 6 tests
  (`@WebMvcTest` slice, security filters disabled)
- `src/test/java/.../venue/VenueSecurityIntegrationTest.java` — 5 tests
  (full `@SpringBootTest`, real security filter chain, real signed JWTs,
  real Postgres; mirrors `OrganizationSecurityIntegrationTest`)

No production code was changed to make these tests pass — the feature was
already implemented and reviewed before this pass; testing here is purely
verification.

## Scenario → test mapping

| Test plan scenario | Covered by | Result |
|---|---|---|
| Org owner/organizer can create a venue for their organization | `VenueServiceImplTest.create_owner_succeeds`, `.create_organizer_succeeds`, `OrganizationVenueControllerTest.create_validRequest_returns201`, `VenueSecurityIntegrationTest.goldenPath_...` (step 1) | Pass |
| A venue can be created without an address (virtual venue) | `VenueServiceImplTest.create_virtualVenue_noAddress_succeeds` | Pass |
| Anyone can view a venue's public details, no login required | `VenueServiceImplTest.get_noSecurityContext_succeedsWithoutTouchingAccessGuard`, `VenueControllerTest.get_existingVenue_returns200`, `VenueSecurityIntegrationTest.goldenPath_...` (step 4 — real `GET` with **no** `Authorization` header at all through the real filter chain) | Pass |
| Org owner/organizer can update their own venue | `VenueServiceImplTest.update_nameOnly_changesOnlyName`, `.update_addressOnly_changesOnlyAddress`, `VenueControllerTest.update_validRequest_returns200`, `VenueSecurityIntegrationTest.goldenPath_...` (steps 5/6, both owner and organizer) | Pass |
| A user from a different organization cannot update someone else's venue (403) | `VenueServiceImplTest.update_nonOwnerNonOrganizerOfVenuesOrg_throwsForbidden`, `.update_authorizationAlwaysResolvedFromPersistedVenuesOrganization_notClientInput`, `VenueSecurityIntegrationTest.goldenPath_...` (step 8 — cross-org isolation: owner of a *different*, real, persisted org still gets 403) | Pass |
| A non-member of the org (or check-in-staff-only member) cannot create or list that org's venues (403) | `VenueServiceImplTest.create_nonOwnerNonOrganizer_throwsForbidden`, `.list_nonMember_throwsForbidden`, `VenueSecurityIntegrationTest.create_nonMemberOfOrg_returns403`, `.create_checkInStaffOnly_returns403`, `.goldenPath_...` (step 3) | Pass |
| `get()` performs no authorization check at all | `VenueServiceImplTest.get_noSecurityContext_succeedsWithoutTouchingAccessGuard` (asserts `verifyNoInteractions(accessGuard)`) | Pass |
| Partial update: only present fields change; blank `name` rejected (400) | `VenueServiceImplTest.update_nameOnly_changesOnlyName`, `.update_addressOnly_changesOnlyAddress`, `.update_allFieldsOmitted_leavesVenueUnchanged`, `.update_blankName_throwsBadRequest`, `VenueControllerTest.update_blankName_returns400` | Pass |
| Nonexistent org/venue id returns 404 | `VenueServiceImplTest.create_nonExistentOrganization_throwsResourceNotFound`, `.list_nonExistentOrganization_throwsResourceNotFound`, `.get_unknownId_throwsResourceNotFound`, `.update_unknownVenue_throwsResourceNotFound`, `VenueControllerTest.get_unknownVenue_returns404`, `.update_unknownVenue_returns404`, `OrganizationVenueControllerTest.create_nonExistentOrganization_returns404`, `VenueSecurityIntegrationTest.create_nonExistentOrganization_returns404`, `.goldenPath_...` (step 9, both GET and PATCH) | Pass |
| Soft-deleted venue is not returned by `get()` | `VenueServiceImplTest.get_softDeletedVenue_throwsResourceNotFound` | Pass |
| `PATCH /venues/**` requires authentication; `GET` doesn't | `VenueSecurityIntegrationTest.noToken_cannotPatchVenue` (401), `.goldenPath_...` step 4 (200, no header) | Pass |
| Any member (incl. `CHECK_IN_STAFF`) or admin can list an org's venues | `VenueServiceImplTest.list_member_succeeds`, `.list_admin_succeeds`, `OrganizationVenueControllerTest.list_returnsMappedList`, `VenueSecurityIntegrationTest.goldenPath_...` (step 2) | Pass |
| `VenueController`/`OrganizationVenueController` request validation still maps to 400 (not 401), confirming the Phase-1 `GlobalExceptionHandler` fix | `OrganizationVenueControllerTest.create_missingName_returns400`, `.create_blankName_returns400` | Pass |

## Findings surfaced by writing these tests

No new CRITICAL/HIGH/MEDIUM-severity bugs were found while writing these
tests — consistent with `code-reviewer`'s sign-off. One behavior worth
recording precisely, since the task called it out as the key thing to prove:

1. **`VenueServiceImpl.get()` genuinely never touches `OrganizationAccessGuard`.**
   Confirmed both at the unit level (`verifyNoInteractions(accessGuard)` in
   `get_noSecurityContext_succeedsWithoutTouchingAccessGuard`) and end-to-end
   (`VenueSecurityIntegrationTest.goldenPath_...` step 4 issues a real
   `MockMvc` `GET` with **no** `Authorization` header through the real
   `SecurityConfig`/`JwtAuthenticationFilter` chain and gets `200`). This
   matches `openapi.yaml`'s `security: []` override on `getVenue` and is not
   a gap — it's confirmed-correct, intentional public-read behavior.
2. **Cross-org isolation on `PATCH /venues/{venueId}` is real, not just
   "any non-member is rejected."** `VenueServiceImplTest.update_authorizationAlwaysResolvedFromPersistedVenuesOrganization_notClientInput`
   and `VenueSecurityIntegrationTest.goldenPath_...` step 8 both specifically
   use a caller who **is** OWNER of a different, real organization (not just
   a plain stranger with no role anywhere) and confirm they still get 403 —
   proving the authorization check is resolved from the persisted venue's
   own `organizationId`, never anything the client could influence (there is
   no org field on `VenueUpdateRequest` to begin with).
3. Restating the two known/already-addressed items from the reviewer's
   pass for visibility (neither re-verified here as bugs — the blank-name
   gap is directly covered by `update_blankName_throwsBadRequest`/
   `VenueControllerTest.update_blankName_returns400`, and the soft-deleted-org
   `existsById` gap is shared, pre-existing `OrganizationServiceImpl`
   behavior out of scope for this module's tests):
   - Blank `name` on `PATCH` → `BadRequestException` (400) — already fixed,
     now regression-guarded by this suite.
   - `organizationRepository.existsById(orgId)` doesn't filter
     soft-deleted organizations — pre-existing gap shared with
     `OrganizationServiceImpl`, not new to this module, not blocking.

## Full run output (tail)

```
[INFO] Running com.junaldadlawan.event_ticketing_api.event.EventOrganizationAccessIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.598 s -- in com.junaldadlawan.event_ticketing_api.event.EventOrganizationAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.EventTicketingApiApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.879 s -- in com.junaldadlawan.event_ticketing_api.EventTicketingApiApplicationTests
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.271 s -- in com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.458 s -- in com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.436 s -- in com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.534 s -- in com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.156 s -- in com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.405 s -- in com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.369 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.286 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.104 s -- in com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.589 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 115, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  16.082 s
[INFO] Finished at: 2026-09-10T12:00:39+08:00
[INFO] ------------------------------------------------------------------------
```

## Database cleanup verification

`VenueSecurityIntegrationTest` hits the real local Postgres (`compose.yml`'s
`postgresql` container, db `event_ticketing`) — it persists real
`Organization`/`OrganizationMember` rows (required because
`VenueServiceImpl.create`/`list` call `OrganizationRepository.existsById`
and the real `OrganizationAccessGuard`) and creates real `Venue` rows via
the HTTP API. Each test tracks every org/member/venue row it creates and
deletes them in `@AfterEach`. Verified post-run that nothing leaked:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM organizations) AS orgs,
          (SELECT count(*) FROM organization_members) AS members,
          (SELECT count(*) FROM organization_member_roles) AS roles,
          (SELECT count(*) FROM venues) AS venues,
          (SELECT count(*) FROM events) AS events,
          (SELECT count(*) FROM users) AS users;"

 orgs | members | roles | venues | events | users
------+---------+-------+--------+--------+-------
    0 |       0 |     0 |      0 |      4 |     6
```

`organizations`/`organization_members`/`organization_member_roles`/`venues`
are all `0` — fully clean. The 4 `events` and 6 `users` rows are
pre-existing rows from earlier manual/curl testing sessions (same rows
noted as pre-existing in `organization-test-results.md`), unrelated to and
unmodified by this test run.
