# Event Ticketing API — Development Roadmap

**Version:** 1.0
**Date:** 2026-09-10
**Derived from:** `requirements.md`, `business-rules.md`, `use-cases.md`,
`resources.md`, `event-ticketing-api-erd.md`, and `openapi.yaml`, checked
against the actual code in `src/main/java` as of this date.

Ordered by-feature (vertical slice), not by-endpoint: each phase only
depends on entities/features already built in an earlier phase — e.g.
`TicketType` needs a real `Event`, `Cart` needs a real `TicketType`,
`Ticket` needs a real `Cart`/checkout, and so on. Within a phase, items are
also roughly in build order. Status markers:

- ✅ Done
- ⚠️ Partially done / done but buggy — needs rework, not a fresh build
- ⬜ Not started

## Phase 0 — Fix what's already built before building on top of it

Small, cross-cutting fixes. Doing these first means later phases aren't
built on top of known-broken behavior.

- ⬜ Fix `EventSpecification.titleContains` (`root.get(keyword)` →
  `root.get("title")`) and `startsAfter`/`startBefore` (both currently use
  the non-existent property `"start"` instead of `"startAt"`, and
  `startBefore` duplicates `startsAfter`'s comparator instead of using
  `lessThanOrEqualTo`) — `GET /events` currently throws on `keyword`,
  `startsAfter`, or `startsBefore`. (`BR-SEARCH-001`)
- ⬜ Fix `EventServiceImpl.listEvents` hardcoding `EventStatus.DRAFT` —
  published events can never be listed today.
- ⬜ Filter soft-deleted rows (`deletedAt != null`) out of `findAll`/
  `listEvents` for both `Event` and `User`.
- ⬜ Add a real `AuditorAware` bean so `created_by`/`updated_by` reflect the
  authenticated caller instead of the hardcoded `"System Audit"` default —
  needed before any phase below can produce meaningful audit trails.

## Phase 1 — Organization & Access ✅⚠️ (auth done, org model not started)

Almost everything downstream references an `Organization`, so this unlocks
the rest of the build. Auth itself (register/login/refresh/logout, JWT,
role-gated endpoints) is already done — this phase is specifically the
organization/membership layer that doesn't exist yet.

- ⬜ `Organization` entity + migration (`id`, `name`, `status`,
  `documents`, `owner_id`, audit columns). (`BR-ORG-*`)
- ⬜ `POST /api/v1/organizations` — submit an application (`UC-ORG-01`).
- ⬜ Admin review: `GET /organizations` (pending queue), `POST
  /organizations/{id}/approve`, `POST /organizations/{id}/reject`
  (`UC-ADMIN-01`).
- ⬜ `OrganizationMember` entity — **reconcile with the existing `User.role`
  single-enum column**: decide whether `ORGANIZER`/`CHECK_IN_STAFF`/`OWNER`
  move out of `User.role` entirely into org-scoped, combinable
  `OrganizationMember.roles`, leaving `User.role` to just distinguish
  `CUSTOMER`/`ADMIN` at the platform level. This is the biggest schema
  change in the whole roadmap — the current single global `Role` enum
  can't represent "owner who's also organizer and also scanner"
  (`BR-AUTH-007`/`008`), so it has to be resolved before anything
  org-role-scoped is built on top of it.
- ⬜ `POST /organizations/{orgId}/members` (assign/self-assign role),
  `GET /organizations/{orgId}/members` (`UC-OWNER-01`, `UC-OWNER-02`).
- ⬜ Update `SecurityConfig`/`JwtAuthenticationFilter` so authorization can
  check org membership + role, not just the flat global role it checks
  today.

## Phase 2 — Venue

Small, and `Event` needs it before its own hardening phase below.

- ⬜ Real `Venue` entity (`event/entity/Venue.java` is currently an empty,
  non-`@Entity` stub) — `id`, `organization_id`, `name`, `address`,
  `latitude`, `longitude`, audit columns.
- ⬜ `POST/GET /organizations/{orgId}/venues`, `GET/PATCH
  /venues/{venueId}`.

## Phase 3 — Event hardening

