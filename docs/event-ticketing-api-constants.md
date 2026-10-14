# Constant values

Every fixed value the API uses, in one place. Update this file when you add or rename a value.

## Where the constants live

- **Event categories** are the only constants stored as data: the `categories` table, filled by migration
  `V25__add_categories.sql` and editable by an admin.
- **Everything else is a Java enum** (under each module's `enums/` package). The database stores the enum's name as
  plain text (for example `'ON_SALE'`) and has no CHECK constraint, so the code is the only place the allowed values are
  enforced. To add or rename a value you change the enum and deploy.
- Roles and what each may do are in `event-ticketing-api-roles.md`.

## 1. Event categories (`categories` table)

An event's `category` must be the name of an **active** category (matched ignoring case). Admins can add, rename, reorder and
deactivate them, so the live list is the table; these are the defaults V25 inserts.

| Sort | Name | Slug | Description |
|---:|---|---|---|
| 10 | Music | `music` | Concerts, gigs and live performances |
| 20 | Sports | `sports` | Games, matches and tournaments |
| 30 | Arts & Theatre | `arts-theatre` | Plays, exhibitions, comedy and dance |
| 40 | Conference | `conference` | Conferences, summits and expos |
| 50 | Festival | `festival` | Multi-act and multi-day festivals |
| 60 | Food & Drink | `food-drink` | Tastings, food fairs and dining events |
| 70 | Nightlife | `nightlife` | Club nights, parties and DJ sets |
| 80 | Community | `community` | Meetups, local and cultural gatherings |
| 90 | Workshop & Education | `workshop-education` | Classes, workshops and training sessions |
| 100 | Family | `family` | Kid-friendly and family events |
| 110 | Business & Networking | `business-networking` | Networking, trade and career events |
| 120 | Charity | `charity` | Fundraisers and charity events |

## 2. Enums

### Accounts and organizations
| Enum | Values | What it is for |
|---|---|---|
| `Role` | `CUSTOMER`, `ADMIN`, `SUPER_ADMIN` | A user's account role; exactly one `SUPER_ADMIN` can exist |
| `AccountStatus` | `ACTIVE`, `SUSPENDED` | Whether an account may sign in (admin moderation) |
| `OrganizationRole` | `OWNER`, `ORGANIZER`, `CHECK_IN_STAFF` | A member's role inside one organization |
| `OrganizationStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` | Where an organization stands (application, approval, suspension) |

### Events and tickets
| Enum | Values | What it is for |
|---|---|---|
| `EventStatus` | `DRAFT`, `PUBLISHED`, `ON_SALE`, `SOLD_OUT`, `CANCELLED`, `COMPLETED`, `SUSPENDED` | An event's lifecycle; `SUSPENDED` is an admin action |
| `TicketTypeKind` | `GENERAL_ADMISSION`, `RESERVED_SEATING` | Whether a ticket type is open entry or tied to a seat |
| `SalesStatus` | `ACTIVE`, `PAUSED` | Whether a ticket type can currently be bought |
| `SeatStatus` | `AVAILABLE`, `HELD`, `SOLD` | A seat's state (held while in a cart) |
| `TicketStatus` | `VALID`, `USED`, `TRANSFERRED`, `REFUNDED`, `CANCELLED` | An issued ticket's state |

### Orders, payments and money
| Enum | Values | What it is for |
|---|---|---|
| `OrderStatus` | `PENDING`, `PAID`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED` | An order's state |
| `PayeeType` | `ORGANIZATION`, `USER` | Who is paid for an order (the organization, or a reselling user) |
| `PaymentStatus` | `PENDING`, `COMPLETED`, `FAILED` | A payment-gateway charge |
| `RefundStatus` | `PENDING`, `COMPLETED`, `FAILED` | A refund |
| `RefundRuleType` | `REFUNDABLE_UNTIL_N_DAYS`, `NO_REFUNDS`, `CUSTOM` | An event's refund policy |
| `PayoutStatus` | `SCHEDULED`, `PAID`, `FAILED` | An organization payout |
| `FeeScope` | `PLATFORM`, `ORGANIZATION`, `EVENT` | What a platform fee rule applies to (most specific wins) |
| `FeeType` | `PERCENTAGE`, `FLAT` | How a platform fee is worked out |
| `DiscountType` | `PERCENTAGE`, `FIXED` | How a promo code discounts |
| `PromoCodeStatus` | `ACTIVE`, `PAUSED` | Whether a promo code can be used |

### Resale and transfer
| Enum | Values | What it is for |
|---|---|---|
| `ResaleListingStatus` | `ACTIVE`, `SOLD`, `CANCELLED`, `EXPIRED` | A ticket put up for resale |
| `PriceCapRule` | `FACE_VALUE`, `FACE_VALUE_PLUS_FEE`, `NONE` | The highest price a ticket may be resold for |
| `TransferSource` | `DIRECT_TRANSFER`, `RESALE` | How a ticket changed hands |

### Check-in
| Enum | Values | What it is for |
|---|---|---|
| `CheckInMode` | `STANDARD`, `PURE_OFFLINE` | How an event's check-in works |
| `CheckInResult` | `VALID`, `DUPLICATE`, `INVALID`, `WRONG_EVENT` | The outcome of one scan |
| `CheckInSourceType` | `SCANNER_DEVICE`, `RECONCILED_FALLBACK` | Where a scan record came from |
| `ScannerDeviceStatus` | `ACTIVE`, `REVOKED` | Whether a scanner device may be used |

### Posts
| Enum | Values | What it is for |
|---|---|---|
| `PostKind` | `ANNOUNCEMENT`, `SALE` | The type of a post |
| `PostStatus` | `HIDDEN`, `EXPIRED`, `SCHEDULED`, `LIVE` | Where a post stands now (only `LIVE` is public) |

### Notifications
| Enum | Values | What it is for |
|---|---|---|
| `NotificationType` | `ORDER_CONFIRMATION`, `PAYMENT_RECEIPT`, `EVENT_REMINDER`, `EVENT_CHANGE`, `EVENT_CANCELLATION`, `WAITLIST_AVAILABILITY`, `REFUND_CONFIRMATION`, `DISPUTE_RESOLVED`, `ACCOUNT_MODERATION_ACTION` | What a notification is about |
| `NotificationChannel` | `EMAIL`, `SMS`, `PUSH` | How it is delivered |
| `NotificationStatus` | `PENDING`, `SENT`, `FAILED` | Delivery state |

### Moderation and disputes
| Enum | Values | What it is for |
|---|---|---|
| `ModerationActionType` | `SUSPEND`, `REINSTATE`, `REMOVE` | What an admin did |
| `ModerationTargetType` | `ORGANIZATION`, `EVENT`, `USER` | What the action was done to |
| `DisputeStatus` | `OPEN`, `INVESTIGATING`, `RESOLVED`, `DISMISSED` | A dispute's state |

### Ticket designer
| Enum | Values | What it is for |
|---|---|---|
| `TicketTemplateFormat` | `PHYSICAL`, `DIGITAL` | Which kind of ticket a template is for |
| `CodeType` | `QR`, `BARCODE`, `NONE` | The scannable code printed on a ticket |
| `TextAlign` | `LEFT`, `CENTER`, `RIGHT` | Alignment of a text field |
| `BackgroundFit` | `COVER`, `CONTAIN`, `STRETCH`, `CUSTOM` | How a background image fills the ticket |
| `TextFieldKey` | `TICKET_TYPE`, `SECTION`, `ROW`, `SEAT`, `TICKET_NUMBER`, `ATTENDEE_NAME`, `EVENT_NAME`, `EVENT_DATE`, `EVENT_TIME`, `VENUE`, `CUSTOM` | Which value a text field prints |

## Not constants (left out on purpose)

- Platform fee rules: set by an admin in `platform_fee_rules`, empty until then.
- Settings such as limits, cache times and JWT lifetimes: `application.properties` (`app.*`).
