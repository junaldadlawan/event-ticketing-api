# Event Ticketing API — Future Features & Enterprise-Readiness Backlog

**Version:** 1.0
**Date:** 2026-10-02
**Derived from:** a code/infra/docs review against `requirements.md` §5
(non-functional requirements), `business-rules.md` (`BR-NFR-*`), and the
deferred/out-of-scope items already recorded in
`event-ticketing-api-roadmap.md`.

`event-ticketing-api-roadmap.md` tracks the **functional** build (Phases
0–14, all functional phases done). This document tracks everything that
remains between "all endpoints work" and "safe to run for real customers":
requirements the spec already promises but the code doesn't yet deliver,
operability, hardening, and governance.

Status markers (same as the roadmap):

- ✅ Done
- ⚠️ Partially done
- ⬜ Not started

Priority:

- **P0** — blocks any real production launch (security / money / legal)
- **P1** — needed to run and support it reliably
- **P2** — quality, hygiene, and scale
- **P3** — nice to have

Evidence column cites the file or finding that shows the current state, so
each item can be re-verified rather than taken on faith.

---

## 1. Security & Compliance

| # | Item | Pri | Status | Current state / evidence | Spec rule |
|---|---|---|---|---|---|
| S-1 | **TLS everywhere** — ACM certificate, HTTPS :443 listener, 301 redirect from :80, HSTS | P0 | ⬜ | ALB listens on :80 only (`deploy/aws/terraform/alb.tf`); JWTs and passwords currently cross the wire in cleartext. Needs a domain first. | BR-NFR-002 |
| S-2 | **Rate limiting & abuse protection** on checkout, promo-code validation, login, register | P0 | ⬜ | No limiter, WAF, or API gateway anywhere in code or Terraform. | BR-NFR-004 |
| S-3 | **Login brute-force protection** — per-account/IP throttling or temporary lockout | P0 | ⬜ | No failed-attempt tracking found. | BR-NFR-004 |
| S-4 | **Data-subject rights** — export-my-data and true erasure/anonymization (not just soft-delete) | P0 | ⬜ | Deletion is soft-delete only (`deletedAt`); no export endpoint; not on the roadmap. Needs a retention policy decision first. | BR-NFR-006 |
| S-5 | **Real payment gateway** (tokenized/hosted fields, webhooks, async payment states, reconciliation) | P0 | ⬜ | Only `MockPaymentGatewayClient`. Raw card data must never touch the API. | §5.3 PCI-DSS |
| S-6 | **AWS WAF** in front of the ALB (managed rule sets, bot control, IP reputation) | P1 | ⬜ | Not present. Complements S-2. | BR-NFR-004 |
| S-7 | **CORS policy** for browser clients | P1 | ⬜ | No CORS configuration in `src/main/java`. | — |
| S-8 | **Replace order-dependent URL matchers with method security** (`@PreAuthorize`) plus a test that asserts the auth requirement of every endpoint | P1 | ⬜ | `SecurityConfig` is ~100 lines of ordered matchers with repeated "must precede the broader permitAll" comments; one reorder silently exposes an endpoint. | — |
| S-9 | **Refresh-token hardening** — rotation with reuse detection, per-device sessions, "log out everywhere" | P2 | ⬜ | Refresh tokens exist (`V5`); rotation/reuse-detection behavior not verified. Review before launch. | — |
| S-10 | **Dependency & code scanning** — Dependabot, CodeQL/SAST, secret scanning, SBOM | P1 | ⬜ | `.github/` contains only `ci.yml` and `deploy.yml`. | — |
| S-11 | **Immutable image tags** in ECR | P2 | ⚠️ | `scan_on_push = true` is already set (`ecr.tf`); tags are `MUTABLE`. SHA-tagging in CI mitigates but doesn't enforce. | — |
| S-12 | **Secret rotation** for the DB password and the three signing secrets | P2 | ⬜ | Secrets live in Secrets Manager but have no rotation configured; rotating the ticket/device credential secrets also needs a re-sign strategy for already-issued credentials. | — |
| S-13 | **Remove dev-default secrets and `ddl-auto=update` from the base properties file**; fail startup if a dev secret is detected outside the `dev` profile | P1 | ⬜ | Base `application.properties` still carries `dev-only-...` secrets, `show-sql=true`, and `ddl-auto=update`. The prod profile overrides them, but the safe default should be the strict one. | — |

---

## 2. Reliability, Scalability & Operations

