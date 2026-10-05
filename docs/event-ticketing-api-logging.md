# Event Ticketing API — Logging

**Version:** 1.0
**Date:** 2026-10-05

The app writes two logical log streams to stdout: the **application log**
(ordinary SLF4J/Logback logging) and the **audit log** (one structured line
per important business transaction, written by
`common/logging/BusinessAuditLogger` to the dedicated `AUDIT` logger).

This is separate from the **database audit trail** (`audit_log_entries`,
`GET /api/v1/audit-log`, BR-NFR-005), which is unchanged. Every action the
database trail records also produces an audit-log line.

## What each profile logs

| | `dev` (and default `mvnw spring-boot:run`) | `prod` (ECS) |
|---|---|---|
| Format | Readable text: `HH:mm:ss.SSS LEVEL [requestId] [userId] logger - message` | One JSON object per line (Spring Boot `logstash` format) |
| App code level | `DEBUG` | `WARN` |
| Framework / Spring web | Default `INFO`. `org.springframework.web` is deliberately **not** `DEBUG`: it logs request/response body objects, i.e. plain passwords and tokens. | `WARN` |
| SQL | Every statement via `org.hibernate.SQL`; bound values too in the `dev` profile (`org.hibernate.orm.jdbc.bind=TRACE`) | Off |
| Per-request line | Yes: `METHOD /path?query -> status (N ms)` | No |
| Audit log (`AUDIT`) | `INFO` (important transactions) | `INFO` (important transactions) |
| Startup / migrations | Everything | Startup confirmation and the Flyway migration summary only |

In prod the output is therefore: audit lines, warnings and errors, and the
startup/migration summary. Nothing else.

Why prod resets loggers one by one: `application.properties` is shared with
dev and sets some specific loggers (for example `org.hibernate.SQL`) to
`DEBUG`; a specific logger beats the root level, so prod must reset each one
explicitly. `ProdLoggingConfigurationTest` loads the real prod profile and
fails if any `logging.level.*` is noisier than `WARN` apart from the
allow-listed `AUDIT`, the startup line, and the Flyway summary.

Known prod startup warning: Spring's `spring.jpa.open-in-view is enabled`
(harmless; disabling it would break lazy loading in controllers, so it is left
alone). The throw-away generated Spring password line is suppressed.

The `dev` profile also prints bound SQL parameter values
(`org.hibernate.orm.jdbc.bind=TRACE`), which can include password hashes and
token ids. That is for local use only; never enable it outside dev.

## Where the logs go

| | Console | File |
|---|---|---|
| `dev` / default local run | Readable text (application and audit lines together) | **Two JSON files** per day (same format and fields as prod): `logs/<yyyy-MM-dd>/application.<n>.log` (everything except audit) and `logs/<yyyy-MM-dd>/audit.<n>.log` (audit log only) |
| `prod` (ECS) | JSON | None. stdout only, shipped to CloudWatch |

Local files (never in prod, where container files vanish on restart):
- **One folder per day**; today's log is already inside today's folder.
- **Size cap per file**: when a file reaches `app.logging.file.max-size`
  (default 10MB) the next numbered file starts (`.0.log`, `.1.log`, ...). A file
  can end a few hundred bytes over the cap, because it rolls after the line that
  crosses it.
- **Retention**: `max-history-days` (14) and `total-size-cap` (500MB) delete
  the oldest.
- Settings: `app.logging.file.dir|max-size|max-history-days|total-size-cap|format`
  (`format` is `logstash` or `ecs`). `logs/` is git-ignored; test runs write to
  `target/test-logs` instead.

Audit lines are kept out of the application file locally (the `AUDIT` logger
has `additivity="false"` in `logback-spring.xml`), so each file is its own
stream. In prod there is no split: both stay on stdout and you filter on
`log_type` in CloudWatch.

Finding things locally (each line is one JSON object):

```
type logs\2026-10-05\audit.0.log
findstr /c:"3f2a9c1e-request-id" logs\2026-10-05\*.log
```

(`cat logs/2026-10-05/audit.*.log` and `grep -h "<request-id>" logs/2026-10-05/*.log`
in Git Bash.) The same request id appears in both files, so a request can be
followed across them.

## Correlation ids

`RequestIdFilter` gives every request an id, returned in the `X-Request-Id`
response header. A client-supplied `X-Request-Id` is reused only if it is 8–64
characters of letters, digits, `.`, `_`, `-`; anything else is replaced.
Every log line for that request carries `requestId`, and `userId` once a valid
access token is presented, so one request can be followed across the
application log and the audit log.

## Audit log fields

| Field | Meaning |
|---|---|
| `log_type` | Always `audit` (use it to filter) |
| `action` | `<resource>.<past_tense_verb>`, e.g. `checkout.completed` |
| `actorId` | The authenticated user (or device) id, or `anonymous` |
| `targetType` / `targetId` | What the action was about, e.g. `Order` + its id |
| `outcome` | `SUCCESS` (logged at INFO) or `FAILURE` (logged at WARN) |
| `requestId`, `userId` | Correlation, as above |

The human-readable message repeats these and may add a short `detail`
(for example `total=4500 USD tickets=1`, `wrong password`, `role=CUSTOMER`).

### Actions that are audited

| Area | Actions |
|---|---|
| Auth / user | `auth.login` (success, wrong password, unknown email, suspended), `user.registered` (logs the requested role), `user.password_changed`, `user.deleted` |
| Organization | `organization.applied`, `.approved`, `.rejected`, plus `organization_member.assigned` |
| Event | `event.created`, `.published`, `.deleted`, plus `event.cancelled` |
| Purchase | `checkout.completed`, `checkout.payment_failed` |
| Tickets | `ticket.transferred`, `resale_listing.created`, `resale_listing.cancelled`, `resale.purchased`, `resale.payment_failed` |
| Check-in | `checkin.validated`, `checkin.fallback_scan` (`WARN` for anything other than a valid scan) |
| Money / oversight | `refund.issued`, `dispute.raised`, `dispute.resolved`/`.dismissed`, `moderation.suspend`/`.reinstate`/`.remove` |

To audit a new action, call
`BusinessAuditLogger.record("thing.did_something", "TargetType", targetId, Outcome.SUCCESS)`
after the transaction's commit point (or `recordAs(actorId, ...)` when the
caller isn't authenticated yet, as in login).

## What must never be logged

Passwords, JWTs and refresh tokens, `paymentMethodToken`, ticket and
scanner-device credentials, and request or response bodies. Emails are logged
masked (`j***@example.com`). `BusinessAuditLogger` strips control characters
from every value and caps length, so user-supplied text can't forge extra
log lines.

## Using it in AWS

`SPRING_PROFILES_ACTIVE=prod` is already set on the ECS task, and the
`awslogs` driver ships stdout to CloudWatch Logs (`/ecs/event-ticketing-api-staging`).
Audit and application lines share that log group; filter on `log_type`.

CloudWatch Logs Insights examples:

```
# all audit lines, newest first
fields @timestamp, action, outcome, actorId, targetType, targetId, requestId
| filter log_type = "audit"
| sort @timestamp desc

# failed logins in the last hour
fields @timestamp, actorId, message
| filter log_type = "audit" and action = "auth.login" and outcome = "FAILURE"

# everything that happened during one request
fields @timestamp, level, message
| filter requestId = "<the X-Request-Id from the response>"
| sort @timestamp asc

# errors only
fields @timestamp, logger_name, message
| filter level = "ERROR"
```

Not included yet (see `docs/event-ticketing-api-future-features.md`, O-4):
CloudWatch alarms on `ERROR` lines or on repeated login failures.
