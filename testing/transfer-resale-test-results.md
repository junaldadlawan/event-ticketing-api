# Ticket Transfer & Resale (Phase 7) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/phase6b-templates-artifacts`
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-TRANSFER-001`–`005`
(transfer ownership/identity/credential-invalidation rules, organizer-controlled
resale, price caps), `BR-NFR-008` (idempotent purchase); `docs/event-ticketing-api-use-cases.md`
`UC-ATTND-08` (transfer a ticket) and `UC-ATTND-09` (resell a ticket);
`testing/transfer-test-plan.md` and `testing/resale-test-plan.md`; the new
`tickettransfer/` module (`TicketTransfer` entity, repository,
`TicketTransferService`/`Impl`, new `TicketController` endpoints), the new
`resalepolicy/` module (`ResalePolicy` entity, repository,
`ResalePolicyService`/`Impl`, `EventResalePolicyController`), the new
`resalelisting/` module (`ResaleListing`/`ResalePurchaseIdempotencyKey`
entities, repositories, `ResaleListingService`/`Impl`,
`ResalePurchaseIdempotencyKeyManager`, `TicketResaleListingController`/
`ResaleListingController`/`EventResaleListingController`), migration
`V14__add_transfer_and_resale_tables.sql`, `Ticket.credentialVersion`, and the
`TicketCredentialService` credential-format change
(`ticketId + "." + sig` → `ticketId + ":" + version + "." + sig`).

Before this pass there was **zero** test coverage for any Phase 7 code —
`git status` showed the entire `tickettransfer/`, `resalepolicy/`, and
`resalelisting/` production packages as untracked with no matching test
files. This pass adds full coverage across all three layers this repo's
convention calls for (Mockito unit tests, `@WebMvcTest` slices, full
`@SpringBootTest` integration tests with real Postgres + real JWTs) and
proves the two headline correctness properties the dispatch called out:
(1) a transfer/resale genuinely changes a ticket's credential for the *same*
ticket row, read directly from Postgres, and (2) a resale purchase produces
a real `TicketTransfer` row, reassigns `owner_id`, and creates a real
`Order`/`Payment` with `payeeType=USER`.

## Commands run and results

**Targeted (new Phase 7 packages + the shared `TicketControllerTest` file
that Phase 7 extended):**
```
mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.tickettransfer.**,com.junaldadlawan.event_ticketing_api.resalepolicy.**,com.junaldadlawan.event_ticketing_api.resalelisting.**,com.junaldadlawan.event_ticketing_api.ticket.controller.TicketControllerTest test
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 129 tests run, 0 failures, 0 errors.

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.563 s -- in com.junaldadlawan.event_ticketing_api.resalelisting.controller.EventResaleListingControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.670 s -- in com.junaldadlawan.event_ticketing_api.resalelisting.controller.ResaleListingControllerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.504 s -- in com.junaldadlawan.event_ticketing_api.resalelisting.controller.TicketResaleListingControllerTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.795 s -- in com.junaldadlawan.event_ticketing_api.resalelisting.ResaleListingIntegrationTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.042 s -- in com.junaldadlawan.event_ticketing_api.resalelisting.service.ResaleListingServiceImplTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.506 s -- in com.junaldadlawan.event_ticketing_api.resalepolicy.controller.EventResalePolicyControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.495 s -- in com.junaldadlawan.event_ticketing_api.resalepolicy.ResalePolicyIntegrationTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in com.junaldadlawan.event_ticketing_api.resalepolicy.service.ResalePolicyServiceImplTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.710 s -- in com.junaldadlawan.event_ticketing_api.ticket.controller.TicketControllerTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.352 s -- in com.junaldadlawan.event_ticketing_api.tickettransfer.service.TicketTransferServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.656 s -- in com.junaldadlawan.event_ticketing_api.tickettransfer.TicketTransferIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Full suite:** `mvnw.cmd test`