- ⬜ Wire `Event.organizationId`/`venue` to real FKs (`organization_id`,
  `venue_id`) — today `organizationId` is an unconstrained UUID and `venue`
  is a bare `int`, both because nothing real exists on the other end yet.
- ⬜ Replace client-supplied `ticketPrefix` with API-generated: a random
  3-letter (A–Z) code, reserved unique platform-wide, assigned once at
  event creation (`BR-TICKET-003`/`004`) — currently the client just sends
  arbitrary text.
- ⬜ `images[]` array (currently a single `image: byte[]`).
- ⬜ `POST /events/{id}/publish`, `POST /events/{id}/cancel` lifecycle
  transitions (`UC-EVENT-03`) — cancel triggers the refund workflow from
  Phase 8, so full cancel behavior can't be finished until that phase
  exists; the status transition itself can land now.

## Phase 4 — Ticket Types & Inventory

- ⬜ `TicketType` entity + `POST/GET /events/{id}/ticket-types`,
  `GET/PATCH /ticket-types/{id}` (`BR-EVENT-003`).
- ⬜ `SeatMap`/`Seat` entities + `GET /events/{id}/seatmap` (reserved
  seating only — GA-only events skip this entirely, per `resources.md`
  §3). (`BR-INV-001`/`002`)

## Phase 5 — Cart, Promo Codes & Checkout

The core purchase flow — depends on `TicketType` existing.

- ⬜ `Cart`/`CartItem` entities, `POST /carts`, `GET /carts/{id}`,
  `POST/DELETE /carts/{id}/items`. Include the `Cart.status`
  (`active`/`converted`/`abandoned`) field flagged during ERD review, and
  decide the hold-expiry mechanism (lazy, filtered at query time — see the
  chat discussion on this) before relying on `BR-INV-005`/`006`'s
  never-oversell guarantee.
- ⬜ `PromoCode` entity + `POST/GET /events/{id}/promo-codes`,
  `POST/DELETE /carts/{id}/promo-code` (`BR-PROMO-*`).
- ⬜ `Order`/`Payment` entities + `POST /carts/{id}/checkout` — payment
  gateway integration, `Idempotency-Key` handling (`BR-NFR-008`), and the
  `payee_type`/`payee_id` split that later lets a resale purchase (Phase 7)
  reuse this same endpoint's machinery.

## Phase 6 — Tickets, Templates & Artifacts

What checkout actually issues.

- ⬜ `Ticket` entity — issued per cart item on successful checkout, with
  the signed QR/barcode credential and `ticket_number` (`BR-TICKET-001`/
  `002`/`005`).
- ⬜ `TicketTemplate` entity + `POST/GET /events/{id}/ticket-templates`,
  `PATCH /ticket-templates/{id}` (`BR-TICKET-007`).
- ⬜ `TicketArtifact` generation + `GET /tickets/{id}/artifact` — depends
  on `TicketTemplate` existing to render against.
- ⬜ `GET /orders/{id}`, `GET /orders/{id}/tickets`, `GET /tickets/{id}`,
  `GET /users/me/orders`, `GET /events/{id}/orders`.

## Phase 7 — Transfer & Resale

Depends on `Ticket` existing.

- ⬜ `TicketTransfer` entity + `POST /tickets/{id}/transfer` (`BR-TRANSFER-001`/
  `002`/`005`).
- ⬜ `ResalePolicy` entity + `GET/PATCH /events/{id}/resale-policy`.
- ⬜ `ResaleListing` entity + `POST /tickets/{id}/resale-listings`,
  `DELETE /resale-listings/{id}`, `GET /events/{id}/resale-listings`,
  `POST /resale-listings/{id}/purchase` (reuses the Phase 5 checkout
  machinery with the seller as payee) (`BR-TRANSFER-003`/`004`).

## Phase 8 — Refunds & Payouts

Depends on `Order`/`Payment` existing. Also finishes Phase 3's event-cancel
refund trigger.

- ⬜ `RefundPolicy` entity + `GET/PATCH /events/{id}/refund-policy`
  (`BR-PAY-004`).
- ⬜ `Refund` entity + `POST/GET /orders/{id}/refunds` (`BR-PAY-002`/
  `003`).
