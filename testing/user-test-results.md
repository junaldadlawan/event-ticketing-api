# User Feature — Proof of Testing

**Date:** 2026-09-10
**Based on:** [user-test-plan.md](user-test-plan.md)
**Command:** `mvnw.cmd test`
**Result:** BUILD SUCCESS — 25 tests run, 0 failures, 0 errors (16 of
which are the new tests added for this feature; the rest are pre-existing).

## Test files added

- `src/test/java/.../user/service/UserServiceImplTest.java` — 11 tests
  (Mockito, no Spring context)
- `src/test/java/.../user/controller/UserControllerTest.java` — 9 tests
  (`@WebMvcTest` slice, security filters disabled — validates request
  handling, not auth)
- `src/test/java/.../user/UserSecurityIntegrationTest.java` — 4 tests
  (full `@SpringBootTest`, real security filter chain, real signed JWTs)

## Scenario → test mapping

| Test plan scenario | Covered by | Result |
|---|---|---|
| Admin can list all users platform-wide | `UserSecurityIntegrationTest.adminToken_canListUsers` | ✅ Pass |
| Non-admin, non-org-owner cannot list users (403) | `UserSecurityIntegrationTest.nonAdminToken_cannotListUsers` | ✅ Pass |
| (also) no token at all cannot list users (401) | `UserSecurityIntegrationTest.noToken_cannotListUsers` | ✅ Pass |
| Password is stored hashed, never in plain text | `UserServiceImplTest.register_hashesPasswordBeforeSaving` | ✅ Pass |
| Admin can update a user's name/email/role | `UserServiceImplTest.update_updatesNameEmailRole`, `UserControllerTest.update_validRequest_returnsUpdatedUser` | ✅ Pass |
| Admin can update a user's password | `UserServiceImplTest.updatePassword_encodesNewPasswordBeforeSaving`, `UserControllerTest.updatePassword_validRequest_returns200` | ✅ Pass |
| Admin can soft-delete a user | `UserServiceImplTest.delete_marksUserDeletedAndSaves`, `UserControllerTest.delete_existingUser_returns204` | ✅ Pass |
| Soft-deleted users don't appear in the user list | `UserServiceImplTest.getUsers_excludesSoftDeletedRows`, `UserSecurityIntegrationTest.adminToken_listExcludesSoftDeletedUser` | ✅ Pass |
| Updating/deleting a non-existent user returns 404 | `UserServiceImplTest.update_unknownId_throwsResourceNotFoundException`, `.delete_unknownId_throwsResourceNotFoundException`, `.getOrThrow_unknownId_throwsResourceNotFoundException`, `UserControllerTest.update_unknownId_returns404`, `.delete_unknownId_returns404` | ✅ Pass |
| Non-admin cannot update/delete/change password for any user (403) | Covered by the same `SecurityConfig` matcher as list (`/api/v1/users/**` → `ADMIN`); proven generally by `UserSecurityIntegrationTest.nonAdminToken_cannotListUsers` — not re-verified per-verb since the rule is a single matcher, not per-endpoint logic | ✅ Pass (by the shared rule) |
| Validation: missing required field on update → 400 | `UserControllerTest.update_missingRequiredField_returns400` | ✅ Pass |
| Validation: missing password field → 400 | `UserControllerTest.updatePassword_missingPasswordField_returns400` | ✅ Pass |

## Finding surfaced by writing these tests (not fixed, flagged only)

**`UserPasswordUpdateRequest.passwordHash` accepts an empty string.** It's
annotated `@NotNull` but not `@NotBlank`, so `{"passwordHash":""}` passes
validation and reaches the service today. Documented as-is in
`UserControllerTest.updatePassword_blankPassword_currentlyAcceptedByValidation`
(asserts the real `200`, not an assumed `400`) rather than silently
tightening production validation as a side effect of writing tests. Worth
a follow-up if you want it fixed.

## Full run output (tail)

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- EventTicketingApiApplicationTests
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- UserControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- UserServiceImplTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- UserSecurityIntegrationTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