**Result:** BUILD SUCCESS — 714 tests run, 0 failures, 0 errors. Of these,
122 are new/changed this pass (51 Mockito unit, 37 `@WebMvcTest` slice, 34
full `@SpringBootTest` integration); the remaining 592 are the pre-existing
suite (the baseline stated in the dispatch), run unchanged to confirm no
regression.

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.225 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 714, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  36.201 s
[INFO] Finished at: 2026-09-11T13:35:35+08:00
[INFO] ------------------------------------------------------------------------
```

**Note on the first full-suite run:** an initial `mvnw.cmd test` run
(714 tests) reported one **error** —
`TicketArtifactIntegrationTest.getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId`
→ `NotFoundException` (ZXing failed to locate the QR finder pattern). This is
the pre-existing, already-documented flaky test from Phase 6b's own results
doc (`testing/ticket-template-artifact-test-results.md`'s "Post-dispatch
addendum" — a ZXing decode flake against the full composited ticket PNG,
occurring roughly 1-in-5 to 1-in-8 runs even after that pass's `TRY_HARDER`
mitigation). It is unrelated to any Phase 7 file — `TicketArtifactIntegrationTest`
and the `ticket/artifact/` package were not touched by this dispatch. An
immediate re-run reproduced 0 failures/errors across all 714 tests (pasted
above), confirming this was the known flake recurring, not a regression
introduced by Phase 7.

## Test files added/changed

- `src/test/java/.../tickettransfer/service/TicketTransferServiceImplTest.java`
  — **new**, 12 tests (Mockito, no Spring context). `transfer`'s full
  authorization/status/recipient-validation chain and the shared
  `recordTransfer` primitive's credential-bump mechanics, exercised directly.
- `src/test/java/.../tickettransfer/TicketTransferIntegrationTest.java` —
  **new**, 11 tests (full `@SpringBootTest`, real filter chain, real signed
  JWTs, real Postgres). The credential-invalidation proof (before/after DB
  read) and the full visibility matrix for both endpoints.
- `src/test/java/.../ticket/controller/TicketControllerTest.java` —
  **extended**, +9 tests for the two new endpoints on this existing
  `@WebMvcTest` slice (`transfer`/`listTransfers` status-code mapping,
  credential-absence from the transfer response).
- `src/test/java/.../resalepolicy/service/ResalePolicyServiceImplTest.java`
  — **new**, 10 tests (Mockito). `get`'s synthesized default, `update`'s
  owner/organizer/admin/stranger/cross-org authorization matrix, and upsert
  semantics (create vs. update-in-place, field clearing).
- `src/test/java/.../resalepolicy/controller/EventResalePolicyControllerTest.java`
  — **new**, 7 tests (`@WebMvcTest` slice).
- `src/test/java/.../resalepolicy/ResalePolicyIntegrationTest.java` —
  **new**, 9 tests (full `@SpringBootTest`, real Postgres). Public `GET`/
  authenticated `PATCH` split, full authorization matrix, and a GET-after-PATCH
  round trip proving persistence.
- `src/test/java/.../resalelisting/service/ResaleListingServiceImplTest.java`
  — **new**, 29 tests (Mockito). `create`'s full validation chain including
  the price-cap math for all three `PriceCapRule` values (and the
  no-fee-configured-defaults-to-zero edge case), `cancel`'s ownership/status
  gates, `listActive`, and `purchase`'s idempotency claim/replay/conflict
  branching plus the successful-purchase Order/Payment/`recordTransfer`
  side effects.
- `src/test/java/.../resalelisting/controller/TicketResaleListingControllerTest.java`
  — **new**, 8 tests (`@WebMvcTest` slice).
- `src/test/java/.../resalelisting/controller/ResaleListingControllerTest.java`
  — **new**, 11 tests (`@WebMvcTest` slice) — cancel + purchase status-code
  mapping (mirrors `CartCheckoutControllerTest`'s style since purchase runs
  like checkout).
- `src/test/java/.../resalelisting/controller/EventResaleListingControllerTest.java`
  — **new**, 2 tests (`@WebMvcTest` slice) — paginated public browse.
- `src/test/java/.../resalelisting/ResaleListingIntegrationTest.java` —
  **new**, 14 tests (full `@SpringBootTest`, real filter chain, real
  Postgres). The full lifecycle end-to-end plus the two headline
  correctness properties (real `TicketTransfer` row + owner reassignment +
  `Order.payeeType=USER`; credential invalidation read from Postgres).

## Scenario → test mapping

### `tickettransfer/service/TicketTransferServiceImplTest` (Mockito, 12 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown ticket → 404 | `.transfer_unknownTicket_throwsResourceNotFound` | Pass |
| Non-owning caller → 403 (BR-TRANSFER-001) | `.transfer_nonOwningCaller_throwsForbidden` | Pass |
| Ticket not `VALID` → 409 | `.transfer_ticketNotValid_throwsConflict` | Pass |
| Recipient == current owner → 400 | `.transfer_recipientIsCurrentOwner_throwsBadRequest` | Pass |
| Recipient not a registered user → 404 | `.transfer_recipientNotRegistered_throwsResourceNotFound` | Pass |
| Recipient soft-deleted → 404 | `.transfer_recipientSoftDeleted_throwsResourceNotFound` | Pass |
| **Valid transfer: version bumped, credential regenerated, owner reassigned, unchanged fields verified, audit row correct** | `.transfer_validRequest_bumpsCredentialVersion_regeneratesCredential_reassignsOwner_andRecordsAuditRow` | Pass |
| `listTransfers`: unknown ticket → 404 | `.listTransfers_unknownTicket_throwsResourceNotFound` | Pass |
| `listTransfers`: access guard approves → returns history | `.listTransfers_accessGuardApproves_returnsHistoryOldestFirst` | Pass |
| `listTransfers`: access guard rejects → forbidden propagates | `.listTransfers_accessGuardRejects_propagatesForbidden` | Pass |
| **`recordTransfer` shared primitive (resale path's entry point): version bumps from whatever it started at, source tagged correctly** | `.recordTransfer_resaleSource_bumpsVersionFromWhateverItStartedAt_andTagsSourceCorrectly` | Pass |
| **`recordTransfer` produces a genuinely different credential for the same ticket id (BR-TRANSFER-005 core proof, unit level)** | `.recordTransfer_producesDifferentCredentialThanBefore_forTheSameTicketId` | Pass |

### `ticket/controller/TicketControllerTest` (`@WebMvcTest`, +9 tests for Phase 7)

| Scenario | Test | Result |
|---|---|---|
| Valid transfer → 200, updated owner, credential absent | `.transfer_validRequest_returns200_withUpdatedOwnerAndWithoutCredential` | Pass |
| Missing `toUserId` → 400 | `.transfer_missingToUserId_returns400` | Pass |
| Unknown ticket → 404 | `.transfer_unknownTicket_returns404` | Pass |
| Non-owning caller → 403 | `.transfer_nonOwningCaller_returns403` | Pass |
| Ticket not valid → 409 | `.transfer_ticketNotValid_returns409` | Pass |
| Recipient is current owner → 400 | `.transfer_recipientIsCurrentOwner_returns400` | Pass |
| `listTransfers`: existing ticket → 200, ordered history | `.listTransfers_existingTicket_returns200_withOrderedHistory` | Pass |
| `listTransfers`: unauthorized → 403 | `.listTransfers_unauthorizedCaller_returns403` | Pass |
| `listTransfers`: unknown ticket → 404 | `.listTransfers_unknownTicket_returns404` | Pass |

### `tickettransfer/TicketTransferIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 11 tests)

