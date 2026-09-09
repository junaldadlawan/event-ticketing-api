# User Test Plan

**Status:** Implemented

Covers admin-managed user listing, updates, password changes, and
soft-delete. This is the platform-wide `GET /users` listing (admin only) —
an organization owner listing *their own org's* users is a separate,
narrower capability covered in `organization-test-plan.md`.

## Test Scenarios

- [ ] Admin can list all users platform-wide
- [ ] Non-admin, non-org-owner cannot list users (403)
- [ ] Password is stored hashed, never in plain text
- [ ] Admin can update a user's name/email/role
- [ ] Admin can update a user's password
- [ ] Admin can soft-delete a user
- [ ] Soft-deleted users don't appear in the user list
- [ ] Updating/deleting a non-existent user returns 404
- [ ] Non-admin cannot update/delete/change password for any user (403)
