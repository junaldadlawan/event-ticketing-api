# Cart, CartItem & PromoCode (Phase 5a) — Proof of Testing

**Date:** 2026-09-10
**Branch:** `feat/phase5a-cart-promocode`
**Based on:** `git status` (all of `cart/` and `promocode/` are untracked new
modules, not visible in `git diff`) — `Cart`/`CartItem` entities,
`CartService`/`Impl`, `CartController`, `CartItemCreateRequest`/
`CartItemResponse`/`CartResponse`/`ApplyPromoCodeRequest`/
`AppliedPromoCodeResponse` DTOs, `CartRepository`/`CartItemRepository`;
`PromoCode` entity, `DiscountType` enum, `PromoCodeService`/`Impl`,
`EventPromoCodeController`, `PromoCodeCreateRequest`/`PromoCodeResponse`,
`PromoCodeRepository`; migration `V10__add_carts_and_promo_codes.sql`;
`SecurityConfig`'s new matchers for `/api/v1/carts/**` and
`/api/v1/events/*/promo-codes`; the new `findByIdForUpdate` pessimistic-lock
finders added to `TicketTypeRepository`/`SeatRepository`; `openapi.yaml`'s
`Cart`/`CartItem`/`PromoCode`/`PromoCodeCreate`/`Money` schemas (~1623,
~1897-2110) and the `/carts`, `/carts/{cartId}`, `/carts/{cartId}/items`,
`/carts/{cartId}/items/{itemId}`, `/carts/{cartId}/promo-code`,
`/events/{eventId}/promo-codes` paths (~638-669, ~859-974); `BR-INV-001–006`,
`BR-CART-001`, `BR-PROMO-001–007` in
`docs/event-ticketing-api-business-rules.md`; `UC-ATTND-02`/`03`,
`UC-EVENT-06` in `docs/event-ticketing-api-use-cases.md`.

`developer` implemented Phase 5a and `code-reviewer` reviewed it **twice**
with all findings fixed and confirmed. Before this pass there was **ZERO**
automated coverage for the `cart`/`promocode` modules — all prior
verification was manual/curl, including the specific concurrency bug fix
documented in `CartServiceImpl.addGaItem`'s comment (the missing
`entityManager.refresh()` call, found and fixed via manual concurrent-POST
testing before this automated suite existed). This work turns that manual
verification into a real, checked-in, executed test suite and additionally
proves the fix with a real automated concurrent test.

