# PayMongo: how payments work and how to run them

PayMongo is the Philippine payment gateway (`payments.gateway=paymongo`). The other gateways stay available: `mock` (default, no
network) and `stripe`. Switching is one environment variable; no code changes.

## Status

| Part | State |
|---|---|
| Checkout with PayMongo (payment intent, webhook, order only after payment) | Built and tested against a stub PayMongo. **Not yet run against real PayMongo**: it needs your test keys. |
| Refunds through PayMongo (by payment id `pay_...`) | Built and tested against a stub. |
| Saved cards on PayMongo (customers and vaulting) | **Built and tested against a stub; not run against real PayMongo.** See "Saved cards" below. Vaulting must be enabled on your account by PayMongo (support@paymongo.com). |
| Paying organizers (wallet transfers to a bank or e-wallet) | **Built and tested against a stub.** See "Paying organizers" below. It needs an activated PayMongo wallet, which your test account does not have yet. Linked accounts (splitting at payment time) are not used. |
| Resale purchases | Not supported with PayMongo yet (they fail with 402). |

## Saved cards

How PayMongo's documentation says a card is reused (read 2026-10-16; **card vaulting is available only to selected accounts**: ask support@paymongo.com to enable it, otherwise saving fails and cards stay "re-enter each time"):
- A card can only be vaulted **by a payment**: the payment intent is created with `setup_future_usage: { session_type: "on_session", customer_id }`. There is no standalone "save a card" call, so **Settings > Payment cannot save a chargeable card by itself**: `POST /users/me/payment-methods` still records only brand, last 4 and expiry (`canChargeAgain: false`). The card becomes chargeable when it is saved through a checkout with `saveCard: true`.
- Visa and Mastercard only; on-session only, so **the security code is asked again each time** (it is sent in the request body as `securityCode`, set on the payment method with `PATCH /payment_methods/{id}` and attached in the same call; never stored or logged). Vaulted cards belong to your merchant account. Deleting a vaulted card at PayMongo is permanent.

What the API does:
1. **Save:** `POST /carts/{id}/checkout` with `{ "saveCard": true }` (new card, browser flow). The user's PayMongo customer is created if needed (`users.paymongo_customer_id`) and the intent carries `setup_future_usage`. When the `payment.paid` webhook has issued the order, the server reads `GET /customers/{id}/payment_methods` and stores each vaulted card not stored yet (`payment_methods.paymongo_payment_method_id`, plus brand, last 4, expiry). An older saved card with the same last 4 and expiry gets the id instead of a duplicate. This step never undoes the order if it fails; each run logs (INFO) whether `saveCard` was set, the PayMongo customer, how many cards PayMongo returned and why each was skipped, and warns when PayMongo returns none (card vaulting not enabled on the account, or the payment was not made with `setup_future_usage`). A card the user removed is brought back (same row, primary if it is their only card) when a save finds nothing new to add; otherwise it stays removed. Every added, upgraded or restored card writes a `payment_method.added` / `payment_method.restored` audit entry.
   PayMongo requires `first_name`, `last_name`, `email` and `default_device` to create the customer (the API sends `email`; the phone is optional and not sent). If the customer cannot be created, or PayMongo refuses the intent because of `setup_future_usage` ("On session payments are not yet supported" or any 4xx naming `setup_future_usage`: vaulting is not enabled on the account), the payment still starts without saving: a WARN is logged, the intent is created again without `setup_future_usage`, `save_card` is false and the start response says `cardSaving: "UNAVAILABLE"` (otherwise `REQUESTED` or `NOT_REQUESTED`). Any other 4xx or a 5xx still fails the checkout, with no second try. After a vaulting refusal the API remembers it for 5 minutes (in memory) and skips asking PayMongo again.
2. **Pay:** `POST /carts/{id}/checkout` with `{ "paymentMethodId", "securityCode", "returnUrl" }` (and `itemIds` as usual). The server starts the payment, then `POST /payment_intents/{id}/attach` with the stored `pm_...`, the intent's client key and the `returnUrl`. Answer: 202 with `nextAction` **NONE** (the webhook completes the order) or **REDIRECT** with `redirectUrl` (3-D Secure). A declined card is a 402 and the cart is untouched.
3. **Old cards:** a card without a stored id is listed with `canChargeAgain: false` and checkout with it is a 409 "Re-enter this card to use it again".

