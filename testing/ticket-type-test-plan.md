# Ticket Type Test Plan

**Status:** Not built yet

Covers GA and reserved-seating ticket types, and seat/quantity inventory.

## Test Scenarios

- [ ] Organizer can create a GA ticket type with price/quantity/sale window
- [ ] Organizer can create a reserved-seating ticket type with a seat map
- [ ] Two buyers can never be sold the same seat (concurrency test)
- [ ] GA quantity never goes negative under concurrent purchases
- [ ] Public can view an event's ticket types once published
- [ ] Organizer can update price/quantity/sale window on their own
      ticket type
- [ ] A non-owning organizer cannot update another org's ticket type (403)
