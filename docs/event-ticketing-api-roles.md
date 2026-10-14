# Roles

Who can do what, at a glance. Update this file when a role or a permission changes (the rules behind it are
BR-AUTH-001..009 in `event-ticketing-api-business-rules.md`).

✅ allowed  ❌ not allowed

## 1. The roles

| Role | Kind | Where it comes from |
|---|---|---|
| Visitor | not signed in | Nobody. Can only browse public things, register and log in. |
| Customer | account role `CUSTOMER` (`users.role`) | Every registered user. |
| Super admin | account role `SUPER_ADMIN` (`users.role`) | The one highest account. Exactly one exists (database unique index, migration V38); it is created by hand (see `db/seed/demo-data.sql`) and can never be granted through the API. |
| Admin | account role `ADMIN` (`users.role`) | Platform staff. Made an admin by the super admin only. |
| Owner | organization role `OWNER` | The applicant, once an admin approves the organization. |
| Organizer | organization role `ORGANIZER` | Given by the owner, or by the owner to themselves. |
| Check-in staff | organization role `CHECK_IN_STAFF` | Given by the owner. |
| Scanner device | device token (`ROLE_SCANNER_DEVICE`), not a user | Created for an event by an owner, organizer or admin. |

Organization roles are held **on top of** the Customer account, per organization, and can be combined (BR-AUTH-007).
So an Owner, Organizer or Check-in staff can also do everything a Customer can. Their rights apply to their own
organization's events only.

## 2. What each role can do

| | Visitor | Customer | Check-in staff | Organizer | Owner | Admin | Super admin | Scanner device |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| **Everyday use** | | | | | | | | |
| Browse public events, categories and live posts | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Register and log in | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Buy tickets (cart and checkout) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Transfer, resell, waitlist, raise a dispute on own tickets | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Edit own profile and picture | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Save, list, choose the primary and remove **own** payment cards | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Apply to create an organization | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| See own organizations (`/organizations/mine`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Inside an organization** | | | | | | | | |
| View the organization's details | ❌ | ❌ (own application only) | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Assign organizer / check-in staff roles to others | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| List the organization's users | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ (all) | ✅ (all) | ❌ |
| **Events** | | | | | | | | |
| Create events (organization must be approved) | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Edit and publish events | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Cancel or delete events | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Manage ticket types, promo codes, ticket templates, refund policy | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Manage check-in config and scanner devices | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| View orders, event analytics | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Issue refunds | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| View the organization's payouts and payout cards | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Add, remove or choose the primary payout card | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Check-in** | | | | | | | | |
| Download the offline ticket list, validate tickets, send fallback scans | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Platform administration** | | | | | | | | |
| Approve or reject organization applications | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| Suspend, reinstate or remove events, organizations, customer accounts | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| Suspend, reinstate, remove, delete or demote an **admin** account | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Make someone an admin | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Manage users, categories, posts | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| Set platform fee rules, generate payouts | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| Platform analytics, audit log, handle disputes | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |

Notes
- **Check-in staff** is a membership today, not a permission set: nothing in the API checks for it beyond letting the
  person see their organization. Scanning is done by scanner devices, which an owner, organizer or admin sets up.
  Give it real rights (for example creating its own scanner device) by adding the checks in the services.
- **Admin cannot edit or publish an event** (BR-AUTH-004): those stay with the owning organization. The super admin has the
  same limit.
- **Super admin** (BR-ADMIN-004..006): every admin power, plus being the only one who may suspend, reinstate, remove,
  delete or demote an admin and the only one who may make someone an admin. Nobody, the super admin included, can
  suspend, remove, delete or change the role of the super admin account, and the `SUPER_ADMIN` role cannot be
  granted through the API.

## 3. Organization status gates the roles

| Status | Effect on its owner, organizers and staff |
|---|---|
| `PENDING` | Application under review. The applicant has no owner role yet. |
| `REJECTED` | Application refused (with a reason). No roles. |
| `APPROVED` | Roles work normally. |
| `SUSPENDED` | Members keep their roles but cannot create or publish events until it is reinstated. |

## 4. Where it lives in the code

- Account role: `user/enums/Role.java`; the token carries it and `SecurityConfig` maps it to `ROLE_ADMIN` /
  `ROLE_CUSTOMER`.
- Organization roles: `organization/enums/OrganizationRole.java`, stored in `organization_members`; checks go through
  `OrganizationAccessGuard` (`isAdmin`, `requireAdmin`, `hasRole`).
- Admin-only endpoints are enforced in the services with `requireAdmin()`, and a few in `SecurityConfig`
  (`/users/**`, `/admin/**`, audit log, platform analytics).

To add a role: add it to the enum, then add a column or row above and the rule to the business rules.
