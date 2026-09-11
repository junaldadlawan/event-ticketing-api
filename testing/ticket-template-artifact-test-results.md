# Ticket Template & Artifact Rendering (Phase 6b) — Proof of Testing

**Date:** 2026-09-11
**Branch:** `feat/phase6b-templates-artifacts`
**Based on:** `docs/event-ticketing-api-business-rules.md` `BR-TICKET-001`
(unique/unguessable credential is the sole source of truth for validity),
`BR-TICKET-007`/`010` (per-event/per-ticket-type physical & digital
templates; ticket number must appear on the artifact distinct from the
QR/barcode), `BR-TICKET-008` (QR must remain valid regardless of
channel/format), `BR-NFR-003` (credentials must be unguessable);
`docs/event-ticketing-api-use-cases.md` `UC-ATTND-06` (download/view a
ticket artifact) and `UC-EVENT-05` (define a ticket template); the new
`tickettemplate/` module (`TicketTemplate` entity,
`TicketTemplateRepository`, `TicketTemplateService`/`Impl`,
`TicketTemplateController`, `EventTicketTemplateController`, request/response
DTOs, migration `V13__add_ticket_templates_table.sql`) and the new
`ticket/artifact/` package (`QrCodeGenerator`, `PngTicketRenderer`,
`PdfTicketRenderer`, `TicketArtifactService`/`Impl`,
`TicketArtifactFields`, `RenderedTicketArtifact`) plus the `TicketController`
`GET /tickets/{ticketId}/artifact` endpoint and the
`GlobalExceptionHandler` fix for `MissingServletRequestParameterException`
(previously fell through to a 401/500 instead of a 400).

This closes a code-reviewer **MEDIUM** finding: zero prior test coverage
existed for the `tickettemplate/` module and `ticket/artifact/` package, two
of whose behaviors are security-load-bearing (`BR-TICKET-001`/`008`): the QR
code must genuinely encode the ticket's raw `credential`, and template
resolution must correctly prefer a ticket-type-specific template over an
event-level one.

## Commands run and results

**Targeted (ticket + tickettemplate packages only):**
```
mvnw.cmd test -Dtest="com.junaldadlawan.event_ticketing_api.tickettemplate.**,com.junaldadlawan.event_ticketing_api.ticket.**"
```
(Postgres already running locally via `docker compose up -d`, container
`postgresql`)

**Result:** BUILD SUCCESS — 105 tests run, 0 failures, 0 errors (5.9s–14.6s
depending on JIT warmup between runs). Of these, 73 are new this pass
(the `tickettemplate/` and `ticket/artifact/` test files below); the
remaining 32 are the pre-existing Phase 6a ticket-issuance tests
(`TicketAccessGuardTest`, `TicketCredentialServiceTest`,
`TicketServiceImplTest`, `TicketControllerTest`,
`TicketAccessIntegrationTest`), run unchanged as part of the same package
wildcard to confirm no regression alongside the new work.

```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, ... -- in com.junaldadlawan.event_ticketing_api.tickettemplate.TicketTemplateAccessIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Full suite:**
```
mvnw.cmd test
```

**Result:** BUILD SUCCESS — 591 tests run, 0 failures, 0 errors (no
regressions vs. the pre-existing ~517-test baseline; the delta reflects the
73 new tests in this pass plus the 32 pre-existing Phase 6a ticket tests and
any other in-flight work already on this branch).

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, ... -- in com.junaldadlawan.event_ticketing_api.venue.VenueSecurityIntegrationTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 591, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  27.668 s
[INFO] Finished at: 2026-09-11T04:59:18+08:00
[INFO] ------------------------------------------------------------------------
```

## Test files added this pass

- `src/test/java/.../tickettemplate/service/TicketTemplateServiceImplTest.java`
  — **new**, 20 tests (Mockito, no Spring context). `create`/`list`/`update`
  authorization (owner/organizer/admin, cross-org rejection, always-org-only
  `list` regardless of event status), partial-update field matrix.
