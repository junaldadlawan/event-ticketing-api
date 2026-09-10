# Checkout Test Plan

**Status:** Implemented

Covers applying promo codes (Phase 5a, see `cart-promocode-test-plan.md`
equivalent — `testing/cart-promocode-test-results.md`) and completing a
purchase via `POST /carts/{cartId}/checkout` (Phase 5b). See
`testing/checkout-test-results.md` for the full scenario → test mapping and
actual run results.

## Test Scenarios

- [x] Applying a valid promo code reduces the cart total (covered in
      `testing/cart-promocode-test-results.md`, Phase 5a's `CartServiceImpl`/
      `CartAccessIntegrationTest` suites)
- [x] Applying an expired promo code fails with a clear error (ditto)
- [x] Applying an exhausted (usage-limit-reached) promo code fails — both
      at apply-time (ditto) and re-checked at checkout-time (BR-PROMO-006):
      `CheckoutIntegrationTest.checkout_promoCodeUsageLimitExhaustedByAnotherBuyerSinceApply_returns422`
- [x] Applying a promo code to an inapplicable ticket type fails (covered in
      `testing/cart-promocode-test-results.md`)
- [x] Checkout with a valid payment succeeds — `Order`/`Payment` created,
      GA quantity/reserved-seat holds finalized, cart cleared. Ticket
      issuance itself is explicitly deferred to Phase 6 (`Order.tickets` is
      documented as always empty for now, not a bug — see
      `OrderResponse`'s Javadoc):
      `CheckoutServiceImplTest.checkout_success_ga_createsOrderAndPayment_deletesCartItem_clearsPromoCode_doesNotFreeKey`,
      `.checkout_success_reservedSeating_flipsSeatFromHeldToSold`,
      `CheckoutIntegrationTest.checkout_success_gaAndReservedSeatingItemsWithPromoCode_composesPhase5aAndPhase5bCorrectly`
- [x] Checkout with a failed payment leaves the cart and holds intact (402):
      `CheckoutServiceImplTest.checkout_paymentDeclined_throwsPaymentFailed_noOrderOrPaymentPersisted_andFreesKey`,
      `CheckoutIntegrationTest.checkout_paymentFailure_thenRetryWithNewIdempotencyKey_succeeds`
- [x] Checkout after a hold expired fails (410) and releases the expired
      hold as a side effect:
      `CheckoutServiceImplTest.checkout_expiredHold_throwsGone_releasesHolds_andFreesKey`,
      `CheckoutIntegrationTest.checkout_expiredHold_returns410_andReleasesHoldEvenThoughRequestFails`
- [x] Replaying the same Idempotency-Key does not double-charge or
      double-issue tickets:
      `CheckoutServiceImplTest.checkout_replayWithSameKey_returnsSameOrder_noRecharge`,
      `CheckoutIntegrationTest.checkout_replaySameIdempotencyKeyAfterSuccess_returnsSameOrder_noRecharge`,
      `.concurrency_sameCartDifferentIdempotencyKeys_exactlyOneOrderAndOnePayment`
- [x] Cross-buyer reuse of an idempotency key is rejected (403):
      `CheckoutIntegrationTest.checkout_crossBuyerIdempotencyKeyReuse_returns403`
- [x] Empty cart cannot be checked out (409):
      `CheckoutIntegrationTest.checkout_emptyCart_returns409_andNoOrderCreated`