- ⬜ Wire event cancellation (Phase 3) to actually trigger refunds for
  every ticket holder (`BR-PAY-005`).
- ⬜ `Payout` entity + `GET /organizations/{id}/payouts` (read-only;
  payouts are system-generated on a schedule, per the spec) (`BR-PAY-006`).

## Phase 9 — Waitlist

Depends on `Event`/`TicketType` existing; the "notify when inventory frees
up" half depends on Phase 11's notification trigger existing to be
meaningful (the join/position tracking can land without it).

- ⬜ `WaitlistEntry` entity + `POST /events/{id}/waitlist`, `GET
  /users/me/waitlist-entries` (`BR-WAIT-001`–`003`).
- ⬜ The active job/trigger that notifies waitlisted users in order when a
  hold/cancellation frees inventory — this is the same "needs an active
  trigger, not just lazy expiry" mechanism flagged in the cart-expiry
  discussion.

## Phase 10 — Check-in & Scanning

Depends on `Ticket` existing.

- ⬜ `CheckInConfig` entity + `GET/PATCH /events/{id}/check-in-config`
  (`BR-CHECKIN-004`).
- ⬜ `ScannerDevice` entity + `POST /events/{id}/scanner-devices`,
  `DELETE /scanner-devices/{id}`, `GET /scanner-devices/{id}/dataset`
  (`BR-CHECKIN-005`–`008`).
- ⬜ `CheckInRecord` entity + `POST /check-in/validate` (`BR-CHECKIN-001`–
  `003`).
- ⬜ `FallbackScanRecord` entity + `POST /check-in/fallback-scans`
  (pure-offline mode only) (`BR-CHECKIN-009`/`010`), `GET
  /tickets/{id}/check-in-records`.

## Phase 11 — Notifications

Cross-cutting — each trigger point (order confirmation, refund
confirmation, etc.) technically depends on the feature that fires it
already existing, so this is naturally built incrementally alongside
Phases 5–10 rather than all at once. Listed here as the point where the
underlying delivery mechanism (email at minimum) should exist.

- ⬜ `Notification` entity + delivery (email at minimum, per
  `requirements.md` §4.12) + `GET /users/me/notifications`.
- ⬜ Wire trigger points as each feature above ships: order confirmation
  (Phase 5), refund confirmation (Phase 8), waitlist availability (Phase
  9), event change/cancellation (Phase 3/8).

## Phase 12 — Disputes & Admin Moderation

- ⬜ `Dispute` entity + `POST /disputes`, `GET /disputes`, `GET/PATCH
  /disputes/{id}` (`BR-ADMIN-003`).
- ⬜ Admin suspend/remove action for an organizer, event, or user account
  (`BR-ADMIN-002`) — beyond the generic soft-delete already built for
  users, this needs a reason/audit trail per action.

## Phase 13 — Audit Log

Ideally wired into each sensitive action as it's built (refunds, role
changes, cancellations, admin interventions — `BR-NFR-005`) rather than
retrofitted at the end, but listed last since it has no functional
dependents of its own.

- ⬜ `AuditLogEntry` entity + `GET /audit-log` (admin only).
- ⬜ Wire writes into: refund issuance (Phase 8), role assignment (Phase
  1), event cancellation (Phase 3), dispute resolution (Phase 12),
  account suspension (Phase 12).

## Phase 14 — Analytics

Depends on `Order`/`Ticket`/`Payout` data existing to aggregate — build
last since there's nothing to report on before then.

- ⬜ `GET /events/{id}/analytics` (`BR-ANALYTICS-001`).
- ⬜ `GET /analytics/platform` (`BR-ANALYTICS-002`).

---

## Not on this roadmap (still genuinely open)

Per `requirements.md` §7 — these need a decision before they can be
scheduled, not just implementation effort: target markets/currency/tax
handling, expected v1 launch scale (affects the inventory-locking design in
Phase 4/5), resale opt-in-vs-opt-out default (Phase 7), mid-event check-in
mode switching (Phase 10), and whether the visible ticket number should
ever work as a manual check-in fallback (Phase 10).
