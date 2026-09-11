# Resale Test Plan

**Status:** Implemented

Covers organizer-controlled peer-to-peer resale
(`GET/PATCH /events/{eventId}/resale-policy`,
`POST /tickets/{ticketId}/resale-listings`,
`DELETE /resale-listings/{listingId}`,
`POST /resale-listings/{listingId}/purchase`,
`GET /events/{eventId}/resale-listings`). See
`testing/transfer-resale-test-results.md` for proof of testing.

## Test Scenarios

- [x] Organizer (and owner, and admin) can enable/disable resale for their
      event; `GET` returns a synthesized `enabled=false` default when no
      policy row exists yet
- [x] Organizer can set a price cap for resale (`FACE_VALUE`,
      `FACE_VALUE_PLUS_FEE` with a configurable fee, or `NONE`/unset = no cap)
- [x] Only the owning organization's owner/organizer, or an admin, may PATCH
      the resale policy (403 for a stranger or a different org's owner)
- [x] Listing a ticket for resale when resale is disabled (or no policy row
      exists at all) fails (403)
- [x] Listing a ticket above the price cap fails (403); at or under the cap
      succeeds; `FACE_VALUE_PLUS_FEE` with no fee configured is treated as a
      zero fee (cap == face value); `NONE`/null cap rule allows any price
- [x] Listing with an asking-price currency different from the ticket's
      face-value currency fails (400)
- [x] Only the ticket's owning buyer may list it for resale (403 otherwise);
      only a `VALID` ticket may be listed (409 otherwise)
- [x] A ticket can only have one active resale listing at a time (409),
      backstopped by the DB partial unique index
- [x] Seller can cancel their own active listing (204); a non-seller cannot
      (403); a non-active listing cannot be cancelled again (409)
- [x] `GET /events/{eventId}/resale-listings` is public and returns only
      `ACTIVE` listings
- [x] Buying a resale listing transfers the ticket (real `TicketTransfer` row,
      `source=RESALE`, `owner_id` reassigned, credential invalidated) and pays
      the seller (`Order.payeeType=USER`/`payeeId=seller`, a real `Payment`)
- [x] A buyer cannot purchase their own listing (403)
- [x] Buying an already-sold listing fails (409); a declined payment leaves
      the listing `ACTIVE` for retry (402)
- [x] Purchase requires an `Idempotency-Key` header (400 if missing); a
      replayed key after success returns the same `Order` with no re-charge