**Command (targeted):**
`mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.cart.**,com.junaldadlawan.event_ticketing_api.promocode.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result (targeted):** BUILD SUCCESS — 122 tests run, 0 failures, 0 errors.

**Command (full suite):** `mvnw.cmd test`
**Result (full suite):** BUILD SUCCESS — 405 tests run, 0 failures, 0 errors
(122 of which are new for this phase; the remaining 283 are the
pre-existing `event`/`organization`/`user`/`venue`/`tickettype`/`seatmap`/
smoke suites, run unchanged to confirm no regression — matches the 283
baseline recorded in `testing/ticket-type-seatmap-test-results.md`).

The two concurrency tests (see below) were additionally run **4 separate
times in isolation** (once as part of the full suite, three more standalone
runs) — all 4 runs passed with the same real DB row-lock behavior, no
flakiness observed.

## Test files added

- `src/test/java/.../cart/service/CartServiceImplTest.java` — **new**, 45
  tests (Mockito, no Spring context; mirrors `TicketTypeServiceImplTest`).
  `EntityManager.refresh()` is a mocked no-op here — there's no real
  persistence context to refresh against mocked repositories, so these
  tests instead mutate the same in-memory `TicketType`/`Seat` instance the
  mocked `findByIdForUpdate` returns, which is functionally what
  `refresh()` accomplishes against a real DB. This is why the pessimistic
  locking itself is **not** claimed as proven here — that's the integration
  test's job.
- `src/test/java/.../cart/controller/CartControllerTest.java` — **new**, 16
  tests (`@WebMvcTest` slice, security filters disabled, service mocked)
- `src/test/java/.../cart/CartAccessIntegrationTest.java` — **new**, 22
  tests (full `@SpringBootTest`, real security filter chain, real signed
  JWTs, real Postgres, real pessimistic row locks) — includes the two
  concurrency tests
- `src/test/java/.../promocode/service/PromoCodeServiceImplTest.java` —
  **new**, 18 tests (Mockito, no Spring context)
- `src/test/java/.../promocode/controller/EventPromoCodeControllerTest.java`
  — **new**, 11 tests (`@WebMvcTest` slice)
- `src/test/java/.../promocode/EventPromoCodeAccessIntegrationTest.java` —
  **new**, 10 tests (full `@SpringBootTest`, real security filter chain,
  real Postgres)

No production code was changed to make these tests pass — Phase 5a was
already implemented and reviewed (twice) before this testing pass; this
work is purely verification. One real concurrency bug was found and fixed
by the developer *before* this automated suite existed (see
`CartServiceImpl.addGaItem`'s inline comment); this suite's concurrency
tests confirm that fix holds.

## Scenario → test mapping

### `CartServiceImpl` (service layer, Mockito)

| Scenario | Test | Result |
|---|---|---|
| `create()` — cart owned by the caller | `.create_savesCartOwnedByCaller` | Pass |
| `get()` — unknown cart → 404; non-owner → 403 (no bypass) | `.get_unknownCart_throwsResourceNotFound`, `.get_nonOwner_throwsForbidden` | Pass |
| `get()` — releases the caller's own expired GA hold lazily on read | `.get_owner_succeedsAndReleasesExpiredHoldForGaTicketType` | Pass |
| `addItem()` — cart/ticket-type/event resolution: unknown cart/ticket type/vanished event → 404; non-owner → 403 | `.addItem_unknownCart_throwsResourceNotFound`, `.addItem_nonOwner_throwsForbidden`, `.addItem_unknownTicketType_throwsResourceNotFound`, `.addItem_ticketTypeEventVanished_throwsResourceNotFound` | Pass |
| `addItem()` — DRAFT/CANCELLED/COMPLETED event → 409 (allow-listed purchasable statuses) | `.addItem_draftEvent_throwsConflict`, `.addItem_cancelledEvent_throwsConflict`, `.addItem_completedEvent_throwsConflict` | Pass |
| `addItem()` — outside the ticket type's sale window (before/after) → 409 | `.addItem_beforeSaleWindow_throwsConflict`, `.addItem_afterSaleWindow_throwsConflict` | Pass |
| **`addItem()` — cart constrained to one event: adding from a different event → 400; same event → succeeds** | `.addItem_differentEventThanExistingCartItems_throwsBadRequest`, `.addItem_sameEventAsExistingCartItems_succeeds` | Pass |
| `addItem()` GA — insufficient quantity → 409; sufficient quantity decrements and creates a ~15-min hold (BR-INV-003) | `.addItem_ga_insufficientQuantity_throwsConflict`, `.addItem_ga_sufficientQuantity_decrementsAndCreatesHold` | Pass |
| `addItem()` GA — `quantity` omitted defaults to 1 | `.addItem_ga_quantityOmitted_defaultsToOne` | Pass |
| **`addItem()` GA — releases expired holds for the same ticket type before checking availability** | `.addItem_ga_releasesExpiredHoldsForSameTicketTypeBeforeChecking` | Pass |
| `addItem()` reserved-seating — missing `seatId` → 400; unknown seat → 404; vanished seat map → 404; seat belongs to a different event → 400 | `.addItem_reservedSeating_missingSeatId_throwsBadRequest`, `.addItem_reservedSeating_unknownSeat_throwsResourceNotFound`, `.addItem_reservedSeating_seatMapVanished_throwsResourceNotFound`, `.addItem_reservedSeating_seatBelongsToDifferentEvent_throwsBadRequest` | Pass |
| `addItem()` reserved-seating — seat not `AVAILABLE`, no stale hold → 409; `AVAILABLE` → flips to `HELD` | `.addItem_reservedSeating_seatNotAvailable_noStaleHold_throwsConflict`, `.addItem_reservedSeating_available_succeeds` | Pass |
| **`addItem()` reserved-seating — a stale hold on this specific seat is released, then the request succeeds** | `.addItem_reservedSeating_staleHoldOnThisSpecificSeat_releasedThenSucceeds` | Pass |
| `removeItem()` — unknown cart → 404; non-owner → 403; item belongs to a different cart → 404 | `.removeItem_unknownCart_throwsResourceNotFound`, `.removeItem_nonOwner_throwsForbidden`, `.removeItem_itemBelongsToDifferentCart_throwsResourceNotFound` | Pass |
| `removeItem()` — GA restores quantity and deletes the row; seat item releases the seat back to `AVAILABLE` | `.removeItem_gaItem_restoresQuantityAndDeletesRow`, `.removeItem_seatItem_releasesSeatBackToAvailable` | Pass |
| `applyPromoCode()` — unknown cart → 404; non-owner → 403; empty cart → 422 (BR-CART-001) | `.applyPromoCode_unknownCart_throwsResourceNotFound`, `.applyPromoCode_nonOwner_throwsForbidden`, `.applyPromoCode_emptyCart_throwsUnprocessableEntity` | Pass |
| **`applyPromoCode()` — a cart spanning more than one event's ticket types → 422 (code path is defensible even though `addItem` should prevent this from being reachable)** | `.applyPromoCode_cartSpansMultipleEvents_throwsUnprocessableEntity` | Pass |
| `applyPromoCode()` — unknown code, expired code, not-yet-valid code, inapplicable ticket type restriction → 422 (BR-PROMO-005/007) | `.applyPromoCode_unknownCode_throwsUnprocessableEntity`, `.applyPromoCode_expiredCode_throwsUnprocessableEntity`, `.applyPromoCode_notYetValidCode_throwsUnprocessableEntity`, `.applyPromoCode_inapplicableTicketType_throwsUnprocessableEntity` | Pass |
| **`applyPromoCode()` — empty `applicableTicketTypeIds` applies to all ticket types (percentage discount math correct)** | `.applyPromoCode_emptyApplicableSet_appliesToAllTicketTypes_percentageDiscount` | Pass |
| `applyPromoCode()` — fixed discount applies the flat amount | `.applyPromoCode_fixedDiscount_appliesFlatAmount` | Pass |
| **`applyPromoCode()` — a discount exceeding the subtotal is clamped to 0, never negative** | `.applyPromoCode_discountExceedsSubtotal_clampedToZero_neverNegative` | Pass |
| `removePromoCode()` — unknown cart → 404; non-owner → 403; clears `promoCodeId` and reverts total | `.removePromoCode_unknownCart_throwsResourceNotFound`, `.removePromoCode_nonOwner_throwsForbidden`, `.removePromoCode_clearsPromoCodeAndRevertsTotal` | Pass |

### `CartController` (slice)

| Scenario | Test | Result |
|---|---|---|
| `create()` → 201 | `.create_returns201` | Pass |
| `get()` → 200/403/404 mapping | `.get_existingCart_returns200`, `.get_notOwner_returns403`, `.get_unknownCart_returns404` | Pass |
| `addItem()` → 201; missing `ticketTypeId` → 400; non-positive `quantity` (`@Positive`) → 400; unknown ticket type → 404; no-longer-available → 409; missing `seatId` for reserved seating → 400 | `.addItem_validRequest_returns201`, `.addItem_missingTicketTypeId_returns400`, `.addItem_nonPositiveQuantity_returns400`, `.addItem_unknownTicketType_returns404`, `.addItem_noLongerAvailable_returns409`, `.addItem_seatIdRequiredForReservedSeating_returns400` | Pass |
| `removeItem()` → 204; unknown item → 404 | `.removeItem_returns204`, `.removeItem_unknownItem_returns404` | Pass |
| `applyPromoCode()` → 200; blank `code` (`@NotBlank`) → 400; inapplicable → 422 | `.applyPromoCode_validRequest_returns200`, `.applyPromoCode_blankCode_returns400`, `.applyPromoCode_inapplicable_returns422` | Pass |
| `removePromoCode()` → 200 | `.removePromoCode_returns200` | Pass |

### `CartAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, real row locks)

