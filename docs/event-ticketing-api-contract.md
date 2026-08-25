# Event Ticketing API — API Contract (Core)

**Version:** 1.2 (Draft)
**Date:** 2026-08-25
**Status:** For review
**Based on:** `requirements.md` v1.6, `resources.md` v1.5
**Spec format:** OpenAPI 3.1 (`openapi.yaml`, companion to this document)

**Revision history:**
- v1.0 initial core contract: auth, organizations, events/ticket types, cart/checkout, orders/tickets, check-in/scanning.
- v1.1 adds the missing `Venue` resource (schema + `POST`/`GET`/`PATCH` endpoints, new section 3.3) — `EventCreate.venue_id` referenced a resource that had no way to be created until now.
- v1.2 adds `created_by`/`updated_by` audit fields (in `openapi.yaml`) to every schema that already had `created_at`/`updated_at`: `User` and `Organization` and `Venue` and `Order` get `created_by`; `Event` — the only schema with both timestamps — gets both `created_by` and `updated_by`. Scoped strictly to entities that already tracked timestamps; not a general "add audit trails everywhere" pass.

## 1. Scope

This is the first pass at the API contract, covering the **core purchase and check-in flow**: identity/auth (just enough to call everything else), events and ticket types, cart and checkout, orders and tickets, and check-in/scanning. It does not yet cover promo codes, waitlists, resale, refunds/payouts detail, notifications, platform administration, or analytics — those are designed in a later pass, building on the same conventions established here. Every endpoint below maps back to a resource in `resources.md` and a requirement in `requirements.md`.

## 2. Conventions

These apply platform-wide and are the baseline every later pass (promo codes, resale, admin, etc.) should follow too.

### 2.1 Base URL & versioning

All endpoints are rooted at `/v1`. The API is versioned in the URL path (requirements 5.6) — a breaking change ships as `/v2`, not a header flag, so existing integrations never silently change behavior.

### 2.2 Authentication

`Authorization: Bearer <access_token>` (JWT) on every request except `POST /v1/auth/register`, `POST /v1/auth/login`, `POST /v1/auth/refresh`, and public read endpoints explicitly marked **Public** below (e.g. browsing published events). Access tokens are short-lived; `POST /v1/auth/refresh` exchanges a refresh token for a new one.

Every non-public endpoint also enforces RBAC per requirements 4.1 — who's allowed is noted per endpoint. A caller who's authenticated but lacks the right role/scope gets `403`, not `401`.

### 2.3 Content type