| # | Item | Pri | Status | Current state / evidence | Spec rule |
|---|---|---|---|---|---|
| R-1 | **High availability** — Multi-AZ RDS, ≥2 ECS tasks across AZs, one NAT per AZ | P1 | ⬜ | `desired_count` defaults to 1; RDS `multi_az = false`; single NAT gateway. | §5.2 (99.9%) |
| R-2 | **Database durability** — `deletion_protection = true`, `skip_final_snapshot = false`, tested restore procedure, PITR drill | P1 | ⬜ | Both currently set to the unsafe value (`rds.tf` lines 27–28); 7-day backups configured but restore never exercised. | §5.2 |
| R-3 | **Autoscaling** on CPU / request count, with scheduled pre-scaling for known on-sales | P1 | ⬜ | No scaling policies. | §5.1 |
| R-4 | **Spike handling** — virtual waiting room / queue for on-sale moments, graceful degradation instead of failure | P1 | ⬜ | No queueing; open question on expected scale in `requirements.md` §7 must be answered first. | §5.1, BR-NFR-001 |
| R-5 | **Caching** for hot read paths (event search/detail, ticket types) | P2 | ⬜ | No `@Cacheable`/cache layer; target is sub-second p95 reads. | §5.1 |
| R-6 | **Pagination hardening** — enforce a maximum page size on every list endpoint; paginate the remaining unbounded lists | P2 | ⚠️ | No page-size cap found in code. `GET /users/me/notifications` and `GET /users/me/waitlist-entries` are still unpaginated lists (noted in the roadmap, Phase 11). | — |
| R-7 | **Multiple environments** (dev / staging / prod) with separate state, accounts or at least separate stacks | P1 | ⬜ | A single `staging` environment exists. | — |
| R-8 | **Deploy safety** — manual approval gate for prod, `terraform plan` on PRs, automatic rollback on failed health check, deployment circuit breaker | P1 | ⬜ | `deploy.yml` runs `terraform apply -auto-approve` straight from `main`; ECS circuit breaker is disabled. | — |
| R-9 | **Test gate on the deploy path** — drop `-DskipTests` from the Dockerfile or make `deploy.yml` depend on a passing `ci.yml` run | P1 | ⬜ | `ci.yml` exists and passes (1194 tests) but is independent of `deploy.yml`; the Docker build skips tests. | — |
| R-10 | **Scheduler infrastructure** with a distributed lock (e.g. ShedLock) so jobs run once when ECS runs >1 task | P0 | ⬜ | Zero `@Scheduled`/`@Async` in the codebase. Unblocks N-2, N-3, N-4, and P-1 below. | — |
| R-11 | **Zero-downtime migrations policy** — expand/contract pattern, migration lint in CI | P2 | ⬜ | Two production-blocking migration bugs (`V4`, missing `timezone`) were found only on the first real deploy because local dev masked them with `ddl-auto=update`. A CI step that runs migrations against an empty database and then Hibernate `validate` would have caught both. | — |

---

## 3. Observability

| # | Item | Pri | Status | Current state / evidence | Spec rule |
|---|---|---|---|---|---|
| O-1 | **Spring Boot Actuator** with separate liveness/readiness probes; point the ALB/ECS health check at it instead of a DB-backed business endpoint | P1 | ⬜ | No Actuator in `pom.xml`; ALB and Docker `HEALTHCHECK` hit `/api/v1/events`. | §5.5 |
| O-2 | **Metrics** (Micrometer → CloudWatch/Prometheus): request latency by endpoint, checkout success/failure, inventory holds, payment gateway errors | P1 | ⬜ | Not present. | §5.5 |
| O-3 | **Structured JSON logging with correlation IDs** (request id in MDC, propagated in responses) | P1 | ✅ | Done: `RequestIdFilter` (`X-Request-Id`), JSON logs in the `prod` profile, a dedicated `AUDIT` logger for business transactions, dev/prod log levels. See `docs/event-ticketing-api-logging.md`. Remaining: alarms on the logs (O-4). | §5.5 |
| O-4 | **CloudWatch alarms + SNS paging** — 5xx rate, unhealthy targets, task restarts, RDS CPU/storage/connections, payment failures | P1 | ⬜ | No alarms, dashboards, or SNS topics in Terraform. | §5.5 |
| O-5 | **Distributed tracing** (OpenTelemetry) across API → DB → payment gateway | P3 | ⬜ | Not present. | §5.5 |
| O-6 | **Business dashboards** over the existing analytics endpoints | P3 | ⬜ | `GET /events/{id}/analytics` and `/analytics/platform` exist (Phase 14) but nothing visualizes them. | — |