| Scenario | Test | Result |
|---|---|---|
| **Buyer-only access: owning buyer → 200; stranger → 403; ADMIN role → 403 too (no admin bypass, a genuine departure from Event/TicketType/Venue)** | `.getCart_buyerOnly_strangerAndAdminBothForbidden` | Pass |
| No token → 401 | `.getCart_noToken_returns401` | Pass |
| GA add → 201, `quantityAvailable` decremented in the DB | `.addItem_ga_success_decrementsAvailability` | Pass |
| Reserved-seating add → 201, seat flips to `HELD` in the DB | `.addItem_reservedSeating_success_seatHeld` | Pass |
| Reserved-seating, missing `seatId` → 400 | `.addItem_reservedSeating_missingSeatId_returns400` | Pass |
| Nonexistent ticket type → 404 | `.addItem_nonExistentTicketType_returns404` | Pass |
| DRAFT event → 409 | `.addItem_draftEvent_returns409` | Pass |
| Outside sale window → 409 | `.addItem_outsideSaleWindow_returns409` | Pass |
| Adding from a different event than what's already in the cart → 400 | `.addItem_differentEventThanExistingCartItems_returns400` | Pass |
| Remove GA item → hold released, `quantityAvailable` restored, row deleted | `.removeItem_ga_releasesHold_restoresAvailability` | Pass |
| Remove seat item → seat back to `AVAILABLE`, row deleted | `.removeItem_seat_releasesHold_seatBackToAvailable` | Pass |
| Remove item as a stranger → 403 | `.removeItem_stranger_returns403` | Pass |
| **Two buyers racing for the LAST unit of GA stock, fired concurrently via two threads — exactly one 201, one 409, final `quantityAvailable == 0`, exactly one `CartItem` row exists across both carts (BR-INV-005/006)** | `.concurrency_gaLastUnitOfStock_exactlyOneSucceeds` | Pass |
| **Two buyers racing for the SAME reserved seat, fired concurrently via two threads — exactly one 201, one 409, final seat `status == HELD`, exactly one `CartItem` row exists across both carts (BR-INV-005)** | `.concurrency_sameSeat_exactlyOneSucceeds` | Pass |
| Expired GA hold (stale `CartItem`, `holdExpiresAt` in the past) is released and its unit reused by a different buyer's `addItem` | `.expiredHoldReclaim_ga_releasedAndReusedByDifferentBuyer` | Pass |
| Expired seat hold (seat still `HELD` in the DB, but the hold row is stale) is released and the seat reused by a different buyer's `addItem` | `.expiredHoldReclaim_seat_releasedAndReusedByDifferentBuyer` | Pass |
| Apply percentage promo code end to end (discount amount, total in the response) → 200; `removePromoCode` reverts the total | `.applyPromoCode_percentageDiscount_endToEnd` | Pass |
| Apply to an empty cart → 422 | `.applyPromoCode_emptyCart_returns422` | Pass |
| Apply an expired code → 422; a not-yet-valid code → 422 | `.applyPromoCode_expiredCode_returns422`, `.applyPromoCode_notYetValidCode_returns422` | Pass |
| Apply a code restricted to a different ticket type than what's in the cart → 422 | `.applyPromoCode_inapplicableTicketType_returns422` | Pass |
| Promo-code endpoints (apply/remove) — stranger → 403 | `.promoCodeEndpoints_stranger_returns403` | Pass |

