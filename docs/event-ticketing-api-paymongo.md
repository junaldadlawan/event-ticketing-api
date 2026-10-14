# PayMongo: how payments work and how to run them

PayMongo is the Philippine payment gateway (`payments.gateway=paymongo`). The other gateways stay available: `mock` (default, no
network) and `stripe`. Switching is one environment variable; no code changes.

## Status

| Part | State |
|---|---|
| Checkout with PayMongo (payment intent, webhook, order only after payment) | Built and tested against a stub PayMongo. **Not yet run against real PayMongo**: it needs your test keys. |
| Refunds through PayMongo (by payment id `pay_...`) | Built and tested against a stub. |
| Saved cards on PayMongo (customers and vaulting) | **Not built.** In `paymongo` mode saved cards behave like the mock gateway. |
| Paying organizers (linked accounts, transfers) | **Not built (phase 2).** Payouts are still recorded as before and sent outside PayMongo. |
| Resale purchases | Not supported with PayMongo yet (they fail with 402). |

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

Use any future expiry date and any 3-digit CVC. PayMongo can change these, so check its testing page if one is rejected.

| Card | Number | Behaviour |
|---|---|---|
| Visa | `4343 4343 4343 4345` | Succeeds, no 3-D Secure |
| Mastercard | `5123 0000 0000 0002` | Succeeds, no 3-D Secure |
| Visa (3-D Secure) | `4120 0000 0000 0007` | Asks to authorize, then succeeds |
| Visa | `4200 0000 0000 0018` | Declined: expired card |
| Mastercard | `5100 0000 0000 0198` | Declined: insufficient funds |

These are PayMongo's published test numbers, not real cards; they are typed into PayMongo's form in the browser, never sent to this API.

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
