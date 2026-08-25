# Event Ticketing API — Resource Identification

**Version:** 1.4 (Draft)
**Date:** 2026-08-25
**Status:** For review
**Based on:** `requirements.md` v1.6

**Revision history:**
- v1.0 initial resource map derived from requirements v1.4.
- v1.1 resolves two of v1.0's open questions: `ScannerDevice`'s fallback role is now a distinct resource (`FallbackScanRecord`, not a role flag on the same shape), and `Ticket.ticket_number` uniqueness is confirmed global — achieved via an event-derived prefix rather than a platform-wide index.
- v1.2 confirms `ResaleListing` as a first-class resource with its own lifecycle (listed/sold/cancelled/expired), and models a sale as running through a real `Order`/`Payment`/`TicketTransfer` rather than a direct handoff between buyer and seller.
- v1.3 pins down the ticket-number prefix mechanism: `Event` gets a new `ticket_number_prefix` attribute — a random 3-letter code reserved once at event creation, unique across all events, used only to seed `Ticket.ticket_number` (not a general-purpose event code).
- v1.4 confirms resale purchases reuse `Order`/`Payment` rather than getting their own shape: `Order` gains `payee_type`/`payee_id` so the payee is the `Organization` on a primary purchase or the reselling `User` on a resale purchase.

## 1. Purpose

This document identifies the API's core resources (entities) derived from the approved requirements. It's the bridge between "what the system must do" (requirements) and "what the system exposes" (endpoints/data model, next step). Each resource lists its purpose, key attributes worth knowing at this stage (not a full schema), its parent/owning resource, and the requirement section it comes from. Field types, exact endpoints, and database design are left to the architecture/design phase.

## 2. Resource Map (by domain)

### 2.1 Identity & Access

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **User** | A single login-capable account. Roles are additive, not exclusive — one User can be an attendee on one event and hold organizer access on another. | id, name, email, auth credentials, created_at | — | 4.1 |
| **Organization** | The business/organizer entity that owns events. Verified before publishing or receiving payouts. | id, name, verification status, payout account ref | Has many Users (via OrganizationMember) | 4.1, 4.2 |
| **OrganizationMember** | Links a User to an Organization with a scoped role (owner, co-organizer, check-in staff). | user_id, organization_id, role, invited_at | Belongs to Organization and User | 4.1 |
| **AdminAccount** | Platform-staff access, distinct from any Organization. | user_id, admin scope | Belongs to User | 3, 4.13 |

### 2.2 Event & Catalog

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Event** | A single event listing. | id, title, description, category, start/end datetime, timezone, images, status, ticket_number_prefix (random 3 letters, assigned once at creation, reserved unique across all events — used only to seed `Ticket.ticket_number`, not a general-purpose event code) | Belongs to Organization; has a Venue | 4.2, 4.10 |
| **Venue** | A physical (or virtual) location, reusable across events, that a SeatMap is defined against. | id, name, address | Referenced by Event | 4.3 |
| **SeatMap** | The section/row/seat layout for reserved-seating events. | id, sections[] | Belongs to Venue (reusable) or Event | 4.3 |
| **Seat** | A single addressable seat within a SeatMap. | id, section, row, seat_number, status (available/held/sold) | Belongs to SeatMap | 4.3 |
| **TicketType** | A purchasable category for an event: GA or reserved-seating tier. | id, name, price, currency, quantity, sale window, per-order limit | Belongs to Event | 4.2, 4.3 |

### 2.3 Inventory & Cart

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Hold** | A temporary reservation of a seat or a GA quantity during active checkout. | id, seat_id or (ticket_type_id + quantity), expires_at (default 10–15 min) | Belongs to Cart | 4.3 |
| **Cart** | A buyer's in-progress selection prior to checkout. | id, buyer_id, items[], applied_promo_code | Belongs to User (buyer) | 4.5 |

