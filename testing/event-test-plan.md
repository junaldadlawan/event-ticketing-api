# Event Test Plan

**Status:** Implemented (partial)

Covers event creation, search/listing, updates, and soft-delete.

## Test Scenarios

- [ ] Organizer/admin can create an event (starts as draft)
- [ ] Draft events are not shown in the public event list
- [ ] Published events are shown in the public event list
- [ ] Searching events by keyword returns only matching titles
- [ ] Filtering events by start date range works correctly
- [ ] A non-organizer/non-admin cannot create/update/delete an event (403)
- [ ] Soft-deleted events don't appear in the event list
- [ ] End date before start date is rejected on create
- [ ] End date before start date is currently NOT rejected on update
      (known gap — write this test to document current behavior)
- [ ] Updating/deleting a non-existent event returns 404

## Planned but not yet implemented

- [ ] Event's ticket number prefix is system-generated and unique
      platform-wide (currently client-supplied — planned fix)
- [ ] Publish/cancel lifecycle transitions
