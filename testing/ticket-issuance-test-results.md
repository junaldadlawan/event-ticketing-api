# Ticket Issuance & Retrieval (Phase 6a) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/phase6a-tickets`
**Based on:** `openapi.yaml`'s `Ticket`/`Order` schemas (`Ticket` has no
`credential` field, ~2131-2145) and `GET /orders/{orderId}`, `GET
/orders/{orderId}/tickets`, `GET /tickets/{ticketId}`, `GET
/users/me/orders`, `GET /events/{eventId}/orders`;
`BR-TICKET-001`–`006`, `BR-CART-004` in
`docs/event-ticketing-api-business-rules.md`; `UC-ATTND-04`/`05` in
`docs/event-ticketing-api-use-cases.md`; the new `ticket/` module
(`Ticket` entity, `TicketStatus`, `TicketRepository`,
`TicketCredentialService`, `TicketService`/`Impl`, `TicketResponse`,
`TicketController`), new `order/service/OrderService.java`/
`OrderServiceImpl.java`, new `order/controller/OrderController.java`,
migration `V12__add_tickets_table.sql`, and the `CheckoutServiceImpl`/
`OrderResponse` retrofit that issues and returns tickets at checkout.

`developer` implemented Phase 6a and `code-reviewer` reviewed it with **no
CRITICAL/HIGH findings** and one MEDIUM (ticket-number/credential
distinctness under `quantity>1` GA not proven against real Postgres). Before
this pass, the only ticket-issuance-adjacent coverage was inside
`CheckoutServiceImplTest`/`CheckoutIntegrationTest` (already updated by the
prior session to assert 1-ticket-per-admission-unit and credential absence)
— there was **zero** dedicated coverage for the `ticket/` module itself or
the new `order/service/OrderServiceImpl`/`order/controller/OrderController`
retrieval endpoints. This pass added that dedicated coverage and closed the
MEDIUM finding.

**Command (targeted, ticket + order packages only):**
`mvnw.cmd -Dtest=com.junaldadlawan.event_ticketing_api.ticket.**,com.junaldadlawan.event_ticketing_api.order.** test`
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)
**Result (targeted):** BUILD SUCCESS — 101 tests run, 0 failures, 0 errors.

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.53 s -- in com.junaldadlawan.event_ticketing_api.order.CheckoutIntegrationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.896 s -- in com.junaldadlawan.event_ticketing_api.order.controller.CartCheckoutControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.443 s -- in com.junaldadlawan.event_ticketing_api.order.controller.OrderControllerTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.679 s -- in com.junaldadlawan.event_ticketing_api.order.OrderAccessIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.612 s -- in com.junaldadlawan.event_ticketing_api.order.service.CheckoutServiceImplTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.123 s -- in com.junaldadlawan.event_ticketing_api.order.service.OrderServiceImplTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.685 s -- in com.junaldadlawan.event_ticketing_api.ticket.controller.TicketControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s -- in com.junaldadlawan.event_ticketing_api.ticket.service.TicketCredentialServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.070 s -- in com.junaldadlawan.event_ticketing_api.ticket.service.TicketServiceImplTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.326 s -- in com.junaldadlawan.event_ticketing_api.ticket.TicketAccessIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

**Command (full suite):** `mvnw.cmd test`
**Result (full suite):** BUILD SUCCESS — 512 tests run, 0 failures, 0 errors
(67 of which are new/changed this pass — the remaining 445 are the
pre-existing suite, including the Phase 5b `order` tests, run unchanged to
confirm no regression).

