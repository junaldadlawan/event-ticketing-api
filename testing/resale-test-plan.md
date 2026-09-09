# Resale Test Plan

**Status:** Not built yet

Covers organizer-controlled peer-to-peer resale.

## Test Scenarios

- [ ] Organizer can enable/disable resale for their event
- [ ] Organizer can set a price cap for resale
- [ ] Listing a ticket for resale when resale is disabled fails (403)
- [ ] Listing a ticket above the price cap fails (403)
- [ ] A ticket can only have one active resale listing at a time (409)
- [ ] Seller can cancel their own active listing
- [ ] Buying a resale listing transfers the ticket and pays the seller
- [ ] Buying an already-sold/cancelled listing fails (409)
