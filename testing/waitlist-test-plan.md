# Waitlist Test Plan

**Status:** Implemented (partial)

Covers joining a waitlist and being notified when inventory frees up.
Phase 9 implements only join/position tracking (BR-WAIT-001) — the
notify-on-inventory-freed trigger (BR-WAIT-002's "notified in order"/
BR-WAIT-003's time-limited window) depends on Phase 11 (Notifications),
which doesn't exist yet, so those two scenarios remain genuinely unbuilt.
See `testing/waitlist-test-results.md`.

## Test Scenarios

- [x] A user can join a waitlist only when sold out (409 otherwise)
- [ ] Waitlisted users are notified in the order they joined — **not
      built**; depends on Phase 11 (Notifications). See
      `testing/waitlist-test-results.md`'s "Scenarios not covered" section.
- [ ] A notified user has a limited time window to purchase before it
      passes to the next person — **not built**; same Phase 11 dependency.
- [x] A user can view their own waitlist entries