### `PromoCodeServiceImpl` (service layer, Mockito)

| Scenario | Test | Result |
|---|---|---|
| Owner/organizer of the event's org can create | `.create_owner_succeeds`, `.create_organizer_succeeds` | Pass |
| **Admin bypass (BR-AUTH-004) — org-role check never even touched** | `.create_adminWithNoOrgRole_bypassesOrgRoleCheck` | Pass |
| Nonexistent event → 404, before touching the access guard | `.create_nonExistentEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403; **owner/organizer of a *different* org → 403** | `.create_stranger_throwsForbidden`, `.create_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| `validUntil` equal to or before `validFrom` → 400 | `.create_validUntilEqualsValidFrom_throwsBadRequest`, `.create_validUntilBeforeValidFrom_throwsBadRequest` | Pass |
| `discountValue > 100` for `PERCENTAGE` → 400 | `.create_percentageDiscountValueOver100_throwsBadRequest` | Pass |
| **Boundary: `discountValue == 100` for `PERCENTAGE` is ACCEPTED, not rejected** | `.create_percentageDiscountValueExactly100_isAccepted` | Pass |
| No upper bound for `FIXED` — a value over 100 is accepted | `.create_fixedDiscountValueOver100_isAccepted_noUpperBoundForFixed` | Pass |
| Duplicate `(eventId, code)` → 409 | `.create_duplicateCodeForSameEvent_throwsConflict` | Pass |
| `applicableTicketTypeIds` omitted → defaults to an empty set | `.create_applicableTicketTypeIdsOmitted_defaultsToEmptySet` | Pass |
| Owner/admin can list | `.list_owner_succeeds`, `.list_admin_succeeds` | Pass |
| **Stranger on a PUBLISHED event → 403 (key regression vs. TicketType's draft-based public visibility — listing is owner/organizer/admin-only, always)** | `.list_strangerOnPublishedEvent_throwsForbidden` | Pass |
| Owner of a different org → 403; unknown event → 404 | `.list_ownerOfDifferentOrganization_throwsForbidden`, `.list_unknownEvent_throwsResourceNotFound` | Pass |

### `EventPromoCodeController` (slice)

| Scenario | Test | Result |
|---|---|---|
| Create → 201; missing required field / blank code / negative `discountValue` → 400 | `.create_validRequest_returns201`, `.create_missingRequiredField_returns400`, `.create_blankCode_returns400`, `.create_negativeDiscountValue_returns400` | Pass |
| `validUntil` before `validFrom` → 400; stranger → 403; nonexistent event → 404; duplicate → 409 | `.create_validUntilBeforeValidFrom_returns400`, `.create_stranger_returns403`, `.create_nonExistentEvent_returns404`, `.create_duplicateCode_returns409` | Pass |
| List → 200; unauthorized → 403; unknown event → 404 | `.list_existingEvent_returns200`, `.list_unauthorizedCaller_returns403`, `.list_unknownEvent_returns404` | Pass |

### `EventPromoCodeAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres)

| Scenario | Test | Result |
|---|---|---|
| Owner, organizer, and admin (no org role — BR-AUTH-004) can all create; stranger and cross-org owner → 403 | `.create_ownerOrganizerAdmin_allSucceed_strangerAndCrossOrgOwnerForbidden` | Pass |
| Nonexistent event → 404; no token → 401 | `.create_nonExistentEvent_returns404`, `.create_noToken_returns401` | Pass |
| Duplicate `(eventId, code)` → 409 | `.create_duplicateCodeForSameEvent_returns409` | Pass |
| `validUntil` before, or equal to, `validFrom` → 400 | `.create_validUntilBeforeValidFrom_returns400`, `.create_validUntilEqualsValidFrom_returns400` | Pass |
| **`discountValue` boundary over real HTTP: 100 accepted (round-trips exactly, not clamped), 101 rejected** | `.create_percentageDiscountValueBoundary_100Accepted_101Rejected` | Pass |
| Owner/organizer/admin can list on a PUBLISHED event | `.list_ownerOrganizerAdmin_succeedEvenOnPublishedEvent` | Pass |
| **Stranger, cross-org owner, AND anonymous (no token) → 403/401 even on a PUBLISHED event — the key regression vs. TicketType's public-once-published pattern** | `.list_strangerOnPublishedEvent_returns403` | Pass |
| Unknown event → 404 | `.list_unknownEvent_returns404` | Pass |

## Findings surfaced by writing these tests

No new bugs were found while writing this suite — consistent with
`code-reviewer`'s two clean sign-offs. Points worth recording precisely:

1. **A real concurrency bug was already found and fixed by the developer
   before this automated suite existed**, and this suite is the first
   automated proof that the fix holds.
   `CartServiceImpl.addGaItem`'s inline comment documents it directly: the
   method loaded the `TicketType` once (unlocked) in `addItem()` to check
   event status/sale window, then called
   `ticketTypeRepository.findByIdForUpdate()` — which *does* issue a real
   locking `SELECT ... FOR UPDATE` and *does* block on the DB row lock, but
   because Hibernate's persistence context is an identity map, it returned
   the *same already-managed, now-stale* Java instance without refreshing
   its fields. Two concurrent requests for the last unit of GA stock could
   both observe the pre-lock `quantityAvailable` and both succeed. The fix
   is `entityManager.refresh(ticketType)` immediately after acquiring the
   lock. `CartAccessIntegrationTest.concurrency_gaLastUnitOfStock_exactlyOneSucceeds`
   fires two real concurrent HTTP requests (via a fixed thread pool + a
   `CountDownLatch` releasing both simultaneously) at a ticket type with
   exactly 1 unit available, and asserts exactly one `201`/one `409`, a
   final `quantityAvailable == 0` (never negative, never double-decremented),
   and exactly one `CartItem` row created across the two buyers' carts.
   This was run 4 times total across this session (full suite + 3 isolated
   re-runs) with identical results every time — no flakiness.
2. **The same class of proof for reserved seating** —
   `.concurrency_sameSeat_exactlyOneSucceeds` fires two concurrent requests
   for the identical `seatId` and confirms exactly one `201`/one `409`,
   final seat `status == HELD`, and exactly one `CartItem` referencing that
   seat exists. `SeatRepository.findByIdForUpdate` doesn't have the same
   stale-instance risk as the GA path did, because `addReservedSeatItem`
   only ever loads the `Seat` once (via the locked finder) — there's no
   earlier unlocked load of the same entity in the same transaction to go
   stale.
3. **Buyer-only cart access genuinely has no admin/organizer bypass** —
   confirmed at both the unit level (`CartServiceImplTest` never stubs an
   `isAdmin()`/role check at all inside `requireOwnerBuyer`, because the
   production code doesn't call it) and, more convincingly, at the HTTP
   level: `CartAccessIntegrationTest.getCart_buyerOnly_strangerAndAdminBothForbidden`
   signs a real ADMIN-role JWT and confirms it still gets `403` on someone
   else's cart — a real behavioral difference from every other module
   tested so far in this codebase (Event/TicketType/Venue all have an
   admin bypass).
4. **Promo-code listing visibility is a genuine, deliberate regression
   class vs. TicketType's draft-based public switch** — confirmed at the
   service unit level (`list_strangerOnPublishedEvent_throwsForbidden`)
   and again end-to-end over real HTTP against a fully `PUBLISHED` event
   (`EventPromoCodeAccessIntegrationTest.list_strangerOnPublishedEvent_returns403`,
   which additionally confirms an anonymous, tokenless request gets `401`
   rather than the `200` a public TicketType list would return).
5. **The `discountValue <= 100` PERCENTAGE boundary is exact, not
   off-by-one** — both the service-unit test and the full-HTTP integration
   test independently confirm `100` is accepted and round-trips as exactly
   `100` (not silently clamped to some other value), while `101` is
   rejected with `400`.
6. **The discount-clamping defense-in-depth in `computeDiscount` is real
   and exercised**, not just a comment: `CartServiceImplTest.applyPromoCode_discountExceedsSubtotal_clampedToZero_neverNegative`
   configures a `FIXED` `discountValue` far larger than the cart subtotal
   and confirms the returned discount amount is clamped to exactly the
   subtotal (not the raw configured value), and the total is `0`, never
   negative.
7. **No test-writing workaround was needed for a missing feature** except
   the two explicitly out-of-scope items already flagged in
   `testing/cart-test-plan.md`/`testing/promocode-test-plan.md`: (a) "a
   user has at most one active cart at a time" is **not implemented** —
   `POST /carts` unconditionally creates a new cart with no check for an
   existing one, so no test was written to assert that constraint (it
   isn't in openapi.yaml either); this is noted in the test plan, not
   silently dropped. (b) Promo-code usage-limit enforcement
   (BR-PROMO-002/006) is explicitly stubbed as always-passing in
   `PromoCodeServiceImpl.applyPromoCode` pending the Order module — the
   `TODO` comment in the production code says so directly — so those two
   scenarios are left unchecked in `promocode-test-plan.md` rather than
   faked with a stub assertion that always trivially passes.

## Full run output (tail, full suite)

```
[INFO] Running com.junaldadlawan.event_ticketing_api.tickettype.TicketTypeAccessIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.561 s -- in com.junaldadlawan.event_ticketing_api.tickettype.TicketTypeAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.322 s -- in com.junaldadlawan.event_ticketing_api.user.controller.UserControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.148 s -- in com.junaldadlawan.event_ticketing_api.user.service.UserServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.283 s -- in com.junaldadlawan.event_ticketing_api.user.UserSecurityIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.338 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.OrganizationVenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.210 s -- in com.junaldadlawan.event_ticketing_api.venue.controller.VenueControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.junaldadlawan.event_ticketing_api.venue.service.VenueServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.291 s -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 405, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  20.564 s
[INFO] Finished at: 2026-09-10T19:09:10+08:00
[INFO] ------------------------------------------------------------------------
```

New-module lines from the same run, for reference:

```
[INFO] Running com.junaldadlawan.event_ticketing_api.cart.CartAccessIntegrationTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.338 s -- in com.junaldadlawan.event_ticketing_api.cart.CartAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.cart.controller.CartControllerTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.963 s -- in com.junaldadlawan.event_ticketing_api.cart.controller.CartControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.cart.service.CartServiceImplTest
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.825 s -- in com.junaldadlawan.event_ticketing_api.cart.service.CartServiceImplTest
[INFO] Running com.junaldadlawan.event_ticketing_api.promocode.controller.EventPromoCodeControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.370 s -- in com.junaldadlawan.event_ticketing_api.promocode.controller.EventPromoCodeControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.promocode.EventPromoCodeAccessIntegrationTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.645 s -- in com.junaldadlawan.event_ticketing_api.promocode.EventPromoCodeAccessIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeServiceImplTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in com.junaldadlawan.event_ticketing_api.promocode.service.PromoCodeServiceImplTest
```

## Concurrency-test isolated re-runs (real output, proving no flakiness)

`CartAccessIntegrationTest` run standalone 3 additional times (beyond the
one full-suite run above), each a fresh JVM/Spring context against the same
real Postgres:

```
=== RUN 1 ===
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.299 s -- in com.junaldadlawan.event_ticketing_api.cart.CartAccessIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
=== RUN 2 ===
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.156 s -- in com.junaldadlawan.event_ticketing_api.cart.CartAccessIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
=== RUN 3 ===
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.283 s -- in com.junaldadlawan.event_ticketing_api.cart.CartAccessIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

(Each `RUN` invoked exactly
`concurrency_gaLastUnitOfStock_exactlyOneSucceeds` and
`concurrency_sameSeat_exactlyOneSucceeds` via
`mvnw.cmd -Dtest=...CartAccessIntegrationTest#concurrency_gaLastUnitOfStock_exactlyOneSucceeds+concurrency_sameSeat_exactlyOneSucceeds test`.)

## Database cleanup verification

All three new integration test classes (`CartAccessIntegrationTest`,
`EventPromoCodeAccessIntegrationTest`, plus the pre-existing suites) hit the
real local Postgres (`compose.yml`'s `postgresql` container, db
`event_ticketing`) and delete every row they create in `@AfterEach`.
Verified after the full test run (`mvnw.cmd test`, 405 tests) that no rows
created by *this session's* tests remain:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM organizations) AS orgs,
          (SELECT count(*) FROM organization_members) AS members,
          (SELECT count(*) FROM organization_member_roles) AS roles,
          (SELECT count(*) FROM venues) AS venues,
          (SELECT count(*) FROM events) AS events,
          (SELECT count(*) FROM ticket_types) AS ticket_types,
          (SELECT count(*) FROM seat_maps) AS seat_maps,
          (SELECT count(*) FROM seats) AS seats,
          (SELECT count(*) FROM carts) AS carts,
          (SELECT count(*) FROM cart_items) AS cart_items,
          (SELECT count(*) FROM promo_codes) AS promo_codes,
          (SELECT count(*) FROM promo_code_applicable_ticket_types) AS promo_applicable;"

 orgs | members | roles | venues | events | ticket_types | seat_maps | seats | carts | cart_items | promo_codes | promo_applicable
------+---------+-------+--------+--------+--------------+-----------+-------+-------+------------+-------------+------------------
    1 |       1 |     2 |      0 |      4 |            3 |         2 |     2 |     1 |          0 |           2 |                1
```

`cart_items` is `0` — fully clean. The remaining non-zero rows are **not**
leftovers from this test suite: they're pre-existing manual/curl
verification data left in the shared dev DB from before this session
(matching the task description — "only manual curl verification" existed
prior to this pass), confirmed by name:

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT count(*) FROM organizations WHERE name LIKE 'Cart Access Test%' OR name LIKE 'Promo Code Access Test%';"
 count
-------
     0

$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT count(*) FROM events WHERE title LIKE 'Cart Access Test%' OR title LIKE 'Promo Code Access Test%';"
 count
-------
     0
```

Zero rows anywhere in the DB carry this test suite's naming conventions
(`Cart Access Test Org`/`Event`, `Promo Code Access Test Org`/`Event`) — the
surviving rows are named `Phase5a Test Org`, `Phase5a Test Concert`,
`GA Single`/`GA Standard`/`Reserved A`, `SAVE20`/`EXPIRED10`, and the
pre-existing `Java Conference 2026`/`Test Concert`/`Updated Title` events
already documented as a pre-session baseline in
`testing/ticket-type-seatmap-test-results.md` (events=3, roles=1 there vs.
events=4, roles=2 here — the +1 delta on each is exactly the manually
created `Phase5a Test Org`/`Phase5a Test Concert` pair, not a leak from this
suite).