```
[INFO] Tests run: 512, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

## Test files added/changed

- `src/test/java/.../ticket/service/TicketServiceImplTest.java` — **new**,
  8 tests (Mockito, no Spring context). `TicketServiceImpl.getTicket`'s
  BR-CART-004-equivalent visibility.
- `src/test/java/.../ticket/controller/TicketControllerTest.java` — **new**,
  3 tests (`@WebMvcTest` slice, security filters mocked, service mocked).
- `src/test/java/.../ticket/service/TicketCredentialServiceTest.java` —
  **new**, 6 tests (plain unit test, no Spring/Mockito needed — the class
  takes its secret via a constructor argument).
- `src/test/java/.../ticket/TicketAccessIntegrationTest.java` — **new**, 9
  tests (full `@SpringBootTest`, real security filter chain, real signed
  JWTs, real Postgres). `GET /tickets/{ticketId}`'s full visibility matrix
  plus a config-level check that `app.ticket.credential.secret` and
  `app.jwt.secret` are genuinely distinct values.
- `src/test/java/.../order/service/OrderServiceImplTest.java` — **new**, 16
  tests (Mockito, no Spring context). All four `OrderService` methods'
  visibility branching, including `listEventOrders`'s deliberately
  non-buyer-scoped rule.
- `src/test/java/.../order/controller/OrderControllerTest.java` — **new**, 9
  tests (`@WebMvcTest` slice).
- `src/test/java/.../order/OrderAccessIntegrationTest.java` — **new**, 15
  tests (full `@SpringBootTest`, real filter chain, real Postgres). Full
  visibility matrix (buyer/organizer/owner/admin/stranger/cross-org) across
  all four retrieval endpoints, with explicit focus on the
  buyer-not-organizer-403 case on `GET /events/{eventId}/orders` and
  no-cross-buyer-leakage on `GET /users/me/orders`.
- `src/test/java/.../order/CheckoutIntegrationTest.java` — **extended**,
  +1 test (`checkout_success_gaCartItemQuantityThree_...`) — closes the
  code-reviewer MEDIUM finding (see below). Also added a
  `addGaItem(token, cartId, ticketTypeId, quantity)` overload (existing
  1-arg `quantity` call sites unchanged, now delegate to it).

## Scenario → test mapping

### `ticket/service/TicketServiceImplTest` (Mockito, 8 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown ticket → 404 | `.getTicket_unknownTicket_throwsResourceNotFound` | Pass |
| Owning buyer → success, without even touching `EventRepository` (short-circuit) | `.getTicket_owningBuyer_succeeds_withoutTouchingEventRepository` | Pass |
| Admin → success, without touching `currentUserId()`/`EventRepository` | `.getTicket_admin_succeeds_withoutTouchingCurrentUserIdOrEventRepository` | Pass |
| Event's organizer → success | `.getTicket_eventOrganizer_succeeds` | Pass |
| Event's owner → success | `.getTicket_eventOwner_succeeds` | Pass |
| Roleless stranger → 403 | `.getTicket_stranger_throwsForbidden` | Pass |
| **Cross-org organizer (DIFFERENT org's organizer, not just a stranger) → 403** | `.getTicket_organizerOfDifferentOrganization_throwsForbidden` | Pass |
| Non-owning caller whose event no longer resolves → 404 (not silently 403) | `.getTicket_nonOwningCaller_eventNoLongerExists_throwsResourceNotFound` | Pass |

### `ticket/controller/TicketControllerTest` (`@WebMvcTest`, 3 tests)

| Scenario | Test | Result |
|---|---|---|
| Existing ticket → 200, `credential` absent from JSON | `.get_existingTicket_returns200_withoutCredential` | Pass |
| Unknown ticket → 404 | `.get_unknownTicket_returns404` | Pass |
| Service throws `ForbiddenException` → 403 | `.get_unauthorizedCaller_returns403` | Pass |

### `ticket/service/TicketCredentialServiceTest` (plain unit test, 6 tests)

| Scenario | Test | Result |
|---|---|---|
| Format is `ticketId + "." + base64url-signature` | `.generate_returnsTicketIdDotBase64UrlSignature` | Pass |
| Signature matches an independently-computed `HmacSHA256` over the ticket id with the same secret | `.generate_matchesManuallyComputedHmacWithTheSameSecret` | Pass |
| Two different ticket ids → distinct credentials | `.generate_twoDifferentTicketIds_produceDistinctCredentials` | Pass |
| Same ticket id twice → identical (deterministic given key+payload) — distinctness comes from the embedded random UUID, not per-call randomness | `.generate_sameTicketIdTwice_isDeterministic` | Pass |
| **Dedicated-secret proof**: the same ticket id signed under two different secrets yields two different credentials | `.generate_differentSecret_producesDifferentCredentialForTheSameTicketId` | Pass |
| **Unguessable proof**: a signature computed with a wrong/guessed secret never matches the real one | `.generate_signatureIsNotGuessableWithoutTheSecret` | Pass |

### `order/service/OrderServiceImplTest` (Mockito, 16 tests)

| Scenario | Test | Result |
|---|---|---|
| `getOrder`: unknown order → 404 | `.getOrder_unknownOrder_throwsResourceNotFound` | Pass |
| `getOrder`: owning buyer → success, buyer-match short-circuits before any `findFirstByOrderId` call | `.getOrder_owningBuyer_succeeds_withoutTouchingTicketOrEventRepositoryForVisibility` | Pass |
| `getOrder`: admin → success | `.getOrder_admin_succeeds` | Pass |
| `getOrder`: event's organizer → success | `.getOrder_eventOrganizer_succeeds` | Pass |
| `getOrder`: roleless stranger → 403 | `.getOrder_stranger_throwsForbidden` | Pass |
| **`getOrder`: cross-org organizer → 403** | `.getOrder_crossOrgOrganizer_throwsForbidden` | Pass |
| `getOrder`: defensive fallback — an order with zero resolvable tickets falls back to buyer-or-admin-only (non-buyer, non-admin → 403 without ever resolving an event) | `.getOrder_nonBuyerNonAdmin_orderHasNoTickets_throwsForbidden` | Pass |
| `getOrderTickets`: owning buyer → 200, ticket list mapped via `TicketResponse` (no credential field to leak) | `.getOrderTickets_owningBuyer_returnsTicketsWithoutCredential` | Pass |
| `getOrderTickets`: roleless stranger → 403 | `.getOrderTickets_stranger_throwsForbidden` | Pass |
| `listMyOrders`: queries strictly by the caller's own id — self-scoped | `.listMyOrders_usesCallerIdAsBuyerId_selfScopedOnly` | Pass |
| `listEventOrders`: unknown event → 404 | `.listEventOrders_unknownEvent_throwsResourceNotFound` | Pass |
| `listEventOrders`: organizer → success | `.listEventOrders_organizer_succeeds` | Pass |
| `listEventOrders`: admin → success, without touching `currentUserId()` | `.listEventOrders_admin_succeeds` | Pass |
| `listEventOrders`: no orders yet for the event → empty page, `OrderRepository.findByIdIn` never called | `.listEventOrders_noOrdersYet_returnsEmptyPage_withoutQueryingOrderRepository` | Pass |
| **`listEventOrders`: the order's OWN BUYER, with no organizer/owner role → 403 (this endpoint is explicitly NOT buyer-scoped)** | `.listEventOrders_ordersOwnBuyerWithNoOrganizerRole_throwsForbidden` | Pass |
| **`listEventOrders`: cross-org owner → 403** | `.listEventOrders_crossOrgOwner_throwsForbidden` | Pass |

### `order/controller/OrderControllerTest` (`@WebMvcTest`, 9 tests)

| Scenario | Test | Result |
|---|---|---|
| `GET /orders/{orderId}` valid → 200, nested `tickets[0].credential` absent | `.getOrder_existingOrder_returns200_ticketsNeverExposeCredential` | Pass |
| `GET /orders/{orderId}` unknown → 404 | `.getOrder_unknownOrder_returns404` | Pass |
| `GET /orders/{orderId}` service-thrown 403 → 403 | `.getOrder_unauthorizedCaller_returns403` | Pass |
| `GET /orders/{orderId}/tickets` valid → 200 array, `credential` absent | `.getOrderTickets_existingOrder_returns200_withoutCredential` | Pass |
| `GET /orders/{orderId}/tickets` service-thrown 403 → 403 | `.getOrderTickets_unauthorizedCaller_returns403` | Pass |
| `GET /users/me/orders` → 200, `PageResponse` shape | `.listMyOrders_returns200_pagedShape` | Pass |
| `GET /events/{eventId}/orders` valid → 200, `PageResponse` shape | `.listEventOrders_organizerOrAdmin_returns200_pagedShape` | Pass |
| `GET /events/{eventId}/orders` unknown event → 404 | `.listEventOrders_unknownEvent_returns404` | Pass |
| `GET /events/{eventId}/orders` service-thrown 403 → 403 | `.listEventOrders_buyerWithoutOrganizerRole_returns403` | Pass |

### `order/OrderAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 15 tests)

