# Ticket Type Test Plan

**Status:** Implemented (partial)

Covers GA and reserved-seating ticket types (CRUD + draft-visibility
gating), and read-only seat-map viewing. Checkout/hold/decrement inventory
logic (BR-INV-003/004/005/006) is out of scope — no checkout module exists
yet (Phase 5), so the two concurrency scenarios below remain untestable
until then. There is also no seat-map *creation* endpoint in this phase's
scope, so a reserved-seating ticket type cannot yet be paired with an
organizer-created seat map end to end — see
`testing/ticket-type-seatmap-test-results.md` for the full scenario→test
mapping and findings.

## Test Scenarios

- [x] Organizer can create a GA ticket type with price/quantity/sale window
- [ ] Organizer can create a reserved-seating ticket type with a seat map
      (not built yet — `kind: RESERVED_SEATING` is creatable, but no
      seat-map creation endpoint exists in this phase's scope; seat maps
      can only be read once inserted directly, not created via the API)
- [ ] Two buyers can never be sold the same seat (concurrency test) —
      not testable yet, no checkout/hold logic exists (Phase 5)
- [ ] GA quantity never goes negative under concurrent purchases —
      not testable yet, no checkout/decrement logic exists (Phase 5)
- [x] Public can view an event's ticket types once published
- [x] Organizer can update price/quantity/sale window on their own
      ticket type
- [x] A non-owning organizer cannot update another org's ticket type (403)
