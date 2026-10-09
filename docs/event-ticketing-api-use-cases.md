# Event Ticketing API — Use Cases

**Version:** 1.0
**Date:** 2026-09-08
**Derived from:** `event-ticketing-api-requirements.md` v1.0 and `event-ticketing-api-business-rules.md` v1.0

This document walks through how each actor actually uses the API, as a set
of use cases grouped by actor. It doesn't introduce anything beyond what
`requirements.md` describes — where a step enforces a specific rule, it's
cross-referenced by ID (`BR-...`) to `business-rules.md` rather than
restated. Use cases are numbered per actor group (`UC-<GROUP>-<N>`).

## Actor: Any Registered User

### UC-USER-01: Register an Account

- **Preconditions:** None — open to the public.
- **Main flow:**
  1. User submits registration details: first name, optional middle name, last name, date of birth, optional phone number, email and password.
  2. API creates the account as an Attendee by default (a role sent by the client is ignored).
- **Exception flow:** A name with anything but letters, spaces, periods, apostrophes or hyphens, a birth date that is not in the past (or is more than 120 years ago), a phone number that is not +digits, or an email with
  characters outside plain ASCII letters, digits and `. _ % + -` (for example `x[]@gmail.com`) → rejected (400).
  An email that already belongs to another account (any letter case) → 409.
- **Postconditions:** User can now log in.
- **Related rules:** —

### UC-USER-02: Log In

- **Preconditions:** User has a registered account.
- **Main flow:**
  1. User submits credentials.
  2. API authenticates and issues a token for subsequent requests. (§4.1)
- **Exception flow:** Invalid credentials → login rejected.
- **Related rules:** —

### UC-USER-03: Set or Remove a Profile Picture

- **Preconditions:** User is signed in and has uploaded an image through the upload endpoint.
- **Main flow:**
  1. User sends the uploaded image's URL as their avatar URL (an empty value removes the picture).
  2. API stores the URL on the account and returns the updated profile.
  3. If a previous picture is no longer used by anything, API deletes its file.
- **Exception flow:** A URL that is not one of this server's uploaded files → rejected (400); nothing changes.
- **Related rules:** BR-PROFILE-001, BR-PROFILE-002, BR-PROFILE-003, BR-PROFILE-004

### UC-USER-04: Read Sales and Announcements

- **Preconditions:** None - open to the public.
- **Main flow:**
  1. Visitor lists posts, optionally for one event or only the site-wide ones.
  2. API returns a page of posts, newest first, each with its event title when it has one.
- **Related rules:** BR-POST-003

## Actor: Attendee (Buyer)

### UC-ATTND-01: Search & Browse Published Events

- **Preconditions:** None — public.
- **Main flow:**
  1. Attendee searches/filters by keyword, category, location/distance,
     date range, or price range.
  2. API returns matching **published** events, paginated and sortable
     (date, popularity, price).
- **Related rules:** BR-SEARCH-001

### UC-ATTND-02: Add Ticket(s) to Cart

- **Preconditions:** Attendee is logged in; event is published and on
  sale.
- **Main flow:**
  1. Attendee selects a ticket type (and, for reserved seating, a
     specific seat) and adds it to their cart.
  2. API places the seat/GA quantity on a temporary hold (10–15 min).
- **Exception flow:** Seat/quantity no longer available → item rejected.
- **Postconditions:** Cart holds the item until it expires or checkout
  completes.
- **Related rules:** BR-INV-003, BR-INV-004, BR-INV-005, BR-INV-006

### UC-ATTND-03: Apply a Promo Code

- **Preconditions:** Attendee has an active cart.
- **Main flow:**
  1. Attendee enters a promo code.
  2. API validates it against the applicable ticket types, usage limits,
     and validity window, then adjusts the cart total.
- **Exception flow:** Code is expired, exhausted, or inapplicable → API
  rejects it with a clear error.
- **Related rules:** BR-PROMO-001–007, BR-CART-001

### UC-ATTND-04: Checkout / Complete Purchase

- **Preconditions:** Cart has at least one held item; payment method
  ready.
- **Main flow:**
  1. Attendee submits payment.
  2. API charges via the payment gateway (no raw card data touches the
     API).
  3. On success, API creates an order, converts each hold into a sale,
     and issues one ticket credential (+ ticket number) per item.
- **Exception flow:** Payment fails → cart and holds remain intact for
  retry; a hold that expires mid-checkout fails the attempt.
- **Postconditions:** Order exists in `pending`→`paid` state; tickets are
  issued and ready for delivery.