`application/json` for all request and response bodies, except the ticket-artifact download endpoint (2.6's `TicketArtifact`), which returns the rendered file directly.

### 2.4 IDs & timestamps

All resource IDs are UUIDv4 strings. All timestamps are ISO 8601 UTC (`2026-08-25T13:00:00Z`), field names suffixed `_at`. `Event.ticket_number_prefix` is the one exception to opaque IDs — see 3.3.

### 2.5 Money

Any monetary value is an object, never a bare number, to avoid float rounding bugs: `{ "amount": 5000, "currency": "USD" }`, where `amount` is an integer in the currency's minor unit (cents).

### 2.6 Errors

Every non-2xx response body has the same shape:

```json
{
  "error": {
    "code": "ticket_type_sold_out",
    "message": "This ticket type has no remaining inventory.",
    "details": []
  }
}
```

`code` is a stable, machine-matchable string (for client logic); `message` is human-readable; `details` is an optional array of `{ "field": "...", "issue": "..." }` for validation errors (422).

### 2.7 Pagination

List endpoints are cursor-paginated:

```
GET /v1/events?limit=20&cursor=eyJ...
```

```json
{
  "data": [ /* ... */ ],
  "pagination": { "next_cursor": "eyJ...", "limit": 20 }
}
```

`next_cursor` is `null` on the last page.

### 2.8 Idempotency

Any endpoint that spends money or issues tickets requires an `Idempotency-Key` header (a client-generated UUID). Replaying the same key with the same body returns the original response rather than double-charging or double-issuing (requirements 5.6). In this pass, that's `POST /v1/carts/{cartId}/checkout`.

### 2.9 Rate limiting

Every response carries `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Checkout and check-in-validation endpoints have tighter limits than reads (requirements 5.3).

## 3. Endpoints

### 3.1 Auth & Identity

*(`User`, requirements 4.1)*

| Method & Path | Who | Description |
|---|---|---|
| `POST /v1/auth/register` | Public | Create a `User` account. Body: `{ name, email, password }`. `201` → `User`. |
| `POST /v1/auth/login` | Public | Body: `{ email, password }`. `200` → `{ access_token, refresh_token, expires_in }`. |
| `POST /v1/auth/refresh` | Public (valid refresh token) | Body: `{ refresh_token }`. `200` → `{ access_token, expires_in }`. |
| `GET /v1/users/me` | Authenticated | Returns the caller's own `User`, including which `Organization`s they belong to and their role in each. |

### 3.2 Organizations

*(`Organization`, requirements 4.1–4.2 — minimal; team-member management deferred to a later pass)*

| Method & Path | Who | Description |
|---|---|---|
| `POST /v1/organizations` | Authenticated | Creates an `Organization` with the caller as owner. `201` → `Organization` (verification status `pending`). |
| `GET /v1/organizations/{orgId}` | Members of that org, or admin | Returns `Organization`. |
| `PATCH /v1/organizations/{orgId}` | Owner/co-organizer of that org | Update name, payout account ref, etc. |

### 3.3 Venues

*(`Venue`, requirements 4.3 — a gap fixed in this revision: `EventCreate.venue_id` referenced a resource that had no schema or endpoints until now)*

A `Venue` is owned by the `Organization` that adds it and is reusable across that organization's events — an organizer defines "Madison Square Garden" once and points multiple events at it, rather than re-entering the address every time. It's deliberately org-scoped rather than a shared platform-wide directory in this pass; a shared/curated venue directory (so two different organizers booking the same physical venue could reuse one record) is a reasonable future enhancement, not a v1 requirement.

| Method & Path | Who | Description |
|---|---|---|
| `POST /v1/organizations/{orgId}/venues` | Owner/co-organizer of that org | Creates a `Venue`. Body: `{ name, address?, latitude?, longitude? }` — `address`/coordinates are optional so a purely virtual venue can be registered too. |
| `GET /v1/organizations/{orgId}/venues` | Members of that org, or admin | Lists the organization's venues, for the organizer to pick from when creating an event. |
| `GET /v1/venues/{venueId}` | Public | Returns one `Venue` — public because a buyer viewing an event needs its address/location. |
| `PATCH /v1/venues/{venueId}` | Owning org's owner/co-organizer | Update name/address/coordinates. |

`Event.venue_id` (3.4 below) references this resource; `GET /v1/events`'s `lat`/`lng`/`radius_km` search params filter on the coordinates stored here.

### 3.4 Events & Catalog

*(`Event`, `TicketType`, `SeatMap`/`Seat`, requirements 4.2–4.4)*

| Method & Path | Who | Description |
|---|---|---|
| `GET /v1/events` | Public | Search/list **published** events. Query: `q`, `category`, `lat`/`lng`/`radius_km`, `date_from`, `date_to`, `price_min`, `price_max`, `sort` (`date`\|`popularity`\|`price`), `cursor`, `limit`. |
| `POST /v1/events` | Organizer (verified org) | Creates an `Event` in `draft` status. Body includes an optional `venue_id` (from 3.3 — omit for a fully virtual event). On creation the API also generates and reserves `Event.ticket_number_prefix` (a random, collision-checked 3-letter code — requirements 4.10). |
| `GET /v1/events/{eventId}` | Public if `published`+; organizer/admin otherwise | Returns `Event`, with an embedded `venue` snapshot (name/address/coordinates) alongside `venue_id`. |
| `PATCH /v1/events/{eventId}` | Owning organizer | Update event fields. Some fields (e.g. venue) may be locked once tickets are on sale — enforced server-side. |
| `POST /v1/events/{eventId}/publish` | Owning organizer | Transitions `draft` → `published`. Requires the org to be verified (4.2). |
| `POST /v1/events/{eventId}/cancel` | Owning organizer or admin | Transitions to `cancelled`; asynchronously triggers the refund workflow (4.6, designed in a later pass) for every issued `Ticket`. |
| `GET /v1/events/{eventId}/seatmap` | Public if event is published | Returns the event's `SeatMap` with all `Seat`s and live status (`available`/`held`/`sold`). Omitted/`404` for GA-only events. |
| `POST /v1/events/{eventId}/ticket-types` | Owning organizer | Creates a `TicketType` (GA or reserved-seating). |
| `GET /v1/events/{eventId}/ticket-types` | Public if event is published | Lists the event's `TicketType`s with live `quantity_available`. |
| `GET /v1/ticket-types/{ticketTypeId}` | Public if parent event is published | Returns one `TicketType`. |
| `PATCH /v1/ticket-types/{ticketTypeId}` | Owning organizer | Update price, quantity, sale window, per-order limit. |

### 3.5 Cart & Checkout

*(`Cart`, `Hold`, requirements 4.3, 4.5)*

| Method & Path | Who | Description |
|---|---|---|
| `POST /v1/carts` | Authenticated (buyer) | Creates an empty `Cart` for the caller. |
| `GET /v1/carts/{cartId}` | Owning buyer | Returns the cart with its items, each item's `Hold` and `expires_at`, and running total. |
| `POST /v1/carts/{cartId}/items` | Owning buyer | Body: `{ ticket_type_id, seat_id? , quantity? }` (`seat_id` for reserved seating, `quantity` for GA). Places a `Hold` (default 10–15 min, per event config) and adds the item. `409` if the seat/quantity is no longer available. |
| `DELETE /v1/carts/{cartId}/items/{itemId}` | Owning buyer | Removes the item and releases its `Hold`. |
| `POST /v1/carts/{cartId}/checkout` | Owning buyer — **requires `Idempotency-Key`** | Body: `{ payment_method_token }` (from the payment gateway's client-side tokenization — the API never sees raw card data, 5.3). On success: charges via `Payment`, creates the `Order`, issues one `Ticket` per item, and converts each `Hold` into a sale. `201` → `Order` (with nested `Ticket`s). `402` on payment failure; the cart and its holds are left intact so the buyer can retry. `410` if a hold expired before checkout completed. |

### 3.6 Orders & Tickets

*(`Order`, `Payment`, `Ticket`, `TicketArtifact`, requirements 4.5, 4.6, 4.10)*

| Method & Path | Who | Description |
|---|---|---|
| `GET /v1/orders/{orderId}` | Owning buyer, the event's organizer, or admin | Returns `Order`, including `payee_type`/`payee_id` and its `Payment` status. |
| `GET /v1/users/me/orders` | Authenticated (buyer) | Lists the caller's own orders. |
| `GET /v1/events/{eventId}/orders` | Owning organizer or admin | Lists orders for one event (for the organizer's sales view — full analytics deferred to a later pass). |
| `GET /v1/tickets/{ticketId}` | Owning buyer, the event's organizer, or admin | Returns `Ticket` — status, seat/ticket-type, ticket_number. **Never returns the raw scannable credential** (5.3); use the artifact endpoint below to get a renderable ticket. |
| `GET /v1/orders/{orderId}/tickets` | Owning buyer, the event's organizer, or admin | Lists all tickets on an order. |
| `GET /v1/tickets/{ticketId}/artifact?format=digital\|physical` | Owning buyer | Returns (or redirects to) the rendered `TicketArtifact` — the actual PDF/image carrying the QR/barcode and the visible `ticket_number`, generated from the event's `TicketTemplate` (4.10). Regenerated on demand if the template changed since last render. |

### 3.7 Check-in & Scanning

*(`CheckInConfig`, `ScannerDevice`, `FallbackScanRecord`, `CheckInRecord`, requirements 4.11)*

| Method & Path | Who | Description |
|---|---|---|
| `GET /v1/events/{eventId}/check-in-config` | Owning organizer, or an authorized `ScannerDevice` for that event | Returns `CheckInConfig`: `mode` (`standard`\|`pure_offline`), `offline_fallback_expiry_seconds` (default 300). |
| `PATCH /v1/events/{eventId}/check-in-config` | Owning organizer | Sets `mode` and, for standard mode, the offline-fallback expiry. Switching modes with an active device holding unsynced scans is flagged as a warning in the response, not blocked (see requirements 4.11 open question). |
| `POST /v1/events/{eventId}/scanner-devices` | Owning organizer | Authorizes a new `ScannerDevice`. In `pure_offline` mode, only one may be active at a time — creating a second without revoking the first is the **override** flow (4.11) and requires `force_replace: true` in the body. `201` → device record **including its one-time-visible credential**. |
| `DELETE /v1/scanner-devices/{deviceId}` | Owning organizer | Revokes a device's credential immediately; further validation/pre-fetch calls from it are rejected. |
| `GET /v1/scanner-devices/{deviceId}/dataset` | The device itself (its credential) | Returns the pre-fetched ticket dataset for offline validation. Allowed for the sole device in `pure_offline` mode (no expiry) and for any device in `standard` mode (refreshed continuously; the client is responsible for treating it as stale past `offline_fallback_expiry_seconds` since its last successful call). `403` in `standard` mode if... *(not applicable — standard-mode devices are always allowed a fallback file by design)*. |
| `POST /v1/check-in/validate` | An authorized `ScannerDevice` | Body: `{ credential, device_id }` (the scanned QR/barcode payload). Checks and marks the ticket used in real time. `200` → `{ result: "valid"\|"duplicate"\|"invalid"\|"wrong_event", ticket_summary? }`. This is the one endpoint offline-fallback logic on the client is standing in for when connectivity drops. |
| `POST /v1/check-in/fallback-scans` | An event's designated fallback recorder (pure offline mode only) | Body: `{ event_id, scans: [{ raw_credential, captured_at }] }` — a batch upload of blind scans captured while the sole `ScannerDevice` was down. Each becomes a `FallbackScanRecord`, reconciled against current ticket state and resolved into a `CheckInRecord`. `200` → per-scan results, including any flagged as duplicate (4.11 — these are surfaced for the organizer's review, not auto-resolved). |
| `GET /v1/tickets/{ticketId}/check-in-records` | Owning organizer or admin | Audit trail of every scan attempt (online, offline-fallback, or reconciled) against one ticket. |

## 4. Deferred to a later pass

These resources exist in `resources.md` but don't have endpoints yet — designed once the core flow above is settled: `PromoCode`, `WaitlistEntry`, `ResalePolicy`/`ResaleListing`, `RefundPolicy`/`Refund`/`Payout` (beyond the cancellation trigger noted in 3.4), `TicketTransfer`, `TicketTemplate` authoring (organizer-side template CRUD — this pass only *consumes* a template via the artifact endpoint), `Notification`, `Dispute`, `AuditLogEntry`, `OrganizationMember` invites, and analytics/reporting endpoints.

## 5. Open items for the next pass

- Exact seat-map authoring endpoints (how an organizer defines sections/rows/seats) weren't designed here — 3.4 only covers reading a seat map, since GA and reads were the priority for "core."
- Whether `Venue` should eventually become a shared, platform-curated directory (so two organizers booking the same physical venue reuse one record) instead of each organization maintaining its own copy — deferred, not a v1 requirement (see 3.3).
- `POST /v1/check-in/fallback-scans`'s reconciliation result format is sketched, not finalized — depends on how duplicate-flagging is meant to surface to the organizer (an open question already flagged in `requirements.md`).
- Webhook/callback endpoints for the payment gateway (e.g. async payment confirmation, disputes) aren't modeled yet — `POST /v1/carts/{cartId}/checkout` currently assumes a synchronous gateway response, which may not hold for every payment method.
