# Checkout Test Plan

**Status:** Not built yet

Covers applying promo codes and completing a purchase.

## Test Scenarios

- [ ] Applying a valid promo code reduces the cart total
- [ ] Applying an expired promo code fails with a clear error
- [ ] Applying an exhausted (usage-limit-reached) promo code fails
- [ ] Applying a promo code to an inapplicable ticket type fails
- [ ] Checkout with a valid payment succeeds and issues tickets
- [ ] Checkout with a failed payment leaves the cart and holds intact (402)
- [ ] Checkout after a hold expired fails (410)
- [ ] Replaying the same Idempotency-Key does not double-charge or
      double-issue tickets
