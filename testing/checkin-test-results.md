# Check-in & Scanning (Phase 10) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/Phase-10-Check-in-&-Scanning`
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-CHECKIN-001`–
`BR-CHECKIN-011` and `BR-TRANSFER-005` (stale-credential-after-transfer
payoff); `docs/event-ticketing-api-use-cases.md` `UC-EVENT-09`,
`UC-SCAN-01`–`UC-SCAN-04`; `testing/checkin-test-plan.md`; the new
`checkin/` module (`CheckInConfig`/`ScannerDevice`/`CheckInRecord`/
`FallbackScanRecord` entities, `CheckInConfigService`/`ScannerDeviceService`/
`CheckInService` + impls, `EventCheckInConfigController`/
`EventScannerDeviceController`/`ScannerDeviceController`/`CheckInController`,
`DeviceAuthenticationFilter`/`ScannerDeviceCredentialService`/
`DeviceAccessGuard`), migration `V18__add_checkin_tables.sql`, the new
`TicketCredentialService.verify()`, and the new `TicketController` endpoint
`GET /tickets/{ticketId}/check-in-records`.

Phase 10's production code, `SecurityConfig`'s new `deviceAuth` matchers, and
the mechanical `DeviceAuthenticationFilter` mock added to all 23 pre-existing
`@WebMvcTest` slices were already on disk and passing when this pass began
(confirmed — see the "starting state" note below). Two Phase-10 test files
already existed at that point too:
`checkin/service/CheckInServiceImplTest.java` (21 tests, the core
`resolveAndMarkTicket` branch coverage) and
`ticket/service/TicketCredentialServiceTest.java` (18 tests, including
`verify()`'s round-trip/tamper/malformed coverage). This pass reviewed both
against the actual production code, confirmed they pass, and added
everything else: the two remaining service-layer Mockito suites
(`ScannerDeviceServiceImplTest`, `CheckInConfigServiceImplTest`), all four
new controllers' `@WebMvcTest` slices, 4 new methods on the pre-existing
`TicketControllerTest` for its new endpoint, and three full `@SpringBootTest`
integration suites covering the complete matrix in the dispatch (device
cross-checks, pure_offline enforcement, live revocation, the SHA-256
`credentialHash` proof, and — the headline scenarios — a real
transfer-then-scan-stale-credential test and a genuine `ExecutorService`
concurrency race proving the `findByIdForUpdate` row lock).

**No production bugs were found.** `resolveAndMarkTicket`'s row lock,
`validate`/`getDataset`'s device-id cross-checks, and
`ScannerDeviceServiceImpl.authorize`'s pure_offline gate all behaved exactly
as documented on first attempt — see Findings below for what was actually
checked and why nothing needed fixing.

## Starting state (before this pass's own additions)

Per the dispatch, Phase 9's full-suite baseline was 868 tests. By the time
this QA pass started, Phase 10's production code AND its own
`CheckInServiceImplTest` (21 tests) and `TicketCredentialServiceTest`'s
`verify()` additions (part of that file's 18 total) were already committed
to the working tree (visible as untracked/modified files in `git status`
before any work in this session). Rather than guess at that intermediate
number, this pass's arithmetic is anchored to concrete, actually-run numbers
only:

- **Full suite at the end of this pass:** 998 tests (see below).
- **Tests added BY this pass** (counted from real `mvnw.cmd test` runs of
  each new/changed file, not estimated): 100 — see the file-by-file table
  under "Test files added/changed."
- **Implied pre-this-pass full-suite count:** 998 − 100 = **898** — some
  margin above the Phase 9 baseline of 868 accounts for Phase 10's own
  pre-existing `CheckInServiceImplTest` (21 tests) and
  `TicketCredentialServiceTest`'s `verify()` additions (a subset of that
  file's 18) already present when this pass started; the exact split isn't
  independently reconstructable since this pass didn't run the suite before
  making its own changes, so 898 is derived arithmetic, not independently
  measured — but every number it's derived from (998 final, 100 added) was
  independently confirmed by an actual `mvnw.cmd test` run, per the
  "never fabricate a test result" rule.

## Commands run and results

**Targeted (new/touched Phase 10 files, `checkin.**` + `ticket.**`):**
```
mvnw.cmd -Dtest="com.junaldadlawan.event_ticketing_api.checkin.**,com.junaldadlawan.event_ticketing_api.ticket.**" test
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 200 tests run (116 in `checkin.**`, 84 in the
pre-existing `ticket.**` package, unmodified except for `TicketControllerTest`
and the already-present `TicketCredentialServiceTest`), 0 failures, 0 errors.

