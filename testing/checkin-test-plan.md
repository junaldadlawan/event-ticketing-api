# Check-in Test Plan

**Status:** Implemented

Covers ticket scanning/validation (`POST /check-in/validate`), scanner
device authorization/revocation (`POST /events/{eventId}/scanner-devices`,
`DELETE /scanner-devices/{deviceId}`), an event's check-in mode/config
(`GET/PATCH /events/{eventId}/check-in-config`), pure-offline dataset
pre-fetch (`GET /scanner-devices/{deviceId}/dataset`), fallback-scan
reconciliation (`POST /check-in/fallback-scans`), and a ticket's scan
history (`GET /tickets/{ticketId}/check-in-records`), per
`docs/event-ticketing-api-business-rules.md` `BR-CHECKIN-001`–`BR-CHECKIN-011`
(and `BR-TRANSFER-005`'s stale-credential payoff) and
`docs/event-ticketing-api-use-cases.md` `UC-EVENT-09`, `UC-SCAN-01`–`UC-SCAN-04`.
See `testing/checkin-test-results.md` for the full scenario → test mapping.

## Test Scenarios

- [x] Scanning a valid ticket marks it used and returns "valid"
- [x] Scanning an already-used ticket returns "duplicate"
- [x] Scanning a ticket for the wrong event returns "wrong_event"
- [x] Check-in staff can only validate tickets for their assigned event
      (a scanner device's own `eventId` gates `WRONG_EVENT`; the
      `device_id` cross-check on `/check-in/validate` and `GET
      /scanner-devices/{id}/dataset` prevents one device's credential from
      acting as/for another device)
- [ ] Standard mode: offline fallback file stops working after its expiry
      window — **out of scope for this server-side API.** This is
      client-side behavior: an offline scanning device locally tracking how
      long it has been disconnected and refusing to trust its own cached
      fallback file past `CheckInConfig.offline_fallback_expiry_seconds`
      (BR-CHECKIN-007). The server has no visibility into a device's local
      clock/connectivity state between requests and never observes "the
      file expired" as an event of its own — there is nothing for a
      server-side test to assert. `offline_fallback_expiry_seconds` itself
      IS server-owned config (settable/gettable via `PATCH`/`GET
      /events/{eventId}/check-in-config`) and that read/write path is
      fully tested; only the client's own enforcement of the window is out
      of scope, matching how Phase 9 left waitlist notification-timing
      (BR-WAIT-002/003) unchecked for an analogous "depends on a mechanism
      this codebase doesn't implement" reason.
- [x] Pure offline mode: only one authorized device allowed unless
      explicitly replaced with `force_replace`
- [x] Fallback scans recorded while offline are reconciled once synced,
      and duplicates are flagged for organizer review
