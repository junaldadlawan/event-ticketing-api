# Authentication Test Plan

**Status:** Implemented

Covers register, login, refresh, logout, and token/role enforcement on
protected endpoints.

## Test Scenarios

- [ ] Register a new user succeeds
- [ ] Register with a duplicate email fails
- [ ] Login with correct credentials returns an access + refresh token
- [ ] Login with wrong password fails (401)
- [ ] Login with unknown email fails (401) — same error as wrong password
- [ ] Refresh with a valid refresh token returns a new access token
- [ ] Refresh with an expired refresh token fails (401)
- [ ] Refresh with an access token (wrong type) fails (401)
- [ ] Refresh with a revoked refresh token fails (401)
- [ ] Logout revokes the refresh token
- [ ] Logout with an already-revoked or garbage token still returns 204
- [ ] Calling a protected endpoint with no token fails (401)
- [ ] Calling a protected endpoint with an expired/malformed token fails (401)
- [ ] Calling a role-gated endpoint with the wrong role fails (403)