```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.112 s -- in com.junaldadlawan.event_ticketing_api.checkin.CheckInConfigIntegrationTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.362 s -- in com.junaldadlawan.event_ticketing_api.checkin.CheckInIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.921 s -- in com.junaldadlawan.event_ticketing_api.checkin.controller.CheckInControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.576 s -- in com.junaldadlawan.event_ticketing_api.checkin.controller.EventCheckInConfigControllerTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.516 s -- in com.junaldadlawan.event_ticketing_api.checkin.controller.EventScannerDeviceControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.425 s -- in com.junaldadlawan.event_ticketing_api.checkin.controller.ScannerDeviceControllerTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.799 s -- in com.junaldadlawan.event_ticketing_api.checkin.ScannerDeviceIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.173 s -- in com.junaldadlawan.event_ticketing_api.checkin.service.CheckInConfigServiceImplTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.425 s -- in com.junaldadlawan.event_ticketing_api.checkin.service.CheckInServiceImplTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.084 s -- in com.junaldadlawan.event_ticketing_api.checkin.service.ScannerDeviceServiceImplTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.081 s -- in com.junaldadlawan.event_ticketing_api.ticket.artifact.QrCodeGeneratorTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.367 s -- in com.junaldadlawan.event_ticketing_api.ticket.artifact.TicketArtifactIntegrationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.265 s -- in com.junaldadlawan.event_ticketing_api.ticket.artifact.TicketArtifactServiceImplTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.546 s -- in com.junaldadlawan.event_ticketing_api.ticket.controller.TicketControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.junaldadlawan.event_ticketing_api.ticket.service.TicketAccessGuardTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.junaldadlawan.event_ticketing_api.ticket.service.TicketServiceImplTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.392 s -- in com.junaldadlawan.event_ticketing_api.ticket.TicketAccessIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 200, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

The headline concurrency test
(`CheckInIntegrationTest.validate_concurrentScansOfSameValidTicket_exactlyOneValidOneDuplicate_neverBothValid`)
was additionally re-run 3 times in isolation to check for flakiness — all 3
passed (see "The concurrency headline result" below).

**Full suite:** `mvnw.cmd test`

**Result:** BUILD SUCCESS — **998 tests run, 0 failures, 0 errors.** This
pass added exactly **100** of those 998 (see the file table below); the
implied pre-pass count is 898 (see "Starting state" above). No regressions
against the pre-existing 898 tests untouched by this pass.

```
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.886 s -- in com.junaldadlawan.event_ticketing_api.waitlist.WaitlistIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 998, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  50.376 s
[INFO] Finished at: 2026-09-11T21:49:05+08:00
[INFO] ------------------------------------------------------------------------
```

## Test files added/changed

| File | Status | Tests | Notes |
|---|---|---|---|
| `checkin/service/CheckInServiceImplTest.java` | pre-existing, reviewed only | 21 | Mockito. Every branch of `resolveAndMarkTicket` (INVALID/VALID/DUPLICATE/WRONG_EVENT/stale-version), both device-id cross-checks, `getDataset`'s SHA-256 hash proof, fallback-batch reconciliation, `listTicketCheckInRecords` visibility. Verified against production code and confirmed passing; not modified. |
| `ticket/service/TicketCredentialServiceTest.java` | pre-existing, reviewed only | 18 | Plain unit test (no Spring, no mocks). `generate`'s dedicated-secret/per-ticket-random/version-bump properties plus `verify()`'s round-trip, tamper rejection, malformed-input handling, and the "doesn't compare embedded version to anything" contract. Confirmed passing; not modified. |
| `checkin/service/ScannerDeviceServiceImplTest.java` | **new** | 13 | Mockito. BR-CHECKIN-008's pure_offline single-active-device gate (no config/STANDARD → unlimited; PURE_OFFLINE with an active device → 409 unless `force_replace`, which revokes ALL existing active devices first), `revoke`'s owner/organizer/admin gate. |
| `checkin/service/CheckInConfigServiceImplTest.java` | **new** | 12 | Mockito. `get`'s STANDARD/300s default when no row exists, `update`'s upsert (+ its `DataIntegrityViolationException` race-recovery path), and `computeModeSwitchWarning`'s two independently-required conditions (mode actually changing AND ≥1 active device). |
| `checkin/controller/EventCheckInConfigControllerTest.java` | **new** | 5 | `@WebMvcTest`. GET/PATCH status-code mapping, warning field round-trips through JSON. |
| `checkin/controller/EventScannerDeviceControllerTest.java` | **new** | 4 | `@WebMvcTest`. `POST .../scanner-devices` 201/404/403/409 mapping. |
| `checkin/controller/ScannerDeviceControllerTest.java` | **new** | 6 | `@WebMvcTest`. `DELETE`/`GET .../dataset` status-code mapping, including the dataset's device-cross-check 403. |
| `checkin/controller/CheckInControllerTest.java` | **new** | 9 | `@WebMvcTest`. `POST /check-in/validate`/`fallback-scans` request validation (`@NotBlank`/`@NotNull`/`@NotEmpty`) and response shape. |
| `checkin/CheckInConfigIntegrationTest.java` | **new** | 15 | Full `@SpringBootTest`. GET reachable by BOTH a user JWT and a device credential (dispatch item #6); PATCH stays owner/organizer/admin-only, device credential gets 403; the mode-switch warning's two conditions proven end-to-end against real Postgres. |
| `checkin/ScannerDeviceIntegrationTest.java` | **new** | 14 | Full `@SpringBootTest`. `authorize`'s RBAC + pure_offline enforcement end-to-end (including a real `force_replace` revoking a real active device), `revoke`'s RBAC, and — the two headline device-security proofs — a revoked device's still-cryptographically-valid credential 401ing (not 403ing) on all three device-only endpoints, and the dataset's `credentialHash` independently verified as a real SHA-256 digest of the DB `credential` column. |
| `checkin/CheckInIntegrationTest.java` | **new** | 18 | Full `@SpringBootTest`. The complete `resolveAndMarkTicket` matrix with real credentials; **the headline transfer-then-scan-stale-credential test**; **the headline concurrent-scan race** (8-thread `ExecutorService`, exactly 1 VALID + 7 DUPLICATE, never 2 VALID); fallback-scan mode-gating, mixed-batch reconciliation, and a same-batch duplicate proof; `GET /tickets/{id}/check-in-records` visibility. |
| `ticket/controller/TicketControllerTest.java` | **modified** (+4 tests) | 18 total (4 new) | `@WebMvcTest`. New `GET /tickets/{ticketId}/check-in-records` coverage: 200 with history, 200 empty array, 404 unknown ticket, 403 unauthorized. |

**This pass's own total: 100 new tests** (13+12+5+4+6+9+15+14+18+4).

No production files were modified by this pass (see Findings — nothing
needed fixing).

## Scenario → test mapping

### `checkin/service/CheckInServiceImplTest` (Mockito, 21 tests, pre-existing)

| Scenario | Test | Result |
|---|---|---|
| `validate`: body `device_id` ≠ authenticated device → 403, no lookup | `.validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_throwsForbidden` | Pass |
| `validate`: unknown authenticated device → 404 | `.validate_unknownAuthenticatedDevice_throwsResourceNotFound` | Pass |
| Unparseable credential → INVALID, no ticketId, no `CheckInRecord` | `.validate_unparseableCredential_returnsInvalid_noTicketId_noCheckInRecordCreated` | Pass |
| Forged credential indistinguishable from malformed → INVALID | `.validate_forgedCredential_indistinguishableFromMalformed_returnsInvalid_noRecord` | Pass |
| Parsed but ticket no longer exists → INVALID | `.validate_parsedTicketNoLongerExists_returnsInvalid_noTicketId_noRecord` | Pass |
| **Genuine current credential, same event → VALID, ticket flips USED, record saved** | `.validate_validCurrentCredential_sameEvent_marksTicketUsed_returnsValid_savesRecord` | Pass |
| Same credential scanned twice → second is DUPLICATE, ticket not re-saved | `.validate_sameCredentialScannedTwice_secondScanReturnsDuplicate_doesNotReSave` | Pass |
| **BR-TRANSFER-005 payoff: stale version (embedded 0, current 1) → INVALID, ticket NOT marked used** | `.validate_staleCredentialVersion_afterSimulatedTransfer_returnsInvalid_notValid` | Pass |
| Valid ticket, different event than device → WRONG_EVENT | `.validate_validCurrentCredential_differentEventThanDevice_returnsWrongEvent` | Pass |
| Ticket neither VALID nor USED (e.g. REFUNDED) → INVALID | `.validate_ticketNeitherValidNorUsed_refunded_returnsInvalid` | Pass |
| Ticket summary includes ticket number/type/seat | `.validate_ticketSummary_includesTicketNumberTicketTypeNameAndSeat` | Pass |
| `getDataset`: path deviceId ≠ authenticated device → 403 | `.getDataset_pathDeviceIdDoesNotMatchAuthenticatedDevice_throwsForbidden` | Pass |
| `getDataset`: unknown device → 404 | `.getDataset_unknownDevice_throwsResourceNotFound` | Pass |
| **`credentialHash` is SHA-256 of the raw credential, never the raw value itself** | `.getDataset_returnsCredentialHash_asSha256OfTheRawCredential_notTheRawCredentialItself` | Pass |
| `submitFallbackScans`: event_id ≠ device's own event → 400 | `.submitFallbackScans_eventIdDoesNotMatchDevicesOwnEvent_throwsBadRequest` | Pass |
| No config row (defaults STANDARD) → 409 | `.submitFallbackScans_modeIsStandardDefault_noConfigRow_throwsConflict` | Pass |
| Explicit STANDARD mode → 409 | `.submitFallbackScans_modeExplicitlyStandard_throwsConflict` | Pass |
| **PURE_OFFLINE, mixed valid/garbage batch → correct per-scan results + `FallbackScanRecord`/`CheckInRecord` rows** | `.submitFallbackScans_pureOfflineMode_mixedBatch_producesCorrectPerScanResultsAndRecords` | Pass |
| `listTicketCheckInRecords`: unknown ticket → 404 | `.listTicketCheckInRecords_unknownTicket_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.listTicketCheckInRecords_roselessStranger_throwsForbidden` | Pass |
| Admin → 200, ordered by `scannedAt` | `.listTicketCheckInRecords_admin_returnsRecords_orderedByScannedAtAsc` | Pass |

### `ticket/service/TicketCredentialServiceTest` (plain unit test, 18 tests, pre-existing)

Covers `generate`'s dedicated-secret/per-ticket-random/HMAC/version-bump
properties (10 tests) plus `verify()`: round-trip with `generate` (2 tests),
tampered signature → empty, signed-under-a-different-secret → empty, null/
empty/malformed inputs → empty (5 tests), and the explicit proof that
`verify()` never compares the embedded version against anything — it only
reports it (`.verify_doesNotCompareEmbeddedVersionAgainstAnything_justReportsIt`).
All pass.

### `checkin/service/ScannerDeviceServiceImplTest` (Mockito, 13 tests, new)

| Scenario | Test | Result |
|---|---|---|
| Unknown event → 404 | `.authorize_unknownEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403, no save | `.authorize_roselessStranger_throwsForbidden` | Pass |
| No config row (defaults STANDARD) → unlimited devices, never even checks active count | `.authorize_noConfigRow_defaultsToStandard_unlimitedDevicesAllowed` | Pass |
| Explicit STANDARD, second device → no conflict | `.authorize_explicitStandardMode_secondDeviceAllowed_noConflict` | Pass |
| PURE_OFFLINE, no active device yet → succeeds | `.authorize_pureOfflineMode_noActiveDeviceYet_succeeds` | Pass |
| PURE_OFFLINE, one active, no `force_replace` → 409 | `.authorize_pureOfflineMode_oneAlreadyActive_noForceReplace_throwsConflict` | Pass |
| PURE_OFFLINE, one active, explicit `force_replace:false` → still 409 | `.authorize_pureOfflineMode_oneAlreadyActive_explicitFalseForceReplace_throwsConflict` | Pass |
| **PURE_OFFLINE, `force_replace:true` → revokes existing THEN authorizes new (in order)** | `.authorize_pureOfflineMode_oneAlreadyActive_forceReplaceTrue_revokesExistingThenAuthorizesNew` | Pass |
| Multiple active devices (edge case), `force_replace` → revokes ALL | `.authorize_pureOfflineMode_multipleActiveDevices_forceReplace_revokesAll` | Pass |
| `revoke`: unknown device → 404 | `.revoke_unknownDevice_throwsResourceNotFound` | Pass |
| Owning organizer → flips to REVOKED, saves | `.revoke_owningOrganizer_flipsStatusToRevoked_andSaves` | Pass |
| Roleless stranger → 403, device unchanged | `.revoke_roselessStranger_throwsForbidden_deviceUnchanged` | Pass |
| Admin, zero org membership → succeeds | `.revoke_admin_succeeds_withNoOrganizationMembershipAtAll` | Pass |

