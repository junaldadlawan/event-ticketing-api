# Organization Test Plan

**Status:** Implemented

Covers the organization application/approval flow and org-scoped role
assignment (owner, organizer, check-in staff), plus the regression risk on
`SecurityConfig`'s event-mutation endpoints introduced by this feature (they
now authorize via org membership instead of the old flat `ORGANIZER` role).

## Test Scenarios

- [x] A registered user can submit an organization application with
      required documents
- [x] An application without required documents is rejected (400, not 401 —
      regression test for the `GlobalExceptionHandler` bug)
- [x] Admin can list pending applications (including case-insensitive
      `?status=` filtering)
- [x] Admin can approve a pending application; applicant becomes owner
- [x] Admin can reject a pending application (with and without a reason)
- [x] Approving/rejecting an already-decided application fails (409)
- [x] A pending or still-unapproved application grants no org privileges
      (no `OrganizationMember` row exists until approval, so self/other
      assignment and update are all rejected pre-approval)
- [x] Owner can assign another user as organizer or check-in staff
- [x] An organizer (not owner) cannot assign roles to other users (403)
- [x] A user can assign themselves an additional role they qualify for
- [x] A user can hold multiple roles at once (e.g. owner + organizer)
- [x] Owner or admin can list the members belonging to an organization
      they're allowed to see; a non-member/non-admin cannot (403)
- [x] A plain CUSTOMER (or any user without org membership) cannot create
      events; a user holding OWNER/ORGANIZER in some org still can, and so
      can ADMIN — proving the `SecurityConfig` event-mutation rewrite didn't
      regress event creation

See [organization-test-results.md](organization-test-results.md) for the
executed test run and which automated test covers each scenario.
