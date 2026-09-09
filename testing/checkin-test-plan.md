# Check-in Test Plan

**Status:** Not built yet

Covers ticket scanning/validation and scanner device management.

## Test Scenarios

- [ ] Scanning a valid ticket marks it used and returns "valid"
- [ ] Scanning an already-used ticket returns "duplicate"
- [ ] Scanning a ticket for the wrong event returns "wrong_event"
- [ ] Check-in staff can only validate tickets for their assigned event
- [ ] Standard mode: offline fallback file stops working after its
      expiry window
- [ ] Pure offline mode: only one authorized device allowed unless
      explicitly replaced with `force_replace`
- [ ] Fallback scans recorded while offline are reconciled once synced,
      and duplicates are flagged for organizer review