| Scenario | Test | Result |
|---|---|---|
| `GET /orders/{orderId}`: owning buyer → 200 | `.getOrder_owningBuyer_returns200` | Pass |
| `GET /orders/{orderId}`: event organizer → 200 | `.getOrder_eventOrganizer_returns200` | Pass |
| `GET /orders/{orderId}`: admin → 200 | `.getOrder_admin_returns200` | Pass |
| `GET /orders/{orderId}`: roleless stranger → 403 | `.getOrder_stranger_returns403` | Pass |
| **`GET /orders/{orderId}`: cross-org organizer → 403** | `.getOrder_crossOrgOrganizer_returns403` | Pass |
| `GET /orders/{orderId}`: unknown order → 404 | `.getOrder_unknownOrder_returns404` | Pass |
| `GET /orders/{orderId}/tickets`: owning buyer → 200, credential absent | `.getOrderTickets_owningBuyer_returns200_withoutCredential` | Pass |
| **`GET /orders/{orderId}/tickets`: cross-org organizer → 403** | `.getOrderTickets_crossOrgOrganizer_returns403` | Pass |
| `GET /users/me/orders`: buyer A sees their own order, never buyer B's | `.listMyOrders_returnsOnlyCallersOwnOrders_neverAnotherBuyersOrder` | Pass |
| `GET /users/me/orders`: no token → 401 | `.listMyOrders_noToken_returns401` | Pass |
| `GET /events/{eventId}/orders`: event owner → 200 | `.listEventOrders_eventOwner_returns200` | Pass |
| `GET /events/{eventId}/orders`: admin → 200 | `.listEventOrders_admin_returns200` | Pass |
| **`GET /events/{eventId}/orders`: the order's OWN BUYER, with no organizer role on that org → 403 (proves this endpoint is NOT buyer-scoped, unlike the other three)** | `.listEventOrders_ordersOwnBuyerWithoutOrganizerRole_returns403` | Pass |
| **`GET /events/{eventId}/orders`: cross-org owner → 403** | `.listEventOrders_crossOrgOwner_returns403` | Pass |
| `GET /events/{eventId}/orders`: unknown event → 404 | `.listEventOrders_unknownEvent_returns404` | Pass |

