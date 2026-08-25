# Event Ticketing API — Resource Identification

**Version:** 1.0 (Draft)
**Date:** 2026-08-25
**Status:** For review
**Based on:** `requirements.md` v1.4 (Approved)

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
| **Event** | A single event listing. | id, title, description, category, start/end datetime, timezone, images, status | Belongs to Organization; has a Venue | 4.2 |
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
| **Order** | The record of a completed (or attempted) purchase. | id, buyer_id, status (pending/paid/cancelled/refunded/partially refunded), total, created_at | Belongs to User (buyer); has many Tickets | 4.5 |
| **Payment** | A payment-gateway transaction tied to an Order. | id, order_id, gateway_ref, amount, status | Belongs to Order | 4.6 |
| **RefundPolicy** | Organizer-defined refund rules for an event. | id, event_id, window/rule definition | Belongs to Event | 4.6 |
| **Refund** | A full or partial refund against an Order/Ticket. | id, order_id, amount, reason, initiated_by, status | Belongs to Order | 4.6 |
| **Payout** | Scheduled transfer of net sales to an Organization. | id, organization_id, gross, fees, net, period, status | Belongs to Organization | 4.6 |
| **PromoCode** | A discount code scoped to an event/organizer. | id, code, discount type/value, applicable ticket types, usage limits, validity window | Belongs to Event | 4.7 |

### 2.5 Ticketing

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **Ticket** | A single issued ticket — the unit that gets checked in. | id, order_id, ticket_type_id, seat_id (nullable), owner_id, ticket_number (`PREFIX-XXXXXX`), credential (signed QR/barcode), status (valid/used/transferred/refunded/cancelled) | Belongs to Order and TicketType; optionally a Seat | 4.5, 4.9, 4.10 |
| **TicketTemplate** | An organizer-defined layout for rendering physical/digital tickets. | id, event_id (or ticket_type_id), format (physical/digital), branding fields, field layout | Belongs to Event or TicketType | 4.10 |
| **TicketArtifact** | A generated, deliverable instance of a ticket (the actual PDF/image), rendered from a Ticket + TicketTemplate. | id, ticket_id, format (physical/digital), file ref | Belongs to Ticket | 4.10 |
| **TicketTransfer** | A record of ownership change between Users. | id, ticket_id, from_user_id, to_user_id, transferred_at | Belongs to Ticket | 4.9 |
| **ResalePolicy** | Organizer-controlled resale setting for an event. | id, event_id, enabled, price_cap_rule | Belongs to Event | 4.9 |
| **ResaleListing** | A ticket a current owner has listed for resale. | id, ticket_id, asking_price, status | Belongs to Ticket | 4.9 |
| **WaitlistEntry** | An attendee waiting for sold-out inventory. | id, event_id or ticket_type_id, user_id, position, notified_at, offer_expires_at | Belongs to Event/TicketType and User | 4.8 |

### 2.6 Check-in & Scanning

| Resource | Description | Key attributes | Owned by / relates to | Source |
|---|---|---|---|---|
| **CheckInConfig** | Per-event check-in policy: mode and its parameters. | id, event_id, mode (standard / pure_offline), offline_fallback_expiry (default 5 min) | Belongs to Event | 4.11 |
| **ScannerDevice** | An authorized scanning device/credential for an event. | id, event_id, device_label, credential, status (active/revoked), role (primary / fallback-recorder) | Belongs to Event (via CheckInConfig) | 4.11 |
| **CheckInRecord** | The result of one scan attempt. | id, ticket_id, device_id, scanned_at, result (valid/duplicate/invalid/wrong_event), sync_source (online / offline_fallback / blind_recorded) | Belongs to Ticket and ScannerDevice | 4.11 |

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

## 4. Open Questions Carried From Requirements

These resources make a few requirements-doc open questions concrete and worth resolving before schema design:

- Whether `ScannerDevice` needs a distinct "fallback-recorder" role (pure offline mode) with a different data shape than a normal device — likely yes, since it only ever produces blind `CheckInRecord`s, never a pre-fetch.
- Whether `TicketNumber` uniqueness (from requirements 4.10) is enforced at the `Event` scope or globally — affects whether `Ticket.ticket_number` needs a global unique index or a composite one with `event_id`.
- Whether `ResaleListing` is a first-class resource in v1 or just a status flag on `Ticket` — requirements only specify organizer-controlled enable/cap, not a full marketplace UX, so a lighter model may be enough.

## 5. Next Step

With resources identified, the natural next step is endpoint design (routes, methods, request/response shapes) followed by the actual data schema (field types, constraints, indexes).
