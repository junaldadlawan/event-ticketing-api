# Order Test Plan

**Status:** Implemented (partial) — see `testing/ticket-issuance-test-results.md`

Covers order visibility and ownership scoping. `GET /orders/{orderId}`,
`GET /orders/{orderId}/tickets`, `GET /users/me/orders`, and `GET
/events/{eventId}/orders` are implemented (Phase 6a); refund is not built
yet, so the order-status scenario below only covers the `pending`→`paid`
transition Phase 5b/6a actually implements.

## Test Scenarios

- [x] Buyer can view their own orders
- [x] Buyer cannot view another buyer's order (403)
- [x] Organizer can view orders for their own event
- [x] Organizer cannot view orders for another organizer's event (403)
- [x] Admin can view any order
- [ ] Order status reflects payment/refund state accurately — refund not built yet; pending→paid is covered by `testing/checkout-test-results.md`