- **Related rules:** BR-CART-002, BR-CART-003, BR-PAY-001, BR-TICKET-001,
  BR-TICKET-002, BR-NFR-008 (idempotent — a retried checkout must not
  double-charge or double-issue)

### UC-ATTND-05: View My Orders & Tickets

- **Preconditions:** Attendee has at least one order.
- **Main flow:**
  1. Attendee requests their order/ticket history.
  2. API returns orders they own, each ticket's status, seat/ticket-type,
     and ticket number (never the raw scannable credential itself).
- **Related rules:** BR-CART-004

### UC-ATTND-06: Download/View a Ticket Artifact

- **Preconditions:** Attendee owns a paid, issued ticket.
- **Main flow:**
  1. Attendee requests the ticket artifact (digital or physical format).
  2. API renders/returns the PDF/image carrying the QR/barcode and the
     visible ticket number, using the event's ticket template.
- **Related rules:** BR-TICKET-007, BR-TICKET-008, BR-TICKET-010

### UC-ATTND-07: Join a Waitlist

- **Preconditions:** Desired ticket type or event is sold out.
- **Main flow:**
  1. Attendee joins the waitlist.
  2. When inventory frees up, API notifies waitlisted users in join order,
     each given a time-limited window to purchase before it passes to the
     next person.
- **Related rules:** BR-WAIT-001, BR-WAIT-002, BR-WAIT-003

### UC-ATTND-08: Transfer a Ticket to Another User

- **Preconditions:** Attendee owns the ticket; recipient is a registered
  user.
- **Main flow:**
  1. Attendee initiates a transfer to the recipient.
  2. API updates the name/identity on the ticket and invalidates the
     previous credential, issuing a new one to the recipient.
- **Related rules:** BR-TRANSFER-001, BR-TRANSFER-002, BR-TRANSFER-005

### UC-ATTND-09: Resell a Ticket

- **Preconditions:** Event's organizer has enabled resale for that event.
- **Main flow:**
  1. Attendee lists their ticket for resale, within any organizer-set
     price cap.
  2. On sale, API invalidates the seller's credential and issues a new
     one to the buyer.
- **Exception flow:** Organizer has resale disabled for the event → listing
  rejected.
- **Related rules:** BR-TRANSFER-003, BR-TRANSFER-004, BR-TRANSFER-005

## Actor: Prospective Organization Owner (any registered user)

### UC-ORG-01: Apply to Create an Organization

- **Preconditions:** User is registered and logged in.
- **Main flow:**
  1. User submits an organization application, including required
     verification documents (e.g. business permit).
  2. Application enters a pending state awaiting admin review.
- **Postconditions:** Applicant holds no organization privileges until
  approved.
- **Related rules:** BR-ORG-001, BR-ORG-002, BR-ORG-005

## Actor: Organization Owner

### UC-ORG-02: See My Organizations

- **Preconditions:** User is signed in.
- **Main flow:**
  1. User asks for their organizations.
  2. API returns every organization the user is a member of (owner, organizer or check-in staff) or applied for,
     whatever its status (pending, approved, rejected, suspended), oldest first - an empty list if there are none.
  3. The client uses the approved ones to pick the organization for a new event, and shows a notice for each
     suspended one (its events cannot be created or published until it is reinstated).
- **Related rules:** BR-ORG-006

### UC-OWNER-01: Assign a Role to Another User

- **Preconditions:** Caller is the organization's owner; target user is
  registered.
- **Main flow:**
  1. Owner assigns a target user the organizer or scanner/check-in-staff
     role, scoped to that organization.
- **Exception flow:** Caller is an organizer (not the owner) attempting to
  assign another user → rejected.
- **Related rules:** BR-AUTH-005, BR-AUTH-006

### UC-OWNER-02: Self-Assign an Additional Role

- **Preconditions:** Caller already holds a role (owner or organizer) in
  the organization.
- **Main flow:**
  1. Caller assigns themselves an additional role they're entitled to
     (e.g. an owner also acting as organizer or scanner).
  2. No separate approval is required.
- **Related rules:** BR-AUTH-007, BR-AUTH-008

## Actor: Event Organizer (including an owner acting as organizer)

### UC-EVENT-01: Create an Event

- **Preconditions:** Caller holds the organizer role for a
  (conceptually) approved organization.
- **Main flow:**
  1. Organizer submits event details: title, description, category,
     venue/location, start/end date-time, timezone, images. The category
     must be an active category from `GET /api/v1/categories` (BR-EVENT-004).
  2. API creates the event in `draft` status.
- **Related rules:** BR-EVENT-001, BR-EVENT-002, BR-EVENT-004

