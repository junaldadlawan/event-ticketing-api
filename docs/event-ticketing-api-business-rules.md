# Event Ticketing API — Business Rules

**Version:** 1.0
**Date:** 2026-09-08
**Derived from:** `event-ticketing-api-requirements.md` v1.0

This document extracts the individual, testable business rules that are
embedded in the functional requirements, and gives each one a stable ID for
reference (e.g. from test cases, code comments, or PR descriptions). It
adds no new rules beyond what `requirements.md` already states — where a
requirement is a feature description rather than a rule (e.g. "the API must
support searching events"), it isn't repeated here. Non-functional
requirements (§5 of the requirements doc) are included where they impose a
concrete behavioral constraint rather than a quality target.

Each rule cites its source section in `requirements.md` in parentheses.

## 1. Identity, Access & Roles

| ID | Rule |
|---|---|
| BR-AUTH-001 | Roles are not mutually exclusive at the account level — a single account may be an organizer on some events and an attendee on others. (§3) |
| BR-AUTH-002 | An organizer may only manage their own events, not another organizer's. (§4.1) |
| BR-AUTH-003 | Check-in staff may only validate tickets for events they are assigned to. (§4.1) |
| BR-AUTH-004 | Admins have platform-wide access, unscoped by organization or event. Exception for events: an admin may view any event in any status, cancel or delete it, and moderate it (suspend/reinstate/remove), but may **not** update or publish an event - those stay with the owning organization's owner/organizer. (§4.1) |
| BR-AUTH-005 | Only an organization's owner — not an organizer it has assigned — may assign *other users* the organizer or scanner/check-in-staff role within that organization. (§3, §4.1) |
| BR-AUTH-006 | An assigned role (organizer or scanner/check-in staff) is scoped to that one organization's own events. (§4.1) |
| BR-AUTH-007 | Roles within an organization are independently combinable, not mutually exclusive or hierarchical — an owner may also be an organizer and/or a scanner/check-in staff for that same organization; an organizer may also be a scanner. (§3) |
| BR-AUTH-008 | A user may assign themselves any additional role they're entitled to within their own organization, without a separate approval step. (§3, §4.1) |
| BR-AUTH-009 | An organization's owner may list the users belonging to their own organization only (needed to manage their roles) — not another organization's users. A platform admin may list users across the entire platform. (§4.1) |

## 2. Organization Onboarding

| ID | Rule |
|---|---|
| BR-ORG-001 | A user must already be registered before they can apply to create an organization. (§4.2) |
| BR-ORG-002 | An organization application must include required verification documents (e.g. business permit). (§4.2) |
| BR-ORG-003 | An organization application must be reviewed by a platform admin, who approves or rejects it. (§4.2) |
| BR-ORG-004 | Only on admin approval does the applicant become that organization's owner. (§4.2) |
| BR-ORG-005 | A rejected or still-pending organization application grants no organization privileges (cannot publish events, receive payouts, or assign roles). (§4.2) |
| BR-ORG-006 | A signed-in user may list their own organizations - those they are a member of in any role, or applied for - of every status, oldest first. They never see an organization they have no relation to (an admin included; the admin review queue is separate). |

## 3. Event Management

| ID | Rule |
|---|---|
| BR-EVENT-001 | An event's status must be one of: draft, published, on-sale, sold out, cancelled, completed. (§4.2) |
| BR-EVENT-002 | An event must have: title, description, category, venue/location, start date-time, end date-time, timezone, images, and status. (§4.2) |
| BR-EVENT-003 | Each ticket type on an event must define its own price, currency, quantity, sale window, and per-order purchase limit. (§4.2) |
| BR-EVENT-004 | An event's category must be the name of an active category from the managed category list (`GET /api/v1/categories`); matching is case-insensitive and the canonical spelling is stored. Only an admin can create, rename, deactivate or re-activate categories; deactivating one never changes events that already use it. |

## 4. Ticket Inventory (Seating & GA)

| ID | Rule |
|---|---|
| BR-INV-001 | Each seat in a reserved-seating event must be individually tracked as available, held, or sold. (§4.3) |
| BR-INV-002 | A general-admission ticket type has a fixed quantity and no specific seat assignment. (§4.3) |
| BR-INV-003 | A seat or GA quantity selected during checkout must be placed on a temporary hold (default 10–15 minutes). (§4.3) |
| BR-INV-004 | A hold must be released automatically if checkout is not completed. (§4.3) |
| BR-INV-005 | Two buyers must never be sold the same seat. (§4.3) |
| BR-INV-006 | GA quantity must never go negative, even under concurrent purchase attempts. (§4.3) |

## 5. Search & Discovery

| ID | Rule |
|---|---|
| BR-SEARCH-001 | Public search/listing returns published events (not draft/unpublished). (§4.4) |

## 6. Cart, Checkout & Orders

| ID | Rule |
|---|---|
| BR-CART-001 | A promo code applied to a cart must adjust the price before payment is taken. (§4.5) |
| BR-CART-002 | An order is created only on successful payment. (§4.5) |
| BR-CART-003 | An order's state must be one of: pending, paid, cancelled, refunded, partially refunded. (§4.5) |
| BR-CART-004 | Order visibility is scoped: the buyer, the event's organizer, and admins may query an order — each limited to what they're allowed to see. (§4.5) |

## 7. Payments & Refunds

| ID | Rule |
|---|---|
| BR-PAY-001 | The API must never store raw payment card data. (§4.6, §5.3) |
| BR-PAY-002 | A refund (full or partial) may only be initiated by the organizer or an admin. (§4.6) |
| BR-PAY-003 | A refund is subject to the event's configured refund policy. (§4.6) |
| BR-PAY-004 | An event's refund policy must be one of: refundable up to N days before the event, no refunds, or a custom policy. (§4.6) |
| BR-PAY-005 | Cancelling an event must trigger an automated refund (or credit) workflow for every ticket holder of that event. (§4.6) |
| BR-PAY-006 | Payout tracking must record gross sales, platform fees, and net payable, per organizer. (§4.6) |

## 8. Promo Codes & Discounts

| ID | Rule |
|---|---|
| BR-PROMO-001 | A promo code's discount type must be either a percentage or a fixed amount. (§4.7) |
| BR-PROMO-002 | A promo code must define usage limits: a total cap and a per-buyer cap. (§4.7) |
| BR-PROMO-003 | A promo code must define a validity window (start/end). (§4.7) |
| BR-PROMO-004 | A promo code must be scoped to specific, applicable ticket types. (§4.7) |
| BR-PROMO-005 | An expired promo code must be rejected at checkout with a clear error. (§4.7) |
| BR-PROMO-006 | An exhausted (usage-limit-reached) promo code must be rejected at checkout with a clear error. (§4.7) |
| BR-PROMO-007 | A promo code applied to an inapplicable ticket type must be rejected at checkout with a clear error. (§4.7) |
| BR-PROMO-008 | An organizer (or admin) may edit a promo code (code, discount, ticket types, limits, validity window) and may pause and resume it; the organization is always taken from the promo code's own event. Once a code has been used, its code and discount type can no longer change and its total limit can't drop below the uses. A code that has been used can't be deleted, only paused; an unused one can be deleted and its name reused. |
| BR-PROMO-009 | A paused or deleted promo code can't be applied to a cart (rejected like an unknown code, without saying it exists) and a cart that already holds it can't check out (nothing is charged); while it is paused or deleted the cart shows no discount. Resuming makes it usable again, and orders that already used it are untouched. |

## 9. Waitlists

| ID | Rule |
|---|---|
| BR-WAIT-001 | An attendee may join a waitlist only once a ticket type or event has sold out. (§4.8) |
| BR-WAIT-002 | When inventory becomes available, waitlisted users must be notified in order (i.e. FIFO by join time). (§4.8) |
| BR-WAIT-003 | A notified waitlisted user has a time-limited window to complete a purchase before it passes to the next person. (§4.8) |

## 10. Ticket Transfer & Resale

| ID | Rule |
|---|---|
| BR-TRANSFER-001 | Only the current ticket owner may transfer it to another registered user. (§4.9) |
| BR-TRANSFER-002 | Transferring a ticket updates the name/identity recorded on it. (§4.9) |
| BR-TRANSFER-003 | Peer-to-peer resale is organizer-controlled per event (the organizer can enable or disable it); the default state if the organizer never sets it is not yet decided — see requirements.md §7 open question. (§4.9) |
| BR-TRANSFER-004 | An organizer may cap resale price (e.g. face value only, or face value plus a fee). (§4.9) |
| BR-TRANSFER-005 | Every transfer or resale must invalidate the previous ticket credential and issue a new one — only the current owner's ticket is valid for check-in. (§4.9) |

## 11. Ticket Design, Numbering & Delivery

| ID | Rule |
|---|---|
| BR-TICKET-001 | Every issued ticket must have a unique, unguessable scannable credential (QR/barcode) that is the sole source of truth for its validity. (§4.10) |
| BR-TICKET-002 | Every issued ticket must also have a human-readable ticket number in the format `<PREFIX>-XXXXXX`, unique platform-wide. (§4.10) |
| BR-TICKET-003 | An event's ticket-number prefix is a random 3-letter (A–Z) code generated and reserved by the API once, at event-creation time — never organizer-chosen text, and never derived from the event's own ID/slug. (§4.10) |
| BR-TICKET-004 | An event's reserved prefix must not collide with any other event's prefix. (§4.10) |
| BR-TICKET-005 | A ticket number's six-character suffix only needs to be unique within its own event, not platform-wide (uniqueness comes from the event's reserved prefix). (§4.10) |
| BR-TICKET-006 | The ticket number is display-only — it carries no check-in authority and is not itself a valid scan credential. (§4.10) |
| BR-TICKET-007 | An organizer's ticket layout/template is defined separately for physical (print) and digital formats, per event or per ticket type. (§4.10) |
| BR-TICKET-008 | A ticket's QR/barcode must remain valid and scannable regardless of whether the resulting artifact is viewed in-app, downloaded, or screenshotted. (§4.10) |
| BR-TICKET-009 | Each ticket credential has exactly one live validity state — a duplicate of it presented in a different format or channel (e.g. a screenshot used after the in-app version was already scanned) must be rejected as already-used. (§4.10) |
| BR-TICKET-010 | The ticket number must appear as a field on both the physical and digital ticket templates/artifacts, distinct from and in addition to the QR/barcode. (§4.10) |
| BR-TICKET-011 | An organizer may design a ticket template: the ticket canvas (ticketWidth/ticketHeight, 100-5000 px, default 900 x 380), a background (colour, an uploaded image with fit COVER/CONTAIN/STRETCH/CUSTOM and, for CUSTOM, a rectangle that may extend past the edges), up to 30 text fields (per-ticket values such as seat and attendee, event values such as date, time and venue, and up to 10 free texts) and which scannable code is printed and where (codeType QR, BARCODE or NONE with codeX, codeY, codeWidth in percent and codeRotation 0-359 for either). Code placement values are given together; the width must reach the type's scannable minimum (QR 15%, barcode 35%) and the code must fit within the ticket width; a NONE template has no placement. Everything is optional - a template with none of it renders the built-in layout. Drawing order is fixed: canvas colour, background image, text fields in list order, then the code on top, kept fully on the ticket with a white quiet zone. Both code types encode the same credential (BR-TICKET-008). Background images may only be our own uploads (PNG, JPEG, WebP or GIF, up to 5 MB, stored under a random name) and are never fetched from other URLs. The attendee printed is the ticket's current owner; the event date and time are shown in the event's own time zone. |
| BR-TICKET-012 | An organizer (or admin) may pause and resume selling an individual ticket type (PUT /ticket-types/{id}/sales-status with status ACTIVE or PAUSED, or the salesPaused flag of the update), in any event status. While a ticket type is paused it cannot be added to a cart, and a cart that already holds it cannot check out - nothing is charged and the cart and its holds are kept, so the buyer can complete the same checkout once selling resumes. Tickets already sold and resale are not affected; setting the state a ticket type is already in is not an error. |
| BR-TICKET-013 | An event's ticket types have an order the organizer controls: each has a position (0 = first), new ticket types go to the end, and the list is always returned in that order. The organizer (or an admin) saves a drag-and-drop arrangement in one call that lists every ticket type of the event exactly once; a list that omits, repeats or adds one is refused and nothing changes. Deleting a ticket type leaves a gap that the next arrangement closes. |