Assumptions to confirm with a real account: the response of `GET /customers/{id}/payment_methods` (read as `data[].attributes.details.{last4, exp_month, exp_year, card_brand}`), that the attach response reports `awaiting_next_action` with `next_action.redirect.url` and `awaiting_payment_method` with `last_payment_error.failed_message`, and that `PATCH /payment_methods/{id}` with `details.cvc` is accepted. Removing a saved card here does not delete it at PayMongo.

## Test a card payment, step by step

Everything needed to run one card payment end to end in PayMongo **test mode**. Tick each box before the next section.

### A. Accounts and keys (PayMongo dashboard, **Test mode switched on**)

- [ ] A PayMongo account with the dashboard in **Test mode** (never use live keys on a dev machine).
- [ ] **Secret key** `sk_test_...` (Developers > API keys) → backend `PAYMONGO_SECRET_KEY`.
- [ ] **Public key** `pk_test_...` (same page) → web app `VITE_PAYMONGO_PUBLIC_KEY` (a public key is safe in the browser).
- [ ] A **webhook** created in test mode (section C) → its secret `whsk_...` → backend `PAYMONGO_WEBHOOK_SECRET`.

### B. Backend environment (`.env.dev`, gitignored)

```
SPRING_PROFILES_ACTIVE=dev
PAYMENTS_GATEWAY=paymongo
PAYMONGO_SECRET_KEY=sk_test_...        # test key, not sk_live_
PAYMONGO_WEBHOOK_SECRET=whsk_...       # the test-mode webhook's secret
```

- [ ] `PAYMENTS_GATEWAY` is **`paymongo`** (with `stripe` or `mock` the webhook does not exist and nothing reaches PayMongo).
- [ ] The file is loaded into the run configuration (IntelliJ: Run > Edit Configurations > Modify options > Environment variables, or the EnvFile plugin).
  Spring Boot does not read `.env` files by itself.
- [ ] The app was **restarted** after changing it. It refuses to start in `paymongo` mode if either secret is empty.

### C. Make the API reachable from the internet (webhook)

PayMongo reports the result by calling your API, and it cannot reach `localhost`.

- [ ] Keep the API running on port 8081, then in a second terminal: `cloudflared tunnel --url http://localhost:8081`
      (installed with `winget install --id Cloudflare.cloudflared`). It prints `https://<random>.trycloudflare.com`. Keep that window open.
- [ ] In PayMongo (test mode) > Developers > Webhooks > Add endpoint:
      URL `https://<random>.trycloudflare.com/api/v1/webhooks/paymongo`
      Events: tick **only `payment.paid`** and **`payment.failed`** (the "Payment" column). Save, copy the `whsk_...` secret into `.env.dev`.
- [ ] Check the route: `curl -X POST -H "Content-Type: application/json" -d "{}" https://<random>.trycloudflare.com/api/v1/webhooks/paymongo`
      must answer **401** (unsigned request refused, which proves the route exists). **404** means the app is not in `paymongo` mode.
- [ ] The tunnel address changes every time `cloudflared` restarts: update the webhook URL in PayMongo when it does (the secret stays the same).

### D. Make the order payable in PHP

PayMongo accepts **only PHP**, and amounts are centavos (PHP 50.00 = 5000). The demo ticket types are priced in **USD**, which the API refuses
(`PayMongo only accepts PHP orders`). The two **Demo Sunset Concert** ticket types (and `demo-data.sql`) are already in PHP: buy those.
Other events are still priced in USD. Switch a ticket type to PHP only if nothing was sold on it (changing one that has sold tickets or USD orders
leaves its history in the wrong currency), once, in the dev database:

```sql
UPDATE ticket_types SET price_currency = 'PHP'
WHERE event_id IN (SELECT id FROM events WHERE title = 'Demo Sunset Concert');   -- or any event you use
```