### 2.4 Commerce

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Order** | The record of a completed (or attempted) purchase — reused for both a primary purchase from the organizer and a resale purchase from another attendee. `payee_type`/`payee_id` say who gets paid: the `Organization` on a primary purchase, or the reselling `User` on a resale purchase (see `ResaleListing`, 2.5). | id, buyer_id, payee_type (organization / user), payee_id, status (pending/paid/cancelled/refunded/partially refunded), total, created_at | Belongs to User (buyer); has many Tickets | 4.5, 4.9 |
| **Payment** | A payment-gateway transaction tied to an Order — same shape regardless of whether the order's payee is the organizer or a reselling attendee. | id, order_id, gateway_ref, amount, status | Belongs to Order | 4.6, 4.9 |
| **RefundPolicy** | Organizer-defined refund rules for an event. | id, event_id, window/rule definition | Belongs to Event | 4.6 |
| **Refund** | A full or partial refund against an Order/Ticket. | id, order_id, amount, reason, initiated_by, status | Belongs to Order | 4.6 |
| **Payout** | Scheduled transfer of net sales to an Organization. | id, organization_id, gross, fees, net, period, status | Belongs to Organization | 4.6 |
| **PromoCode** | A discount code scoped to an event/organizer. | id, code, discount type/value, applicable ticket types, usage limits, validity window | Belongs to Event | 4.7 |

### 2.5 Ticketing

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Ticket** | A single issued ticket — the unit that gets checked in. | id, order_id, ticket_type_id, seat_id (nullable), owner_id, ticket_number (`PREFIX-XXXXXX`, globally unique — prefix copied from the event's reserved `ticket_number_prefix`, suffix random and unique within the event), credential (signed QR/barcode), status (valid/used/transferred/refunded/cancelled) | Belongs to Order and TicketType; optionally a Seat | 4.5, 4.9, 4.10 |
| **TicketTemplate** | An organizer-defined layout for rendering physical/digital tickets. | id, event_id (or ticket_type_id), format (physical/digital), branding fields, field layout | Belongs to Event or TicketType | 4.10 |
| **TicketArtifact** | A generated, deliverable instance of a ticket (the actual PDF/image), rendered from a Ticket + TicketTemplate. | id, ticket_id, format (physical/digital), file ref | Belongs to Ticket | 4.10 |
| **TicketTransfer** | A record of ownership change between Users — created either by a direct transfer (4.9) or automatically when a `ResaleListing` sells. | id, ticket_id, from_user_id, to_user_id, transferred_at, source (direct_transfer / resale) | Belongs to Ticket | 4.9 |
| **ResalePolicy** | Organizer-controlled resale setting for an event: whether resale is allowed at all, and any price cap. | id, event_id, enabled, price_cap_rule | Belongs to Event | 4.9 |
| **ResaleListing** *(first-class resource)* | A current owner's active offer to resell one ticket. Has its own lifecycle independent of the ticket's other history — a ticket can be listed, delisted, and relisted over time, and each attempt is its own record. | id, ticket_id, event_id (denormalized for browse/search), seller_id, asking_price, status (active / sold / cancelled / expired), listed_at, resolved_at (nullable), buyer_order_id (nullable, set once sold) | Belongs to Ticket; validated against the event's ResalePolicy at creation; produces an Order (see 2.4) and a TicketTransfer when sold | 4.9 |
| **WaitlistEntry** | An attendee waiting for sold-out inventory. | id, event_id or ticket_type_id, user_id, position, notified_at, offer_expires_at | Belongs to Event/TicketType and User | 4.8 |

### 2.6 Check-in & Scanning

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **CheckInConfig** | Per-event check-in policy: mode and its parameters. | id, event_id, mode (standard / pure_offline), offline_fallback_expiry (default 5 min) | Belongs to Event | 4.11 |
| **ScannerDevice** | An authorized, pre-fetch-capable scanning device — the "real" device in either mode: the sole device in pure offline mode, or any of the N devices in standard mode. Always able to produce a `CheckInRecord` with a known result at scan time (online, or offline within the fallback-file window). | id, event_id, device_label, credential, status (active/revoked) | Belongs to Event (via CheckInConfig) | 4.11 |
| **FallbackScanRecord** | *Distinct from `ScannerDevice`* — the last-resort stand-in used only in pure offline mode after the sole `ScannerDevice` fails. It was never issued the pre-fetched dataset, so it can't determine a result at scan time (it can verify the credential's signature to reject obvious forgeries, but can't know if a ticket's already been used elsewhere). It just captures the raw scan; the record starts unresolved and is reconciled once synced. | id, event_id, raw_credential, captured_at, synced_at (nullable until upload), reconciled_result (nullable), reconciled_ticket_id (nullable) | Belongs to Event; resolves into a Ticket/CheckInRecord once synced | 4.11 |
| **CheckInRecord** | The final, known result of a scan — either produced immediately by a `ScannerDevice`, or produced when a `FallbackScanRecord` is reconciled after sync. | id, ticket_id, source_type (scanner_device / reconciled_fallback), source_id, scanned_at, result (valid/duplicate/invalid/wrong_event) | Belongs to Ticket; relates to ScannerDevice or FallbackScanRecord | 4.11 |