## 12. Check-in & Validation

| ID | Rule |
|---|---|
| BR-CHECKIN-001 | Validating a scanned credential must mark the corresponding ticket "used" in near-real-time, preventing reuse across all formats/channels for that ticket. (§4.11) |
| BR-CHECKIN-002 | A validation response must indicate one of: valid, invalid, duplicate/already-used, or wrong-event. (§4.11) |
| BR-CHECKIN-003 | A check-in-staff credential may only validate tickets for the event(s) it is assigned to. (§4.11) |
| BR-CHECKIN-004 | Each event's check-in mode (standard/online vs. pure offline) is an explicit, manual organizer choice — the API must never infer or auto-switch it based on observed network conditions. (§4.11) |
| BR-CHECKIN-005 | In standard mode, the organizer sets the number of authorized scanner devices (one or more). (§4.11) |
| BR-CHECKIN-006 | In standard mode, each authorized device must maintain a local offline-fallback file, refreshed by the API while the device is online. (§4.11) |
| BR-CHECKIN-007 | In standard mode, a device's offline-fallback file becomes unusable a fixed time after it goes offline — 5 minutes by default, organizer-overridable per event. (§4.11) |
| BR-CHECKIN-008 | In pure offline mode, an event has exactly one authorized ("real") scanner device, which pre-fetches the full ticket dataset and validates fully offline with no expiry window. (§4.11) |
| BR-CHECKIN-009 | In pure offline mode, a fallback device (brought in if the sole real device fails) was never issued the pre-fetched dataset, so it may only record scans locally — it cannot validate in real time. (§4.11) |
| BR-CHECKIN-010 | Recorded fallback scans (pure offline mode) must be checked against the server once the device is back online, and any duplicates found must still be surfaced to the organizer for after-the-fact review, even though they couldn't be prevented in real time. (§4.11) |
| BR-CHECKIN-011 | A scanning client must be able to query its event's current check-in mode, its own authorization status, and — in standard mode — the configured offline-fallback expiry window. (§4.11) |

