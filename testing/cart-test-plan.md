# Cart Test Plan

**Status:** Implemented

Covers cart creation, adding/removing GA and reserved-seating items,
temporary holds (placement, expiry, release, and reclaim by another buyer),
purchasability/sale-window/single-event-per-cart gating, and promo-code
application/removal on a cart. See
`testing/cart-promocode-test-results.md` for the full scenario→test mapping,
concurrency-test output, and findings.

## Test Scenarios

- [x] Buyer can create a cart
- [x] Adding a ticket type to the cart places a temporary hold (~15 min,
      BR-INV-003)
- [x] Hold expiry is lazily released (on the buyer's own next `GET`, and on
      contention from another buyer's `addItem`) — no scheduled job exists
      or is claimed; this is the mechanism actually implemented
- [x] Removing an item releases its hold (GA quantity restored / seat back
      to `AVAILABLE`)
- [x] Adding a seat/quantity that's no longer available fails (409)
- [x] A user can only view/modify their own cart (403 for others) — and,
      notably, there is **no** admin/organizer bypass for cart access at
      all (confirmed explicitly, unlike Event/TicketType/Venue)
- [ ] A user has at most one active cart at a time — **not implemented**:
      `POST /carts` always creates a new cart with no check for an existing
      one for the caller; not asserted as a bug (openapi.yaml's
      `createCart` doesn't document this constraint either), but flagged
      here since the original test plan assumed it
- [x] Two buyers racing for the last unit of GA stock: exactly one succeeds
      (real concurrent-request test against real Postgres row locks,
      BR-INV-005/006)
- [x] Two buyers racing for the same reserved seat: exactly one succeeds
      (same, for `Seat`)
- [x] A cart is constrained to a single event; adding an item from a
      different event fails (400)
- [x] Promo code application adjusts the cart total (percentage and fixed
      discount, clamped to never go negative) and removal reverts it
      (BR-CART-001)
