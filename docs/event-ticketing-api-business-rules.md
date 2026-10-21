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
| BR-AUTH-010 | Registration (and later profile edits) only accepts plain characters: a name (kept as a required first name, an optional middle name and a required last name - there is no single full-name field) is letters of any language, spaces, periods, apostrophes and hyphens, starting with a letter, in each part; an email is ASCII letters, digits and `. _ % + -` in a valid shape. Anything else is rejected. Every self-registered account is a customer - a role sent by the client is ignored; the password may contain any character. Registration also requires a date of birth (in the past, at most 120 years ago) and accepts an optional phone number in international format (+ and 8-15 digits, nothing else); both are returned only in the user's own profile and to admins, and an account created before they existed simply has none. |
| BR-AUTH-011 | An email belongs to one account only, compared ignoring case (a deleted account still holds its email). Registering with, or changing to, an email another account has is refused with 409 and a readable message - never a server error; keeping your own email in an update is fine. A deleted account is treated as not found. |
| BR-AUTH-012 | Every error answers one JSON shape (status, title and a readable detail): 400 for a body that is missing, not JSON or has a value of the wrong type (the field is named) and for failed validation (every bad field is listed); 401 for missing or invalid credentials or token; 403 for a role that may not do it; 404 for an unknown id or a deleted account; 405 / 415 for a wrong method or content type; 409 for a conflict with existing data; 500 only for a genuine server fault, with no internals shown. |

## 2. Organization Onboarding

| ID | Rule |
|---|---|
| BR-ORG-001 | A user must already be registered before they can apply to create an organization. (§4.2) |
| BR-ORG-002 | An organization application must include required verification documents (e.g. business permit). (§4.2) |
| BR-ORG-003 | An organization application must be reviewed by a platform admin, who approves or rejects it. (§4.2) |
| BR-ORG-004 | Only on admin approval does the applicant become that organization's owner. (§4.2) |
| BR-ORG-005 | A rejected or still-pending organization application grants no organization privileges (cannot publish events, receive payouts, or assign roles). (§4.2) |
| BR-ORG-006 | A signed-in user may list their own organizations - those they are a member of in any role, or applied for - of every status, oldest first. They never see an organization they have no relation to (an admin included; the admin review queue is separate). |
| BR-ORG-007 | **Team management.** The member list names every member (from their own account); the owner, organizers and admins also see each member's email and phone, while a caller who is only check-in staff sees names only. Nobody edits these here: a member's account stays theirs to change. Only the owner (or, since BR-ORG-008, an organizer with TEAM) sets a member's roles, to exactly a given set of ORGANIZER and/or CHECK_IN_STAFF (an empty set, OWNER or an unknown role is refused; use removal instead of an empty set), and only the owner removes a member with all their roles; organizers and admins are refused (403). The owner's own membership can never be changed or removed (409), and the OWNER role is never given this way. Role changes are logged with the member's name and the roles before and after, removals with the name and the roles they had; their past actions stay in the logs. Adding a member (`POST /members`) is unchanged. |
| BR-ORG-008 | **Organizer permissions.** The owner chooses what each organizer may do, from EVENTS (events, ticket types, seat maps, venues, ticket designs), PROMO_CODES, CHECK_IN (check-in configuration and scanner devices), ORDERS_REFUNDS (orders, refunds, refund and resale policies), FINANCE (payout accounts, cards and payouts), ANALYTICS (sales and audience analytics, activity logs) and TEAM (adding, changing and removing members). An organizer without the permission an action needs gets 403. The owner always holds every permission; an organizer gets everything but TEAM unless chosen otherwise (and every organizer that existed before this rule got exactly that); permissions only count while the member holds ORGANIZER, and check-in staff are unaffected. An organizer with TEAM may add, change and remove members like the owner, but never the owner's membership or their own (409), and never grant a permission they do not hold themselves (403). Each member sees their own permissions (`GET /organizations/{orgId}/members/me`) so the web app shows only what they may use. Permission changes are logged as `organization_member.permissions_changed` with the before and after. Admin access is unchanged. The organization's finance summary and its FINANCE activity tab stay owner-only. |

## 3. Event Management