## 13. Notifications

| ID | Rule |
|---|---|
| BR-NOTIFY-001 | The API must trigger a notification for each of: order confirmation, payment receipt, event reminder, event change (time/venue), event cancellation, waitlist availability, and refund confirmation. (§4.12) |

## 14. Platform Administration

| ID | Rule |
|---|---|
| BR-ADMIN-001 | An admin may approve or reject a new organizer/organization application. (§4.2, §4.13) |
| BR-ADMIN-002 | An admin may suspend or remove an organizer, event, or user account for policy violations. (§4.13) |
| BR-ADMIN-003 | An admin may view and intervene in disputes (e.g. buyer-reported fraud, chargeback support). (§4.13) |

## 15. Analytics & Reporting

| ID | Rule |
|---|---|
| BR-ANALYTICS-001 | An organizer may view sales analytics only for their own events (tickets sold, revenue, remaining inventory, sales-over-time). (§4.14) |
| BR-ANALYTICS-002 | An admin may view platform-wide aggregate metrics (total GMV, active organizers, event volume, and similar). (§4.14) |

## 14a. Posts (Sales & Announcements)

| ID | Rule |
|---|---|
| BR-POST-001 | Only an admin may publish or remove a post. Anyone, signed in or not, may read the posts. |
| BR-POST-002 | A post is either site-wide (no event) or about one existing, not-deleted event. Its kind is ANNOUNCEMENT or SALE; its title is 1-120 characters and its body at most 2000 (may be empty); neither may contain HTML. |
| BR-POST-003 | Posts are listed newest first (by the moment they went live: their publish time, else their creation time). By default the list is the site-wide posts plus the posts of public events (published, on sale, sold out or completed); posts of draft, cancelled, suspended or deleted events are never listed. A page holds at most 50 posts. Only live posts are listed to the public (see BR-POST-005). |
| BR-POST-004 | Removing a post is a soft delete: it disappears from the list and cannot be removed twice. Publishing and removing are recorded in the audit trail (post.created, post.deleted). |
| BR-POST-005 | A post may be scheduled (publishAt: not public before that moment), may expire (expiresAt: not public from that moment on; on creation it must be in the future and after publishAt) and may be hidden (an admin switch that keeps it off the public list whatever its dates say). Only a post that is not hidden, already published and not yet expired is live. An admin may change these, and the text, at any time (an expiry in the past ends the post right now), and may list every post that is not deleted with its status (HIDDEN, EXPIRED, SCHEDULED or LIVE). Changes are recorded in the audit trail (post.updated). |
| BR-POST-006 | A post may carry one optional picture (imageUrl), which must be an image uploaded through this server (POST /uploads) and still stored here; a link to anywhere else, or to a file that does not exist, is refused. An admin may add, replace or remove it (an empty value removes it). When a picture is replaced or removed, its file is deleted from storage unless another post, a user profile picture, a ticket template, an event image or an organization document still uses it. Soft-deleting a post releases its picture the same way: a deleted post is not restorable through the API, so it does not keep its file alive. Upload limits are unchanged. |

