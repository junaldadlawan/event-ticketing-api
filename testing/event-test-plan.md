# Event Test Plan

**Status:** Implemented (partial) — see
[event-hardening-test-results.md](event-hardening-test-results.md) for
Phase 3's automated coverage of create/update/publish/cancel/delete
authorization and the ticket-prefix generation.

Covers event creation, search/listing, updates, publish/cancel lifecycle,
and soft-delete.

## Test Scenarios

- [x] Organizer/admin can create an event (starts as draft) — `EventServiceImplTest.createEvent_owner_succeeds`/`.createEvent_organizer_succeeds`/`.createEvent_adminWithNoOrgRole_bypassesOrgRoleCheck`
- [ ] Draft events are not shown in the public event list
- [ ] Published events are shown in the public event list
- [ ] Searching events by keyword returns only matching titles
- [ ] Filtering events by start date range works correctly
- [x] A non-organizer/non-admin cannot create/update/publish/cancel/delete
      an event (403), **including an owner/organizer of a different
      organization** — `EventServiceImplTest.createEvent_ownerOfDifferentOrganization_throwsForbidden`/`.updateEvent_ownerOfDifferentOrganization_throwsForbidden`/`.publishEvent_ownerOfDifferentOrganization_throwsForbidden`/`.cancelEvent_ownerOfDifferentOrganization_throwsForbidden`/`.delete_ownerOfDifferentOrganization_throwsForbidden`, `EventOrganizationAccessIntegrationTest.goldenPath_...`/`.crossOrgOwner_forbiddenOnDraftGet_andOnDelete`
- [ ] Soft-deleted events don't appear in the event list
- [ ] End date before start date is rejected on create
- [ ] End date before start date is currently NOT rejected on update
      (known gap — write this test to document current behavior; N/A as of
      Phase 3 — `EventUpdateRequest` no longer carries dates at all, so this
      scenario no longer applies)
- [x] Updating/deleting a non-existent event returns 404 — `EventServiceImplTest.updateEvent_unknownEvent_throwsResourceNotFound`/`.delete_unknownEvent_throwsResourceNotFound`, `EventControllerTest.update_unknownEvent_returns404`/`.delete_unknownEvent_returns404`

## Implemented in Phase 3 (event-hardening-test-results.md)

- [x] Event's ticket number prefix is system-generated and unique
      platform-wide (never client-supplied) — `EventServiceImplTest.createEvent_ticketPrefixCollision_retriesUntilUnique`/`.createEvent_allCandidatesCollide_throwsIllegalState`
- [x] Publish/cancel lifecycle transitions (valid transitions succeed,
      invalid ones 409) — `EventServiceImplTest.publishEvent_*`/`.cancelEvent_*` (parameterized status matrices), `EventOrganizationAccessIntegrationTest.goldenPath_...`
- [x] `venueId` on create must belong to the same organization (400
      otherwise) — `EventServiceImplTest.createEvent_venueFromDifferentOrganization_throwsBadRequest`, `EventOrganizationAccessIntegrationTest.create_venueFromDifferentOrganization_returns400`
- [x] Draft events are only visible to the owning org's owner/organizer/admin
      (403 for anyone else, including anonymous) — `EventServiceImplTest.getEvent_draftStatus_*`, `EventOrganizationAccessIntegrationTest.goldenPath_...`
- [x] `DELETE` requires per-event authorization (previously had none at all)
      — `EventServiceImplTest.delete_*`, `EventOrganizationAccessIntegrationTest.crossOrgOwner_forbiddenOnDraftGet_andOnDelete`

## Not covered by this pass (pre-existing gaps, out of Phase 3's scope)

The unchecked boxes above (public listing, search/filter, soft-delete
filtering in `listEvents`, end-date-before-start-date validation) belong to
`EventServiceImpl.listEvents`/`createEvent`'s validation, which Phase 3 did
not touch — see `CLAUDE.md`'s Known gaps section (`listEvents` hardcodes
`EventStatus.DRAFT`, soft-delete isn't filtered from queries yet). Not
tested here since testing them wouldn't currently prove anything meaningful
against the hardcoded/known-broken behavior.