| ID | Rule |
|---|---|
| BR-EVENT-001 | An event's status must be one of: draft, published, on-sale, sold out, cancelled, completed. (§4.2) |
| BR-EVENT-002 | An event must have: title, description, category, venue/location, start date-time, end date-time, timezone, images, and status. (§4.2) |
| BR-EVENT-003 | Each ticket type on an event must define its own price, currency, quantity, sale window, and per-order purchase limit. (§4.2) The sale window must run for at least the minimum sale duration (400 "Ticket sales must run for at least <duration>") and end at least the sales-close time before the event starts (400 "Ticket sales must end at least <duration> before the event starts (by <time>)"); both values are admin settings (BR-ADMIN-SETTINGS-002, 1 hour each by default) and the messages show the current ones (e.g. "2 hours", "90 minutes"). They are checked on create and on any update that changes the sale start or end (against the resulting window); an update that leaves the window alone does not re-check it, so a ticket type created under older settings keeps its window. The start may be omitted on create to mean "now", taken from the server's clock, and the 1-hour minimum is measured from that time; an explicit start, even one in the past, is kept as given. |
| BR-EVENT-004 | An event's category must be the name of an active category from the managed category list (`GET /api/v1/categories`); matching is case-insensitive and the canonical spelling is stored. Only an admin can create, rename, deactivate or re-activate categories; deactivating one never changes events that already use it. |
| BR-EVENT-005 | **Completed events.** Once an event's end time has passed it is over: a published, on-sale or sold-out event becomes completed. A background job does this every minute, and the reads that show an event's status (`GET /events/{id}`, the public search, listing its ticket types) do it on the spot, so the status is right between runs; each completion is audit-logged as `event.completed`. An event that is completed, or whose end time has passed even if it is not marked completed yet, is read-only: editing, cancelling or deleting it, creating, editing, pausing, resuming, reordering or deleting its ticket types, creating, editing, pausing/resuming or deleting its promo codes, and creating, editing or deleting its ticket templates are refused with 409 "Event has ended". Nothing more can be sold: adding to a cart (or raising a line's quantity) and checking out are refused with the same 409, even when a ticket type's sale window runs past the event's end; a payment that completes after the end is refunded instead of issuing tickets. Everything else keeps working: all reads, orders and tickets, check-in, refunds, disputes, posts and payouts; reviews (BR-REVIEW-001) and the recap (BR-EVENT-RECAP-001) open only once it has ended. Draft, cancelled and suspended events are not completed. An admin can still take an ended event down through moderation (REMOVE). |
| BR-EVENT-006 | **Event activity history.** Every change to an event is kept as a who / what / when entry in the audit log, tagged with the event: the event itself (created, updated, published, cancelled, deleted, and completed - by the system, so with no actor), its ticket types (created, updated, reordered, sales paused/resumed, deleted), promo codes (created, updated, paused/resumed, deleted), ticket templates (created, updated, deleted), refund and resale policy and check-in configuration changes, scanner devices authorized or revoked, refunds issued on its orders, reviews (BR-REVIEW-001, rating only) and recap messages (BR-EVENT-RECAP-001), and admin moderation of it. Each entry also says *what* happened (`details`): an update lists only the fields it actually changed, by their API name, with the value before and after (Money as amount + currency, dates as ISO strings, lists such as images as their count, text capped at 500 characters), and an update that changed nothing is not logged at all; a created or deleted item, a paused/resumed ticket type or promo code and a scanner device keep their name (so a deleted one can still be named), a reorder keeps the new order, a refund keeps its order, amount and reason, and event moderation keeps the admin's reason. Details never contain secrets, card data, passwords, tokens or buyers' personal data; entries from before this existed have none. The event's owner or organizer, or an admin, reads it newest first at `GET /events/{eventId}/activity` (403 for anyone else, 404 for an unknown event); a deleted event's history stays readable. Entries are append-only. Check-in scans (see the check-in records) and admin posts are not part of it. |

## 4. Ticket Inventory (Seating & GA)

| ID | Rule |
|---|---|
| BR-INV-001 | Each seat in a reserved-seating event must be individually tracked as available, held, or sold. (§4.3) |
| BR-INV-002 | A general-admission ticket type has a fixed quantity and no specific seat assignment. (§4.3) |
| BR-INV-003 | A seat or GA quantity selected during checkout must be placed on a temporary hold (default 10–15 minutes). (§4.3) |
| BR-INV-004 | A hold must be released automatically if checkout is not completed. (§4.3) |
| BR-INV-005 | Two buyers must never be sold the same seat. (§4.3) |
| BR-INV-006 | GA quantity must never go negative, even under concurrent purchase attempts. (§4.3) |
| BR-TICKET-LIMIT-001 | **Per-buyer limit.** A ticket type may set `max_per_user` (an integer of at least 1; null = unlimited, the default): the most tickets of that type one buyer may buy in total. When it is set, `max_per_order` must not be more (400 "Max per order can't be more than the per-buyer limit"); left out on create, `max_per_order` defaults to the lower of 10 and `max_per_user`. A buyer's count is the tickets of that type from their own paid orders that are not refunded or cancelled - tickets they gave away still count, tickets transferred to them do not - plus what is in their cart. Adding to the cart or raising a line's quantity past the limit, and checking out once other orders have used it up, is refused with 422 "You can buy at most N <type> tickets; you already have M." (at checkout before anything is charged). Every ticket type response returns `max_per_user`; on an update, null clears it. |
| BR-TICKET-GATE-001 | **Gate-open time.** A ticket type may set `gate_opens_minutes_before` (0 to 1440; null = not set): gates open that many minutes before the event starts. It is shown to buyers only and does not change check-in. Every ticket type response returns it; on an update, null clears it. |

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
| BR-CART-006 | A general-admission ticket type has one line per cart: adding it again adds to the existing line (carts that already had several lines are not merged), and the ticket type's per-order limit (`maxPerOrder`) applies to the merged quantity. A reserved seat stays one line per seat. The quantity of a GA line can be changed (`PATCH /carts/{id}/items/{itemId}`, 1 or more): the held inventory moves by the difference under the same lock as an add, lowering gives tickets back at once, raising needs them available, and the line keeps its hold expiry (neither an add nor a change extends it). A seated line, an expired hold or a quantity that does not fit is a 409; below 1 is a 400. |
| BR-CART-005 | A cart can be priced and paid in parts. `GET /carts/{id}?itemIds=` quotes only the chosen items with the checkout's own rules, and `POST /carts/{id}/checkout` with `itemIds` creates the order, the PayMongo payment and the fee snapshots for those items only. The other items stay in the cart with their holds unchanged, so one cart can have several orders (the unique index on orders.cart_id was dropped, V53). The platform fee's fixed part is per ticket (BR-FEE-002), so the fees of the parts add up to the fee of the whole cart. A promo code applies to a selection only if its rules (applicable ticket types, usage limits) are met by those items; otherwise it is left out of that order and stays on the cart, and it leaves the cart once an order uses it. An unknown item id or an empty list is a 400; an expired hold on a selected item is a 409 on the quote and a 410 on checkout. The idempotency key covers the selection: the same key with other `itemIds` is a 409, the same `itemIds` replays the same order. |

## 7. Payments & Refunds

| ID | Rule |
|---|---|
| BR-PAY-001 | The API must never store raw payment card data. (§4.6, §5.3) |
| BR-PAY-002 | A refund (full or partial) may only be initiated by the organizer or an admin. (§4.6) |
| BR-PAY-003 | A refund is subject to the event's configured refund policy. (§4.6) |
| BR-PAY-004 | An event's refund policy must be one of: refundable up to N days before the event, no refunds, or a custom policy. (§4.6) |
| BR-PAY-005 | Cancelling an event must trigger an automated refund (or credit) workflow for every ticket holder of that event. (§4.6) |
| BR-PAY-006 | Payout tracking must record gross sales, platform fees, and net payable, per organizer. (§4.6) |
| BR-PAY-007 | An organization's payout cards store only what the payment gateway returns: a token, brand, last 4 digits, expiry and holder name. The full card number and the security code are never stored; the token is never returned or logged. |
| BR-PAY-008 | The organization's first card is always primary. An organization has exactly one primary card; making another card primary unsets the old one in the same transaction. |
| BR-PAY-009 | The primary card cannot be removed while other cards exist; the last remaining card can be. Removing a card is a soft delete. |
| BR-PAY-010 | Only an approved organization may add a card, and a card whose expiry month has passed is refused. Only the organization's owner or an admin may add, remove or choose the primary card; the owner, organizer or an admin may list them. |
| BR-PAY-011 | A payout is made out to the organization's primary card and records its id. With no primary card the payout cannot be generated (409). When PayMongo is the gateway it is made out to the organization's payout account (bank or e-wallet) instead, and without one it cannot be generated (409). |
| BR-PAY-012 | An organization has at most one payout account: a bank or e-wallet chosen from PayMongo's list, the name on the account and the number. Only the organization's owner or an admin sets or removes it. The full number is stored encrypted and is never returned or logged; only its last 4 digits are shown. |
| BR-PAY-013 | Only an admin sends a payout, and only a SCHEDULED or FAILED one with something to send. Sending claims the payout first (it becomes PROCESSING) so it can never be sent twice at once; a PROCESSING or PAID payout is refused (409). The net amount goes from the platform's PayMongo wallet by InstaPay (up to PHP 50,000) or PESONet (more), in PHP only. PayMongo's transfer fee is borne by the platform, not deducted from the organizer. |
| BR-PAY-015 | Only an admin may see the payout wallet (`GET /payout-wallet`): its status (a payout can only be sent from an `activated` wallet), available and pending balance, limits, usage and account. It is read from PayMongo on every call and never stored; organizers never see it. |
| BR-PAY-016 | The admin's batch payout screen lists what can be paid out now, one line per approved organization and event (`GET /payouts/eligible`): the orders due (paid or partly refunded, in no payout yet) placed in a period (UTC days, both included, at most 366, not in the future), and the net a payout of that line would pay, worked out exactly as a payout does it. `COMPLETED` (default) lists events that are over (status completed, or ended and not cancelled, suspended or a draft); `ALL` lists every event. |
| BR-PAY-017 | A line is **on hold** and cannot be paid while any of its orders has a refund still pending, or while its orders use more than one currency. The hold is checked again when the payout is generated, not only when the list was shown. |
| BR-PAY-018 | `POST /payouts/batch` creates one payout per chosen line, each settling that event's due orders and recording the event. It is all or nothing: if any line has nothing due, is on hold, belongs to an organization that is not approved or has no payout destination, nothing is created. The same line cannot be listed twice, and a line already settled cannot be paid again. A payout without an event is a period payout, as before. |
| BR-PAY-019 | Only an admin may cancel a payout (`POST /payouts/{id}/cancel`), and only while it is SCHEDULED; a PROCESSING (with PayMongo), PAID, FAILED or already CANCELLED payout is refused (409). Cancelling and sending cannot race: whichever claims the payout first wins and the other gets 409. In one transaction the payout becomes CANCELLED (final: it can never be sent or refreshed) and its orders are released, so they are owed again and can be settled by a new payout. A cancelled payout counts nowhere in the finance figures (not paid out, not in payouts, not a transaction). The cancel is audit-logged (`payout.cancelled`). |
| BR-PAY-014 | A transfer settles later: PayMongo's signed webhook (or an admin's refresh) turns the payout PAID or FAILED, with the reason. A repeated or late event changes nothing. A FAILED payout can be sent again with a new reference number. If PayMongo cannot be reached the outcome is unknown: the payout is marked FAILED with a warning to check PayMongo's dashboard before sending again. |

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
| BR-CHECKIN-012 | A scanner device may have an optional location (max 100 chars); each check-in record keeps the location the scan was made at. |

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
| BR-ADMIN-004 | There is exactly one super admin account, the highest user (`SUPER_ADMIN`). The database refuses a second one. It is created by hand (the demo seed script), never through the API, and the `SUPER_ADMIN` role cannot be granted or assigned by anyone through the API. |
| BR-ADMIN-005 | The super admin has every admin power. An admin cannot suspend, reinstate, remove, delete or demote another admin, nor make anyone an admin: only the super admin can (403 otherwise). An admin keeps these powers over customers. |
| BR-ADMIN-006 | Nobody, the super admin included, can suspend, reinstate, remove, delete or change the role of the super admin account (403). |
| BR-ADMIN-007 | **Activity logs per management tab.** Every management action is kept in the audit log, tagged with the event and the organization it is about, and each tab reads its own log at `GET /activity?area=...` (newest first, with who did it and the event's title). An organization's tabs (`organizationId` given) only ever show that organization's entries: EVENTS (everything done to its events) and TEAM (its members and the organization itself) for its owner or organizer, FINANCE (its payouts and refunds) for its owner only, SETTINGS (its payout card and account) for its owner or organizer; an admin reads all of them. The platform's tabs (no organizationId) are admin-only: EVENTS, POSTS, ORGANIZATIONS, USERS, FINANCE (payouts, platform and gateway fees), DISPUTES (raised, every status or resolution change) and SETTINGS (categories, fees, ticket-sale timing). Entries say what happened as BR-EVENT-006 does (updates list only the changed fields, items keep their name; a member assignment its member and role, a payout its amount and event); they never hold secrets, card or account numbers (only brand or bank and the last 4), passwords, tokens or buyers' personal data. Buyer actions (checkout, payment methods, resale, transfers, logins) are not part of it. |
| BR-ADMIN-SETTINGS-002 | **Ticket-sale timing settings.** The two BR-EVENT-003 timing rules are platform settings kept in the database: `sales_close_minutes_before_event` (0 to 10080, default 60) and `min_sale_duration_minutes` (1 to 10080, default 60). Any signed-in user may read them (`GET /platform-settings/ticket-sales`) so the organizer's form can show them; only an admin may change them (`PUT`, 403 otherwise), which is logged in the platform SETTINGS activity as `ticket_sale_settings.updated` with before and after. New values apply to ticket types created afterwards and to sale windows edited afterwards; existing ticket types are not changed. |

## 15. Analytics & Reporting

| ID | Rule |
|---|---|
| BR-ANALYTICS-001 | An organizer may view sales analytics only for their own events (tickets sold, revenue, remaining inventory, sales-over-time). (§4.14) |
| BR-FIN-001 | An admin may view the platform's money for a period (`GET /analytics/platform/finance`): UTC days, both included (default the last 30 days, at most 366), in one currency (default PHP; other currencies are listed and left out). It counts the orders placed in the period that are paid, partly refunded or refunded, with their completed refunds. Nothing is stored: it is computed live from orders, refunds and payouts. |
| BR-FIN-002 | Platform income is the platform fee kept (nothing when the platform bore the fee; capped at what is left after refunds, so nothing on a fully refunded order). Platform net income is that minus the gateway cost the platform itself bears. The gateway fee used is what the gateway **really kept**, read from its own payment record (the payment amount minus the net it paid out: fee plus any foreign-card fee), not the quote; an order whose real fee is not known yet falls back to its recorded fee, and one with no fee information counts 0 and is flagged (`unknownFeeOrders`). The platform bears what nobody else covered: the real fee minus what the customer was charged on top and minus what comes off the organization, so all of it when the platform is the payer or no payer was recorded, and a negative amount (a surplus) when the customer was charged more than the gateway kept; and the whole fee of a fully refunded order (the gateway keeps its fee, the customer got theirs back). A partial refund does not reduce the gateway cost, and refunds count against the period the order was placed in. |
| BR-FIN-005 | The gateway's real fee is stored on the order (`gateway_fee_actual_amount`) the first time the platform summary is asked for it and the gateway can be read: for the PayMongo orders that lack it, it reads the gateway's latest 100 paid payments and matches them by payment id. It is best-effort and throttled (at most once a minute, and only when some order lacks it): a gateway that is down never breaks the summary, and an order older than those 100 payments stays unknown. Payouts are not changed by it: what comes off an organization is still the fee recorded when the order was placed. |
| BR-FIN-003 | The organizers' share in the summary is worked out per order exactly as a payout does it (gross after refunds minus the platform fee and the gateway fee that come off the organization, never more than the gross), so the summary always agrees with the payouts: it is split into money already in PAID payouts, in scheduled, processing or failed payouts, and not in any payout yet. |
| BR-FIN-004 | An organization's owner (or an admin) may view that organization's own income for a period (`GET /organizations/{orgId}/finance`), with the same period rules. It shows sales, refunds, what came off (the platform fee and the gateway fee, including fees customers paid on top), the income left and how much of it is paid out, in payouts or still owed. It never shows the platform's income or costs, and an organizer, another member or another organization's owner is refused. |
| BR-ANALYTICS-002 | An admin may view platform-wide aggregate metrics (total GMV, active organizers, event volume, and similar). (§4.14) |
| BR-ANALYTICS-003 | An event's funnel (page views, then tickets put in carts, then tickets checked out) comes from two append-only logs. **Views** (`POST /events/{id}/views`, public, always 204 whatever happens, so it never reveals whether an event exists or was counted): one per event and visitor per 30 minutes; the visitor is the signed-in user, else a SHA-256 of a random id the browser keeps (never an IP address); the event's own owner and organizers and admins are not counted; only published, on-sale, sold-out and completed events count; at most 60 a minute per client. **Cart activity**: every cart line change is logged in the same transaction as the change: ADD (a line added or its quantity raised, by the increase), REMOVE (a line deleted or lowered, by the decrease), EXPIRE (a hold that ran out and was cleaned up) and CHECKOUT (the line's order became PAID). A log write that fails never fails or undoes the cart change. The funnel (`GET /events/{id}/analytics/funnel`) is for the event's owner/organizers or an admin, by day in the event's time zone (at most 366 days); nothing before the logs started (`trackingSince`) is known. Raw rows are deleted after 13 months. |

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
| BR-FEE-002 | A rule is a percentage (0-100, up to two decimals) of the ticket total after promo discounts, rounded half up to the minor unit, a flat amount **per ticket**, or both (the fee is then the percentage on the ticket total plus the flat amount for every ticket: `fee = ticket total x percentage + flat x number of tickets`); at least one is required. A rate of 0 is allowed and waives the fee. |
| BR-FEE-003 | A rule has a scope: the platform-wide default, one organization, or one event. At most one rule per scope; the most specific one applies (event, then organization, then platform default). With no rule, no fee is charged. Removing an override makes the next less specific rule apply again. |
| BR-FEE-004 | The fee is calculated on every purchase. A rule also says who bears it (default `CUSTOMER`): `CUSTOMER` adds it on top of the ticket price (the buyer pays ticket total + fee, and the cart shows the fee before payment); `ORGANIZATION` charges the buyer nothing extra and deducts the fee from the organizer's payout. The platform cannot be the payer: `PLATFORM` is refused with 400, and rules stored with it became `CUSTOMER` (migration V51). The order keeps the payer in its snapshot, so orders placed when `PLATFORM` was allowed are unchanged. An order with a ticket total of 0 pays no fee; the flat part of a rule in a different currency than the order is skipped. |
| BR-FEE-005 | The fee is stored on the order together with a snapshot of the rule that produced it. Changing or removing a rule never changes an order already placed. |
| BR-FEE-006 | The fee is not the organizer's revenue: an event's analytics revenue is the ticket revenue without the fee. Platform-wide GMV counts what buyers paid, fee included. |
| BR-FEE-007 | The fee is deducted when the payout is generated: per order, gross = what the buyer paid minus refunds, fees = the order's platform fee unless the platform bore it (never more than the gross left after refunds), net = gross - fees, which is what the organizer is owed. A fully refunded order is not paid out (the buyer gets the fee back too). |
| BR-FEE-009 | The flat part is charged for every ticket in the order, so one cart paid in parts costs the same fees in total as paid at once (BR-CART-005). Rules stored before this change keep their numbers, which now mean "per ticket"; orders already placed keep their own fee snapshot and are never recalculated. Refunds are by amount (not per ticket) and the platform fee is still kept up to what is left after refunds (BR-FEE-007): a refund does not hand back a per-ticket share of the fee on its own. |
| BR-FEE-008 | Only an admin generates a payout, for a period (UTC days, end not in the future). It settles the organization's paid or partly refunded orders in that period that are not yet in a payout, so no order is ever paid out twice. Refunds issued after an order was settled are not netted against the payout. |

## 14e. Payment Gateway Fee (the processing fee the payment gateway charges)

| ID | Rule |
|---|---|
| BR-GWFEE-001 | Only an admin may read or change the payment gateway fee setting. It is one platform-wide setting: two numbers, like the gateway's own rate "3.125% + PHP 13.39": a percentage (0-100, up to three decimals) and an optional fixed amount (minor units + currency, 1339 = PHP 13.39) and **who pays**: `CUSTOMER` or `ORGANIZATION` (`PLATFORM` is refused with 400). The default is 0% with `CUSTOMER`, so nothing changes until an admin sets it. |
| BR-GWFEE-002 | The fee is worked out on the purchase total before it (ticket total after promo plus the platform fee): base x percentage, rounded half up to the minor unit, plus the flat amount when the order is in the flat amount's currency (a flat amount in another currency is skipped). No fee on a free order. |
| BR-GWFEE-003 | `CUSTOMER`: the fee is added on top of the price, shown to the buyer in the cart (`gatewayFee`) and in the order, and included in what they pay. It is not the organizer's money: it comes off the order's gross when the payout is generated, so the organizer keeps the ticket price. |
| BR-GWFEE-004 | `ORGANIZATION`: the buyer sees and pays nothing extra; the fee is deducted from the organizer's share when the payout is generated, together with the platform fee. |
| BR-GWFEE-005 | The platform can no longer be set as the payer (refused with 400; a stored `PLATFORM` became `CUSTOMER` in V51). Orders placed while it was allowed keep it in their snapshot: the buyer and the organizer were unaffected, and payouts and reports still treat them that way. What the platform does bear is a cost, not a setting: the gateway fee nobody else covered (see BR-FIN-002), including the extra a foreign card cost over the domestic rate the customer was charged. |
| BR-GWFEE-006 | The fee, who pays it and the percentage and flat amount used are stored on each order when it is placed. Changing the setting later never changes an order already placed or its payout. A fee the buyer paid is part of the order total and is returned to the buyer by a full refund (the gateway keeps its own fee). |
| BR-GWFEE-007 | An event's revenue and sales-over-time figures never include the fee a buyer paid on top, the same as the platform fee. Changes to the setting are audit-logged. |
| BR-GWFEE-009 | The setting can carry a second, **foreign** rate (a percentage and a fixed amount in the same currency) for cards issued outside the Philippines, because PayMongo charges those more (3.5% + PHP 15.00 domestic, 4.5% + PHP 15.00 foreign). Leaving it out means a foreign card costs the domestic rate, so existing settings behave as before. The customer is always charged the domestic rate in the cart, since the card is not known when the price is shown. When the payment arrives the order records where the card was issued (`DOMESTIC`, or `FOREIGN` for any country but PH, or unknown) and the fee that really applied: the foreign fee for a foreign card. At payout, when the organization bears the fee the fee that applied comes off its share; when the customer bears it the organizer is charged only what the customer paid and the platform absorbs the extra cost of a foreign card; when the platform bears it nothing comes off. |
| BR-GWFEE-008 | An admin can compare the setting with what the gateway really charged: `GET /payment-gateway-fee/check` reads the gateway's latest paid payments (PayMongo has no rate endpoint, so the rate comes from the payments themselves), shows the fee, the foreign-card surcharge and the net of each next to what the setting would have charged, recognises the card rate (domestic 3.5% + PHP 15.00, international 4.5% + PHP 15.00, VAT included) and suggests the matching setting. It only reads; it never changes the setting. |

## 14b. Profile Picture

| ID | Rule |
|---|---|
| BR-PROFILE-001 | A user may set, replace or remove only their own profile picture. |
| BR-PROFILE-002 | The picture must be an image uploaded through this server (POST /uploads) and still stored here; a link to anywhere else is refused. The upload rules (type and size limits) are unchanged. |
| BR-PROFILE-003 | When a picture is replaced or removed, its file is deleted from storage unless another user, ticket template, event or organization document still uses it. |
| BR-PROFILE-004 | The picture URL is only part of the existing user payloads (own profile and the admin user list); it is not added to any response that exposes private data about a user. |

## 14d. Saved Payment Methods (customer cards)

| ID | Rule |
|---|---|
| BR-CARD-001 | Any signed-in user (customer, admin or super admin) may save, list, choose the primary of and remove their own cards, and only their own. Another user's card is "not found" (404), never "forbidden" and never visible. |
| BR-CARD-002 | Only what the payment provider reports about a card is stored: the provider's payment-method id, brand, last 4 digits, expiry and holder name. A card number or security code is never stored, returned or logged; the provider's id and any token are never returned or logged. |
| BR-CARD-003 | A user's first card is always primary. A user has exactly one primary card; making another primary unsets the old one in the same transaction, and the database refuses a second primary. |
| BR-CARD-004 | The primary card cannot be removed while the user has other cards (409). The only remaining card can be removed. Removal is a soft delete and also detaches the card from the provider's customer. |
| BR-CARD-005 | A card whose expiry month has passed is refused, last 4 must be 4 digits, the holder name has a length limit and may not contain HTML, and a token that fails (`tok_fail...`) is refused. |
| BR-CARD-006 | With the Stripe gateway the card's brand, last 4 and expiry are always read from Stripe, never from the client. With the default mock gateway they come from the request and Stripe is never called. |
| BR-CARD-007 | A user's Stripe Customer is created the first time one is needed and then reused; two requests at the same moment create one. The customer id is always the signed-in user's own and is never taken from the client. |
| BR-CARD-008 | Checkout may pay with a saved card of the signed-in user (`paymentMethodId`) or with a one-off token (`paymentMethodToken`), exactly one of the two. |
| BR-CARD-009 | Adding, choosing the primary of and removing a card are audit-logged (with the last 4 only). |
| BR-CARD-010 | With the Stripe gateway a checkout really charges the card: a Stripe PaymentIntent in the order's currency for the buyer's customer and card, and only a succeeded one counts as paid. The payment's gateway reference is the PaymentIntent id. A retried checkout (same Idempotency-Key) can never charge twice. |
| BR-CARD-011 | A declined card, or a card that needs extra authentication (3-D Secure, not supported yet), is a failed payment (402): nothing is charged, no order is created, the cart and holds stay. With the Stripe gateway the payment method must be a Stripe `pm_...` id (a saved card, or one from Stripe.js). |
| BR-PM-001 | With the PayMongo gateway (`payments.gateway=paymongo`) the card is entered in the browser with PayMongo's public key; the API never receives a card number. Checkout only starts the payment (a PayMongo payment intent in PHP, amounts in centavos) and answers 202 with the intent's client key and a checkout id. |
| BR-PM-002 | BR-CART-002 still holds with PayMongo: no order exists until PayMongo confirms the payment. The signed `payment.paid` webhook issues the order, payment record and tickets (the payment id `pay_...` is kept for refunds); `payment.failed` leaves the cart and holds untouched and frees the buyer to try again. |
| BR-PM-003 | A webhook is accepted only with a valid `Paymongo-Signature` (HMAC-SHA256 of timestamp + raw body with the webhook secret); otherwise 401 and nothing changes. Processing is idempotent: a webhook delivered twice issues one order. |
| BR-PM-004 | If a payment succeeded but the order can no longer be issued (the hold expired while the buyer was paying, a ticket type was paused, the amount paid differs from the order total), the payment is refunded automatically and the buyer starts again. A refund that itself fails is logged for manual attention. |
| BR-PM-006 | With PayMongo a card is saved by a payment made with "save this card" (`saveCard`): PayMongo vaults it on the user's customer and, after the order is issued, the API stores its payment method id (`pm_...`) with the brand, last 4 and expiry. Never the card number or security code. Saving never fails or undoes the order, and never blocks the payment (if the PayMongo customer cannot be created, or PayMongo refuses card vaulting for the account, the payment goes ahead without saving and the checkout answers `cardSaving: UNAVAILABLE`; any other refusal still fails the checkout). A removed card still vaulted at PayMongo is restored when a save finds no new card; an old card with the same last 4 and expiry is upgraded. Each change is audited (`payment_method.added` / `payment_method.restored`). Vaulting must be enabled on the PayMongo account. |
| BR-PM-007 | Paying with a saved card (`paymentMethodId` + `securityCode` + `returnUrl`): the server starts the payment and attaches the stored method; the security code is used for that call only (never stored or logged). The answer says NONE (the webhook completes the order) or REDIRECT (3-D Secure). Another user's card is a 404; a card without a stored method (`canChargeAgain: false`) is a 409 "Re-enter this card to use it again"; a declined card is a 402 with the cart and holds intact and the idempotency key free again. |
| BR-PM-005 | PayMongo accepts PHP only: starting a checkout for an order in another currency is refused. A repeated start with the same Idempotency-Key returns the same pending checkout (one payment intent). Only the buyer who started a checkout can read its status. |
| BR-CARD-012 | With the Stripe gateway a refund is sent to Stripe against the original PaymentIntent. A payment made before Stripe was switched on (a mock reference) cannot be refunded in Stripe and the refund fails with a clear message. |

## 14f. Event Reviews & Recap

| ID | Rule |
|---|---|
| BR-REVIEW-001 | **Event reviews.** Someone who attended an event (holds a USED, i.e. checked-in, ticket for it; 403 otherwise) may review it once it has ended (409 before; "ended" as in BR-EVENT-005): a rating of 1 to 5 and an optional comment (max 1000 characters, no HTML). One review per user per event (a second one is a 409); its author may edit it (`PUT /events/{id}/reviews/me`) or delete it (`DELETE .../reviews/me`, after which they may review again), under the same conditions, and read it back (`GET .../reviews/me`, 404 when they have none). Anyone may read the event's summary (`GET .../reviews/summary`): the average rating (2 decimals, null with no reviews), the count, and the count per star - never comments or reviewers. Comments and reviewer names are shown only in `GET /events/{id}/reviews` (paged, newest first), to the organization's owner, an organizer with the ANALYTICS permission, or an admin (403 for anyone else), and to the review's own author. Each change is logged in the event's activity as `review.created` / `review.updated` / `review.deleted` with the rating (the comment is never logged). Reviews are soft-deleted. |
| BR-EVENT-RECAP-001 | **Event recap.** Once an event has ended (409 before), its organization's owner or an organizer with the EVENTS permission may post messages to the event's recap - a chat-style feed of text (max 2000 characters, no HTML, may be empty) and/or one picture, which must be an image uploaded through `POST /uploads` (as for posts); a message with neither is a 400. The same people may edit (`PATCH`) or delete a message; admins may delete too. The feed (`GET /events/{id}/recap`) is public for a published event that has ended, oldest first, each message with its text, picture, time, its author's name and the organization's name; for a draft or cancelled event only its managers and admins can read it (404 for everyone else). Each change is logged in the event's activity as `recap_message.created` / `updated` / `deleted`. Messages are soft-deleted, and a picture's file is removed once nothing uses it any more. |

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