## 14c. Platform Fee ("admin cut")

| ID | Rule |
|---|---|
| BR-FEE-001 | Only an admin may set, change or remove platform fee rules. |
| BR-FEE-002 | A rule is either a percentage (0-100, up to two decimals) of the ticket total after promo discounts, rounded half up to the minor unit, or a flat amount per order. A rate of 0 is allowed and waives the fee. |
| BR-FEE-003 | A rule has a scope: the platform-wide default, one organization, or one event. At most one rule per scope; the most specific one applies (event, then organization, then platform default). With no rule, no fee is charged. Removing an override makes the next less specific rule apply again. |
| BR-FEE-004 | The fee is calculated on every purchase and added on top of the ticket price: the buyer pays ticket total + fee, and the cart shows the fee before payment. An order with a ticket total of 0 pays no fee; a flat rule in a different currency than the order is skipped. |
| BR-FEE-005 | The fee is stored on the order together with a snapshot of the rule that produced it. Changing or removing a rule never changes an order already placed. |
| BR-FEE-006 | The fee is not the organizer's revenue: an event's analytics revenue is the ticket revenue without the fee. Platform-wide GMV counts what buyers paid, fee included. |
| BR-FEE-007 | The fee is deducted when the payout is generated: per order, gross = what the buyer paid minus refunds, fees = the order's platform fee (never more than the gross left after refunds), net = gross - fees, which is what the organizer is owed. A fully refunded order is not paid out (the buyer gets the fee back too). |
| BR-FEE-008 | Only an admin generates a payout, for a period (UTC days, end not in the future). It settles the organization's paid or partly refunded orders in that period that are not yet in a payout, so no order is ever paid out twice. Refunds issued after an order was settled are not netted against the payout. |