---

## 4. Functional Gaps Already Deferred in the Roadmap

These are recorded as "out of scope, documented rather than overlooked" in
`event-ticketing-api-roadmap.md`; collected here so they have one home.

| # | Item | Pri | Status | Notes |
|---|---|---|---|---|
| N-1 | **Real email/SMS/push provider** (SES or similar) with bounce/complaint handling and templating | P0 | ⬜ | Only `MockEmailSender` exists. |
| N-2 | **`EVENT_REMINDER` notifications** (e.g. 24h before start) | P1 | ⬜ | Time-based; needs R-10. |
| N-3 | **`EVENT_CHANGE` notifications** | P2 | ⬜ | No trigger exists today because start/end/venue are immutable after creation. Decide whether event rescheduling becomes a feature; if so, this fires from it. |
| N-4 | **Waitlist offer lifecycle** — redeem endpoint plus expiry cascade to the next person in line | P1 | ⬜ | `notifiedAt`/`offerExpiresAt` are populated but there is no API to redeem an offer and no job to cascade an expired one (BR-WAIT-003). Needs R-10. |
| P-1 | **Payout generation job** | P1 | ⬜ | `GET /organizations/{id}/payouts` exists but the table is always empty — nothing generates payouts. Needs R-10 and S-5. |
| P-2 | **Tax / VAT handling and multi-currency** | P1 | ⬜ | Open decision in `requirements.md` §7. Analytics currently assumes one currency per scope and falls back to `"USD"`. |
| P-3 | **Settlement reconciliation** between gateway reports, `Payment`, `Refund`, and `Payout` | P1 | ⬜ | Needed before real money moves. |
| C-1 | **Cart hold-expiry background sweep** | P2 | ⚠️ | Holds expire lazily at query time (roadmap Phase 5 decision); an active sweep would also trigger waitlist notification sooner. |

---

## 5. Data & Schema Hygiene

| # | Item | Pri | Status | Notes |
|---|---|---|---|---|
| D-1 | **Remove `ddl-auto=update`** from the base and dev profiles; Flyway is now complete | P1 | ⬜ | See S-13. |
| D-2 | **Widen `events.created_by`/`updated_by` from `VARCHAR(20)` to `VARCHAR(255)`** | P2 | ⬜ | Every other table uses 255, so the events audit trail can't hold a user id. Requires a new migration (do not edit applied ones). |
| D-3 | **Wire an `AuditorAware` bean** so `created_by`/`updated_by` are populated automatically instead of per-service by hand | P2 | ⬜ | Listed as Phase 0 in the roadmap and still open. |
| D-4 | **Enforce soft-delete filtering centrally** (`@SQLRestriction`/`@Where`) rather than per-query | P2 | ⬜ | Easy to forget in a new query. |
| D-5 | **Real foreign keys, or a documented integrity-check job** | P3 | ⬜ | The schema deliberately has no FK constraints (see V6–V9 headers); orphaned rows are possible if application logic has a bug. At minimum, add a periodic orphan-detection report. |
| D-6 | **Index review** against real query plans (`EXPLAIN`) once representative data exists | P2 | ⬜ | Several indexes exist; none validated under load. |
| D-7 | **Data retention & archival policy** for audit log, notifications, check-in records, idempotency keys | P2 | ⬜ | All append-only tables grow forever. Ties to S-4. |
| D-8 | **Seed data kept in sync** — CI step that runs `db/seed/dev-seed.sql` against a freshly migrated DB | P3 | ⬜ | Prevents the dev seed from silently breaking when a migration changes a column. |

---

## 6. Testing & Quality

| # | Item | Pri | Status | Notes |
|---|---|---|---|---|
| T-1 | **Code coverage reporting and a threshold** (JaCoCo) | P2 | ⬜ | 1194 tests exist, coverage is unmeasured. |
| T-2 | **Load / spike tests** (k6 or Gatling) for the on-sale scenario | P1 | ⬜ | The core NFR (§5.1) has no test. Blocks sizing R-3/R-4. |
| T-3 | **Contract tests** to keep `openapi.yaml` and the controllers from drifting | P2 | ⬜ | The spec is documented as "ahead of" the implementation; make drift visible. |
| T-4 | **Authorization matrix test** — every endpoint × every role | P1 | ⬜ | Pairs with S-8. |
| T-5 | **Concurrency tests** for oversell, double-scan, double-redeem under real parallelism | P1 | ⚠️ | Locking is implemented and reviewed; confirm the tests actually exercise parallel threads against Postgres. |
| T-6 | **DAST** (OWASP ZAP) against a staging deployment | P2 | ⬜ | |
| T-7 | **Migration test in CI** — migrate an empty DB, run Hibernate `validate`, run the seed | P1 | ⬜ | See R-11. |

