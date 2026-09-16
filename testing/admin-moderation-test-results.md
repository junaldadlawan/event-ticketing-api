# Admin Moderation Actions (Phase 12) — Proof of Testing

**Date:** 2026-09-16
**Branch:** `feat/phase12-disputes-moderation`
**Based on:** `testing/admin-moderation-test-plan.md`, `BR-ADMIN-002` — the
new `moderation/` module (`ModerationAction` entity, `ModerationActionRepository`,
`ModerationActionService`/`Impl`, `ModerationActionController`), migrations
`V21__add_moderation_actions_table.sql` and
`V22__add_suspension_support.sql`, the new `EventStatus.SUSPENDED`/
`OrganizationStatus.SUSPENDED`/`AccountStatus{ACTIVE,SUSPENDED}` enum
values, the new `SecurityConfig` matcher for `/api/v1/admin/**`, and the
four enforcement points: `EventServiceImpl.updateEvent`,
`VenueServiceImpl.create`, `AuthServiceImpl.login`, plus verification (no
code change) of `EventServiceImpl.publishEvent`/`listEvents` and
`CartServiceImpl`'s add-item allow-list.

Like disputes, this was a from-scratch implementation (original design,
confirmed this session) with full three-tier coverage added in the same
pass, plus a dedicated end-to-end suspension-enforcement suite.

**Command:** `mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.moderation.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result:** BUILD SUCCESS — 47 tests run, 0 failures, 0 errors.

```
[INFO] Running com.junaldadlawan.event_ticketing_api.moderation.controller.ModerationActionControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.186 s
[INFO] Running com.junaldadlawan.event_ticketing_api.moderation.ModerationActionIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.690 s
[INFO] Running com.junaldadlawan.event_ticketing_api.moderation.service.ModerationActionServiceImplTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.528 s
[INFO] Running com.junaldadlawan.event_ticketing_api.moderation.SuspensionEnforcementIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.739 s
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

## Test files added

- `src/test/java/.../moderation/service/ModerationActionServiceImplTest.java`
  — 23 tests (Mockito, no Spring context). The admin-only gate on both
  `create`/`list`; the SUSPEND/REINSTATE/REMOVE matrix for all three target
  types; `previousStatus` capture on SUSPEND; the REINSTATE lookback via
  `ModerationActionRepository.findFirstByTargetIdAndActionOrderByCreatedAtDesc`
  (including the no-prior-SUSPEND-row default: `APPROVED` for Organization,
  `DRAFT` for Event); the already-suspended / not-currently-suspended /
  already-removed conflict guards; and that `REMOVE` genuinely delegates to
  `EventService.delete`/`UserService.delete` rather than duplicating the
  soft-delete logic.
- `src/test/java/.../moderation/controller/ModerationActionControllerTest.java`
  — 7 tests (`@WebMvcTest` slice, security filters disabled). Request
  validation and 200/201/400/403/404/409 status mapping.
- `src/test/java/.../moderation/ModerationActionIntegrationTest.java` — 9
  tests (full `@SpringBootTest`, real filter chain, real signed JWTs, real
  Postgres). Real `Organization`/`Event`/`User` rows suspended and
  reinstated via real `POST /api/v1/admin/moderation-actions` calls, with
  the resulting status verified straight from Postgres afterward — proving
  the endpoint's effect is a real status mutation, not just a log row.
  Includes the HTTP-layer 401/403 checks (`SecurityConfig`'s
  `hasRole("ADMIN")` matcher) and the `GET` filter-by-`targetType`/`targetId`
  case.
- `src/test/java/.../moderation/SuspensionEnforcementIntegrationTest.java`
  — 8 tests (full `@SpringBootTest`, real filter chain, real Postgres). The
  dedicated proof that `SUSPENDED` is genuinely enforced downstream, against
  real (directly-persisted) suspended `Organization`/`Event`/`User` rows —
  see the dispatch's "SuspensionEnforcementIntegrationTest-style test...
  proving the 4 enforcement points actually work end-to-end" requirement.

## Scenario → test mapping

