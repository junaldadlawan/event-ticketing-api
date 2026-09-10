# Promo Code Test Plan

**Status:** Implemented (partial)

Covers organizer-facing promo-code creation/listing
(`/events/{eventId}/promo-codes`) and application to a cart. Checkout-time
usage-limit enforcement (BR-PROMO-002/006 — total cap, per-buyer cap) is
explicitly stubbed as always-passing in `PromoCodeServiceImpl.applyPromoCode`
pending the Order module (a later dispatch), so those two scenarios remain
untestable until then. See `testing/cart-promocode-test-results.md` for the
full scenario→test mapping and findings; promo-code *application* to a cart
is exercised in `CartAccessIntegrationTest`.

## Test Scenarios

- [x] Owner/organizer of the event's org can create a promo code
      (percentage or fixed discount, applicable ticket types, usage limits,
      validity window) — BR-PROMO-001/003/004, UC-EVENT-06
- [x] Admin can create a promo code with no org membership at all
      (BR-AUTH-004 bypass)
- [x] A stranger, or an owner/organizer of a *different* organization,
      cannot create a promo code for someone else's event (403)
- [x] A duplicate `(eventId, code)` is rejected (409)
- [x] `validUntil` must be strictly after `validFrom` (400, including the
      equal-instant edge case)
- [x] `discountValue` for a `PERCENTAGE` code must be `<= 100` — value 100
      is accepted (boundary), 101 is rejected (400); no such upper bound
      applies to `FIXED`
- [x] Listing an event's promo codes is owner/organizer/admin-only,
      **always** — including once the event is fully `PUBLISHED`, unlike
      TicketType's draft-based public-visibility switch (key regression
      class deliberately tested)
- [x] Applying a valid promo code to a cart adjusts the total correctly
      (percentage and fixed) — see `cart-test-plan.md`
- [x] Applying an expired or not-yet-valid promo code is rejected (422)
- [x] Applying a promo code restricted to ticket types not in the cart is
      rejected (422)
- [ ] An exhausted (usage-limit-reached) promo code is rejected at checkout
      (BR-PROMO-006) — not testable yet; no Order/checkout module exists to
      count completed usages against `usageLimitTotal`/`usageLimitPerBuyer`
- [ ] A per-buyer usage cap is enforced (BR-PROMO-002) — same reason