## 14b. Profile Picture

| ID | Rule |
|---|---|
| BR-PROFILE-001 | A user may set, replace or remove only their own profile picture. |
| BR-PROFILE-002 | The picture must be an image uploaded through this server (POST /uploads) and still stored here; a link to anywhere else is refused. The upload rules (type and size limits) are unchanged. |
| BR-PROFILE-003 | When a picture is replaced or removed, its file is deleted from storage unless another user, ticket template, event or organization document still uses it. |
| BR-PROFILE-004 | The picture URL is only part of the existing user payloads (own profile and the admin user list); it is not added to any response that exposes private data about a user. |

## 16. Cross-Cutting Rules (from Non-Functional Requirements)

| ID | Rule |
|---|---|
| BR-NFR-001 | The system must never oversell inventory, even under sharp demand spikes — degrade gracefully (e.g. queueing) rather than fail. (§5.1) |
| BR-NFR-002 | All traffic must be encrypted in transit (TLS); sensitive data at rest (PII, payment tokens) must be encrypted. (§5.3) |
| BR-NFR-003 | Ticket credentials (QR/barcode) must be cryptographically signed or otherwise unguessable. (§5.3) |
| BR-NFR-004 | Checkout and promo-code-validation endpoints must be rate-limited to deter bots and scalping automation. (§5.3) |
| BR-NFR-005 | Sensitive actions (refunds, account role changes, event cancellations, admin interventions) must be recorded in an audit log. (§5.3) |
| BR-NFR-006 | The API must support data-subject rights on personal data (access, export, deletion requests), per applicable privacy regulation. (§5.4) |
| BR-NFR-007 | The API must be versioned in a way that allows non-breaking evolution as new clients integrate. (§5.6) |
| BR-NFR-008 | A checkout/purchase operation must be idempotent — a client retry must not double-charge or double-issue tickets. (§5.6) |

## Not yet rule-worthy (explicitly deferred in the requirements doc)

Per §7 (Assumptions & Open Questions), the following are unresolved and
therefore have no firm rule yet: target markets/currency/tax handling,
expected v1 launch scale, whether resale is opt-in or opt-out by default,
mid-event check-in mode switching behavior, how post-sync duplicate scans
should be operationally handled beyond "surfaced for review," and whether
the visible ticket number should ever work as a manual check-in fallback.