- [ ] The cart total on the checkout screen shows **₱** (PHP), not `$`.
- [ ] The platform fee is a percentage rule today (5%), so it follows the ticket currency. A **flat** fee in another currency would be skipped.
- [ ] Total is at least PHP 1.00 (PayMongo's card minimum). Example: General Admission 5000 (PHP 50.00) + 5% fee = 5250 (PHP 52.50).

### E. Web app (the PayMongo card form)

The card is entered in the browser and never sent to this API. The web app must, in this order:

- [ ] Call `POST /api/v1/carts/{id}/checkout` with an `Idempotency-Key` header and body `{}` → **202** `{checkoutId, clientKey, amount, currency}`.
- [ ] Create a PayMongo payment method from the card with PayMongo.js (`pk_test_...`), then **attach** it to the payment intent using the `clientKey`
      (the intent id is the part of the client key before `_client`) and a `return_url`.
- [ ] If PayMongo answers `awaiting_next_action`, redirect the buyer to `next_action.redirect.url` (3-D Secure); the buyer comes back to `return_url`.
- [ ] Poll `GET /api/v1/checkouts/{checkoutId}` every 1 to 2 seconds until `status` is `PAID` (then `orderId` is set), `FAILED` or `REFUNDED`.

### F. Run the test

1. Sign in as a demo customer (for example `customer1@demo.test`), add a ticket of a PHP-priced event to the cart and open checkout.
2. Enter a test card from the table below (success first): number `4343 4343 4343 4345`, expiry any future date (for example `12/30`),
   security code any 3 digits (for example `123`), any name.
3. Pay. The web app shows pending, then paid.

**Expected result (success card):**

- The API log shows `checkout.started` then `checkout.completed`.
- The checkout status is `PAID` with an `orderId`; the order is `PAID` and the tickets exist.
- PayMongo dashboard (test mode) > Payments shows a `paid` payment; Developers > Webhooks > your endpoint shows a delivered `payment.paid` with a 200 answer.

Check the database (any SQL client or `docker exec`):

```sql
SELECT status, order_id, failure_reason, amount, currency FROM pending_checkouts ORDER BY created_at DESC LIMIT 3;
SELECT o.status, p.gateway_ref, p.amount_amount, p.amount_currency   -- gateway_ref must start with pay_
FROM orders o JOIN payments p ON p.order_id = o.id ORDER BY o.created_at DESC LIMIT 3;
```

### G. More cases to try

| Case | Card | What should happen |
|---|---|---|
| 3-D Secure | `4120 0000 0000 0007` | Redirect to PayMongo's prompt, choose **Authorize**, return, status becomes `PAID` |
| Declined: insufficient funds | `5100 0000 0000 0198` | Payment fails, status `FAILED`, cart and holds intact, pay again with the same cart |
| Declined: expired card | `4200 0000 0000 0018` | Same as above |
| Hold expires while paying | any success card, wait past the hold time before paying | Payment succeeds, then the API **refunds it automatically**: status `REFUNDED`, no order |
| Refund an order | pay with a success card, then refund the order as an organizer/admin | PayMongo shows the refund (the payment id `pay_...` is used) |
| Same Idempotency-Key twice | repeat the start call | Same `checkoutId` and `clientKey`, one payment intent |

### H. If it does not work

| Symptom | Cause and fix |
|---|---|
| Webhook URL answers **404** | The app is not in `paymongo` mode: set `PAYMENTS_GATEWAY=paymongo`, load the env file, restart |
| Webhook delivery shows **401** in PayMongo | Wrong secret: use the `whsk_` of **this** webhook, in the same mode (test/live), with no spaces; restart after changing it |
| App does not start | `PAYMONGO_SECRET_KEY` or `PAYMONGO_WEBHOOK_SECRET` is empty |
| Checkout answers 400 `PayMongo only accepts PHP orders` | The ticket type is not in PHP: section D |
| Status stays `PENDING` after paying | The webhook never arrived: tunnel closed or its URL changed, events not ticked, or the webhook was created in the other mode. Check PayMongo's delivery log |
| Card form refuses the card or shows an error | The web app is missing the public key `VITE_PAYMONGO_PUBLIC_KEY`, or a live card was used in test mode |
| Status `REFUNDED` with a reason | The payment succeeded but the order could not be issued (hold expired, amount differs); the money was returned. Try again |
| 402 on a checkout | Only happens on the Stripe/mock gateways; with PayMongo, failures arrive as `FAILED` through the webhook |

## What you need

1. A PayMongo account with **test mode** keys: the secret key (`sk_test_...`, for this API) and the public key (`pk_test_...`, for the web
   app's card form only).
2. A webhook in the PayMongo dashboard pointing at `https://<your public url>/api/v1/webhooks/paymongo` for the events
   **`payment.paid`** and **`payment.failed`**. PayMongo shows a webhook secret for it. For local development expose port 8081 with a tunnel.
3. These variables in `.env.dev` (never in a tracked file): `PAYMENTS_GATEWAY=paymongo`, `PAYMONGO_SECRET_KEY`, `PAYMONGO_WEBHOOK_SECRET`.
   With `paymongo` selected the app refuses to start without both secrets.

## The flow (why it differs from Stripe)

The card never touches this server. It is entered in the browser with PayMongo's public key.

1. Web app: `POST /api/v1/carts/{id}/checkout` with an `Idempotency-Key` header and body `{}`.
   API: prices the cart, creates a PayMongo payment intent (PHP, centavos) and a pending checkout, answers **202**
   `{checkoutId, status: "PENDING", clientKey, amount, currency}`. **No order exists yet.**
2. Web app: PayMongo.js creates a payment method from the card (public key) and attaches it to the intent with the `clientKey`
   and a `return_url`. If the bank asks for 3-D Secure, the buyer is redirected and comes back to the return URL.
3. PayMongo calls the webhook. `payment.paid`: the API issues the order, payment record and tickets. `payment.failed`: the cart and holds
   stay, the buyer can try again with the same key.
4. Web app: polls `GET /api/v1/checkouts/{checkoutId}` until the status is `PAID` (with `orderId`), `FAILED` or `REFUNDED`.

Safety nets: a webhook is accepted only with a valid `Paymongo-Signature`; a webhook delivered twice issues one order; a repeated start with
the same key returns the same checkout; if the buyer paid but the hold expired meanwhile (or the amount differs), the payment is refunded
automatically (status `REFUNDED`) and the failure is audit-logged.

## Test cards (PayMongo test mode)

From PayMongo's testing page, [docs.paymongo.com/docs/payment-acceptance-testing](https://docs.paymongo.com/docs/payment-acceptance-testing)
(read directly on 2026-10-15). Use **any future expiry date and any 3-digit security code (CVC)**: the table shows `12/30` and `123`, but any future date and any 3 digits work. PayMongo can change these, so the page is the authority if one
is rejected. They only work with test keys; they are public test numbers, typed into PayMongo's form in the browser and never sent to this API.

| Card | Number | Expiry (MM/YY) | Security code | Behaviour |
|---|---|---|---|---|
| Visa | `4343 4343 4343 4345` | `12/30` | `123` | Succeeds, no 3-D Secure |
| Visa | `4571 7360 0000 0075` | `12/30` | `123` | Succeeds, no 3-D Secure |
| Mastercard | `5123 0000 0000 0002` | `12/30` | `123` | Succeeds, no 3-D Secure |
| Mastercard | `5123 0000 0000 0001` | `12/30` | `123` | Succeeds (3-D Secure supported, not required) |
| Visa (3-D Secure) | `4120 0000 0000 0007` | `12/30` | `123` | Succeeds if you choose **Authorize** at the prompt |
| declined | `4200 0000 0000 0018` | `12/30` | `123` | Declined: expired card |
| declined | `4300 0000 0000 0017` | `12/30` | `123` | Declined: invalid CVC |
| declined | `5100 0000 0000 0198` | `12/30` | `123` | Declined: insufficient funds |
| declined | `4111 1111 1111 1111` | `12/30` | `123` | Declined: generic |

## PayMongo's fee and who pays it (admin setting)

PayMongo keeps a processing fee from every payment. An **admin** decides, with `PUT /api/v1/payment-gateway-fee`, how much fee to apply and
**who bears it**: the customer (added on top and shown in the cart as `gatewayFee`), the organization (deducted from its payout) or the
platform (absorbed). The default is 0% / platform, so nothing changes until it is set.

The fee has two numbers, a **percentage** (up to three decimals) and a **fixed amount**. PayMongo's published card rates are
[VAT-exclusive](https://www.paymongo.com/en/pricing): domestic **3.125% + PHP 13.39**, international **4.02% + PHP 13.39**. Enter them as they
are, or add 12% VAT yourself because the app does not (my calculation x 1.12, confirm with PayMongo or a payment's details):

| Card | Published rate (no VAT) | Percentage | Fixed amount (minor units) |
|---|---|---|---|
| Domestic | 3.125% + PHP 13.39 | 3.125 | 1339 |
| International (what the PayMongo test cards count as) | 4.02% + PHP 13.39 | 4.02 | 1339 |

With VAT added (x 1.12): domestic 3.5% + PHP 15.00 (3.5 and 1500), international 4.5% + PHP 15.00 (4.5 and 1500).

**Domestic and foreign cards.** PayMongo charges a card issued abroad more, so the setting has two rates: `percentage` / `flatAmount` are the
domestic one and `foreignPercentage` / `foreignFlatAmount` the whole rate for a card issued outside the Philippines. With the VAT-included rates seen on
real payments: domestic **3.5% + PHP 15.00** and foreign **4.5% + PHP 15.00**, so

`{"percentage": 3.5, "flatAmount": {"amount": 1500, "currency": "PHP"}, "payer": "CUSTOMER", "foreignPercentage": 4.5, "foreignFlatAmount": {"amount": 1500, "currency": "PHP"}}`

Leave the two foreign fields out and a foreign card costs the domestic rate (the old behaviour). The card is typed into PayMongo's form after the cart
is priced, so the customer is always **charged the domestic rate** in the cart. When PayMongo's `payment.paid` webhook arrives it says where the card was
issued (`source.country`; `PH` is domestic, anything else foreign): the order then records the card origin and the fee that really applied. At payout:
the organization bears the fee → the fee that applied (foreign for a foreign card) comes off its share; the customer bears it → only what the customer
paid comes off and the platform absorbs the extra cost of a foreign card; the platform bears it → nothing comes off. The fee check compares each real
payment with the matching rate.

Example body without foreign rate: `{"percentage": 3.125, "flatAmount": {"amount": 1339, "currency": "PHP"}, "payer": "CUSTOMER"}`. A single percentage cannot match both
card types, and the fee is worked out on the price before the fee, not on the total the buyer ends up paying, so it can differ by a few centavos
from what PayMongo actually keeps. The exact fee of a payment is its `fee` / `net_amount` on PayMongo's payment record.

## Paying organizers

PayMongo cannot pay to a card. Organizers are paid from the **platform's PayMongo wallet** by a transfer to a **bank or e-wallet account**:
InstaPay (real time, up to PHP 50,000, 24/7) or PESONet (next banking day, up to PHP 10,000,000); the API picks by amount. PayMongo charges the
wallet a fee per transfer (about PHP 10 at the time of writing) which the platform bears. Card money lands in the wallet when PayMongo settles it,
so the wallet must hold at least the payout (and the fee) when you send.

How it works here:

1. The organization's owner (or an admin) sets the payout account: `GET /payout-banks` lists the banks, `PUT /organizations/{id}/payout-account`
   saves the bank code, the name on the account and the number (stored encrypted, only the last 4 are ever shown).
2. An admin generates the payout as before (`POST /organizations/{id}/payouts`); with PayMongo it needs the payout account instead of a card.
3. An admin sends it: `POST /payouts/{id}/send`. The payout turns **PROCESSING** and the transfer goes to PayMongo.
4. PayMongo reports the end through the same webhook as payments (`transfer.outward.successful` / `transfer.outward.failed`), or an admin calls
   `POST /payouts/{id}/refresh`. The payout turns **PAID**, or **FAILED** with a reason, and a failed one can be sent again.

### Check the wallet: `GET /api/v1/payout-wallet` (admin)

Shows what PayMongo reports for the wallet the payouts are sent from, read live on every call:

| Field | Meaning |
|---|---|
| `status` / `activated` | `activated` or `deactivated`. A new wallet is **deactivated** until you sign the Statement of Acceptance in the Dashboard (it also goes deactivated when locked); a deactivated wallet cannot send. |
| `balance.available` | What can be sent right now (centavos). It has to cover the payout **plus the transfer fee**. |
| `balance.pending` | In transit, not sendable yet. Card payments settle into the wallet on PayMongo's payout cycle, so it can stay low for a while after a test checkout. |
| `limits` | `balance` cap, and `inwardDaily` / `inwardMonthly` / `outwardDaily` / `outwardMonthly` (999999999999999999 = no limit). Which ones apply depends on the wallet `type` (`basic` or `fully_verified`). |
| `usage` | How much has already moved today and this month, to compare with the limits (a payout over the remaining `outwardDaily` is refused by PayMongo). |
| `account` | The wallet's own name and number: where a top-up is sent, and what goes in `PAYMONGO_WALLET_ACCOUNT_*`. |
| `livemode`, `isDefault`, `statementOfAcceptanceSignedAt`, `createdAt`, `updatedAt` | Test or live, whether it is the default wallet, when it was activated, and its dates. |
| `matchesConfiguredAccount` | True when this is the wallet named in `PAYMONGO_WALLET_ACCOUNT_NUMBER`; false means the server would send from a different wallet than the one it reads. |

404 means the PayMongo account has no wallet yet. Outside `paymongo` mode the endpoint shows a simulated wallet (`provider` MOCK). The field
names come from PayMongo's Wallet Resource reference and are not yet confirmed against a live wallet.

### Set it up (backend environment, `.env.dev`)

| Variable | What it is |
|---|---|
| `PAYOUT_ENCRYPTION_KEY` | 32 random bytes in base64 that encrypt the account numbers. Generate your own, for example `openssl rand -base64 32`. Without it, saving a payout account answers 503. Keep it: losing it makes the stored account numbers unreadable. |
| `PAYMONGO_WALLET_ACCOUNT_NUMBER` | The account number of your PayMongo wallet (Dashboard > Wallet, or `GET /v2/wallets`). |
| `PAYMONGO_WALLET_ACCOUNT_NAME` | The account name shown on that wallet. |

In the PayMongo dashboard add the two transfer events to the webhook you already registered: **`transfer.outward.successful`** and
**`transfer.outward.failed`**. The wallet has to be activated on your PayMongo account first (`GET /v2/wallets` answers an empty list until then;
ask PayMongo support if you cannot find it).

### Test it (test mode simulates the transfer, no real money)

Use these as the organization's account number; the outcome follows the number (PayMongo's Transfer Test Cases):

| Account number | Result |
|---|---|
| `999999990001` | succeeds |
| `999999990002` | fails (`test_failed_number`) |
| `999999990003` | fails (`account_not_found`) |
| `999999990004` | fails (`account_not_active`) |
| `999999990005` | fails (`account_limit_reached`) |
| `999999990006` | fails (`internal_server_error`) |
| any other | stays pending |

Steps: set the payout account (any bank from `/payout-banks`, the name is free in test mode) with one of the numbers above, generate a payout for an
organization that has paid orders, send it, then watch it go PROCESSING and PAID (or FAILED with the reason). If the webhook is not set up, use
`POST /payouts/{id}/refresh`.

### Assumptions to confirm with the first real test

The transfer request (`POST /v2/batch_transfers`, one transfer in the batch) and the bank list (`GET /v1/wallets/receiving_institutions?provider=instapay`,
confirmed against your test key) follow PayMongo's documentation; the response and the webhook payload of a transfer were written from the
documentation and a stub, not seen live because the test account has no wallet yet. Confirm the transfer id (`tr_...`) is in the create response
(`data.transfers[0].id`) and in the webhook (`attributes.data.attributes.transfer_id`), and that failures carry `provider_error_code` / `provider_error`.

### The finance summary uses PayMongo's real fee

`GET /analytics/platform/finance` shows what PayMongo really kept on each payment (fee + foreign-card fee, read from its own payment record, the
amount minus the net it paid out), not the quote: a PHP 105.00 payment by a US card shows 19.73. For an order that has no fee recorded (placed before
fees were stored), the platform pays all of it, so the platform's net income can be negative until the fee is covered by customers or organizations.
The real fee is read from PayMongo the first time the summary is opened (the latest 100 paid payments, at most once a minute) and kept on the order.

### Check the fee against PayMongo: `GET /api/v1/payment-gateway-fee/check` (admin)

PayMongo has no endpoint for its fee rates, but every payment it records carries the fee it took. This endpoint reads your latest paid payments
(`?limit=` 1 to 20, default 5) and shows them next to your setting. For each payment: `fee` (base), `foreignFee` (1% surcharge when the card was
issued abroad, as with the PayMongo test cards, which are US cards), `totalFee`, `netAmount`, `effectivePercent`, the card brand and country, and
`expectedWithConfigured` / `differenceFromConfigured` / `matchesConfigured` against the current setting. `matchedRate` recognises the rate:

| Matched rate | What PayMongo took (VAT included) | Setting that reproduces it |
|---|---|---|
| `DOMESTIC_CARD` | 3.5% + PHP 15.00 | `percentage` 3.5, `flatAmount` 1500 |
| `INTERNATIONAL_CARD` | 4.5% + PHP 15.00 (3.5% + 15.00, plus the 1% foreign-card fee) | `percentage` 4.5, `flatAmount` 1500 |

`suggestedSetting` is the newest recognised rate, ready to send to `PUT /payment-gateway-fee`. Example from the first PayMongo test payment: PHP 105.00
by a US Visa, `fee` 18.68 + `foreignFee` 1.05 = 19.73, `netAmount` 85.27, matched `INTERNATIONAL_CARD`. It is an estimate from sample payments: the
fee a customer is charged is computed on the price before the fee, while PayMongo takes its fee from the total charged, so they can differ by a few centavos.

## Things that must be true

- Orders must be in **PHP**. PayMongo accepts only PHP, and the API refuses to start a checkout in another currency. (The demo events are
  priced in USD today; ticket prices have to be PHP for PayMongo.)
- Amounts are centavos (PHP 100.00 = 10000), the same as everywhere in this API.
- PayMongo's minimum card payment is PHP 1.00.

## Assumptions to confirm with the first real test

The request and webhook shapes were written from PayMongo's public documentation and tested against a stub, not against the live service.
Confirm these with your test key, and tell the developer if one differs:

- The payment intent request (`data.attributes`: `amount`, `currency`, `payment_method_allowed`, `payment_method_options.card.request_three_d_secure`,
  `capture_type`, `description`, `metadata`) and the response (`data.id`, `data.attributes.client_key`).
- The webhook event: `data.attributes.type` (`payment.paid` / `payment.failed`) and the payment inside it
  (`data.attributes.data.id` = `pay_...`, `.attributes.payment_intent_id`, `.amount`, `.currency`).
- The signature header `Paymongo-Signature: t=...,te=...,li=...` (HMAC-SHA256 of `<t>.<raw body>` with the webhook secret).
- The refund request (`payment_id`, `amount`, `reason`, `notes`); `reason` must be one of `duplicate`, `fraudulent`, `others`.

## Web app changes (not made here)

Collect the card with PayMongo.js (public key), attach with the `clientKey`, handle the 3-D Secure redirect and `return_url`, and poll
`GET /checkouts/{checkoutId}`. When the gateway is `paymongo` the checkout call takes no payment token.
