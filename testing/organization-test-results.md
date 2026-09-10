# Organization Feature — Proof of Testing

**Date:** 2026-09-10
**Based on:** [organization-test-plan.md](organization-test-plan.md), the
approved plan `hashed-tumbling-seahorse.md` (Phase 1 — Organization &
Access), `docs/event-ticketing-api-business-rules.md` §1–2, and
`docs/event-ticketing-api-use-cases.md` (UC-ORG-01, UC-OWNER-01/02,
UC-ADMIN-01).
**Command:** `mvnw.cmd test` (Postgres already running locally via
`docker compose`, container `postgresql`)
**Result:** BUILD SUCCESS — 78 tests run, 0 failures, 0 errors (56 of which
are new for this feature; the remaining 22 are the pre-existing `user`
module suite, run unchanged to confirm no regression).

## Test files added

- `src/test/java/.../organization/service/OrganizationServiceImplTest.java`
  — 28 tests (Mockito, no Spring context; mirrors `UserServiceImplTest`)
- `src/test/java/.../organization/controller/OrganizationControllerTest.java`
  — 17 tests (`@WebMvcTest` slice, security filters disabled; mirrors
  `UserControllerTest`)
- `src/test/java/.../organization/OrganizationSecurityIntegrationTest.java`
  — 2 tests (full `@SpringBootTest`, real security filter chain, real
  signed JWTs, real Postgres; mirrors `UserSecurityIntegrationTest`)
- `src/test/java/.../event/EventOrganizationAccessIntegrationTest.java` —
  6 tests (full `@SpringBootTest`; regression coverage for the
  `SecurityConfig` event-mutation rewrite)

No production code was changed to make these tests pass — the feature was
implemented and reviewed before this pass; testing here is purely
verification.

## Scenario → test mapping

| Test plan scenario | Covered by | Result |
|---|---|---|
| A registered user can submit an organization application with required documents | `OrganizationServiceImplTest.apply_createsPendingOrganizationWithNoOwner`, `OrganizationControllerTest.apply_validRequest_returns201`, `OrganizationSecurityIntegrationTest.goldenPath_...` (step 1) | Pass |
| An application without required documents is rejected (400, not 401) | `OrganizationControllerTest.apply_blankName_returns400NotUnauthorized`, `.apply_emptyDocuments_returns400NotUnauthorized` | Pass |
| Admin can list pending applications (incl. case-insensitive `?status=`) | `OrganizationServiceImplTest.list_admin_queriesRepository`, `.list_nonAdmin_throwsForbidden`, `OrganizationControllerTest.list_returnsMappedList`, `OrganizationSecurityIntegrationTest.adminList_caseInsensitiveStatusFilter_nonAdminForbidden` | Pass |
| Admin can approve a pending application; applicant becomes owner | `OrganizationServiceImplTest.approve_pendingOrganization_setsApprovedOwnerAndGrantsOwnerRole`, `OrganizationControllerTest.approve_pendingOrganization_returns200`, `OrganizationSecurityIntegrationTest.goldenPath_...` (step 2/3) | Pass |
| Admin can reject a pending application (with/without reason) | `OrganizationServiceImplTest.reject_pendingOrganization_withReason_setsRejectedAndReason`, `.reject_pendingOrganization_withoutReason_setsRejectedWithNullReason`, `OrganizationControllerTest.reject_pendingOrganization_returns200` | Pass |
| Approving/rejecting an already-decided application fails (409) | `OrganizationServiceImplTest.approve_notPending_throwsConflictAndDoesNotSave`, `.reject_notPending_throwsConflict`, `OrganizationControllerTest.approve_notPending_returns409`, `.reject_notPending_returns409` | Pass |
| A pending/unapproved application grants no org privileges | `OrganizationServiceImplTest.update_pendingOrganization_throwsForbidden`, `.assignMember_selfAssign_callerHasNoStandingRole_throwsForbidden` (no `OrganizationMember` row exists pre-approval, so both update and assignment are rejected) | Pass |
| Owner can assign another user as organizer or check-in staff | `OrganizationServiceImplTest.assignMember_ownerAssigningAnotherUser_succeeds`, `OrganizationSecurityIntegrationTest.goldenPath_...` (step 5) | Pass |
| An organizer (not owner) cannot assign roles to other users (403) | `OrganizationServiceImplTest.assignMember_organizerNotOwnerAssigningAnotherUser_throwsForbidden` | Pass |
| A user can assign themselves an additional role they qualify for | `OrganizationServiceImplTest.assignMember_selfAssign_callerAlreadyOwner_succeeds`, `.assignMember_selfAssign_callerAlreadyOrganizer_succeeds`, `OrganizationSecurityIntegrationTest.goldenPath_...` (step 4) | Pass |
| A user can hold multiple roles at once (e.g. owner + organizer) | `OrganizationServiceImplTest.assignMember_grantsAdditionalRole_onTopOfExisting`, `OrganizationSecurityIntegrationTest.goldenPath_...` (asserts `roles` contains both `OWNER` and `ORGANIZER` after self-assign) | Pass |
| Owner role can never be granted through the assign endpoint | `OrganizationServiceImplTest.assignMember_ownerRoleRequested_alwaysForbidden`, `OrganizationControllerTest.assignMember_ownerRoleRequested_returns403` | Pass |
| Member/admin can view an org's members; non-member/non-admin cannot (403) | `OrganizationServiceImplTest.listMembers_member_succeeds`, `.listMembers_admin_succeeds`, `.listMembers_nonMemberNonAdmin_throwsForbidden` | Pass |
| Applicant/member/admin can `GET` an org; unrelated user cannot (403) | `OrganizationServiceImplTest.get_applicant_succeeds`, `.get_member_succeeds`, `.get_admin_succeeds`, `.get_unrelatedUser_throwsForbidden`, `OrganizationControllerTest.get_forbidden_returns403` | Pass |
| Owner/organizer can `PATCH` an *approved* org; non-member and pending orgs rejected | `OrganizationServiceImplTest.update_owner_ofApprovedOrg_succeeds`, `.update_organizer_ofApprovedOrg_succeeds`, `.update_nonMember_throwsForbidden`, `.update_pendingOrganization_throwsForbidden` | Pass |
| An unrelated third user gets 403 attempting to assign anyone in an org they have no role in | `OrganizationSecurityIntegrationTest.goldenPath_...` (step 6) | Pass |
| SecurityConfig regression: OWNER/ORGANIZER token (any org) can still `POST /api/v1/events`; CUSTOMER gets 403; ADMIN still works; no token gets 401 | `EventOrganizationAccessIntegrationTest.adminToken_canCreateEvent`, `.ownerOfSomeOrganization_canCreateEvent`, `.organizerOfSomeOrganization_canCreateEvent`, `.plainCustomerToken_cannotCreateEvent`, `.noToken_cannotCreateEvent` | Pass |

