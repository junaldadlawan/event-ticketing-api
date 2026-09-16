# Audit Log (Phase 13) — Proof of Testing

**Date:** 2026-09-17
**Branch:** `feat/Phase13-Audit-Log` (based on `main` post-Phase-12-merge, PR #26)
**Based on:** `testing/audit-log-test-plan.md`, `BR-NFR-005` — the new
`auditlog/` module (`AuditLogEntry` entity, `AuditLogEntryRepository`,
`AuditLogService`/`Impl`, `AuditLogController`), migration
`V23__add_audit_log_table.sql`, the new `SecurityConfig` matcher for
`GET /api/v1/audit-log`, and the five `auditLogService.record(...)` call
sites: `RefundServiceImpl.issueRefund`, `OrganizationServiceImpl.assignMember`,
`EventServiceImpl.cancelEvent`, `DisputeServiceImpl.update`,
`ModerationActionServiceImpl.create`.

Fully spec'd from `openapi.yaml`/the ERD (unlike Phase 12's moderation
half) — straightforward implementation pass, full three-tier coverage
(`AuditLogServiceImplTest`/`AuditLogControllerTest`/`AuditLogIntegrationTest`)
plus a real-Postgres assertion added to each of the five existing
integration tests for the features being logged, rather than duplicating
that coverage in new dedicated tests.

**Command:** `mvnw.cmd test` (full suite; Postgres already running locally,
container `postgresql`)
**Result:** BUILD SUCCESS — 1174 tests run, 0 failures, 0 errors (12 new
audit-log tests + 5 existing integration tests extended with an audit-log
assertion; base was 1162 on this branch before Phase 13).

```
[INFO] Running com.junaldadlawan.event_ticketing_api.auditlog.AuditLogIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.683 s
[INFO] Running com.junaldadlawan.event_ticketing_api.auditlog.controller.AuditLogControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.220 s
[INFO] Running com.junaldadlawan.event_ticketing_api.auditlog.service.AuditLogServiceImplTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.299 s
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 1174, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

## Scenario → test mapping

| Test plan scenario | Covered by | Result |
|---|---|---|
| Refunds create an audit log entry | `RefundServiceImpl.issueRefund`'s `auditLogService.record(...)` call; `RefundIntegrationTest.create_owner_fullRefund_returns201_marksOrderRefundedAndTicketsRefundedInPostgres` (extended) | ✅ Pass |
| Role/permission changes create an audit log entry | `OrganizationServiceImpl.assignMember`'s call; `OrganizationSecurityIntegrationTest.goldenPath_applyApproveSelfAssignAssignOther_unrelatedUserForbidden` (extended) | ✅ Pass |
| Event cancellations create an audit log entry | `EventServiceImpl.cancelEvent`'s call; `EventOrganizationAccessIntegrationTest.goldenPath_createPublishCancel_crossOrgRejected` (extended) | ✅ Pass |
| Admin interventions (suspensions, dispute resolutions) create an audit log entry | `DisputeServiceImpl.update` + `ModerationActionServiceImpl.create`'s calls; `DisputeIntegrationTest.update_admin_resolvesDispute_notifiesRaiser` and `ModerationActionIntegrationTest.suspendReinstateOrganization_realStatusMutatesInPostgres` (both extended) | ✅ Pass |
| Only admin can view the audit log (403 for others) | `AuditLogIntegrationTest.list_nonAdmin_returns403`, `AuditLogControllerTest.list_nonAdmin_returns403` | ✅ Pass |
| The audit log has no update/delete endpoint | `AuditLogController` only exposes `GET` — confirmed by inspection, no PATCH/DELETE/POST mapping exists on `/api/v1/audit-log` | ✅ Pass (by construction) |

## Judgment calls (not explicitly specified, decided during implementation)

- **`action` string format**: free-text `"<resource>.<past_tense_verb>"`
  (e.g. `"refund.issued"`, `"organization_member.assigned"`,
  `"event.cancelled"`, `"dispute.resolved"`/`"dispute.dismissed"`,
  `"moderation.suspend"`/`"moderation.reinstate"`/`"moderation.remove"`),
  matching `openapi.yaml`'s own example rather than introducing an enum —
  keeps future sensitive actions loggable without a schema change.
- **Role-assignment `targetType`/`targetId`**: `"OrganizationMember"` /
  the organization's id, since `OrganizationMember` has no standalone
  UUID id of its own (a composite `(userId, organizationId)` key) — the
  organization id is the most useful lookup key available.
- **Dispute action naming**: split into `"dispute.resolved"` vs.
  `"dispute.dismissed"` depending on which terminal status was reached,
  rather than a single generic `"dispute.updated"` — more informative for
  an admin reviewing the log.
- **Moderation logs all three action types**, not just `SUSPEND` — the
  roadmap's own line item is titled "account suspension" but
  `BR-NFR-005` frames "admin interventions" broadly, and `REINSTATE`/
  `REMOVE` are equally sensitive actions worth a trail.

## Nothing left out

All items from `testing/audit-log-test-plan.md` are covered; no wiring
points beyond the five the roadmap named were added (no audit logging was
added to organization approve/reject, event publish, etc.), matching the
"no scope creep" instruction carried over from Phase 12.
