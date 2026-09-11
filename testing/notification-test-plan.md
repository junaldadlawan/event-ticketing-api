# Notification Test Plan

**Status:** Implemented

Covers notification triggers and delivery status for the four wired trigger
points (checkout, refund, event cancellation, waitlist restock) plus
`GET /users/me/notifications`. See `testing/notification-test-results.md`
for the full scenario → test mapping and proof-of-testing detail.

## Test Scenarios

- [x] A notification is triggered on order confirmation
- [x] A notification is triggered on refund confirmation
- [x] A notification is triggered when waitlist inventory becomes available
- [x] A notification is triggered on event cancellation
- [x] A user can view their own notification history
- [x] A failed notification delivery does not roll back the action that
      triggered it

## Out of scope for Phase 11 (deliberate, not missed)

BR-NOTIFY-001 lists seven notification types. Two of them are NOT wired
this phase, and this is a deliberate scope decision, not a gap in this
pass's coverage:

- **EVENT_CHANGE** has no trigger in this codebase at all. `EventUpdateRequest`
  / `EventServiceImpl.updateEvent` only ever supports mutating
  title/description/category/images — an event's start/end time and venue
  are immutable after creation in the current implementation, so there is
  no code path that ever changes an event's time/venue for a notification
  to fire from. Nothing to test until that mutation exists.
- **EVENT_REMINDER** is inherently time-based (e.g. "24 hours before the
  event"), not triggered by any user action, and this codebase has no
  `@Scheduled`/job-scheduling infrastructure anywhere to drive that. There
  is no trigger to test.

Both are correctly modeled in `notification/enums/NotificationType` for a
future phase that adds the underlying mutation/scheduling capability, but
neither has a call site today.