- `src/test/java/.../tickettemplate/controller/TicketTemplateControllerTest.java`
  — **new**, 4 tests (`@WebMvcTest` slice, security filters mocked).
- `src/test/java/.../tickettemplate/controller/EventTicketTemplateControllerTest.java`
  — **new**, 8 tests (`@WebMvcTest` slice).
- `src/test/java/.../tickettemplate/TicketTemplateAccessIntegrationTest.java`
  — **new**, 12 tests (full `@SpringBootTest`, real security filter chain,
  real signed JWTs, real Postgres). Owner/organizer/admin succeed;
  cross-org owner/stranger 403 even on a published event (no
  draft/published visibility split, unlike `TicketType`); update resolves
  ownership from the persisted template's own `eventId`.
- `src/test/java/.../ticket/artifact/QrCodeGeneratorTest.java` — **new**, 3
  tests (plain unit test, no Spring/Mockito). Genuine ZXing decode
  round-trip.
- `src/test/java/.../ticket/artifact/TicketArtifactServiceImplTest.java` —
  **new**, 13 tests (Mockito, using the *real* `QrCodeGenerator` alongside
  mocked repositories/renderers so the captured `BufferedImage` can be
  genuinely decoded). Visibility delegation, seat-description resolution,
  the QR-encodes-real-credential assertion, template resolution order,
  format→content-type/filename switch.
- `src/test/java/.../ticket/artifact/TicketArtifactIntegrationTest.java` —
  **new**, 13 tests (full `@SpringBootTest`, real filter chain, real
  Postgres, real PDFBox/ImageIO/ZXing). Byte-level proof companion to the
  above — decodes the QR from the actual HTTP response bytes and compares
  against the `credential` column read directly from Postgres.

## Scenario → test mapping

### `TicketTemplateServiceImplTest` (Mockito, 20 tests)

| Scenario | Test | Result |
|---|---|---|
| Owner can create a template | `create_owner_succeeds` | Pass |
| Organizer can create a template | `create_organizer_succeeds` | Pass |
| Admin bypasses org-role lookup entirely | `create_adminWithNoOrgRole_bypassesOrgRoleCheck` | Pass |
| Ticket-type-scoped template persists `ticketTypeId` | `create_ticketTypeScoped_persistsTicketTypeId` | Pass |
| Unknown event → 404 | `create_nonExistentEvent_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `create_stranger_throwsForbidden` | Pass |
| **Owner of a DIFFERENT org → 403 (not just a stranger)** | `create_ownerOfDifferentOrganization_throwsForbidden` | Pass |
| **`list()` is owning-org/admin-only ALWAYS — stranger 403 even on a PUBLISHED event (no visibility split, unlike `TicketType`)** | `list_strangerOnPublishedEvent_stillThrowsForbidden` | Pass |
| Owner can list | `list_owner_succeeds` | Pass |
| Admin can list | `list_admin_succeeds` | Pass |
| Unknown event → 404 | `list_unknownEvent_throwsResourceNotFound` | Pass |
| Owner can update | `update_owner_succeeds` | Pass |
| Unknown template → 404 | `update_unknownTemplate_throwsResourceNotFound` | Pass |
| Roleless stranger → 403 | `update_stranger_throwsForbidden` | Pass |
| **Cross-org organizer, PATCH against another org's template → 403, resolved strictly from the template's own `eventId`, never client input** | `update_crossOrgOrganizer_throwsForbidden_resolvedFromTemplatesOwnEventId` | Pass |
| Admin can update with no org-role check | `update_admin_succeedsWithNoOrgRole` | Pass |
| Partial update: `logoUrl` only | `update_logoUrlOnly_changesOnlyLogoUrl` | Pass |
| Partial update: `backgroundImageUrl` only | `update_backgroundImageUrlOnly_changesOnlyThatField` | Pass |
| Partial update: `primaryColor` only | `update_primaryColorOnly_changesOnlyThatField` | Pass |
| All-null update leaves template unchanged | `update_allFieldsNull_leavesTemplateUnchanged` | Pass |

### `TicketTemplateControllerTest` / `EventTicketTemplateControllerTest` (`@WebMvcTest`, 12 tests total)

Request validation (missing `format`, invalid URL), 201/200/403/404 status
mapping for `create`/`list`/`update` — security enforcement itself is left
to the integration test below, per this repo's slice-test convention.

### `TicketTemplateAccessIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, 12 tests)