| Scenario | Test | Result |
|---|---|---|
| **Owning buyer transfers to a registered recipient → 200; credential genuinely differs in Postgres before/after; `credentialVersion` 0→1; unchanged fields (ticketNumber/eventId/ticketTypeId/seatId/status) verified fresh from the DB; a real `TicketTransfer` audit row exists with correct from/to/source** | `.transfer_owningBuyer_toRegisteredRecipient_returns200_andGenuinelyInvalidatesCredentialInDb` | **Pass** |
| Non-owning caller → 403, ticket unchanged in DB | `.transfer_nonOwningCaller_returns403_ticketUnchanged` | Pass |
| Recipient not registered → 404 | `.transfer_recipientNotRegistered_returns404` | Pass |
| Transfer to self → 400 | `.transfer_toSelf_returns400` | Pass |
| Already-`USED` ticket → 409 | `.transfer_ticketAlreadyUsed_returns409` | Pass |
| Unknown ticket → 404 | `.transfer_unknownTicket_returns404` | Pass |
| No token → 401 | `.transfer_noToken_returns401` | Pass |
| Owning buyer (post-transfer, now the recipient) can list history | `.listTransfers_owningBuyerAfterTransfer_returns200_withHistory` | Pass |
| Event organizer can list history | `.listTransfers_eventOrganizer_returns200` | Pass |
| Roleless stranger → 403 | `.listTransfers_roselessStranger_returns403` | Pass |
| Admin can list history | `.listTransfers_admin_returns200` | Pass |

