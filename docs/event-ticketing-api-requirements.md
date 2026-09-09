# Event Ticketing API — Requirements Document

**Version:** 1.0
**Date:** 2026-09-08
**Status:** Draft — compiled into a single V1 baseline from the incremental v1.1–v1.6 drafts; needs approval.

## 1. Purpose

This document defines the functional and non-functional requirements for an Event Ticketing API: a platform that lets multiple independent organizers create and sell tickets for their own events, while attendees discover events, purchase tickets, and check in at the venue. It is the first artifact in the step-by-step build of the Event Ticketing API and is meant to be the reference point for the architecture and design work that follows.

## 2. Scope

The system is a **multi-organizer marketplace**, comparable in shape to Eventbrite or Ticketmaster: any number of organizers can onboard, list events, define ticket types, and sell to the public through a shared platform. The API is the system of record for events, inventory, orders, payments, and check-in — it is channel-agnostic and expected to power a web app, a mobile app, and organizer-facing tools.

**This project delivers the API only.** Client applications that would consume it — the public web/buying app, an organizer console (including any ticket-layout design UI), and a mobile check-in/scanning app — are explicitly out of scope for this project; they are referenced here only to justify why certain API capabilities (e.g. storing a ticket template, exposing a validation endpoint) are required.

### 2.1 In scope (v1)

- Event and ticket-type creation and management by organizers
- Reserved seating (seat maps) and general admission / tiered ticket inventory
- Public event search and discovery
- Cart, checkout, and payment processing, including refunds and cancellations
- Promo codes and discounts
- Waitlists for sold-out events
- Ticket transfer between attendees and organizer-approved resale
- API support for organizer-defined ticket layouts/templates, and generation of physical (print) and digital ticket artifacts from them
- Digital ticket delivery via in-app view, email, and screenshot/downloaded image; physical ticket printing
- A validation API for at-door check-in/scanning (consumed by a separate mobile app — see 2.2/6)
- Notifications (order confirmation, reminders, event changes)
- Platform administration (organizer approval, dispute handling, moderation)
- Basic sales analytics and reporting for organizers and admins

### 2.2 Out of scope (v1)

See section 6.

## 3. User Roles

| Role | Description |
|---|---|
| **Attendee (buyer)** | Browses events, purchases tickets, manages their own orders and tickets. |
| **Event organizer** | Creates and manages events, ticket types, pricing, and promo codes; views sales and analytics for their own events. |
| **Organization owner** | The user whose organization application was approved by an admin; has full authority over that organization, including assigning other users as organizer or check-in staff/scanner for it. |
| **Check-in staff / scanner** | Validates tickets/QR codes at the venue entrance, typically via a mobile scanning app (separate client, not built in this project); scoped to a single event, granted access by the organizer. |
| **Platform admin** | Manages the platform: approves organizers, moderates events, resolves disputes, has cross-organizer visibility. |

Roles are not mutually exclusive at the account level — a single account may hold an "organizer" role on some events and be an "attendee" on others. Within one organization, roles are independently combinable rather than a strict hierarchy: an organization owner may also hold the organizer role, the scanner/check-in-staff role, or both, for that same organization; likewise an organizer may also be a scanner. A user may assign **themselves** any additional role they're entitled to within their own organization without a separate approval step — self-assignment is unrestricted. Assigning a role to a *different* user still requires the organization owner (4.1).

## 4. Functional Requirements

### 4.1 Identity & Access

- The API must support account registration and authentication for every role, with organizer and check-in-staff accounts scoped to the events/organizations they belong to.
- The API must support role-based access control (RBAC): an organizer can only manage their own events; check-in staff can only validate tickets for events they're assigned to; admins have platform-wide access.
- The API must support token-based authentication (e.g. OAuth 2.0 / JWT) suitable for first-party web/mobile clients and third-party integrations.
- An organization's owner must be able to assign other registered users scoped roles within that organization — organizer or scanner/check-in staff — each limited to that organization's own events; only the owner (not an assigned organizer) may make these assignments for other users.
- A user may assign themselves any additional role they're entitled to within their own organization (e.g. an owner also acting as organizer or scanner, or an organizer also acting as scanner), without requiring separate approval. Roles within an organization are independently combinable, not mutually exclusive or hierarchical.