| Scenario | Test | Result |
|---|---|---|
| Owner → 201, branding persisted | `create_owner_returns201_withBrandingPersisted` | Pass |
| Organizer and admin (no org membership row at all) also succeed | `create_organizerAndAdmin_alsoSucceed` | Pass |
| Stranger → 403 | `create_stranger_returns403` | Pass |
| **Owner of a different org → 403** | `create_crossOrgOwner_returns403` | Pass |
| No token → 401 | `create_noToken_returns401` | Pass |
| **List is owning-org/admin-only ALWAYS, even on a PUBLISHED event (stranger 403, anonymous 401)** | `list_stranger_returns403_evenOnPublishedEvent` | Pass |
| Owner → 200 | `list_owner_returns200` | Pass |
| Admin (no org membership) → 200 | `list_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |
| Owner partial-update → 200 | `update_owner_returns200_partialUpdate` | Pass |
| **Cross-org organizer PATCH → 403, resolution from the template's own persisted `eventId`** | `update_crossOrgOrganizer_returns403` | Pass |
| Unknown template → 404 | `update_unknownTemplate_returns404` | Pass |
| Admin (no org membership) can update | `update_admin_returns200_withNoOrganizationMembershipAtAll` | Pass |

### `QrCodeGeneratorTest` (plain unit test, 3 tests)

| Scenario | Test | Result |
|---|---|---|
| **Generate then genuinely ZXing-decode → exact original payload** | `generate_thenDecode_returnsExactOriginalPayload` | **Pass** |
| Custom size honors requested dimensions | `generate_customSize_honorsRequestedDimensions` | Pass |
| Two different payloads decode to two distinct values | `generate_twoDifferentPayloads_decodeToDistinctValues` | Pass |

### `TicketArtifactServiceImplTest` (Mockito + real `QrCodeGenerator`, 13 tests)

| Scenario | Test | Result |
|---|---|---|
| Unknown ticket → 404, no access-guard call | `render_ticketNotFound_throwsResourceNotFound` | Pass |
| Access denied → `ForbiddenException` propagates, nothing rendered | `render_accessDenied_propagatesForbidden_withoutRenderingAnything` | Pass |
| Event no longer exists → 404 | `render_eventNoLongerExists_throwsResourceNotFound` | Pass |
| Ticket type no longer exists → 404 | `render_ticketTypeNoLongerExists_throwsResourceNotFound` | Pass |
| GA ticket → seat description is "General Admission", `SeatRepository` never touched | `render_gaTicket_seatDescriptionIsGeneralAdmission_withoutTouchingSeatRepository` | Pass |
| Reserved-seat ticket → seat description includes section/row/seat number | `render_reservedSeatTicket_seatDescriptionIncludesSectionRowSeatNumber` | Pass |
| Reserved-seat ticket, seat no longer exists → 404 | `render_reservedSeatTicket_seatNoLongerExists_throwsResourceNotFound` | Pass |
| **THE key security assertion: QR encodes the REAL `credential`, ZXing-decoded, never `ticketNumber`/`id`** | `render_qrCode_encodesActualCredential_neverTicketNumberOrId` | **Pass** |
| **Template resolution: ticket-type-specific preferred over event-level; event-level lookup never even runs once the specific one matches** | `render_templateResolution_ticketTypeSpecificPreferredOverEventLevel` | **Pass** |
| Template resolution: event-level fallback when no ticket-type-specific template | `render_templateResolution_eventLevelFallback_whenNoTicketTypeSpecificTemplate` | Pass |
| No template at all → still renders, `primaryColorHex` null | `render_templateResolution_noTemplateAtAll_fieldsPrimaryColorIsNull_stillRenders` | Pass |
| `format=digital` → PNG content type/filename, PDF renderer untouched | `render_digitalFormat_returnsPngContentTypeAndFilename` | Pass |
| `format=physical` → PDF content type/filename, PNG renderer untouched | `render_physicalFormat_returnsPdfContentTypeAndFilename` | Pass |

### `TicketArtifactIntegrationTest` (full `@SpringBootTest`, real filter chain, real Postgres, real PDFBox/ImageIO/ZXing, 13 tests)

| Scenario | Test | Result |
|---|---|---|
| `?format=digital` → 200, `image/png`, genuinely `ImageIO`-readable | `getArtifact_digitalFormat_returns200_pngContentType_genuinelyImageIOReadable` | Pass |
| `?format=physical` → 200, `application/pdf`, genuinely PDFBox-loadable, exactly 1 page | `getArtifact_physicalFormat_returns200_pdfContentType_genuinelyPdfBoxLoadable_exactlyOnePage` | Pass |
| `?format=xyz` → 400 | `getArtifact_invalidFormat_returns400` | Pass |
| **Missing `format` param → 400, NOT 401 (`GlobalExceptionHandler` regression test for the `MissingServletRequestParameterException` fix shipped in this same dispatch)** | `getArtifact_missingFormatParam_returns400_notUnauthorized` | **Pass** |
| No token → 401 | `getArtifact_noToken_returns401` | Pass |
| **Stranger → 403** | `getArtifact_stranger_returns403` | Pass |
| Unknown ticket → 404 | `getArtifact_unknownTicket_returns404` | Pass |
| **THE most important test: real HTTP response bytes, ZXing-decoded, equal the real `credential` column read directly from Postgres — never `ticketNumber`, never `id`** | `getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId` | **Pass** |
| **Template resolution: ticket-type-specific template's accent color wins over an event-level template that also exists for the same event+format** | `getArtifact_templateResolution_ticketTypeSpecificPreferredOverEventLevel` | Pass |
| Template resolution: event-level template used when no ticket-type-specific one exists | `getArtifact_templateResolution_eventLevelOnly_usedWhenNoTicketTypeSpecificTemplate` | Pass |
| No template at all → still 200, default accent color | `getArtifact_templateResolution_noTemplateAtAll_stillRenders200_withDefaultAccent` | Pass |
| Reserved-seat ticket → PDF text contains section/row/seat, not "General Admission" | `getArtifact_reservedSeatTicket_pdfTextContainsSectionRowSeatNumber` | Pass |
| GA ticket → PDF text contains "General Admission" | `getArtifact_gaTicket_pdfTextContainsGeneralAdmission` | Pass |

## The QR-decode-equals-credential result (single most important assertion in this suite)

Verified at three independent layers, all passing:

1. **`QrCodeGeneratorTest.generate_thenDecode_returnsExactOriginalPayload`** —
   a realistic credential-shaped payload (`"<uuid>.<base64url-signature>"`)
   generated by `QrCodeGenerator`, then decoded back via ZXing's
   `MultiFormatReader` over a `HybridBinarizer`-binarized
   `RGBLuminanceSource` — **decoded value equals the original payload
   exactly.**
2. **`TicketArtifactServiceImplTest.render_qrCode_encodesActualCredential_neverTicketNumberOrId`**
   — using the *real* `QrCodeGenerator` (mocked repositories/renderers
   around it), the `TicketArtifactFields.qrCodeImage()` captured off the
   real render call, ZXing-decoded — **equals the ticket's real
   `credential` field, and is explicitly asserted NOT equal to
   `ticketNumber` or `id`.**
3. **`TicketArtifactIntegrationTest.getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId`**
   — full HTTP round trip: real JWT, real Postgres-persisted `Ticket` row,
   real `GET /tickets/{ticketId}/artifact?format=digital` call, PNG bytes
   taken from the actual `MockHttpServletResponse`, ZXing-decoded, compared
   against `ticketRepository.findById(ticketId).orElseThrow().getCredential()`
   read fresh from Postgres — **decoded value equals the real credential
   column exactly, and is explicitly asserted NOT equal to `ticketNumber` or
   `id`.**

All three passed on the actual test run (see full suite output above). No
assertion was weakened to make any of these pass.

## Findings surfaced by writing these tests

No new production bugs were found in the `tickettemplate/` or
`ticket/artifact/` code itself — every behavior these tests assert (QR
payload correctness, template resolution preference order, CRUD
authorization matrix, format→content-type mapping, seat-description
resolution) matched production code on the real test run.

The one real gap this pass closed (not a new bug, but the reason this
dispatch exists) was in `GlobalExceptionHandler`: prior to this pass, there
was no handler for `MissingServletRequestParameterException`, so a request
missing the required `format` query parameter fell through to Spring's
default error handling instead of a clean `400`.
`getArtifact_missingFormatParam_returns400_notUnauthorized` is the
regression test proving the fix — it also specifically proves the real
filter chain authenticates the caller *first* (so a missing-param request
from an authenticated caller is genuinely `400`, not `401`).

## Post-dispatch addendum (2026-09-11): flaky QR-decode test fixed

A later verification pass of this branch hit
`TicketArtifactIntegrationTest.getArtifact_qrPayloadDecodesToActualCredential_notTicketNumberOrId`
failing intermittently (`com.google.zxing.NotFoundException`, roughly 1-in-5
to 1-in-8 runs against real Postgres). Root-caused by re-running the single
test 5-8x in a row until it reproduced, then bisecting:

- **Not** the cause: `QrCodeGenerator`'s `MARGIN` hint (bumped 1→2 as a
  minor hardening anyway, since a 1-module quiet zone is below the QR spec's
  recommended 4) — reproduced identically at both margins.
- **Not** the cause: `PngTicketRenderer`'s AWT rescale of the QR image from
  its generated 300px down to the 260px it's drawn at — explicitly setting
  `RenderingHints.KEY_INTERPOLATION` to `VALUE_INTERPOLATION_NEAREST_NEIGHBOR`
  for that `drawImage` call (kept anyway, since blurred module edges are
  never better for a barcode reader) did not change the failure rate.
- **The actual cause**: unlike `QrCodeGeneratorTest`/
  `TicketArtifactServiceImplTest` (which decode the *pure*, standalone QR
  image), this test's `decodeQr` helper runs ZXing's `MultiFormatReader`
  over the *entire* composited 900×380 ticket PNG — event title text, the
  accent bar, ticket-number text, and the QR all in one image. Without the
  `TRY_HARDER` decode hint, `MultiFormatReader` intermittently failed to
  locate the finder patterns in the larger, busier image. Adding
  `DecodeHintType.TRY_HARDER` to that one helper made 8/8 repeated runs pass
  cleanly (previously ~1 failure per 5-8 runs) with zero production code
  behavior change beyond the two defensive tweaks above — the underlying PNG
  bytes were correct all along; only the test's own reader configuration was
  too weak for a full-page scan. Confirmed via a full `mvnw.cmd test` run
  (591 tests, 0 failures/errors) after the fix.

## Database cleanup verification

```
$ docker exec -i postgresql psql -U user -d event_ticketing -c \
  "SELECT (SELECT count(*) FROM ticket_templates) AS ticket_templates,
          (SELECT count(*) FROM tickets) AS tickets,
          (SELECT count(*) FROM events) AS events,
          (SELECT count(*) FROM organizations) AS organizations,
          (SELECT count(*) FROM organization_members) AS organization_members;"

 ticket_templates | tickets | events | organizations | organization_members
------------------+---------+--------+---------------+-----------------------
                0 |       0 |      4 |             1 |                     1
```

`ticket_templates` and `tickets` — the tables this pass's tests actually
write to — are both `0`: full cleanup confirmed for every test file added in
this pass. The `events`=4/`organizations`=1/`organization_members`=1 rows are
pre-existing residue that predates this session (timestamps 2026-08-31
through 2026-09-10, all before today's run) and is already documented as
out-of-scope leftover debris in `testing/ticket-issuance-test-results.md`'s
own cleanup section from the prior Phase 6a pass — unchanged and
unaffected by anything in this pass.