### `resalepolicy/service/ResalePolicyServiceImplTest` (Mockito, 10 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown event → 404 | `.get_unknownEvent_throwsResourceNotFound` | Pass |
| No policy row yet → synthesized `enabled=false` default | `.get_noPolicyRowYet_returnsSynthesizedDisabledDefault` | Pass |
| Existing policy row → mapped fields | `.get_existingPolicyRow_mapsFields` | Pass |
| Unknown event → 404 (update) | `.update_unknownEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `.update_stranger_throwsForbidden` | Pass |
| **Owner of a DIFFERENT org → 403** | `.update_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| **Admin bypasses org-role lookup entirely** | `.update_admin_bypassesOrgRoleCheck_withNoCurrentUserIdOrHasRoleCallAtAll` | Pass |
| Organizer succeeds | `.update_organizer_succeeds` | Pass |
| No existing row → creates a new policy for the event | `.update_noExistingRow_createsNewPolicyForTheEvent` | Pass |
| **Existing row → upserts in place (not a duplicate); omitted `feeAmount` clears the field** | `.update_existingRow_upsertsInPlace_ratherThanCreatingASecondRow` | Pass |

### `resalepolicy/controller/EventResalePolicyControllerTest` (`@WebMvcTest`, 7 tests)

Request validation (missing `enabled`, invalid fee currency), 200/403/404
status mapping for `get`/`update` — mirrors this repo's slice-test
convention (security enforcement left to the integration test).

### `resalepolicy/ResalePolicyIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 9 tests)