### 4.2 Event & Organizer Management

- Organizers must be able to create, update, publish, unpublish, and cancel events.
- An event must support: title, description, category, venue/location, start/end date-time, timezone, images, and status (draft, published, on-sale, sold out, cancelled, completed).
- Organizers must be able to create multiple ticket types per event (e.g. GA, VIP, Early Bird), each with its own price, currency, quantity, sale window, and per-order purchase limits.
- Becoming an organization owner requires an application-and-approval flow: an already-registered user submits an application to create an `Organization`, including required verification documents (e.g. business permit); a platform admin reviews it and approves or rejects it. Only on approval does the applicant become that organization's owner, gaining the ability to publish events, receive payouts, and assign team members (4.1). A rejected or still-pending application grants no organization privileges.

### 4.3 Ticket Inventory: Seating & General Admission

- The API must support **reserved seating**: venues/organizers can define a seat map (sections, rows, seats), and each seat is individually tracked as available, held, or sold.
- The API must support **general admission / tiered** inventory: a ticket type has a fixed quantity with no specific seat assignment.
- During checkout, selected seats or GA quantities must be placed on a **temporary hold** (e.g. 10–15 minutes) to prevent overselling, and released automatically if checkout is not completed.
- Inventory changes must be consistent under concurrent purchase attempts — two buyers must never be sold the same seat, and GA quantity must never go negative.

### 4.4 Search & Discovery

- The API must support searching and filtering published events by keyword, category, location/distance, date range, and price range.
- The API must support paginated listing of events, with sorting (e.g. by date, popularity, price).

### 4.5 Cart, Checkout & Orders

