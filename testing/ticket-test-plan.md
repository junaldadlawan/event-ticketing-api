# Ticket Test Plan

**Status:** Not built yet

Covers issued tickets, credentials, ticket numbers, and artifacts.

## Test Scenarios

- [ ] A ticket is issued per cart item after a successful purchase
- [ ] Each ticket has a unique, unguessable credential
- [ ] Each ticket has a human-readable ticket number in the correct format
- [ ] The raw scannable credential is never returned by the API
- [ ] Requesting a ticket artifact (digital/physical) returns a valid file
- [ ] A ticket already marked "used" cannot be used again in any format
- [ ] Only the owning buyer, event organizer, or admin can view a ticket