| Scenario | Test | Result |
|---|---|---|
| **`GET`, no policy yet, NO TOKEN AT ALL → 200, disabled default (proves the "no SecurityConfig change needed" claim)** | `.get_noPolicyYet_noTokenAtAll_returns200_disabledDefault` | **Pass** |
| Unknown event → 404 | `.get_unknownEvent_returns404` | Pass |
| Owner → 200, enables resale with a `FACE_VALUE` cap; round-trip GET reflects it | `.update_owner_returns200_enablesResaleWithFaceValueCap` | Pass |
| Organizer → 200, sets a `FACE_VALUE_PLUS_FEE` cap with a fee | `.update_organizer_returns200_withFeeCap` | Pass |
| Admin (no org membership) → 200 | `.update_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |
| Roleless stranger → 403 | `.update_roselessStranger_returns403` | Pass |
| **Cross-org owner → 403** | `.update_crossOrgOwner_returns403` | Pass |
| **`PATCH`, no token → 401 (proves PATCH genuinely requires auth via the existing broad matcher)** | `.update_noToken_returns401` | Pass |
| Unknown event → 404 (update) | `.update_unknownEvent_returns404` | Pass |

### `resalelisting/service/ResaleListingServiceImplTest` (Mockito, 29 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown ticket → 404 | `.create_unknownTicket_throwsResourceNotFound` | Pass |
| Non-owning caller → 403 | `.create_nonOwningCaller_throwsForbidden` | Pass |
| Ticket not `VALID` → 409 | `.create_ticketNotValid_throwsConflict` | Pass |
| No resale policy row at all → 403 | `.create_noResalePolicyRowAtAll_throwsForbidden` | Pass |
| Resale explicitly disabled → 403 | `.create_resaleDisabled_throwsForbidden` | Pass |
| Asking-price currency ≠ ticket type's face-value currency → 400 | `.create_askingPriceCurrencyMismatch_throwsBadRequest` | Pass |
| `FACE_VALUE` cap exceeded → 403 | `.create_faceValueCap_askingPriceAboveFaceValue_throwsForbidden` | Pass |
| `FACE_VALUE` cap, price exactly at cap → succeeds | `.create_faceValueCap_askingPriceExactlyAtFaceValue_succeeds` | Pass |
| **`FACE_VALUE_PLUS_FEE`, price within face+fee → succeeds** | `.create_faceValuePlusFeeCap_askingPriceWithinFacePlusFee_succeeds` | Pass |
| **`FACE_VALUE_PLUS_FEE`, price above face+fee → 403** | `.create_faceValuePlusFeeCap_askingPriceAboveFacePlusFee_throwsForbidden` | Pass |
| **`FACE_VALUE_PLUS_FEE` with no fee configured → treated as zero fee, cap == face value** | `.create_faceValuePlusFeeCap_noFeeConfigured_treatedAsZeroFee_capEqualsFaceValue` | Pass |
| **`NONE` cap rule → any price allowed** | `.create_noneCapRule_anyPriceAllowed` | Pass |
| **Null cap rule → treated same as `NONE`, any price allowed** | `.create_nullPriceCapRule_treatedSameAsNone_anyPriceAllowed` | Pass |
| Ticket already has an active listing → 409 | `.create_ticketAlreadyHasActiveListing_throwsConflict` | Pass |
| **Concurrent race lost at DB level (`DataIntegrityViolationException`) → mapped to 409** | `.create_concurrentRaceLostAtDbLevel_dataIntegrityViolation_mappedToConflict` | Pass |
| `cancel`: unknown listing → 404 | `.cancel_unknownListing_throwsResourceNotFound` | Pass |
| `cancel`: non-seller → 403 | `.cancel_nonSeller_throwsForbidden` | Pass |
| `cancel`: not active → 409 | `.cancel_notActive_throwsConflict` | Pass |
| `cancel`: owning seller, active → succeeds | `.cancel_owningSeller_activeListing_succeeds` | Pass |
| `listActive`: unknown event → 404 | `.listActive_unknownEvent_throwsResourceNotFound` | Pass |
| `listActive`: success, only active listings mapped | `.listActive_success_returnsOnlyActiveListingsMapped` | Pass |
| `purchase`: cross-buyer idempotency key → 403 | `.purchase_crossBuyerIdempotencyKey_throwsForbidden` | Pass |
| `purchase`: replay with completed key → same order, no recharge | `.purchase_replayWithCompletedKey_returnsSameOrder_noRecharge` | Pass |
| `purchase`: in-flight duplicate key → 409 | `.purchase_inFlightDuplicateKey_throwsConflict` | Pass |
| `purchase`: listing not found → 404, frees key | `.purchase_listingNotFound_throwsResourceNotFound_andFreesKey` | Pass |
| `purchase`: listing no longer active → 409, frees key | `.purchase_listingNoLongerActive_throwsConflict_andFreesKey` | Pass |
| `purchase`: buyer == seller → 403, frees key | `.purchase_sellerAttemptsToBuyOwnListing_throwsForbidden_andFreesKey` | Pass |
| `purchase`: payment declined → 402, listing stays active, frees key | `.purchase_paymentDeclined_throwsPaymentFailed_listingStaysActive_andFreesKey` | Pass |
| **`purchase`: success — seller as payee, Order/Payment created, `TicketTransferService.recordTransfer` invoked with `RESALE` source, listing marked SOLD with `buyerOrderId`** | `.purchase_success_chargesSellerAsPayee_createsOrderAndPayment_recordsTransfer_marksListingSold` | **Pass** |

### `resalelisting/controller/*ControllerTest` (`@WebMvcTest`, 21 tests total)

Request validation (missing `askingPrice`/`paymentMethodToken`/
`Idempotency-Key`, invalid currency format), 201/200/204/400/402/403/404/409
status-code mapping across all three controllers, and the paginated
public-browse response shape — mirrors this repo's slice-test convention.

### `resalelisting/ResaleListingIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 14 tests)

| Scenario | Test | Result |
|---|---|---|
| Owning buyer, resale enabled, within cap → 201 | `.create_owningBuyer_resaleEnabledWithinCap_returns201` | Pass |
| Resale disabled (no policy row) → 403 | `.create_resaleDisabled_returns403` | Pass |
| Above price cap → 403 | `.create_askingPriceAbovePriceCap_returns403` | Pass |
| Non-owning caller → 403 | `.create_nonOwningCaller_returns403` | Pass |
| Ticket already has an active listing → 409 | `.create_ticketAlreadyHasActiveListing_returns409` | Pass |
| No token → 401 | `.create_noToken_returns401` | Pass |
| Owning seller cancels → 204, no longer in the active-listings browse | `.cancel_owningSeller_returns204_thenNoLongerInActiveList` | Pass |
| Non-seller cancel attempt → 403 | `.cancel_nonSeller_returns403` | Pass |
| Public browse returns only `ACTIVE` listings (a cancelled one is excluded) | `.listActive_public_returnsOnlyActiveListings` | Pass |
| **THE headline test: purchase reassigns `owner_id`, invalidates the credential (read from Postgres), records a real `TicketTransfer` row (`source=RESALE`), creates a real `Order` (`payeeType=USER`/`payeeId=seller`) and `Payment`, marks the listing `SOLD` with `buyerOrderId` set** | `.purchase_success_reassignsOwner_recordsResaleTransfer_createsOrderWithUserPayee` | **Pass** |
| Buyer cannot purchase their own listing → 403 | `.purchase_buyerIsSeller_returns403` | Pass |
| A second buyer's purchase of an already-sold listing → 409 | `.purchase_listingAlreadySold_returns409_onSecondBuyer` | Pass |
| Payment declined → 402, listing stays `ACTIVE`, ticket/idempotency-key untouched | `.purchase_paymentDeclined_returns402_listingStaysActiveForRetry` | Pass |
| Replaying the same idempotency key after success → same Order, no double `Payment`/`TicketTransfer` | `.purchase_replaySameIdempotencyKey_returnsSameOrder_noDoubleCharge` | Pass |

## The credential-invalidation result (BR-TRANSFER-005 — most important assertion for transfer)

Verified at two independent layers, both passing:

1. **`TicketTransferServiceImplTest.recordTransfer_producesDifferentCredentialThanBefore_forTheSameTicketId`**
   — unit level: `recordTransfer` called on a `Ticket` with a mocked
   `TicketCredentialService`, asserting the returned credential differs from
   the original AND the ticket `id` is unchanged (same row, not a new
   ticket).
2. **`TicketTransferIntegrationTest.transfer_owningBuyer_toRegisteredRecipient_returns200_andGenuinelyInvalidatesCredentialInDb`**
   — full HTTP round trip: real JWT, real Postgres-persisted `Ticket` row
   with a known original `credential`, real
   `POST /tickets/{ticketId}/transfer` call, then a **fresh read of the same
   row from Postgres** (`ticketRepository.findById(...)`) — the new
   `credential` differs from the original, `credentialVersion` is `0→1`, and
   `ticketNumber`/`eventId`/`ticketTypeId`/`seatId`/`status` are all
   unchanged from the pre-transfer values.

## The resale-purchase cross-module-reuse result (most important assertion for resale)

**`ResaleListingIntegrationTest.purchase_success_reassignsOwner_recordsResaleTransfer_createsOrderWithUserPayee`**
proves, against real Postgres in one HTTP call, that a resale purchase does
not just move money — it genuinely goes through the same
`TicketTransferService.recordTransfer` primitive as a direct transfer:

- `tickets.owner_id` reassigned to the buyer, `credential`/`credential_version`
  changed (same mechanism, same proof idiom as direct transfer).
- A real `ticket_transfers` row exists with `source='RESALE'`,
  `from_user_id`=seller, `to_user_id`=buyer.
- A real `orders` row exists with `payee_type='USER'`, `payee_id`=seller
  (not the event's organization — resale pays the seller directly).
- A real `payments` row exists for that order with the listing's asking
  price and `status=COMPLETED`.
- The `resale_listings` row is marked `SOLD` with `buyer_order_id` set to
  the new order's id.

This is the one property a purely mocked unit test cannot prove (the unit
test only proves `ticketTransferService.recordTransfer(...)` was *called*
with the right arguments) — the integration test proves the whole chain
actually persists correctly end-to-end.

## Findings surfaced by writing these tests

No production bugs were found. Every behavior these tests assert —
the price-cap math for all three `PriceCapRule` values including the
no-fee-configured and null-rule edge cases, the upsert semantics of
`ResalePolicyServiceImpl.update`, the credential-version-bump mechanics of
the shared `recordTransfer` primitive, the idempotency claim/replay/conflict
branching mirrored from `CheckoutServiceImpl`, and the full authorization
matrices — matched the production code exactly on the first passing run.

The dispatch's specific claim that **no `SecurityConfig` changes were
needed** for any Phase 7 endpoint was verified behaviorally, not just read
from a comment: `ResalePolicyIntegrationTest.get_noPolicyYet_noTokenAtAll_returns200_disabledDefault`
proves `GET /events/{eventId}/resale-policy` is genuinely public with zero
`Authorization` header at all, `ResalePolicyIntegrationTest.update_noToken_returns401`
proves `PATCH` genuinely requires auth, and the full authorization matrices
in both `ResaleListingIntegrationTest` and `TicketTransferIntegrationTest`
(403/401 for unauthorized callers, 200/201 for authorized ones) confirm the
new `/api/v1/tickets/**`, `/api/v1/resale-listings/**`, and
`/api/v1/events/**/resale-listings` request paths are correctly gated by
the pre-existing broad matchers alone.

One pre-existing, out-of-scope observation carried over from prior
sessions' notes: the shared dev Postgres database carries a small number of
leftover rows (`organizations`=1, `organization_members`=1, `events`=4,
`users`=11, `ticket_types`=3) that predate this session (all timestamped
between 2026-08-31 and 2026-09-10) and are not touched by anything in this
pass — verified identical before and after this pass's targeted 129-test run
and the full 714-test suite run (see cleanup verification below).

## Concurrency-sensitive rule: noted but not additionally tested (per the dispatch's own "optional stretch goal" framing)

`TicketRepository.findByIdForUpdate` and `ResaleListingRepository.findByIdForUpdate`
exist specifically so a resale purchase and a concurrent direct transfer of
the same ticket, or two concurrent purchases of the same listing, can't
race. The dispatch explicitly called a real concurrent-request test for this
"optional, not required if time-constrained; correctness of the
single-request path matters more." This pass proves the single-request path
exhaustively (see `purchase_listingAlreadySold_returns409_onSecondBuyer`
above, which proves the *sequential* double-purchase case is rejected) but
does not add a genuinely concurrent `ExecutorService`-based race test for
the resale paths — `CheckoutIntegrationTest`'s existing
`concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment`
already proves this same `findByIdForUpdate` locking idiom prevents a
double-sale under real concurrency for the structurally identical checkout
path, which is the closest existing proof of the locking primitive's
correctness. If a dedicated concurrent test for the resale/transfer race is
wanted, it would need to be added as a follow-up.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'ticket_transfers' t, count(*) from ticket_transfers
   union all select 'resale_policies', count(*) from resale_policies
   union all select 'resale_listings', count(*) from resale_listings
   union all select 'resale_purchase_idempotency_keys', count(*) from resale_purchase_idempotency_keys
   union all select 'tickets', count(*) from tickets
   union all select 'orders', count(*) from orders
   union all select 'payments', count(*) from payments
   union all select 'users', count(*) from users
   union all select 'ticket_types', count(*) from ticket_types
   union all select 'organizations', count(*) from organizations
   union all select 'organization_members', count(*) from organization_members
   union all select 'events', count(*) from events;"

                t                 | count
----------------------------------+-------
 ticket_transfers                 |     0
 resale_policies                  |     0
 resale_listings                  |     0
 resale_purchase_idempotency_keys |     0
 tickets                          |     0
 orders                           |     0
 payments                         |     0
 users                            |    11
 ticket_types                     |     3
 organizations                    |     1
 organization_members             |     1
 events                           |     4
```

