# Ticket Transfer Test Plan

**Status:** Implemented

Covers transferring a ticket to another registered user
(`POST /tickets/{ticketId}/transfer`, `GET /tickets/{ticketId}/transfers`).
See `testing/transfer-resale-test-results.md` for proof of testing.

## Test Scenarios

- [x] Ticket owner can transfer a ticket to another registered user
- [x] Transferring a ticket invalidates the old credential (proven against
      real Postgres — old vs. new `credential`/`credential_version` compared)
- [x] Only the current owner can transfer a ticket (403 for anyone else)
- [x] Transfer to an unregistered user fails (404)
- [x] Transfer to a soft-deleted user fails (404)
- [x] Transferring a ticket to its own current owner is rejected (400)
- [x] Only a `VALID` ticket may be transferred (409 otherwise)
- [x] Only `ownerId`/`credential`/`credentialVersion` change on transfer —
      `ticketNumber`/`eventId`/`ticketTypeId`/`seatId`/`status` untouched
- [x] Transfer produces a `TicketTransfer` audit row with correct
      `fromUserId`/`toUserId`/`source=DIRECT_TRANSFER`
- [x] `GET /tickets/{ticketId}/transfers` visibility: owning buyer, event
      organizer/owner, and admin can view; a roleless stranger cannot (403)