### `checkin/service/CheckInConfigServiceImplTest` (Mockito, 12 tests, new)

| Scenario | Test | Result |
|---|---|---|
| `get`: unknown event → 404 | `.get_unknownEvent_throwsResourceNotFound` | Pass |
| No config row → STANDARD/300s default | `.get_noConfigRowYet_returnsStandardDefault_300Seconds` | Pass |
| Existing row → persisted values | `.get_existingConfigRow_returnsPersistedValues` | Pass |
| `update`: unknown event → 404 | `.update_unknownEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.update_roselessStranger_throwsForbidden` | Pass |
| No existing row → creates via `saveAndFlush`, previous mode defaults STANDARD | `.update_noExistingRow_createsNewConfig_defaultsPreviousModeToStandard` | Pass |
| Race lost to a concurrent first PATCH → recovers via `findByEventId` | `.update_noExistingRow_raceLostToConcurrentFirstPatch_recoversViaFindByEventId` | Pass |
| **Mode changes + ≥1 active device → warning** | `.update_modeActuallyChanges_atLeastOneActiveDevice_returnsWarning` | Pass |
| **Mode changes + 0 active devices → no warning** | `.update_modeActuallyChanges_zeroActiveDevices_noWarning` | Pass |
| **Same mode + ≥1 active device → no warning (short-circuits before even querying devices)** | `.update_sameModeAsBefore_atLeastOneActiveDevice_noWarning` | Pass |
| Omitted mode → only expiry updates | `.update_omittedMode_updatesOnlyExpiry_modeUnchanged` | Pass |
| `updatedBy` set to caller id | `.update_setsUpdatedByToCallerId` | Pass |