All four brand-new Phase 7 tables (`ticket_transfers`, `resale_policies`,
`resale_listings`, `resale_purchase_idempotency_keys`) are `0` — full
cleanup confirmed, no leftover rows from this pass's tests at all. `tickets`/
`orders`/`payments` (tables this pass's tests also write to, alongside
Phase 6a) are likewise `0`. `users`=11/`ticket_types`=3 were independently
confirmed to be pre-existing rows dated 2026-08-31 through 2026-09-10 (i.e.
before today) via a direct timestamp query — none match this pass's
`resale-*`/`transfer-*` test-user email patterns (a targeted count query
returned `0`). `organizations`=1/`organization_members`=1/`events`=4 are the
same pre-existing residue already documented in the Phase 6a/6b results
docs, unrelated to and unmodified by this pass.

## Post-dispatch addendum (2026-09-11): code-reviewer CRITICAL + 2 MEDIUM fixed

A `code-reviewer` pass over this dispatch's files (run after this results
doc was first written) found one CRITICAL and two MEDIUM issues, all fixed
and covered by new regression tests:

- **CRITICAL — stale resale listing could pay out to the wrong seller.**
  `ResaleListingServiceImpl.doPurchase` never checked that the listing's
  `sellerId` still matched the ticket's current `ownerId`, and
  `TicketTransferServiceImpl.transfer` never touched resale listings at
  all. Sequence: seller A lists ticket T for resale, then directly
  transfers T to B via `POST /tickets/{T}/transfer` (the listing stays
  ACTIVE); buyer C then purchases the still-ACTIVE listing — C pays A (not
  B, the actual current owner), and the ticket is silently reassigned from
  B to C with no compensation. Fixed two ways: (1)
  `TicketTransferServiceImpl.transfer` now auto-cancels any ACTIVE listing
  on a ticket before reassigning its owner (`ResaleListingRepository`
  injected as a new dependency); (2) `ResaleListingServiceImpl.doPurchase`
  now independently re-verifies `ticket.getOwnerId() ==
  listing.getSellerId()` after locking the ticket, throwing `409` if stale
  - defense in depth, so this can never pay out to the wrong person even if
    (1) is ever bypassed. New tests:
  `TicketTransferServiceImplTest.transfer_ticketHasActiveResaleListing_autoCancelsItBeforeReassigningOwner`/
  `..._doesNotTouchResaleListingRepository`,
  `ResaleListingServiceImplTest.purchase_ticketOwnerNoLongerMatchesListingSeller_throwsConflict_andFreesKey`.
