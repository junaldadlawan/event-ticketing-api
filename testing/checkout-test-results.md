# Checkout / Order / Payment (Phase 5b) — Proof of Testing

**Date:** 2026-09-10
**Branch:** `feat/phase5b-checkout`
**Based on:** `openapi.yaml`'s `Order`/`Payment`/`Money` schemas and the
`POST /carts/{cartId}/checkout` path (required `Idempotency-Key` UUID
header, `201`/`402`/`410` responses, ~975-1013); `BR-CART-001–004`,
`BR-PAY-001`, `BR-PROMO-002`/`006`, `BR-NFR-008` in
`docs/event-ticketing-api-business-rules.md`; `UC-ATTND-04` in
`docs/event-ticketing-api-use-cases.md`; the `order/` module source
(`Order`/`Payment`/`CheckoutIdempotencyKey` entities, `OrderStatus`/
`PayeeType`/`PaymentStatus` enums, `OrderRepository`/`PaymentRepository`/
`CheckoutIdempotencyKeyRepository`, `MockPaymentGatewayClient`,
`CheckoutServiceImpl`/`CheckoutIdempotencyKeyManager`, `OrderResponse`/
`CheckoutRequest` DTOs, `CartCheckoutController`);
`promocode/service/PromoCodeUsageLimitGuard.java`; the checkout-supporting
parts of `cart/service/CartServiceImpl.java` (`releaseExpiredHoldsForCart`,
`CartService.get()`'s total/discount computation).

`developer` implemented Phase 5b and `code-reviewer` reviewed it **twice**,
with 2 CRITICAL + 1 HIGH findings found and fixed, independently confirmed
with real evidence against Postgres (see the inline comments in
`CheckoutServiceImpl`/`CheckoutIdempotencyKey` referencing each finding).
Before this testing pass, only 3 targeted regression tests existed
(`CheckoutIntegrationTest`, proving those three specific findings) — no
broader coverage. This pass (continuing a prior session that was cut off by
a rate limit after 12 tests, mid-way through starting
`CartCheckoutControllerTest`) found `CartCheckoutControllerTest` (13 tests)
and `CheckoutServiceImplTest` (12 tests) already complete and passing on
resumption, and added the remaining planned integration coverage: 6 new
`CheckoutIntegrationTest` methods (empty-cart 409, expired-hold 410 +
side-effect, payment-failure-then-retry, idempotent replay, cross-buyer
key reuse, and a capstone GA+reserved-seating+promo end-to-end checkout),
bringing `CheckoutIntegrationTest` from 3 to 9 tests.

**Command (targeted, order package only):**
`mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.order.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result (targeted):** BUILD SUCCESS — 34 tests run, 0 failures, 0 errors.

```
[INFO] Running com.junaldadlawan.event_ticketing_api.order.CheckoutIntegrationTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.70 s -- in com.junaldadlawan.event_ticketing_api.order.CheckoutIntegrationTest
[INFO] Running com.junaldadlawan.event_ticketing_api.order.controller.CartCheckoutControllerTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.905 s -- in com.junaldadlawan.event_ticketing_api.order.controller.CartCheckoutControllerTest
[INFO] Running com.junaldadlawan.event_ticketing_api.order.service.CheckoutServiceImplTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.092 s -- in com.junaldadlawan.event_ticketing_api.order.service.CheckoutServiceImplTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

**Command (full suite):** `mvnw.cmd test`
**Result (full suite):** BUILD SUCCESS — 445 tests run, 0 failures, 0 errors
(34 of which are the `order` package documented here; the remaining 411 are
the pre-existing `cart`/`promocode`/`event`/`organization`/`user`/`venue`/
`tickettype`/`seatmap`/smoke suites, run unchanged to confirm no
regression).

```
[INFO] Tests run: 445, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  32.341 s
[INFO] Finished at: 2026-09-10T23:29:02+08:00
[INFO] ------------------------------------------------------------------------
```

## Test files added/changed

- `src/test/java/.../order/service/CheckoutServiceImplTest.java` — already
  complete on resumption (12 tests, Mockito, no Spring context; mirrors
  `CartServiceImplTest`'s style). Not modified this pass.
- `src/test/java/.../order/controller/CartCheckoutControllerTest.java` —
  already complete on resumption (13 tests, `@WebMvcTest` slice, security
  filters mocked out, service mocked). Not modified this pass.
- `src/test/java/.../order/CheckoutIntegrationTest.java` — **extended**
  this pass from 3 to 9 tests (full `@SpringBootTest`, real security filter
  chain, real signed JWTs via `JwtService`, real Postgres, real pessimistic
  row locks). Added: `SeatMapRepository`/`SeatRepository` autowiring and
  `persistReservedTicketType`/`persistSeatMap`/`persistAvailableSeat`/
  `addReservedSeatItem`/`checkout()` fixtures, a parametrized
  `persistPromoCode(eventId, code, usageLimitTotal, discountType,
  discountValue)` overload (the original hardcoded `"LIMIT1"`/`FIXED`/`100`
  overload is preserved and now delegates to it), and 6 new `@Test`
  methods (see mapping below).

## Scenario → test mapping

### `CheckoutServiceImplTest` (service layer, Mockito — pre-existing, verified passing)

| Scenario | Test | Result |
|---|---|---|
| Unknown cart → 404, before ever touching the idempotency-key manager | `.checkout_unknownCart_throwsResourceNotFound` | Pass |
| Non-owner (not the cart's buyer) → 403 | `.checkout_nonOwner_throwsForbidden` | Pass |
| Idempotency key already claimed by a **different** buyer → 403, key not freed (never ours to free) | `.checkout_crossBuyerIdempotencyKey_throwsForbidden_andDoesNotFreeKey` | Pass |
| Replay with the same key after completion → returns the same `Order`, no gateway call, no second insert, key not touched again | `.checkout_replayWithSameKey_returnsSameOrder_noRecharge` | Pass |
| Key claimed but `orderId` still null (genuinely in-flight concurrent request) → 409 | `.checkout_inFlightDuplicateKey_throwsConflict` | Pass |
| Empty cart → 409, key freed for retry | `.checkout_emptyCart_throwsConflict_andFreesKey` | Pass |
| Expired hold → 410 (Gone), `releaseExpiredHoldsForCart` invoked as a side effect, key freed, gateway never called | `.checkout_expiredHold_throwsGone_releasesHolds_andFreesKey` | Pass |
| Promo usage limit exhausted since `applyPromoCode` time → 422, rejected **before** charging (gateway/Order never touched), key freed | `.checkout_promoUsageLimitExhaustedSinceApply_throwsUnprocessableEntity_beforeCharging_andFreesKey` | Pass |
| Payment declined (`tok_fail`) → 402, no `Order`/`Payment` persisted, no `CartItem` deleted, key freed | `.checkout_paymentDeclined_throwsPaymentFailed_noOrderOrPaymentPersisted_andFreesKey` | Pass |
| Successful GA checkout — `Order`/`Payment` created correctly (buyer, cartId, promoCode, total), `CartItem` deleted, cart's `promoCodeId` cleared, idempotency key stamped with the new `orderId` (not freed) | `.checkout_success_ga_createsOrderAndPayment_deletesCartItem_clearsPromoCode_doesNotFreeKey` | Pass |
| Successful reserved-seating checkout — `Seat.status` flips `HELD` → `SOLD` | `.checkout_success_reservedSeating_flipsSeatFromHeldToSold` | Pass |
| No applied promo code → the checkout-time usage-limit guard is never invoked at all | `.checkout_success_noAppliedPromoCode_promoUsageGuardNeverInvoked` | Pass |

### `CartCheckoutControllerTest` (`@WebMvcTest` slice — pre-existing, verified passing)

| Scenario | Test | Result |
|---|---|---|
| Missing `Idempotency-Key` header → 400 | `.checkout_missingIdempotencyKeyHeader_returns400` | Pass |
| Malformed (non-UUID) `Idempotency-Key` → 400 | `.checkout_malformedIdempotencyKeyHeader_returns400` | Pass |
| Blank `paymentMethodToken` → 400 | `.checkout_blankPaymentMethodToken_returns400` | Pass |
| Missing `paymentMethodToken` field entirely → 400 | `.checkout_missingPaymentMethodToken_returns400` | Pass |
| Valid request → 201, response shape matches `OrderResponse` (`status`, `payeeType`, empty `tickets` array) | `.checkout_validRequest_returns201WithOrderBody` | Pass |
| Non-owner → 403 | `.checkout_nonOwner_returns403` | Pass |
| Unknown cart → 404 | `.checkout_unknownCart_returns404` | Pass |
| Empty cart → 409 | `.checkout_emptyCart_returns409` | Pass |
| Expired hold → 410 | `.checkout_expiredHold_returns410` | Pass |
| Payment declined → 402 | `.checkout_paymentDeclined_returns402` | Pass |
| Promo usage limit exhausted → 422 | `.checkout_promoUsageLimitExhausted_returns422` | Pass |
| In-flight duplicate idempotency key → 409 | `.checkout_inFlightDuplicateIdempotencyKey_returns409` | Pass |
| Cross-buyer idempotency-key reuse → 403 | `.checkout_crossBuyerIdempotencyKeyReuse_returns403` | Pass |

### `CheckoutIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, real row locks)

| Scenario | Test | Result |
|---|---|---|
| **CRITICAL 1 regression — two different idempotency keys on the same cart, fired concurrently: exactly one 201, exactly one `Order`, exactly one `Payment`** | `.concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment` | Pass (pre-existing) |
| **CRITICAL 2 regression — `CheckoutIdempotencyKey` (`Persistable<UUID>`) routes a fresh-instance duplicate id through `persist()`, throwing a real constraint violation instead of silently merging over the existing row** | `.checkoutIdempotencyKey_duplicateIdOnFreshInstance_throwsConstraintViolationInsteadOfSilentlyMerging` | Pass (pre-existing) |
| **HIGH regression — promo code usage limit re-checked at checkout time (BR-PROMO-006), not just at `applyPromoCode` time; rejected buyer's cart/items/hold remain intact and the idempotency key is freed** | `.checkout_promoCodeUsageLimitExhaustedByAnotherBuyerSinceApply_returns422` | Pass (pre-existing) |
| Empty cart → 409, no `Order` created, idempotency key freed for retry | `.checkout_emptyCart_returns409_andNoOrderCreated` | Pass (new) |
| Expired hold → 410, no `Order`, key freed, **and the expired hold is actually released as a side effect** (`CartItem` deleted, `TicketType.quantityAvailable` restored) even though the request itself fails | `.checkout_expiredHold_returns410_andReleasesHoldEvenThoughRequestFails` | Pass (new) |
| Payment failure (`tok_fail`) → 402, cart/holds fully intact, key freed; retrying immediately with a **new** idempotency key and `tok_ok` succeeds (201, `Order` created, cart emptied) | `.checkout_paymentFailure_thenRetryWithNewIdempotencyKey_succeeds` | Pass (new) |
| Replaying the **same** idempotency key after a successful checkout returns the identical `Order` id, with still exactly one `Order` row for the cart and exactly one `Payment` row (no re-charge) | `.checkout_replaySameIdempotencyKeyAfterSuccess_returnsSameOrder_noRecharge` | Pass (new) |
| A second buyer reusing buyer A's already-completed idempotency key against their own cart → 403, buyer B's cart/hold left untouched | `.checkout_crossBuyerIdempotencyKeyReuse_returns403` | Pass (new) |
| **Capstone** — one cart with both a GA item and a reserved-seating item plus an applied promo code, checked out in a single request: 201; `Order.total` reflects the promo discount (3000 subtotal − 300 fixed discount = 2700); `Order.payeeId` == the event's `organizationId`; exactly one `Payment` (`COMPLETED`, same amount); GA `TicketType.quantityAvailable` stays at its hold-time-decremented value (untouched further by checkout); `Seat.status` flips `HELD` → `SOLD`; both `CartItem`s deleted; `cart.promoCodeId` cleared — proving Phase 5a (cart/hold/promo) and Phase 5b (checkout) compose correctly together | `.checkout_success_gaAndReservedSeatingItemsWithPromoCode_composesPhase5aAndPhase5bCorrectly` | Pass (new) |

## Findings surfaced by writing these tests

No new production bugs were found while extending `CheckoutIntegrationTest`
— consistent with `code-reviewer`'s two clean sign-offs on Phase 5b. All
settled/documented behavior (idempotency-key claim/replay/conflict/
cross-buyer branching, empty-cart 409, expired-hold 410 + release side
effect, payment-failure-then-retry, GA+reserved-seating+promo composition)
matched the production code exactly on the first passing run — no assertion
had to be weakened to match unexpected real behavior.

One pre-existing, out-of-scope observation carried over from the DB
cleanup check (not a Phase 5b issue, not touched by this pass): the shared
dev Postgres database has a small number of leftover rows
(`organizations`=1, `events`=4, `ticket_types`=3, `promo_codes`=2,
`seat_maps`=2, `seats`=2) that predate this session — same counts measured
before and after this pass's 34-test `order` run and the full 445-test
suite run, and named `Phase5a Test Org`/`Phase5a Test Concert`/`GA Single`/
`GA Standard`/`Reserved A`/`SAVE20`/`EXPIRED10`/`Java Conference 2026`/
`Test Concert`/`Updated Title` — none of which match any naming used by
this pass's tests (`Checkout Test Org`, `Checkout Test Event`, `LIMIT1`,
`CAPSTONE10`), and already flagged as pre-existing residue in
`testing/cart-promocode-test-results.md`. All tables this pass's tests
actually write to (`orders`, `payments`, `checkout_idempotency_keys`,
`cart_items`, `carts`) are verified at `0` rows after every run in this
pass — full cleanup confirmed.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'orders' t, count(*) from orders
   union all select 'payments', count(*) from payments
   union all select 'checkout_idempotency_keys', count(*) from checkout_idempotency_keys
   union all select 'cart_items', count(*) from cart_items
   union all select 'carts', count(*) from carts;"

             t             | count
---------------------------+-------
 orders                    |     0
 payments                  |     0
 checkout_idempotency_keys |     0
 cart_items                |     0
 carts                     |     0
```

Confirmed identical (all `0`) both immediately after the targeted 34-test
`order` package run and after the full 445-test suite run — no leftover
rows from any test in this pass.
