# Venue Test Plan

**Status:** Implemented

Covers organization-owned venues used by events. No `BR-VENUE-*`/`UC-VENUE-*`
docs exist for this module yet — behavior is per `openapi.yaml`'s `Venues`
paths (`/organizations/{orgId}/venues`, `/venues/{venueId}`, ~lines
367-428) and schemas (`Venue`/`VenueCreate`/`VenueUpdate`, ~lines
1728-1758). See [venue-test-results.md](venue-test-results.md).

## Test Scenarios

- [x] Org owner/organizer can create a venue for their organization
- [x] A venue can be created without an address (virtual venue)
- [x] Anyone can view a venue's public details, no login required
- [x] Org owner/organizer can update their own venue
- [x] A user from a different organization cannot update someone else's
      venue (403)
- [x] A non-member of the org (or check-in-staff-only member) cannot create
      or list that org's venues (403)
- [x] `get()` performs no authorization check at all (works with no
      security context / no Authorization header)
- [x] Partial update: only fields present in the PATCH body change; a
      present-but-blank `name` is rejected (400)
- [x] Nonexistent org/venue id returns 404 on create/list/get/update