---

## 7. Documentation & Governance

| # | Item | Pri | Status | Notes |
|---|---|---|---|---|
| G-1 | **Rewrite `README.md`** — quickstart, tech stack, links to `docs/`, `openapi.yaml`, `deploy/aws/README.md`, env-var reference | P1 | ⬜ | Currently a single sentence. |
| G-2 | **`.env.example`** listing every required environment variable | P1 | ⬜ | Prod config now reads seven variables with no defaults. |
| G-3 | **LICENSE** | P1 | ⬜ | No license file; legally ambiguous. |
| G-4 | **SECURITY.md** — vulnerability disclosure policy and contact | P1 | ⬜ | |
| G-5 | **CONTRIBUTING.md** — branch/PR conventions, commit style, review checklist | P2 | ⬜ | The working rule (new branch, PR, never commit to `main`) is currently only in `CLAUDE.md`. |
| G-6 | **CHANGELOG.md** and a release/versioning process | P2 | ⬜ | Project is still `0.0.1-SNAPSHOT`; `pom.xml` name/description/license/scm are empty. |
| G-7 | **Architecture diagram** (context, deployment, request flow) | P2 | ⬜ | The ERD exists; no system view. |
| G-8 | **ADRs** (`docs/adr/`) for decisions currently only in code comments: no FK constraints, Flyway + ddl-auto history, lazy hold expiry, OIDC deploys, HMAC credentials instead of JWT | P2 | ⬜ | |
| G-9 | **Runbook** — unhealthy service, rollback, reading CloudWatch logs, failed migration recovery, rotating secrets | P1 | ⬜ | The first deploy needed all of this and none of it was written down. |
| G-10 | **SLOs and an incident process** | P2 | ⬜ | The 99.9% target has no measurement or ownership. |
| G-11 | **Disaster-recovery plan** with RTO/RPO | P2 | ⬜ | Ties to R-2. |
| G-12 | **Privacy policy / data-classification doc** | P1 | ⬜ | Ties to S-4. Legal/product input needed. |
| G-13 | **API versioning & deprecation policy** | P2 | ⬜ | `/api/v1` exists (BR-NFR-007); no policy for evolving it. |
| G-14 | **Refresh `CLAUDE.md`** | P1 | ⬜ | Stale: still says no global exception handler exists, only `contextLoads()` is tested, and only the `event`/`user` modules exist. |
| G-15 | **Keep `openapi.yaml` honest** — mark unimplemented operations or generate from code | P2 | ⬜ | See T-3. |

---

## 8. Open Product Decisions

These need an answer before the related items can be scheduled (carried
over from `requirements.md` §7):

1. Target markets, currency, and tax handling → blocks P-2, S-4, G-12.
2. Expected v1 launch scale (organizers, peak concurrent buyers per on-sale)
   → blocks R-3, R-4, T-2.
3. Resale default (opt-in vs. opt-out per event).
4. Mid-event check-in mode switching (standard ↔ pure-offline).
5. Whether the visible ticket number should ever serve as a manual check-in
   fallback.
6. Whether event rescheduling (changing start/end/venue) becomes a feature
   → decides whether N-3 exists at all.
7. Data retention periods per table → blocks D-7, S-4.

---

## 9. Suggested Sequencing

1. **Foundations that unblock others:** R-10 (scheduler), S-1 (TLS, needs a
   domain), O-1/O-3 (health probes, structured logs), D-1/S-13 (strict
   config defaults), R-9/T-7 (test gate and migration check on the deploy
   path).
2. **Before any real user:** S-2/S-3 (rate limiting, brute-force), S-5/N-1
   (real payments and email), S-4 (data-subject rights), R-1/R-2 (HA and
   database durability), O-4 (alarms), G-9 (runbook).
3. **Before the first big on-sale:** T-2 (load test), R-3/R-4 (autoscaling,
   waiting room), R-5/R-6 (caching, pagination caps), S-6 (WAF).
4. **Ongoing:** the Tier P2/P3 hygiene, governance, and documentation items.
