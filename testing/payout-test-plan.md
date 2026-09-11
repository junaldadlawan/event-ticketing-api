# Payout Test Plan

**Status:** Implemented (partial — read-only)

Covers viewing an organization's payout history. See
`testing/refund-payout-test-results.md` for the full scenario → test mapping
and proof-of-testing output.

There is **no payout-generation job anywhere in this codebase** — `payouts`
will always be empty via any real flow (`POST`/creation is deliberately out
of scope for this phase per openapi.yaml). Every `Payout` row exercised by
these tests is seeded directly via `PayoutRepository.save(...)`, not
produced by a real background process — if/when a generation job is built,
this plan should gain scenarios for it.

## Test Scenarios

- [x] Organization owner/admin can view payout history
- [x] A non-member cannot view another organization's payouts (403)
- [x] Payouts are read-only via the API (no create/update endpoint exists —
      confirmed by inspecting `OrganizationPayoutController`, which only
      exposes `GET`)

## Additional scenarios covered beyond the original plan

- [x] Organizer (not just owner) can view payout history
- [x] Admin bypasses the organization-role check entirely, even with no
      organization membership at all
- [x] Unknown organization → 404
- [x] Paginated response shape (`content`/`totalElements`) matches this
      codebase's established `PageResponse` convention, not openapi.yaml's
      literally-documented plain-array shape — consistent with every other
      paginated list in this codebase
