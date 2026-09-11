# Refund Test Plan

**Status:** Implemented

Covers refunds and event-cancellation-triggered refunds. See
`testing/refund-payout-test-results.md` for the full scenario → test mapping
and proof-of-testing output.

## Test Scenarios

- [x] Organizer/admin can issue a full refund
- [x] Organizer/admin can issue a partial refund
- [x] Refund outside the event's refund policy window fails (409)
- [x] Cancelling an event triggers a refund for every ticket holder on it
- [x] Non-organizer/non-admin cannot issue a refund (403)

## Additional scenarios covered beyond the original plan

- [x] A `NO_REFUNDS` policy (or no policy row at all) denies organizer/admin
      refund requests (409), but does NOT block the mandatory
      cancellation-triggered refund path
- [x] A `CUSTOM` policy always allows a refund (its terms are informational,
      not machine-checked)
- [x] Omitting `amount` refunds the full remaining balance; an explicit
      `amount` exceeding the remaining balance is rejected (400), as is a
      currency mismatch against the order's own currency
- [x] A refund reaching the order's full total marks the order `REFUNDED`
      and every one of its tickets `REFUNDED`; a partial refund marks the
      order `PARTIALLY_REFUNDED` and leaves tickets `VALID`
- [x] A prior partial refund followed by a second refund reaching the full
      total correctly transitions to `REFUNDED` (cumulative math, not a
      flat re-check)
- [x] A gateway-declined refund still creates a `FAILED` `Refund` row (HTTP
      201 — "initiated", not an error) and leaves the order/tickets untouched
- [x] `GET /orders/{orderId}/refunds` visibility (buyer, event
      organizer/owner, or admin) is broader than `POST`'s organizer/admin-only
      gate — the order's own buyer cannot self-initiate a refund
- [x] `GET /events/{eventId}/refund-policy` is NOT public (unlike resale
      policy) — anonymous → 401, authenticated non-buyer/non-organizer
      stranger → 403, a buyer with a ticket on the event (no org role) → 200
- [x] Event cancellation's refund sweep is best-effort per order: one
      order's failure (e.g. a missing `Payment` row) does not stop the rest
      of the event's orders from being refunded; already
      `REFUNDED`/`CANCELLED` orders are skipped, not re-refunded