- **MEDIUM — resale price cap used the ticket type's CURRENT price, not
  what was actually paid.** An organizer raising tiered/early-bird pricing
  after a ticket was bought could let that ticket resell above
  BR-TRANSFER-004's intended cap. Fixed by snapshotting the ticket type's
  price onto a new `Ticket.faceValue` column at checkout issuance (`V15__add_ticket_face_value.sql`,
  nullable so pre-existing tickets fall back to the ticket type's current
  price unchanged) and anchoring `ResaleListingServiceImpl.requirePriceWithinCap`
  to it instead. New test:
  `ResaleListingServiceImplTest.create_faceValueCap_usesTicketsOwnFaceValue_notTicketTypesCurrentHigherPrice`.
- **MEDIUM — `ResalePolicyServiceImpl.update`'s upsert had no
  `DataIntegrityViolationException` backstop** for the race where two
  concurrent first-time PATCHes for the same event both miss `findByEventId`
  and both attempt an insert against V14's unique index on `event_id` -
  inconsistent with `ResaleListingServiceImpl.create`'s already-established
  handling of the equivalent race. Fixed with the same
  catch-and-retry-against-the-winner pattern; three pre-existing tests
  updated to stub `saveAndFlush` (now used for the first-insert path so the
  constraint violation surfaces synchronously) instead of `save`.

**Verification:** `mvnw.cmd test-compile` and `mvnw.cmd test` both clean
after every fix; full suite BUILD SUCCESS, 720 tests, 0 failures/errors
(714 baseline + 6 new regression tests from this addendum).