### UC-EVENT-02: Update an Event

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer submits updated fields.
  2. API applies the update.
- **Exception flow:** A different organizer attempts to update it →
  rejected. (§4.1)
- **Related rules:** BR-AUTH-002

### UC-EVENT-03: Publish / Unpublish / Cancel an Event

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer transitions the event's status (e.g. `draft` →
     `published`, or → `cancelled`).
  2. If cancelled, API triggers an automated refund/credit workflow for
     every ticket holder.
- **Note:** publishing and updating are for the owning organization's
  owner/organizer only; a platform admin cannot do either (an admin can
  view every event's status, cancel or delete it, or moderate it - see
  UC-ADMIN-02 and BR-AUTH-004).
- **Related rules:** BR-EVENT-001, BR-PAY-005

### UC-EVENT-04: Create a Ticket Type

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer defines a ticket type (e.g. GA, VIP, Early Bird) with
     price, currency, quantity, sale window, and per-order limit.
  2. Organizer may later pause selling the ticket type and resume it (for
     example while a problem is fixed): a paused type can't be added to a
     cart or checked out.
  3. Organizer drags the ticket types into the order buyers should see them
     in, and that arrangement is saved.
- **Related rules:** BR-EVENT-003, BR-TICKET-012, BR-TICKET-013

### UC-EVENT-05: Define a Ticket Template

- **Preconditions:** Organizer owns the event (or ticket type).
- **Main flow:**
  1. Organizer submits a physical and/or digital ticket layout —
     branding elements plus dynamic fields (attendee name, ticket type,
     seat, ticket number, event details) and the platform-generated
     QR/barcode.
- **Related rules:** BR-TICKET-007, BR-TICKET-010

### UC-EVENT-06: Create a Promo Code

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer defines discount type (percentage/fixed), applicable
     ticket types, usage limits, and validity window.
  2. Organizer may later edit the promo code, pause and resume it, or delete
     it while nothing has used it; a used code can only be paused.
- **Related rules:** BR-PROMO-001-004, BR-PROMO-008, BR-PROMO-009

### UC-EVENT-07: Set an Event's Refund Policy

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer configures the refund policy: refundable up to N days
     before the event, no refunds, or custom.
- **Related rules:** BR-PAY-004

### UC-EVENT-08: Issue a Refund

- **Preconditions:** Order exists on the organizer's event (or caller is
  admin).
- **Main flow:**
  1. Organizer or admin initiates a full or partial refund, subject to
     the event's refund policy.
- **Related rules:** BR-PAY-002, BR-PAY-003

### UC-EVENT-09: Configure Check-in Mode & Scanner Devices

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer chooses the event's check-in mode: standard (online) or
     pure offline — this is always an explicit manual choice.
  2. In standard mode, organizer sets the authorized device count and,
     optionally, overrides the offline-fallback expiry (default 5 min).
  3. In pure offline mode, organizer authorizes exactly one real scanner
     device (pre-fetches the full dataset).
- **Related rules:** BR-CHECKIN-004–009

### UC-EVENT-10: View Sales Analytics

- **Preconditions:** Organizer owns the event.
- **Main flow:**
  1. Organizer requests analytics for their event(s): tickets sold,
     revenue, remaining inventory, sales-over-time.
- **Related rules:** BR-ANALYTICS-001

## Actor: Check-in Staff / Scanner

### UC-SCAN-01: Validate a Ticket (Online)

- **Preconditions:** Scanner is authorized for the event; device is
  online.
- **Main flow:**
  1. Scanner submits a scanned QR/barcode value to the validation
     endpoint.
  2. API checks and marks the ticket "used" in near-real-time, returning
     valid / invalid / duplicate / wrong-event.
- **Related rules:** BR-CHECKIN-001, BR-CHECKIN-002, BR-CHECKIN-003

### UC-SCAN-02: Validate a Ticket Offline

- **Preconditions:** Standard mode: device recently online, fallback file
  not yet expired. Pure offline mode: device is the sole authorized real
  device with the pre-fetched dataset.
- **Main flow:**
  1. Scanner validates the scan against the local dataset/fallback file
     without a live server call.
- **Exception flow:** Standard-mode fallback file has expired (>5 min
  offline, or organizer-configured window) → device must stop validating
  locally until reconnected. (§4.11)
- **Related rules:** BR-CHECKIN-006, BR-CHECKIN-007, BR-CHECKIN-008

### UC-SCAN-03: Submit Fallback Scans After Reconnecting

- **Preconditions:** Pure offline mode; a non-authorized fallback device
  recorded blind scans while the sole real device was down.