### `order/CheckoutIntegrationTest` (extension) — closes the code-reviewer MEDIUM finding

| Scenario | Test | Result |
|---|---|---|
| **A single GA `CartItem` at `quantity=3`, checked out against real Postgres in one transaction, issues 3 `Ticket` rows whose `ticketNumber` values are pairwise distinct AND whose `credential` values (read directly from the DB, since the API never returns it) are pairwise distinct — also implicitly proves Hibernate's auto-flush-before-query lets `existsByEventIdAndTicketNumber` see earlier, same-transaction, uncommitted `Ticket` rows** | `.checkout_success_gaCartItemQuantityThree_issuesThreeTicketsWithDistinctNumbersAndCredentials` | Pass |

## Scenarios not covered (out of scope for Phase 6a)

Per `ticket-test-plan.md`, two scenarios remain genuinely not-built and are
left unchecked rather than faked:

- **Ticket artifact rendering (digital/physical PDF/image)** — `UC-ATTND-06`
  describes it, but no artifact-generation code exists yet; there is nothing
  to test.
- **A used ticket cannot be reused / check-in validation** — `BR-CHECKIN-001`
  is Phase 10's job; `TicketCredentialService`'s own javadoc explicitly
  scopes itself to generation only ("Generation only — no
  verification/lookup method here. That's Phase 10's check-in validation
  job."), and `TicketStatus.USED` exists as an enum value but nothing in
  Phase 6a ever transitions a ticket to it.
- **"Order status reflects payment/refund state accurately"** (from
  `order-test-plan.md`) — refunds don't exist yet (no `refund` module); only
  the `PENDING`→`PAID` transition Phase 5b/6a actually implements is
  exercised (already covered by `CheckoutIntegrationTest`/
  `CheckoutServiceImplTest`).

## Findings surfaced by writing these tests

No new production bugs were found. All settled/documented behavior (BR-CART-004
visibility scoping across all five endpoints, the deliberately-different
non-buyer-scoped rule on `GET /events/{eventId}/orders`, credential-never-
exposed, ticket-number format, the dedicated HMAC secret) matched the
production code exactly on first passing run.

The one thing this pass specifically set out to prove — the code-reviewer's
MEDIUM finding about `quantity>1` GA ticket-number/credential distinctness —
is now proven against real Postgres rather than mocked stubs (see
`checkout_success_gaCartItemQuantityThree_...` above): all 3 issued tickets'
`ticketNumber`s were pairwise distinct and all 3 `credential`s (fetched
directly from the `tickets` table, since the API never returns that column)
were pairwise distinct, with each `credential` correctly prefixed by its own
ticket's id per `TicketCredentialService`'s format. No production code was
touched to make this pass — it passed on the first real run.

One pre-existing, out-of-scope observation, unchanged from prior sessions'
notes (`testing/checkout-test-results.md`, `testing/cart-promocode-test-results.md`):
the shared dev Postgres database carries a small number of leftover rows
(`organizations`=1, `organization_members`=1, `events`=4) that predate this
session and are not touched by anything in this pass — verified identical
before and after this pass's targeted 101-test run and the full 512-test
suite run.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "select 'tickets' t, count(*) from tickets
   union all select 'orders', count(*) from orders
   union all select 'payments', count(*) from payments
   union all select 'checkout_idempotency_keys', count(*) from checkout_idempotency_keys
   union all select 'cart_items', count(*) from cart_items
   union all select 'carts', count(*) from carts
   union all select 'organizations', count(*) from organizations
   union all select 'organization_members', count(*) from organization_members
   union all select 'events', count(*) from events;"

             t             | count
---------------------------+-------
 tickets                   |     0
 orders                    |     0
 payments                  |     0
 checkout_idempotency_keys |     0
 cart_items                |     0
 carts                     |     0
 organizations             |     1
 organization_members      |     1
 events                    |     4
```

`tickets`/`orders`/`payments`/`checkout_idempotency_keys`/`cart_items`/`carts`
(the tables this pass's tests actually write to) are all `0` — full cleanup
confirmed. `organizations`=1/`organization_members`=1/`events`=4 are the same
pre-existing residue documented in prior sessions' results docs, unrelated to
and unmodified by this pass.
