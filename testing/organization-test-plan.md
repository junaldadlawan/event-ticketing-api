# Organization Test Plan

**Status:** Not built yet

Covers the organization application/approval flow and org-scoped role
assignment (owner, organizer, check-in staff).

## Test Scenarios

- [ ] A registered user can submit an organization application with
      required documents
- [ ] An application without required documents is rejected
- [ ] Admin can list pending applications
- [ ] Admin can approve a pending application; applicant becomes owner
- [ ] Admin can reject a pending application
- [ ] Approving/rejecting an already-decided application fails (409)
- [ ] A pending or rejected application grants no org privileges
- [ ] Owner can assign another user as organizer or check-in staff
- [ ] An organizer (not owner) cannot assign roles to other users (403)
- [ ] A user can assign themselves an additional role they qualify for
- [ ] A user can hold multiple roles at once (e.g. owner + organizer)
- [ ] Owner can list the users belonging to their own organization
- [ ] Owner cannot list another organization's users (403)