- **Main flow:**
  1. Fallback device uploads its batch of recorded scans once back
     online.
  2. API reconciles each against current ticket state and surfaces any
     duplicates found for the organizer's after-the-fact review.
- **Related rules:** BR-CHECKIN-009, BR-CHECKIN-010

### UC-SCAN-04: Query Event Check-in Status

- **Preconditions:** Scanner is authorized for the event.
- **Main flow:**
  1. Scanning client queries the event's current mode, its own
     authorization status, and (standard mode) the configured
     offline-fallback expiry window.
- **Related rules:** BR-CHECKIN-011

## Actor: Platform Admin

### UC-ADMIN-01: Review an Organization Application

- **Preconditions:** A pending application exists.
- **Main flow:**
  1. Admin reviews the application and its verification documents.
  2. Admin approves or rejects it.
- **Postconditions:** On approval, the applicant becomes that
  organization's owner. On rejection, the applicant retains no
  organization privileges.
- **Related rules:** BR-ORG-003, BR-ORG-004, BR-ORG-005, BR-ADMIN-001

### UC-ADMIN-02: Suspend or Remove an Account, Event, or Organization

- **Preconditions:** Caller is an admin.
- **Main flow:**
  1. Admin suspends or removes an organizer, event, or user account for a
     policy violation.
- **Related rules:** BR-ADMIN-002

### UC-ADMIN-03: View & Intervene in a Dispute

- **Preconditions:** A dispute exists (e.g. buyer-reported fraud,
  chargeback).
- **Main flow:**
  1. Admin reviews the dispute and takes action.
- **Related rules:** BR-ADMIN-003

### UC-ADMIN-04: View Platform-Wide Analytics

- **Preconditions:** Caller is an admin.
- **Main flow:**
  1. Admin requests platform-wide metrics: total GMV, active organizers,
     event volume, and similar aggregates.
- **Related rules:** BR-ANALYTICS-002

### UC-ADMIN-05: Publish, Schedule, Hide or Remove a Sale or Announcement

- **Preconditions:** Caller is an admin.
- **Main flow:**
  1. Admin publishes a post (site-wide or for one event) with a kind, title, optional body and optional picture
     (an image uploaded beforehand through the upload endpoint).
  2. API stores it and returns it; it shows up in the public list.
  3. Admin may add, replace or remove the picture, and may schedule the post (it appears at its publish time), give it an expiry (it disappears then) or hide it
     (it is kept off the public list until unhidden), and may change these or the text later.
  4. Later, admin removes the post; it no longer shows up.
- **Exception flow:** Unknown or deleted event → 404. Blank or HTML title, too-long title or body → 400. A picture URL that is not one of this server's uploaded files → 400. An expiry in the past, or not after the publish time → 400. Non-admin → 403. Changing or removing an unknown or already removed post → 404.
- **Related rules:** BR-POST-001, BR-POST-002, BR-POST-004, BR-POST-005, BR-POST-006

### UC-ADMIN-06: Set the Platform Fee

- **Preconditions:** Caller is an admin.
- **Main flow:**
  1. Admin sets the platform-wide default fee as a percentage or a flat amount per order.
  2. Admin may override it for one organization or one event (a rate of 0 waives the fee), and may remove an override again.
  3. Admin may check which rule currently applies to an event.
- **Exception flow:** Unknown organization or event → 404. Missing, negative or over-100 percentage, a flat amount without currency, or a rule that mixes both kinds → 400. Non-admin → 403.
- **Related rules:** BR-FEE-001, BR-FEE-002, BR-FEE-003

### UC-ADMIN-07: Generate an Organization Payout

- **Preconditions:** Caller is an admin; the organization has paid orders in the period that are not in a payout yet.
- **Main flow:**
  1. Admin picks the organization and a period (start and end day).
  2. API totals the due orders: gross (paid minus refunds), the platform fees, and the net the organizer is owed.
  3. API records a scheduled payout and marks those orders as settled.
  4. The organization's owner or organizer can then see the payout with its fees.
- **Exception flow:** Nothing is due in the period, or the orders use more than one currency → 409. End before start or in the future → 400. Non-admin → 403.
- **Related rules:** BR-FEE-007, BR-FEE-008, BR-PAY-006

## Not covered (no requirement to derive a use case from)

Notifications (§4.12) aren't modeled as their own use cases here since
they're system-triggered side effects of the use cases above (e.g. a
successful checkout triggers an order-confirmation notification), not a
user-initiated action. Non-functional rules (`BR-NFR-*`) constrain *how*
the use cases above must behave (encryption, rate limiting, audit
logging, idempotency) rather than describing use cases of their own.