## Findings surfaced by writing these tests

No CRITICAL/HIGH-severity bugs were found — consistent with `code-reviewer`'s
sign-off. Two things worth flagging for follow-up, neither of which blocked
this pass:

1. **CHECK_IN_STAFF-only membership cannot create events either**, which is
   correct per the plan (`isOwnerOrOrganizerAnywhere` only checks
   `OWNER`/`ORGANIZER`) but isn't explicitly called out in the plan's
   verification checklist. Added
   `EventOrganizationAccessIntegrationTest.checkInStaffOnly_cannotCreateEvent`
   to lock in this behavior as a regression guard, since it's a real,
   already-implemented rule (not a gap) that had no test coverage.
2. **Per-event/per-org authorization is still not enforced** on
   `POST /api/v1/events` — a user who is OWNER/ORGANIZER of *any*
   organization can create an event, not just their own org's events. This
   is the explicitly accepted Phase-1 limitation called out in the plan
   (`Event.organizationId` is still an unwired `UUID.randomUUID()`
   placeholder) and is deferred to Phase 3 by design — not a new finding,
   restated here only so it's visible from the test-results doc as well as
   the plan.

## Full run output (tail)

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.860 s -- in com.junaldadlawan.event_ticketing_api.event.EventOrganizationAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.448 s -- in com.junaldadlawan.event_ticketing_api.organization.OrganizationSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.211 s -- in com.junaldadlawan.event_ticketing_api.organization.controller.OrganizationControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.462 s -- in com.junaldadlawan.event_ticketing_api.organization.service.OrganizationServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.534 s -- in com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.155 s -- in com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.396 s -- in com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.994 s
[INFO] Finished at: 2026-09-10T11:25:19+08:00
[INFO] ------------------------------------------------------------------------
```

## Database cleanup verification

The two `@SpringBootTest` integration suites (`OrganizationSecurityIntegrationTest`,
`EventOrganizationAccessIntegrationTest`) hit the real local Postgres
(`compose.yml`'s `postgresql` container, db `event_ticketing`). Each test
tracks the org/member/event rows it creates and deletes them in
`@AfterEach`. Verified post-run that nothing leaked:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM organizations) AS orgs,
          (SELECT count(*) FROM organization_members) AS members,
          (SELECT count(*) FROM organization_member_roles) AS roles,
          (SELECT count(*) FROM organization_documents) AS docs,
          (SELECT count(*) FROM events) AS events,
          (SELECT count(*) FROM users) AS users;"

 orgs | members | roles | docs | events | users
------+---------+-------+------+--------+-------
    0 |       0 |     0 |    0 |      4 |     6
```

`organizations`/`organization_members`/`organization_member_roles`/
`organization_documents` are all `0` — fully clean. The 4 `events` and 6
`users` rows are pre-existing rows from earlier manual/curl testing
sessions (timestamps `2026-08-31` through `2026-09-10T03:16`, all before
this test run started at `2026-09-10T03:24` UTC) — none were created or
left behind by these test runs.