### `checkin/controller/*ControllerTest` (`@WebMvcTest`, 24 tests total, new)

Status-code/request-validation mapping only (RBAC lives in the integration
tests below): `EventCheckInConfigControllerTest` (5 — GET/PATCH 200/404/403),
`EventScannerDeviceControllerTest` (4 — POST 201/404/403/409),
`ScannerDeviceControllerTest` (6 — DELETE 204/404/403, GET dataset 200
w/hashed credentials/403/404), `CheckInControllerTest` (9 — validate 200/
missing-field 400 ×2/403; fallback-scans 200/empty-list 400/missing-field
400/409/400). All pass.

### `checkin/CheckInConfigIntegrationTest` (full `@SpringBootTest`, 15 tests, new)

| Scenario | Test | Result |
|---|---|---|
| GET, no config yet → 200, STANDARD default | `.get_noConfigYet_returns200_standardDefault` | Pass |
| **GET reachable by a user JWT** (dispatch item #6) | `.get_withUserJwt_returns200_notForbidden` | Pass |
| **GET reachable by a device credential too — not device-only, unlike the other three** | `.get_withDeviceCredential_returns200` | Pass |
| GET, no token → 401 | `.get_noToken_returns401` | Pass |
| GET, unknown event → 404 | `.get_unknownEvent_returns404` | Pass |
| PATCH: owner → 200, persists mode+expiry | `.update_owner_returns200_persistsMode` | Pass |
| PATCH: organizer → 200 | `.update_organizer_returns200` | Pass |
| PATCH: admin, zero org membership → 200 | `.update_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |
| PATCH: roleless stranger → 403 | `.update_roselessStranger_returns403` | Pass |
| **PATCH: device credential → 403, deviceAuth cannot PATCH** | `.update_deviceCredential_returns403_deviceAuthCannotPatch` | Pass |
| PATCH: no token → 401 | `.update_noToken_returns401` | Pass |
| PATCH: unknown event → 404 | `.update_unknownEvent_returns404` | Pass |
| **Mode changes + active device exists → warning, end-to-end** | `.update_modeChangesWithActiveDevice_returnsWarning` | Pass |
| Mode changes + zero devices → no warning, end-to-end | `.update_modeChangesWithZeroActiveDevices_noWarning` | Pass |
| Same mode + active device exists → no warning, end-to-end | `.update_sameModeAsBefore_activeDeviceExists_noWarning` | Pass |

### `checkin/ScannerDeviceIntegrationTest` (full `@SpringBootTest`, 14 tests, new)

| Scenario | Test | Result |
|---|---|---|
| `authorize`: owner → 201 w/credential | `.authorize_owner_returns201_withCredential` | Pass |
| Roleless stranger → 403 | `.authorize_roselessStranger_returns403` | Pass |
| No token → 401 | `.authorize_noToken_returns401` | Pass |
| **PURE_OFFLINE, second device w/o `force_replace` → 409, still exactly 1 active device** | `.authorize_pureOfflineMode_secondDeviceWithoutForceReplace_returns409` | Pass |
| **PURE_OFFLINE, `force_replace` → real existing device REVOKED in DB, new one ACTIVE** | `.authorize_pureOfflineMode_forceReplace_revokesExistingAndAuthorizesNew` | Pass |
| STANDARD (no config row), multiple devices allowed | `.authorize_standardMode_multipleDevicesAllowed_noConflict` | Pass |
| `revoke`: owner → 204, DB status REVOKED | `.revoke_owner_returns204_deviceStatusBecomesRevoked` | Pass |
| Roleless stranger → 403 | `.revoke_roselessStranger_returns403` | Pass |
| Unknown device → 404 | `.revoke_unknownDevice_returns404` | Pass |
| **Revoked device's still-valid credential → 401 (not 403) on ALL THREE device-only endpoints** (dispatch item #4) | `.revokedDevice_credentialStillStructurallyValid_butFailsAllThreeDeviceOnlyEndpoints_with401` | Pass |
| **`getDataset`: `credentialHash` independently verified = real SHA-256(ticket.credential)** (dispatch item #5) | `.getDataset_ownDevice_returns200_withCredentialHashesNotRawCredentials` | Pass |
| Dataset with a user JWT instead of device credential → 403 | `.getDataset_userJwtInstead_returns403` | Pass |
| Dataset, no token → 401 | `.getDataset_noToken_returns401` | Pass |
| **Dataset: path deviceId is a DIFFERENT active device's own credential → 403** (dispatch item #2) | `.getDataset_pathDeviceIdIsADifferentActiveDevice_returns403` | Pass |

### `checkin/CheckInIntegrationTest` (full `@SpringBootTest`, 18 tests, new)

| Scenario | Test | Result |
|---|---|---|
| `validate` with a user JWT → 403 (SecurityConfig matcher) | `.validate_userJwtInstead_returns403` | Pass |
| `validate`, no token → 401 | `.validate_noToken_returns401` | Pass |
| Genuine current credential, same event → 200 VALID, ticket USED in DB, record saved | `.validate_genuineCurrentCredential_sameEvent_returns200_valid_marksTicketUsed_savesRecord` | Pass |
| Same credential twice → second 200 DUPLICATE, 2 records | `.validate_sameCredentialScannedTwice_secondScanReturnsDuplicate` | Pass |
| Unparseable credential → 200 INVALID, no ticketId | `.validate_unparseableCredential_returnsInvalid_noTicketId` | Pass |
| Ticket's event ≠ device's event → 200 WRONG_EVENT, ticket untouched | `.validate_ticketBelongsToDifferentEventThanDevice_returnsWrongEvent` | Pass |
| Body `deviceId` ≠ authenticated device → 403, ticket untouched | `.validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_returns403` | Pass |
| **THE headline scenario: real transfer via `POST /tickets/{id}/transfer` bumps `credentialVersion`; scanning the ORIGINAL pre-transfer credential → 200 INVALID, ticket stays VALID; the NEW credential scans VALID** | `.validate_staleCredentialAfterRealTransfer_returnsInvalid_notValid` | **Pass** |
| **THE concurrency headline: 8 real threads scanning the SAME valid ticket's SAME credential simultaneously → exactly 1 VALID + 7 DUPLICATE, never 2 VALID; ticket ends USED; exactly 8 `CheckInRecord` rows** | `.validate_concurrentScansOfSameValidTicket_exactlyOneValidOneDuplicate_neverBothValid` | **Pass** (4/4 across the targeted run + 3 additional isolated re-runs) |
| `submitFallbackScans` with a user JWT → 403 | `.submitFallbackScans_userJwtInstead_returns403` | Pass |
| Non-pure_offline mode → 409 | `.submitFallbackScans_notPureOfflineMode_returns409` | Pass |
| `event_id` ≠ device's own event → 400 | `.submitFallbackScans_eventIdDoesNotMatchDevicesOwnEvent_returns400` | Pass |
| Mixed valid/garbage batch → correct per-scan results + `FallbackScanRecord`/`CheckInRecord` rows | `.submitFallbackScans_pureOfflineMode_mixedBatch_returns200_withCorrectPerScanResults_andReconciliationRows` | Pass |
| **BR-CHECKIN-010: same credential twice in one fallback batch → second flagged DUPLICATE in the response AND both persisted to `CheckInRecord` for organizer review** | `.submitFallbackScans_sameCredentialTwiceInOneBatch_secondFlaggedDuplicate_bothPersistedForReview` | Pass |
| `GET /tickets/{id}/check-in-records`: owning organizer sees real scan history | `.listCheckInRecords_owningOrganizer_returns200_withRealScanHistory` | Pass |
| Roleless stranger → 403 | `.listCheckInRecords_roselessStranger_returns403` | Pass |
| Admin → 200 (empty array, no scans yet) | `.listCheckInRecords_admin_returns200` | Pass |
| Unknown ticket → 404 | `.listCheckInRecords_unknownTicket_returns404` | Pass |

### `ticket/controller/TicketControllerTest` (`@WebMvcTest`, 4 new tests)

| Scenario | Test | Result |
|---|---|---|
| Existing ticket, with history → 200 | `.listCheckInRecords_existingTicket_returns200_withHistory` | Pass |
| No records yet → 200, empty array | `.listCheckInRecords_noRecordsYet_returns200_withEmptyArray` | Pass |
| Unknown ticket → 404 | `.listCheckInRecords_unknownTicket_returns404` | Pass |
| Unauthorized caller → 403 | `.listCheckInRecords_unauthorizedCaller_returns403` | Pass |

## The concurrency headline result

`CheckInIntegrationTest.validate_concurrentScansOfSameValidTicket_exactlyOneValidOneDuplicate_neverBothValid`
starts 8 real HTTP threads (`CountDownLatch`-synchronized start, same idiom
as `WaitlistIntegrationTest`'s headline test and
`CheckoutIntegrationTest.concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment`)
all calling `POST /check-in/validate` with the IDENTICAL credential for the
SAME valid ticket at the same instant, against real Postgres. It asserts:

- **Exactly one of the 8 requests resolves VALID**, the other 7 resolve
  DUPLICATE — never 2 (or more) VALID.
- **The ticket ends up `USED`** in the database (read fresh, not just
  trusted from a response body).
- **Exactly 8 `CheckInRecord` rows exist** for the ticket — one per attempt,
  including the 7 DUPLICATE attempts (nothing is silently dropped).

This passed on its first attempt (unlike Phase 9's waitlist concurrency test,
which failed deterministically against the original implementation and
required two rounds of fixes) and was additionally re-run 3 times in
isolation — all 4 total runs passed with the exact same 1-VALID/7-DUPLICATE
split. This is strong (though not absolute) evidence that
`CheckInServiceImpl.resolveAndMarkTicket`'s `ticketRepository.findByIdForUpdate`
pessimistic row lock genuinely serializes the check-then-mark-used sequence,
as its own javadoc claims.

## Findings surfaced by writing these tests

**No production bugs were found in this pass.** Specifically checked and
confirmed correct, not just assumed:

- **The row lock actually works under real concurrency** (see above) — this
  is exactly the class of bug the dispatch called out from Phase 7/8/9's
  code-reviewer passes (stale-listing race, double-refund race, position
  race), and it was the single highest-risk item in this phase given
  `resolveAndMarkTicket`'s read-check-then-write shape. It held up on the
  first attempt.
- **`validate`'s and `getDataset`'s device-id cross-checks are both real
  and independently enforced** — confirmed a device's own valid credential
  genuinely cannot act on a different device's identity (`ScannerDeviceIntegrationTest.getDataset_pathDeviceIdIsADifferentActiveDevice_returns403`)
  or submit a scan attributed to a different device
  (`CheckInServiceImplTest.validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_throwsForbidden`,
  exercised end-to-end in `CheckInIntegrationTest.validate_deviceIdInBodyDoesNotMatchAuthenticatedDevice_returns403`).
- **Revocation is genuinely immediate and checked live, not via a stale
  cache/token blacklist** — a revoked device's structurally-still-valid HMAC
  credential correctly 401s (no `Authentication` ever gets set, matching
  `DeviceAuthenticationFilter`'s live `status == ACTIVE` check) rather than
  403ing, on all three device-only endpoints in one test.
- **`credentialHash` is a genuine SHA-256 digest of the ticket's actual
  `credential` DB column**, independently recomputed and compared in the
  test (not just asserted non-equal to the raw value) — confirmed it's not,
  say, a hash of the ticket id or some other derivable value that would
  look plausible but not actually match what an offline client would
  compute from a freshly-scanned raw credential.
- **`ScannerDeviceServiceImpl.authorize`'s pure_offline gate revokes BEFORE
  authorizing**, and does so for ALL currently-active devices (not just the
  first one found) — confirmed with a 2-existing-active-devices edge case,
  though in practice `authorize` itself only ever creates one ACTIVE device
  at a time so >1 active device shouldn't organically occur; this proves
  the loop is defensively correct rather than assuming exactly-one.
- **BR-CHECKIN-010's "duplicates flagged for organizer review" is fully
  wired**, not just the immediate per-scan response: a duplicate scan
  reconciled via `/check-in/fallback-scans` is BOTH returned as `DUPLICATE`
  in that call's response array AND persisted as a `CheckInRecord`
  (`sourceType=RECONCILED_FALLBACK`) visible later via `GET
  /tickets/{id}/check-in-records` — confirmed end-to-end with a
  same-batch duplicate, not just inferred from the fact that `validate`'s
  DUPLICATE branch happens to reuse the same code path.

## Scenarios not covered (out of scope per the dispatch / genuinely
out of scope)

- **"Standard mode: offline fallback file stops working after its expiry
  window"** — left unchecked in `testing/checkin-test-plan.md` with an
  explanation. This is client-side behavior (an offline scanning device
  locally tracking how long it's been disconnected and refusing to trust
  its own cached fallback file past `offline_fallback_expiry_seconds`) —
  the server never observes "the file expired" as an event of its own, so
  there is nothing for a server-side test to assert. The server-owned half
  of this (storing/returning `offline_fallback_expiry_seconds` via
  `GET`/`PATCH /events/{eventId}/check-in-config`) IS fully tested — see
  `CheckInConfigIntegrationTest.update_owner_returns200_persistsMode`
  asserting the 600-second value round-trips.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'check_in_configs' t, count(*) from check_in_configs
   union all select 'scanner_devices', count(*) from scanner_devices
   union all select 'check_in_records', count(*) from check_in_records
   union all select 'fallback_scan_records', count(*) from fallback_scan_records
   union all select 'tickets', count(*) from tickets;"

           t           | count
-----------------------+-------
 check_in_configs      |     0
 scanner_devices       |     0
 check_in_records      |     0
 fallback_scan_records |     0
 tickets               |     0
(5 rows)
```

All four brand-new Phase 10 tables are `0` — full cleanup confirmed,
including after the 8-thread concurrency test (its `@AfterEach` sweeps
`CheckInRecord` rows by tracked ticket id, which catches every concurrently-
created record since they all attach to the one pre-tracked ticket) and the
fallback-scan tests (`FallbackScanRecord` rows swept by tracked event id,
since concurrent/batch-created rows aren't individually captured
synchronously in the test body). `tickets` is also `0` — this pass is the
first to persist `Ticket` rows via a test file under `checkin/`, and its
`@AfterEach` explicitly deletes every tracked ticket id, confirmed by the
query above. The pre-existing `events`/`organizations`/`organization_members`/
`users` residue documented in prior phases' results docs (4/1/1/11) is
unrelated to and unmodified by this pass.

## Post-dispatch addendum (2026-09-11): code-reviewer found 1 HIGH, 2 MEDIUM, all fixed

A `code-reviewer` pass over this dispatch's files (run after this results
doc was first written) found one HIGH and two MEDIUM issues, all fixed and
the HIGH covered by a new regression test:

- **HIGH — a TOCTOU race let `pure_offline` mode end up with two
  simultaneously-`ACTIVE` scanner devices, violating BR-CHECKIN-008's
  "exactly one authorized device" invariant.**
  `ScannerDeviceServiceImpl.authorize()` read the event's active-device list
  via a plain `ScannerDeviceRepository.findByEventIdAndStatus` (no lock) and
  no DB-level constraint backed the invariant either (`V18` only indexes
  `scanner_devices(event_id)`, no partial unique index). Two concurrent
  `POST /events/{eventId}/scanner-devices` requests for the same
  `pure_offline` event (or two concurrent `force_replace` calls) could both
  read the same pre-lock device list under Postgres `READ_COMMITTED`, both
  pass the check/revoke step, and both insert a new `ACTIVE` device - after
  which both could pull the full offline dataset and both could validate
  scans, defeating the "exactly one real device" guarantee pure_offline mode
  depends on. This pass's own tests for this path
  (`ScannerDeviceServiceImplTest`) were Mockito-based and sequential, and the
  one real concurrency test in this suite (`CheckInIntegrationTest`'s
  8-thread scan race) covers a completely different code path
  (`CheckInServiceImpl.resolveAndMarkTicket`'s ticket lock), so this race was
  never exercised. Fixed by locking the event's `CheckInConfig` row first -
  new `CheckInConfigRepository.findByEventIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`,
  same idiom as `OrderRepository`/`TicketRepository`'s `findByIdForUpdate`) -
  so concurrent `authorize()` calls for a pure_offline event now actually
  serialize on that row; an absent config row (STANDARD mode, no invariant
  to protect) takes no lock, so the STANDARD-mode unlimited-devices path is
  unaffected. New test:
  `ScannerDeviceIntegrationTest.authorize_pureOfflineMode_concurrentRequests_onlyOneEverActive`
  (8-thread `ExecutorService`, asserts exactly 1 success + `threadCount - 1`
  conflicts + exactly 1 `ACTIVE` device in the DB afterward). The three
  pre-existing `ScannerDeviceServiceImplTest` cases that stubbed
  `checkInConfigRepository.findByEventId` were updated to stub
  `findByEventIdForUpdate` instead, since that's now the only method the
  production code calls.
- **MEDIUM — `ScannerDeviceAuthorizeRequest`/`CheckInConfigUpdateRequest`
  had no validation, and neither controller method applied `@Valid`.** A
  missing `deviceLabel` (a `NOT NULL` column) fell through to an unhandled
  `DataIntegrityViolationException` at insert time (a raw 500, since no
  global `@ControllerAdvice` exists yet per `CLAUDE.md`) instead of a clean
  400; a negative/zero `offlineFallbackExpirySeconds` was silently persisted
  and echoed back by `GET`, undermining BR-CHECKIN-007's "fixed time window"
  semantics. Fixed by adding `@NotBlank` to `deviceLabel`, `@Positive` to
  `offlineFallbackExpirySeconds`, and `@Valid` on both controller methods -
  matching every other Phase 10 request DTO's existing pattern
  (`ValidateScanRequest`, `FallbackScanBatchRequest`). No new test added:
  this is the same validation idiom already covered by existing tests
  elsewhere in the suite (e.g. `CheckInControllerTest`'s missing-field 400
  cases), and re-adding an equivalent case here would duplicate that
  coverage rather than exercise anything new.
- **MEDIUM — filter-ordering safety for `deviceAuth` relied on undocumented,
  fragile Spring Security behavior.** `JwtAuthenticationFilter` and
  `DeviceAuthenticationFilter` both anchor at
  `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`, so
  their relative order is whatever this dispatch's registration order
  (JWT first) happens to produce via a stable sort - nothing enforced it.
  This worked safely only because `JwtAuthenticationFilter` called
  `SecurityContextHolder.clearContext()` unconditionally on any
  `JwtException`, and a device credential always fails JWT parsing - but if
  the two `addFilterBefore` calls were ever reordered, a legitimate device
  request's just-set `Authentication` would be silently wiped by the JWT
  filter's `clearContext()` call, 401-ing every `deviceAuth` endpoint for
  every device. Fixed by removing the `clearContext()` call - it has no
  positive effect today (nothing is set yet when this filter's catch runs in
  the current order) and its only effect was this footgun; a short comment
  now documents why. No new test added: this fix makes the two filters'
  relative order a non-issue rather than changing any currently-observable
  behavior, so there's nothing new for a test to assert against without
  reaching into filter-registration internals.

**Verification:** targeted `checkin.**`/`auth.**` run (117 tests, 0
failures) followed by the full suite (999 tests, 0 failures - 998 from the
original dispatch + this addendum's 1 new regression test). `BUILD
SUCCESS`.
