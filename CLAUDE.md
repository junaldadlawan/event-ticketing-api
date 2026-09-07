# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repo.

## Project overview

Backend API for a multi-organizer event ticketing marketplace: event & ticket
management, checkout & payments, promo codes, waitlists, transfer/resale,
organizer-defined ticket templates, and QR-based check-in (see `README.md`).

`docs/` (`event-ticketing-api-requirements.md`, `-resources.md`,
`-contract.md`) and `openapi.yaml` describe the **target** design and are well
ahead of the current implementation. Right now only the `event` and `user`
modules exist — no auth/JWT, no `Organization`/`Venue`/`Order`/`Ticket`
entities yet. Treat `docs/` and `openapi.yaml` as the design to build toward,
not the current state.

## Tech stack

Java 21, Spring Boot 4.1.1, Maven (with wrapper), PostgreSQL via Docker
Compose, Flyway, Lombok, Spring Data JPA, Bean Validation, Spring Security
(currently permissive, see Known gaps below).

## Commands

- Start local Postgres: `docker compose up -d` (db `event_ticketing`,
  user/pass `user`/`password`, from `compose.yml`)
- Build: `mvnw.cmd clean install`
- Run: `mvnw.cmd spring-boot:run` (serves on port 8081)
- Test: `mvnw.cmd test`

## Project structure

Base package: `com.junaldadlawan.event_ticketing_api`

- `common/` — cross-cutting code: `config/` (e.g. `SecurityConfig`), `dto/`
  (`PageResponse`), `entity/` (`Auditable`), `exception/` (`ApiException`,
  `ResourceNotFoundException`), `validation/` (`@NoHtml`, `@ValidEndTime`)
- `event/` — full-layer module: `controller/`, `service/` (+ `*Impl`),
  `repository/`, `entity/`, `dto/`, `enums/`, `specification/`
- `user/` — partial module: `controller/`, `service/` (+ `*Impl`),
  `repository/`, `entity/`, `dto/`. No update/delete endpoints yet, no
  `specification/`. When adding user features, follow `event/`'s fuller
  pattern rather than replicating `user/`'s current gaps.

## Architectural conventions

Follow these established patterns for new code:

- **Layering**: Controller → Service interface + Impl → Repository (Spring
  Data JPA) → Entity.
- **DTOs as records**: response DTOs have a static `from(Entity)` factory
  (e.g. `EventResponse.from`, `UserResponse.from`); request DTOs are plain
  validated records.
- **Constructor injection**: `@RequiredArgsConstructor` (Lombok) on
  controllers/services. Entities use
  `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.
- **404 handling**: a `getOrThrow(id)` helper in the service impl throws
  `ResourceNotFoundException` (see `EventServiceImpl`) — reuse this pattern
  for any single-resource fetch/update/delete.
- **Auditing**: all entities extend `common/entity/Auditable.java`
  (`createdBy/At`, `updatedBy/At`, `deletedAt`, `markDeleted()` for soft
  delete).
- **Dynamic queries**: JPA `Specification` composed via
  `Specification.where(...).and(...)` (see
  `event/specification/EventSpecification.java`).
- **Pagination**: controllers return `common/dto/PageResponse.java` (wraps
  `Page<T>`), not `Page<T>` directly.

## Database & migrations

Flyway migrations live in `src/main/resources/db/migration/` (`V1` init
schema, `V2` adds `users` table, `V3`/`V4` iterative fixups including a
self-corrected column-name typo). Migrations are still evolving — check the
latest migration file rather than assuming `V1`/`V2` alone define the schema.

Note: `application.properties` has both `spring.jpa.hibernate.ddl-auto=update`
**and** `spring.flyway.enabled=true` active simultaneously, which is unusual —
don't assume Flyway alone owns schema state.

## Known gaps / TODOs

Surfacing these so they aren't mistaken for finished behavior:

- No global `@ControllerAdvice`/exception handler exists yet, despite
  `ApiException`'s Javadoc describing one — an unhandled `ApiException`
  currently falls through to Spring's default error response.
- `SecurityConfig` disables CSRF and permits all requests — placeholder, not
  real auth.
- `EventSpecification.titleContains` uses `root.get(keyword)` instead of
  `root.get("title")` (bug); `startBefore` duplicates `startsAfter`'s
  comparator instead of using `lessThanOrEqualTo`.
- `EventServiceImpl.listEvents` hardcodes `EventStatus.DRAFT` regardless of
  caller intent.
- `event/entity/Venue.java` is an empty stub, not wired to `Event` (venue is
  just an `int` column).
- Soft-delete (`deletedAt`/`markDeleted()`) isn't filtered out in queries yet
  — `findAll`/`listEvents` would still return soft-deleted rows.
- No `AuditorAware` bean, so `@CreatedBy`/`@LastModifiedBy` stay at their
  hardcoded default instead of reflecting a real user.

## Testing

Only test file is the default `contextLoads()` smoke test — no unit/slice
test conventions established yet. Test-scoped starters available:
`spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`,
`spring-boot-starter-webmvc-test`.