### 2.7 Engagement & Admin

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Notification** | A record of a triggered notification and its delivery status. | id, user_id, type, channel, related_object, sent_at, status | Belongs to User | 4.12 |
| **Dispute** | An admin-tracked dispute (fraud report, chargeback). | id, order_id or ticket_id, raised_by, status, resolution | Relates to Order/Ticket | 4.13 |
| **AuditLogEntry** | An immutable record of a sensitive action. | id, actor_id, action, target, timestamp | Relates to whatever it logged | 5.3 |

## 3. Notable Relationships

- `Organization` → `Event` → `TicketType` → `Ticket` is the core ownership chain.
- `Event` → `SeatMap` → `Seat` only applies to reserved-seating events; GA-only events skip SeatMap/Seat entirely.
- `Order` → `Payment` / `Refund` are 1-to-many (a refund can be partial, and an order can have more than one payment attempt).
- `Ticket` is the join point for almost everything downstream of purchase: transfers, resale, templates/artifacts, and check-in all key off `ticket_id`.
- `CheckInConfig` governs how many `ScannerDevice` records an event may have and what each is allowed to do (pre-fetch/offline vs. online-only, per requirements 4.11).
- `FallbackScanRecord` only ever exists under `CheckInConfig.mode = pure_offline`, and only after the event's single `ScannerDevice` has been marked failed/replaced — it's the exception path, not a peer of `ScannerDevice`.
- `Ticket.ticket_number`'s prefix is copied from `Event.ticket_number_prefix` (a random 3-letter code reserved once, at event creation) — the relationship (Ticket → Event, via TicketType) is what guarantees the number's global uniqueness without a platform-wide lookup.
- A `Ticket` may have at most one *active* `ResaleListing` at a time — it must resolve (sold/cancelled/expired) before the ticket can be relisted, since a ticket is a single admission unit and can't be sold to two buyers at once.
- When a `ResaleListing` sells, the platform runs it like a mini-checkout rather than a direct handoff between the two parties: it creates an `Order`/`Payment` (so the organizer's price cap and any platform fee are actually enforced and collected, not just advisory), then on payment success creates a `TicketTransfer` (source: resale) and flips `Ticket.owner_id` — mirroring how a primary purchase resolves, just with the seller as the payee instead of the organizer.

## 4. Open Questions Carried From Requirements

- ~~Whether `ScannerDevice` needs a distinct "fallback-recorder" role or data shape.~~ **Resolved:** it's a separate resource, `FallbackScanRecord` — see 2.6.
- ~~Whether ticket number uniqueness is per-event or global.~~ **Resolved:** global, via an event-derived prefix — see 2.5 and 3.
- ~~Whether `ResaleListing` is a first-class resource or a status flag on `Ticket`.~~ **Resolved:** first-class resource — see 2.5 and 3. It carries its own lifecycle (listed/sold/cancelled/expired) independent of the ticket, and a sale runs through a real `Order`/`Payment`/`TicketTransfer` rather than a direct handoff.
- ~~How exactly the event-derived ticket-number prefix is generated.~~ **Resolved:** a random 3-letter (A–Z) code, generated once and reserved at event creation, unique across all events — see `Event.ticket_number_prefix` in 2.2. Not a deterministic encoding of the event's own ID, and not reused as a general-purpose event code elsewhere in the API.
- ~~Whether a resale purchase reuses `Order`/`Payment` or needs its own shape.~~ **Resolved:** reused — `Order` gains `payee_type`/`payee_id` so the same resource works for both cases: payee is the `Organization` on a primary purchase, and the reselling `User` on a resale purchase. See 2.4.

## 5. Next Step

With resources identified, the natural next step is endpoint design (routes, methods, request/response shapes) followed by the actual data schema (field types, constraints, indexes).
