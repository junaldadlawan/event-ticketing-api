# Ticket Test Plan

**Status:** Implemented (partial) — see `testing/ticket-issuance-test-results.md`
and `testing/ticket-template-artifact-test-results.md`

Covers issued tickets, credentials, ticket numbers, and artifacts. Issuance
(at checkout), `GET /tickets/{ticketId}` retrieval (Phase 6a), and
`GET /tickets/{ticketId}/artifact` digital/physical rendering plus
`TicketTemplate` definition/resolution (Phase 6b) are implemented;
check-in/used-state validation is not built yet (Phase 10+).

## Test Scenarios

- [x] A ticket is issued per cart item after a successful purchase
- [x] Each ticket has a unique, unguessable credential
- [x] Each ticket has a human-readable ticket number in the correct format
- [x] The raw scannable credential is never returned by the API
- [x] Requesting a ticket artifact (digital/physical) returns a valid file — see `testing/ticket-template-artifact-test-results.md`
- [ ] A ticket already marked "used" cannot be used again in any format — not built yet (Phase 10 check-in)
- [x] Only the owning buyer, event organizer, or admin can view a ticket