| `admin-moderation-test-plan.md` scenario | Covered by | Result |
|---|---|---|
| Suspend changes real status, not just a log entry | `ModerationActionServiceImplTest.create_organizationSuspend_capturesPreviousStatus_notifiesOwner`, `.create_eventSuspend_capturesPreviousStatus_noNotification`, `.create_userSuspend_previousStatusAlwaysActive_notifiesUser`, `ModerationActionIntegrationTest.suspendReinstateOrganization_realStatusMutatesInPostgres`, `.suspendEvent_realStatusMutatesInPostgres`, `.suspendReinstateUser_realAccountStatusMutatesInPostgres` | Pass |
| Reinstate restores the pre-suspension status | `ModerationActionServiceImplTest.create_organizationReinstate_restoresFromLatestSuspendAction`, `.create_userReinstate_setsActive`, `ModerationActionIntegrationTest.suspendReinstateOrganization_realStatusMutatesInPostgres`, `.suspendReinstateUser_realAccountStatusMutatesInPostgres` | Pass |
| Remove reuses each target's own soft-delete | `ModerationActionServiceImplTest.create_organizationRemove_marksDeleted`, `.create_eventRemove_delegatesToEventServiceDelete`, `.create_userRemove_delegatesToUserServiceDelete`, `ModerationActionIntegrationTest.removeEvent_delegatesToRealSoftDelete` | Pass |
| Non-admin cannot create/list (403) | `ModerationActionServiceImplTest.create_nonAdmin_throwsForbidden`, `.list_nonAdmin_throwsForbidden`, `ModerationActionControllerTest.create_nonAdmin_returns403`, `ModerationActionIntegrationTest.create_nonAdmin_returns403`, `.list_nonAdmin_returns403` (HTTP-layer `hasRole("ADMIN")`) | Pass |
| Already-suspended/not-suspended/already-removed → 409 | `ModerationActionServiceImplTest.create_organizationSuspend_alreadySuspended_throwsConflict`, `.create_organizationReinstate_notCurrentlySuspended_throwsConflict`, `.create_organizationRemove_alreadyRemoved_throwsConflict`, `.create_userSuspend_alreadySuspended_throwsConflict`, `.create_userReinstate_notSuspended_throwsConflict`, `.create_userRemove_alreadyRemoved_throwsConflict`, `.create_eventRemove_alreadyRemoved_throwsConflict`, `ModerationActionIntegrationTest.suspendUserTwice_returns409` | Pass |
| Unknown target → 404 | `.create_organizationUnknown_throwsResourceNotFound`, `.create_userUnknown_throwsResourceNotFound`, `ModerationActionControllerTest.create_unknownTarget_returns404` | Pass |
| `GET` optional filters | `.list_bothFiltersPresent_delegatesToFindByTargetTypeAndTargetId`, `.list_noFilters_delegatesToFindAll`, `ModerationActionIntegrationTest.list_admin_filtersByTargetType` | Pass |
| Best-effort notification (USER/ORGANIZATION yes, EVENT no) | `.create_organizationSuspend_capturesPreviousStatus_notifiesOwner` (verifies `notify(...)` called), `.create_eventSuspend_capturesPreviousStatus_noNotification` (`verifyNoInteractions(notificationService)`), `.create_userSuspend_previousStatusAlwaysActive_notifiesUser` | Pass |
| Suspended Event cannot be updated | `SuspensionEnforcementIntegrationTest.updateEvent_suspended_returns409` | Pass |
| Suspended Event cannot be published (already-correct) | `.publishEvent_suspended_returns409` | Pass |
| Suspended Event excluded from cart add-item (already-correct) | `.addCartItem_suspendedEvent_returns409` | Pass |
| Suspended Event excluded from public search (already-correct) | `.publicEventSearch_excludesSuspendedEvent` | Pass |
| Suspended Organization cannot create venues | `.createVenue_suspendedOrganization_returns403` | Pass |
| Suspended account cannot log in, correct password | `.login_suspendedAccount_correctPassword_returns403` | Pass |
| Suspended account + wrong password → 401, not 403 (no info leak) | `.login_suspendedAccount_wrongPassword_stillReturns401_notLeaking403` | Pass |
| Active account login still works (regression guard) | `.login_activeAccount_correctPassword_returns200` | Pass |

## Findings surfaced by writing these tests

No production bugs were found in the moderation logic itself — again, a
from-scratch implementation tested in the same pass. Two things worth
recording:

1. **The initial `SuspensionEnforcementIntegrationTest` drafts for
   `updateEvent`/`publishEvent` got 403, not 409, on the first run.** The
   test only persisted the `Organization` and `Event` rows directly,
   without granting the caller an `OWNER`/`ORGANIZER` `OrganizationMember`
   role — so `EventServiceImpl`'s own
   `requireOwnerOrOrganizerOrAdmin` check rejected the caller before the
   code ever reached the new suspended-status check. Fixed by adding the
   same `grantOrgRole` helper `CartAccessIntegrationTest` uses. Not a
   production bug — the test was under-set-up.
2. **`ModerationActionServiceImplTest`'s shared `@BeforeEach` stub for
   `moderationActionRepository.save(...)` needed `lenient()`.** Several
   conflict/not-found/forbidden tests never reach the `save` call at all,
   and Mockito's strict-stubbing mode correctly flagged the unused stub in
   those tests as `UnnecessaryStubbing`. Fixed by marking that one shared
   stub `lenient()` (it's still exercised by every success-path test).

## Judgment calls made beyond the dispatch's explicit spec

- **Event `REINSTATE`'s no-prior-SUSPEND-row default is `DRAFT`.** The
  dispatch only specified `APPROVED` as Organization's fallback explicitly;
  for Event it said "same pattern" without naming a default. `DRAFT` was
  chosen as the narrowest, safest fallback (every event starts there) —
  documented in a code comment on `ModerationActionServiceImpl.applyToEvent`
  and the roadmap entry.
- **User `REMOVE`'s `previousStatus`** is recorded as the account's
  `accountStatus` at the moment of removal (`ACTIVE` or `SUSPENDED`),
  for consistency with how Organization/Event's `REMOVE` records their
  pre-removal status — the dispatch's "previousStatus is always just
  ACTIVE" note was specifically about `SUSPEND`'s previousStatus, not
  `REMOVE`'s.

## Database cleanup verification

Both integration test files persist real `Organization`/`Event`/`User`/
`ModerationAction`/`Notification` rows against the shared local Postgres
instance. Each test tracks every row it creates and deletes them in
`@AfterEach`.

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'moderation_actions' t, count(*) from moderation_actions;"

         t          | count
--------------------+-------
 moderation_actions |     0
```

Fully clean — no leftover rows from this pass's tests. (`organizations`/
`events`/`users` carry pre-existing residue from earlier manual/other test
sessions, documented in prior phases' results docs — not touched by this
pass beyond rows this pass created and cleaned up itself.)
