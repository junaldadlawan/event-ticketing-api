# Admin Moderation Test Plan

**Status:** Built (Phase 12) — see `testing/admin-moderation-test-results.md`

Covers `BR-ADMIN-002`: a single unified, append-only `ModerationAction` log
for suspending/reinstating/removing an Organization, Event, or User, and the
real enforcement of the resulting `SUSPENDED` status across the four wiring
points the Phase 12 dispatch called out. This module is an original design
confirmed this session — not in `openapi.yaml`/the ERD.

## Test Scenarios

- [x] Admin can suspend an Organization/Event/User; the target's real
      status/account state changes, not just a log entry
- [x] Admin can reinstate a suspended Organization/Event/User, restoring the
      status captured before suspension
- [x] Admin can remove an Organization/Event/User, reusing each target's
      own existing soft-delete
- [x] Non-admin cannot create or list moderation actions (403), enforced
      both at the HTTP layer (`SecurityConfig`) and the service layer
- [x] SUSPEND on an already-suspended target, REINSTATE on a non-suspended
      one, and REMOVE on an already-removed one all return 409
- [x] Unknown target id returns 404
- [x] `GET /admin/moderation-actions` supports optional `targetType`/
      `targetId` filters
- [x] A best-effort `ACCOUNT_MODERATION_ACTION` notification fires for
      `USER` actions and `ORGANIZATION` actions with an owner; `EVENT`
      actions fire none (no single obvious recipient)
- [x] **Suspension enforcement, end-to-end against real suspended targets:**
  - [x] A suspended Event cannot be updated (`PATCH /events/{id}` → 409)
  - [x] A suspended Event cannot be published (`POST /events/{id}/publish` → 409, already-correct exclusion verified)
  - [x] A suspended Event cannot receive new cart items (already-correct allow-list exclusion verified)
  - [x] A suspended Event is excluded from public event search (already-correct filter verified)
  - [x] A suspended Organization cannot have new venues created (`POST /organizations/{orgId}/venues` → 403)
  - [x] A suspended User cannot log in (403) even with the correct password
  - [x] A suspended User with the WRONG password still gets a generic 401, not a 403 (no account-existence/suspension leak)
