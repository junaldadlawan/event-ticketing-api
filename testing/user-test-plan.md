# User Test Plan

**Status:** Implemented

Covers admin-managed user listing, updates, password changes, and
soft-delete. This is the platform-wide `GET /users` listing (admin only) —
an organization owner listing *their own org's* users is a separate,
narrower capability covered in `organization-test-plan.md`.

## Test Scenarios

- [x] Admin can list all users platform-wide
- [x] Non-admin, non-org-owner cannot list users (403)
- [x] Password is stored hashed, never in plain text
- [x] Admin can update a user's name/email/role
- [x] Admin can update a user's password
- [x] Admin can soft-delete a user
- [x] Soft-deleted users don't appear in the user list
- [x] Updating/deleting a non-existent user returns 404
- [x] Non-admin cannot update/delete/change password for any user (403)

See [user-test-results.md](user-test-results.md) for the executed test run
and which automated test covers each scenario.