- The API must support adding one or more ticket types (and, for reserved seating, specific seats) to a cart before checkout.
- The API must support applying a promo code to a cart and reflect the adjusted price before payment.
- The API must create an **order** on successful payment, containing one or more tickets, the buyer's identity, and payment details.
- Orders and their state transitions (pending, paid, cancelled, refunded, partially refunded) must be queryable by the buyer, the organizer, and admins (each scoped to what they're allowed to see).

### 4.6 Payments & Refunds

- The API must integrate with a third-party payment gateway/processor for card and (optionally) digital wallet payments; the API itself must not store raw card data (see 5.3, PCI-DSS).
- The API must support full and partial refunds, initiated by the organizer or admin, subject to the event's refund policy.
- The API must support organizer-defined refund policies per event (e.g. refundable up to N days before the event, no refunds, custom).
- The API must support event cancellation triggering an automated refund (or credit) workflow to all ticket holders.
- The API must support payouts to organizers (e.g. scheduled transfer of net sales minus platform fees) — the specific payout provider integration is an architecture-phase decision, but the requirement to track gross sales, fees, and net payable per organizer is in scope.

### 4.7 Promo Codes & Discounts

- Organizers must be able to create promo codes with: discount type (percentage or fixed amount), applicable ticket types, usage limits (total and per-buyer), and validity window.
- The API must validate a promo code at checkout and reject expired, exhausted, or inapplicable codes with a clear error.

### 4.8 Waitlists

- When a ticket type or event sells out, the API must allow attendees to join a waitlist.
- When inventory becomes available (e.g. via a cancellation or organizer-added capacity), the API must notify waitlisted users, in order, with a time-limited window to purchase.

### 4.9 Ticket Transfer & Resale

- The API must allow a ticket owner to transfer a ticket to another registered user (name/identity on the ticket updates accordingly).
- The API must support organizer-controlled resale: an organizer can enable/disable peer-to-peer resale for their event, and optionally cap resale price (e.g. face value only, or face value + fee).
- Every transfer or resale must invalidate the previous ticket credential and issue a new one, so only the current owner's ticket is valid for check-in.

### 4.10 Ticket Design & Delivery

> Scope note: this project builds the **API only**. The organizer-facing ticket-layout tool and the mobile scanning app are separate client applications, built and maintained outside this project, that would consume the endpoints/data model described below. Requirements here define what the API must store and expose so such clients are possible — not any client UI itself.

- On successful purchase, the API must issue a unique, unguessable ticket credential per seat/ticket (e.g. signed QR code or barcode) that is the single source of truth for that ticket's validity, independent of how it is later presented.
- Alongside that scannable credential, the API must issue a human-readable **ticket number** in the format `<PREFIX>-XXXXXX`, unique platform-wide. The prefix is a **random 3-letter code (A–Z)** the API generates and assigns to the event once, at event-creation time, and reserves against every other event's prefix so no two events can ever collide — it is not organizer-chosen text, and it is not the event's general-purpose ID/slug used elsewhere in the API; it exists solely to seed ticket numbers. The six-character suffix is separately randomly generated by the API at issuance, alphanumeric, and only needs to be unique *within that event* (avoiding visually ambiguous characters such as `O`/`0` or `I`/`1` is recommended); the event's reserved prefix is what turns that per-event uniqueness into global uniqueness, without the API needing to check the suffix against every ticket ever issued platform-wide. This number exists purely so a buyer can tell tickets apart at a glance (e.g. when holding several in one order); it is not itself a check-in credential and carries none of the QR/barcode's validation authority unless a future revision adds it as a manual fallback (see open questions).
- The ticket number must be included as a field in both the physical and digital ticket templates/artifacts described below, distinct from and in addition to the QR/barcode.
- The API must let an organizer submit and store a ticket layout/template per event (or per ticket type), separately for **physical (print)** and **digital** formats — a data model for branding elements (logo, colors, background image/artwork) and dynamic fields (attendee name, ticket type, seat, ticket number, event details) plus the platform-generated QR/barcode. Rendering that template into an actual designed ticket (image/PDF) is an API-level generation step; designing it visually is a client concern outside this project.
- The API must generate a **digital ticket** artifact (e.g. PDF/image) suitable for delivery by email or in-app viewing, and the issued QR/barcode must remain scannable and valid whether the resulting artifact is viewed in-app, downloaded, or screenshotted.
- The API must generate a **physical ticket** artifact in a print-ready format (e.g. print-at-home PDF, or a layout organizers can hand off to a professional printer), carrying a QR/barcode with the same validity guarantees as the digital version.
- Regardless of format or delivery channel (in-app, email, downloaded/screenshotted image, or printed), each ticket credential must have exactly one live validity state in the API, so a duplicate presented in a different format (e.g. a screenshot used after the in-app ticket was already scanned) is rejected as already-used.

### 4.11 Check-in & Validation

> Scope note: the dedicated mobile scanning app used by check-in staff is a separate client application outside this project. This section defines the validation API it would call, not the app itself.

- The API must expose a validation endpoint that accepts a scanned QR/barcode value and checks/marks the corresponding ticket "used" in near-real-time, preventing reuse (anti-duplication / anti-fraud) across all formats and delivery channels for that same ticket.
- The validation endpoint's response must give a scanning client enough information to show clear real-time feedback: valid, invalid, duplicate/already-used, or wrong-event.
- Validation requests must respect the RBAC scoping from 4.1: a check-in-staff credential can only validate tickets for the event(s) it's assigned to.

**Scanner count & offline policy**

- Per event, the organizer must explicitly choose one of two check-in modes as an event setting — this is a manual organizer decision made in the event's configuration, not a mode the API infers or switches automatically based on observed network conditions.

- **Standard (online) mode:**
  - The organizer sets the number of authorized scanner devices for the event — one or more.
  - Every authorized device operates primarily online, calling the validation endpoint in real time for each scan.
  - Each device must also maintain a local offline-fallback file so a scan can still be checked if that device's connection drops. The API must keep this file refreshed on each device while it's online, so it reflects recent ticket state.
  - The offline-fallback file becomes unusable a fixed time after the device goes offline — **5 minutes by default** — after which the device must stop validating locally until connectivity (and a refreshed file) returns. The organizer must be able to override this expiry window per event.
  - Because more than one device can be validating at once in this mode, a short-lived local cache bounds — but does not eliminate — the window in which two devices could independently accept the same ticket while both are briefly offline; this is treated as an acceptable, time-boxed risk rather than something the API prevents outright.

- **Pure offline mode:**
  - The event has exactly one authorized ("real") scanner device. That device pre-fetches the full ticket dataset ahead of the event and validates scans fully offline as well as online, with no expiry window (unlike the bounded fallback file in standard mode).
  - If that device fails or becomes unusable, the organizer may bring in a different app/device as a fallback — but that fallback device was never issued the pre-fetched dataset, so it cannot validate in real time. It can only record each scanned barcode locally; those recorded scans are checked against the server once the device is back online.
  - Duplicate ticket use during that fallback window (e.g. a ticket already let in by the original device, then scanned again by the fallback recorder before anyone knows better) cannot be prevented — this is an accepted risk of pure offline mode, not a case the API is expected to catch in real time. Once the recorded scans do sync, the API should still surface any duplicates it finds for the organizer's after-the-fact review, even though it couldn't stop them as they happened.

- A scanning client must be able to query its event's current mode (standard vs. pure offline), its authorization status, and, in standard mode, the configured offline-fallback expiry window.

### 4.12 Notifications

- The API must trigger notifications (email at minimum; SMS/push as future channels) for: order confirmation, payment receipt, event reminders, event changes (time/venue change), event cancellation, waitlist availability, and refund confirmation.

### 4.13 Platform Administration

- Admins must be able to review and approve/reject new organizer applications.
- Admins must be able to suspend or remove an organizer, event, or user account for policy violations.
- Admins must be able to view and intervene in disputes (e.g. buyer-reported fraud, chargeback support).

### 4.14 Analytics & Reporting

- Organizers must be able to view sales analytics for their own events: tickets sold, revenue, remaining inventory, sales-over-time.
- Admins must be able to view platform-wide metrics: total GMV, active organizers, event volume, and similar aggregate reporting.

## 5. Non-Functional Requirements

### 5.1 Performance & Scalability

- The system must handle sharp demand spikes at "on-sale" moments (e.g. thousands of concurrent buyers competing for limited inventory) without overselling and with graceful degradation (e.g. queueing) rather than failure.
- Typical read endpoints (event search, event details) should target sub-second p95 response times under normal load.
- The architecture must support horizontal scaling of stateless API layers independently from the inventory/locking layer, which is the most contention-sensitive component.

### 5.2 Availability & Reliability

- The API should target high availability (e.g. 99.9%+) for purchase-path endpoints, since downtime during an on-sale directly costs organizers revenue.
- Ticket validation at check-in must remain functional during partial backend outages or degraded connectivity, given its time-criticality at a live event.
- The system must be resilient to partial failures in third-party dependencies (payment gateway, email provider) — e.g. a notification failure must not roll back a successful payment.

### 5.3 Security & Compliance

- The API must never store raw payment card data; payment collection must be handled via a PCI-DSS-compliant processor (e.g. tokenized/hosted payment fields).
- All traffic must be encrypted in transit (TLS); sensitive data at rest (PII, payment tokens) must be encrypted.
- Ticket credentials (QR/barcode) must be cryptographically signed or otherwise unguessable to prevent forgery — this matters more, not less, once tickets can be freely screenshotted or printed, since anyone possessing the image effectively possesses the credential; single-use invalidation at scan time (4.11) is the primary control against sharing/duplication.
- The API must implement rate limiting and abuse protection, particularly on checkout and promo-code-validation endpoints, to deter bots and scalping automation.
- The API must maintain an audit log of sensitive actions (refunds, account role changes, event cancellations, admin interventions).

### 5.4 Data Privacy

- The system must support handling personal data (buyer names, emails, payment metadata) in line with applicable privacy regulations (e.g. GDPR/CCPA-style rights: access, export, deletion requests), scoped further once target markets are known.

### 5.5 Observability

- The API must emit structured logs, metrics, and traces sufficient to diagnose checkout failures, inventory contention, and payment errors in production.
- Key business metrics (orders/minute, checkout conversion, failed payments) should be observable in near-real-time, especially during on-sale windows.

### 5.6 API Design Standards

- The API should follow a consistent style (e.g. REST with JSON) with clear resource modeling for events, ticket types, seats, orders, tickets, and users.
- The API must be versioned to allow non-breaking evolution as new clients (web, mobile, organizer tools) integrate against it.
- Mutating operations on checkout (e.g. "purchase") should be idempotent to safely handle client retries without double-charging or double-issuing tickets.

## 6. Out of Scope (v1)

- Physical/hardware point-of-sale integration (e.g. dedicated box-office scanner devices) — v1 assumes mobile/web-based check-in.
- Multi-currency dynamic pricing / currency conversion beyond simple per-event currency selection.
- Built-in marketing tools (e.g. email campaign builder) beyond transactional notifications.
- **All client applications**: the public web/buying app, the organizer console (including any visual ticket-layout/design editor), and the mobile check-in/scanning app. This project delivers the API they would each call, not the apps themselves.
- Native mobile SDKs — client apps (including a future mobile scanner) consume the API over HTTP.

## 7. Assumptions & Open Questions

- **Assumption:** Payment processing will integrate with a third-party provider (e.g. Stripe) rather than the platform becoming a payment processor itself. To confirm in the architecture phase.
- **Assumption:** Standard industry-practice NFR targets (as stated in section 5) apply; no specific contractual SLA, compliance regime, or target region has been set yet.
- **Open question:** Target initial markets/regions (affects currency, tax/VAT handling, and applicable privacy law).
- **Open question:** Expected scale for v1 launch (number of organizers, peak concurrent buyers per on-sale) — needed to size the inventory-locking and queueing design in the architecture phase.
- **Open question:** Resale policy defaults — should peer-to-peer resale be opt-in or opt-out for organizers at launch?
- **Open question:** Mid-event mode switching — if an organizer switches an event from pure offline mode to standard mode (or vice versa) while check-in is already underway and the sole device holds unsynced offline scans, how should that transition work? Assumed rare enough to require a forced sync/re-online step rather than a fully seamless transition, to be confirmed in the architecture phase.
- **Open question:** How exactly duplicate scans surfaced after a pure-offline-mode fallback sync (section 4.11) should be handled operationally — e.g. flagged to the organizer only, or also triggering an automatic notification/refund conversation with the affected attendee. Treated as a reporting concern for v1, not an API-enforced resolution.
- **Open question:** Should the visible ticket number (4.10) also work as a manual check-in fallback — e.g. staff typing it in when a QR code won't scan? Not requested for v1; if added later, it would need its own rate-limiting and audit-logging given it's short and human-typed (see 5.3).

## 8. Glossary

- **GA (General Admission):** Ticket type with no assigned seat, sold against a quantity pool.
- **Hold:** A temporary reservation of a seat or GA quantity during active checkout, before payment completes.
- **Organizer:** The account/entity that owns and manages an event.
- **On-sale:** The moment tickets for an event become publicly purchasable, often associated with demand spikes.
- **GMV (Gross Merchandise Value):** Total value of tickets sold through the platform before fees/refunds.
- **Standard (online) mode:** An event check-in configuration where one or more authorized devices validate online by default, each backed by a short-lived (default 5 min, configurable) local offline-fallback file for brief connectivity loss.
- **Pure offline mode:** An event check-in configuration with exactly one authorized device holding the full pre-fetched ticket dataset, able to validate fully offline; if that device fails, a fallback device may record scans blind (no validation, no duplicate protection) until it can sync.
- **Offline-fallback file:** The local, time-boxed dataset a standard-mode scanning device uses to keep validating briefly after losing connectivity.
- **Ticket number:** The visible `<PREFIX>-XXXXXX` identifier printed/displayed on a ticket for human reference, distinct from its scannable QR/barcode credential. The prefix is a random 3-letter code reserved uniquely per event (not organizer-chosen, and not the event's general-purpose ID), which is what makes the whole number unique platform-wide.
