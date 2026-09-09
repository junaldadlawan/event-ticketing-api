# Cart Test Plan

**Status:** Not built yet

Covers cart creation, adding/removing items, and temporary holds.

## Test Scenarios

- [ ] Buyer can create a cart
- [ ] Adding a ticket type to the cart places a temporary hold
- [ ] Hold expires automatically after the configured window
- [ ] Removing an item releases its hold
- [ ] Adding a seat/quantity that's no longer available fails (409)
- [ ] A user can only view/modify their own cart (403 for others)
- [ ] A user has at most one active cart at a time
