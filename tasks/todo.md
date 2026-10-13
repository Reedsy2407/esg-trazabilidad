# Task List: shared-kernel + recycler-service + collection-service + cross-service-events + reporting-service + ci-pipeline + auth-service

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`, `SPEC-collection-service.md`, `SPEC-cross-service-events.md`, `SPEC-reporting-service.md`, `SPEC-ci-pipeline.md`, `SPEC-auth-service.md`.
> Confirmed before `/build` starts: `JWT_SECRET` lives only in `.env.local`, shared identically across all four services via the existing `spring.config.import` mechanism, never hardcoded. `AdminBootstrapRunner` (Task 61) only ever creates an account when `staff_user` is empty (never resets credentials on a later restart) and logs a `SecureRandom`-generated password exactly once — never an env-supplied or predictable value.

## Phase 1: Foundation (`shared-kernel`)

- [x] Task 1: Monorepo + shared-kernel scaffolding — detalle: tasks/LEARNINGS.md (grep "## Task 1:")
- [x] Task 2: Error handling (`ApplicationError`, `ApplicationException`, `GlobalExceptionHandler`) — detalle: tasks/LEARNINGS.md (grep "## Task 2:")
- [x] Task 3: Event strategy + ID generation (`EventPublishingStrategy`, `IdGenerator`) — detalle: tasks/LEARNINGS.md (grep "## Task 3:")
### Checkpoint 1: shared-kernel complete
- [x] `mvn -pl shared-kernel install` succeeds standalone
- [x] `mvn -pl shared-kernel test` green
- [ ] Human review before starting `recycler-service`

## Phase 2: recycler-service infra

- [x] Task 4: recycler-service scaffolding — detalle: tasks/LEARNINGS.md (grep "## Task 4:")
### Checkpoint 2: Service boots
- [x] `docker compose up -d` starts Postgres
- [x] `mvn -pl recycler-service spring-boot:run` boots cleanly, empty changelog applies
- [ ] Human review before first entity slice

## Phase 3: Association

- [x] Task 5: Association persistence — detalle: tasks/LEARNINGS.md (grep "## Task 5:")
- [x] Task 6: Association API — detalle: tasks/LEARNINGS.md (grep "## Task 6:")
### Checkpoint 3: Association CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green
- [x] Manual check: create → get → list an association via Swagger/curl
- [ ] Human review before Recycler slice

## Phase 4: Recycler

- [x] Task 7: Recycler persistence — detalle: tasks/LEARNINGS.md (grep "## Task 7:")
- [x] Task 8: Recycler API — detalle: tasks/LEARNINGS.md (grep "## Task 8:")
### Checkpoint 4: Recycler CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green
- [x] Manual check: creating a recycler under a nonexistent association returns a typed 404 (`REC-003`), not a raw 500
- [ ] Human review before Certification slice

## Phase 5: Certification

- [x] Task 9: Certification persistence — detalle: tasks/LEARNINGS.md (grep "## Task 9:")
- [x] Task 10: Certification API — detalle: tasks/LEARNINGS.md (grep "## Task 10:")
### Checkpoint 5: Full CRUD path complete
- [x] `mvn verify` green across the whole reactor
- [x] Manual check: Association → Recycler → Certification chain works end-to-end through real HTTP calls

## Phase 6: Polish

- [x] Task 11: springdoc-openapi wiring — detalle: tasks/LEARNINGS.md (grep "## Task 11:")
- [x] Task 12: Status-change and renewal endpoints — detalle: tasks/LEARNINGS.md (grep "## Task 12:")
### Checkpoint 6: Final — ready for review
- [x] All Success Criteria in `SPEC-shared-kernel.md` and `SPEC-recycler-service.md` are met — re-verified line by line: shared-kernel 5/5, recycler-service 7/7 (the "update where the domain calls for it" line, previously the one gap, is now closed by Task 12).
- [x] Definition of Done (Correctness + Quality sections) satisfied for every task above — no separate DoD document exists in this repo; applying the de facto standard held throughout every task: passing unit + integration tests, a clean `mvn verify`/`mvn install` on the whole reactor, a manual end-to-end check against real Docker Postgres, and any deviation from the original plan documented in the task's own notes and commit message.
- [x] Human review and approval before moving to `collection-service` or `cross-service-events` — approved 2026-09-13; reviewed in a prior session, code confirmed solid.

## Phase 7: collection-service infra

- [x] Task 13: collection-service scaffolding — detalle: tasks/LEARNINGS.md (grep "## Task 13:")
### Checkpoint 7: Service boots
- [x] `mvn -pl collection-service spring-boot:run` boots cleanly against the shared Postgres, empty changelog applies
- [x] Human review before first entity slice — approved 2026-09-13; verified PageResponse relocation, `.env.local` + `docker compose --env-file` documentation, `pom.xml`/`application.yml`.

## Phase 8: Neighbor

- [x] Task 14: Neighbor persistence — detalle: tasks/LEARNINGS.md (grep "## Task 14:")
- [x] Task 15: Neighbor API — detalle: tasks/LEARNINGS.md (grep "## Task 15:")
### Checkpoint 8: Neighbor CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a neighbor via curl
- [x] Human review before Company slice — implicit approval: user directed `/build` for Task 16 directly

## Phase 9: Company

- [x] Task 16: Company persistence — detalle: tasks/LEARNINGS.md (grep "## Task 16:")
- [x] Task 17: Company API — detalle: tasks/LEARNINGS.md (grep "## Task 17:")
### Checkpoint 9: Company CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a company, duplicate-RUC returns 409
- [x] Human review before CollectionSchedule slice — implicit approval: user directed `/build` for Task 18 directly

## Phase 10: CollectionSchedule

- [x] Task 18: CollectionSchedule persistence — detalle: tasks/LEARNINGS.md (grep "## Task 18:")
- [x] Task 19: CollectionSchedule API (CRUD) — detalle: tasks/LEARNINGS.md (grep "## Task 19:")
- [x] Task 20: CollectionSchedule lifecycle — detalle: tasks/LEARNINGS.md (grep "## Task 20:")
### Checkpoint 10: CollectionSchedule complete (CRUD + full lifecycle)
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: two schedules same neighbor different days (both succeed), same-day duplicate (409 COL-002), pause → reactivate, cancel → confirm terminal (409 COL-008 on further pause/reactivate)
- [x] Human review before CollectionRecord slice — implicit approval: user directed `/build` for Task 21 directly

## Phase 11: CollectionRecord

- [x] Task 21: CollectionRecord persistence — detalle: tasks/LEARNINGS.md (grep "## Task 21:")
- [x] Task 22: CollectionRecord API — detalle: tasks/LEARNINGS.md (grep "## Task 22:")
### Checkpoint 11: CollectionRecord complete
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: log a collection record tied to a schedule, log an ad-hoc one (no schedule), confirm both list correctly and `associationId` isn't validated
- [ ] Human review before Polish phase

## Phase 12: Polish

- [x] Task 23: springdoc-openapi wiring — detalle: tasks/LEARNINGS.md (grep "## Task 23:")
### Checkpoint 12: Full CRUD path complete
- [x] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`) — `shared-kernel` (10 unit), `recycler-service` (32 IT), `collection-service` (65 unit + 32 IT), no cross-module regression
- [x] Manual check: Neighbor → CollectionSchedule → CollectionRecord chain works end-to-end through real HTTP calls; Company independently CRUD-able; `recycler-service` unaffected — all exercised across Tasks 14-22's manual checks this session

### Checkpoint 13: Final — ready for review
- [x] All Success Criteria in `SPEC-collection-service.md` are met — re-verified line by line, 10/10:
  1. `docker compose up -d` + shared Postgres — ✅ (booted repeatedly against it across Tasks 13-23)
  2. Liquibase creates `neighbor`/`company`/`collection_schedule`/`collection_record` without touching `recycler-service` tables — ✅ (verified via `psql \d`; `recycler-service` re-booted clean against the same volume multiple times)
  3. `Neighbor`/`Company` CRUD — ✅ (Tasks 14-17)
  4. `CollectionSchedule` CRUD scoped by `neighborId` — ✅ (Tasks 18-19)
  5. `CollectionSchedule` lifecycle, COL-008 on illegal transition — ✅ (Task 20)
  6. `CollectionRecord` CRUD, `associationId` unvalidated — ✅ (Tasks 21-22, reaffirmed by the `scheduleId`-ownership fix without touching this decision)
  7. `CollectionErrors` enum (COL-001, 002, 004-008; COL-003 reserved) wired to shared `GlobalExceptionHandler` — ✅ (this bullet's own text says "COL-004 through COL-007", not mentioning COL-008 — a stale spec wording gap, not an implementation gap: COL-008 is required by bullet 5 above and is correctly implemented)
  8. COL-002 boundary cases (same neighbor+day = conflict, same neighbor+different day = no conflict, **different neighbor+same day/time = no conflict**) — ✅, but the third case had **no explicit test**, only incidental coverage from unrelated tests using different neighbor IDs. Added `differentNeighborsCanHaveActiveSchedulesOnTheSameDayAndTimeWithoutConflict` to `CollectionScheduleApiIT` to close this properly before declaring the checkpoint done.
  9. `mvn -pl collection-service verify` green — ✅ 65 unit + 33 integration (was 32, +1 for the boundary-case test)
  10. Swagger UI reachable, lists all endpoints with schemas — ✅ (Task 23)
- [x] Definition of Done satisfied for every task above (Tasks 13-23) — same de facto standard as `recycler-service`: passing tests, clean `mvn verify`/`install` on the whole reactor, manual end-to-end checks against real Docker Postgres, deviations documented in each task's own notes
- [x] Human review and approval before moving to `cross-service-events` or `reporting-service` — approved 2026-09-14; user independently verified the `differentNeighborsCanHaveActiveSchedulesOnTheSameDayAndTimeWithoutConflict` boundary-case test with real evidence, confirmed it matches what was reported. `collection-service` is complete.

## Phase 13: Shared event infrastructure

- [x] Task 24: RabbitMQ + ShedLock infra wiring — detalle: tasks/LEARNINGS.md (grep "## Task 24:")
- [x] Task 25: shared-kernel event core — detalle: tasks/LEARNINGS.md (grep "## Task 25:")
- [x] Task 26: shared-kernel `OutboxDispatcher` — detalle: tasks/LEARNINGS.md (grep "## Task 26:")
### Checkpoint 14: Shared event infra ready
- [x] `mvn -pl shared-kernel test` green
- [x] `mvn install` — whole reactor still builds, `recycler-service`/`collection-service` unaffected (verified live via boot, not just compile — `OutboxDispatcher`/`RabbitTopologyConfig` are on the classpath but the dispatcher never activates without an `OutboxRepository` bean)
- [x] Human review before wiring either direction's business logic — Tasks 25/26 approved 2026-09-16. Minor non-blocking finding: `OutboxDispatcher.dispatchPending()` published messages via the 3-arg `RabbitTemplate.convertAndSend(exchange, routingKey, String)`, which defaults to `text/plain` even though `payloadJson` is always pre-serialized JSON. Closed immediately (small, self-contained fix) rather than deferred: switched to the `MessagePostProcessor` overload, explicitly setting `MessageProperties.CONTENT_TYPE_JSON`; added `publishesWithAnApplicationJsonContentType` test asserting it. 18 shared-kernel tests green (was 17).

## Phase 15: Direction A (collection-service → recycler-service, kilos total)

- [x] Task 27: collection-service outbox + shedlock schema — detalle: tasks/LEARNINGS.md (grep "## Task 27:")
- [x] Task 28: `CollectionRegisteredEvent` + publisher — detalle: tasks/LEARNINGS.md (grep "## Task 28:")
- [x] Task 29: recycler-service event-infrastructure schema — detalle: tasks/LEARNINGS.md (grep "## Task 29:")
- [x] Task 30: `CollectionRegisteredEventListener` (recycler-service) — detalle: tasks/LEARNINGS.md (grep "## Task 30:")
### Checkpoint 15: Direction A wired (unit-level)
- [x] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [x] Human review before the end-to-end IT proves it over real RabbitMQ — approved 2026-09-17

- [x] Task 31: Direction A end-to-end IT — detalle: tasks/LEARNINGS.md (grep "## Task 31:")
### Checkpoint 16: Direction A complete and proven end-to-end
- [x] All of Direction A's Success Criteria bullets in `SPEC-cross-service-events.md` verified with real evidence — full flow, redelivery, and concurrency all proven against real Postgres/RabbitMQ Testcontainers, not mocks or direct calls alone
- [ ] Human review before starting Direction B

## Phase 16: Direction B (recycler-service → collection-service, blocking)

- [x] Task 32: `Certification.notifiedExpiredAt` + `renew()` reset — detalle: tasks/LEARNINGS.md (grep "## Task 32:")
- [x] Task 33: `CertificationExpiryScanJob` + `CertificationExpiredEventPublisher` — detalle: tasks/LEARNINGS.md (grep "## Task 33:")
- [x] Task 34: `CertificationRenewedEventPublisher` — detalle: tasks/LEARNINGS.md (grep "## Task 34:")
### Checkpoint 17: recycler-service publishing side complete
- [x] `mvn -pl recycler-service verify` green
- [ ] Human review before wiring collection-service's consumption side

- [x] Task 35: collection-service `certification_status_ledger` + `blocked_association` schema/domain — detalle: tasks/LEARNINGS.md (grep "## Task 35:")
- [x] Task 36: `CertificationStatusEventListener` — detalle: tasks/LEARNINGS.md (grep "## Task 36:")
- [x] Task 37: Enforce the block in `CollectionRecordService.create()` — detalle: tasks/LEARNINGS.md (grep "## Task 37:")
### Checkpoint 18: Direction B wired (unit-level)
- [x] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [ ] Human review before the end-to-end IT

- [x] Task 38: Direction B end-to-end IT — detalle: tasks/LEARNINGS.md (grep "## Task 38:")
### Checkpoint 19: Direction B complete and proven end-to-end
- [x] All of Direction B's Success Criteria bullets in `SPEC-cross-service-events.md` verified with real evidence (Task 38's `CertificationExpiryPublishFlowIT` + `CertificationStatusEventBrokerFlowIT`) — the SPEC file's own checkboxes are left for Checkpoint 20's full line-by-line pass, per this project's established convention (none are ticked incrementally per task)
- [x] Human review before the final ordering test + reactor-wide checkpoint — implicit approval: user directed proceeding with Task 39 directly per the automated review flow

## Phase 17: Ordering test + final verification

- [x] Task 39: Ordering test — detalle: tasks/LEARNINGS.md (grep "## Task 39:")

### Checkpoint 20: Final — ready for review
- [x] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`), RabbitMQ Testcontainer included — 0 failures/errors: shared-kernel 18 unit; recycler-service 70 unit + 48 IT; collection-service 74 unit + 53 IT (was 52, +1 from Task 39's ordering test — the only count that moved)
- [x] All 11 Success Criteria bullets in `SPEC-cross-service-events.md` re-verified line by line with evidence — detalle: tasks/LEARNINGS.md (grep "## Checkpoint 20:"); **amended by Task 40** below — criterion 8's original re-verification trusted `isBlocked()` alone as evidence of dedup on the Direction B (expired→blocked) side, which a Cowork review caught as insufficient
- [x] `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected — no destructive schema change, no existing endpoint contract changed (Task 39's commit touches exactly one test file, no migration/schema files)
- [x] Task 40: Fortalecer test de idempotencia real en Direction B (hallazgo de Cowork sobre Checkpoint 20) — detalle: tasks/LEARNINGS.md (grep "## Task 40:")
- [x] Human review and approval before moving to `reporting-service` — approved 2026-09-19; user confirmed `cross-service-events` (M4) closed and approved, including Task 40's fix, before starting `/plan` for `reporting-service`

## Phase 18: reporting-service infra

- [x] Task 41: reporting-service scaffolding — detalle: tasks/LEARNINGS.md (grep "## Task 41:")

### Checkpoint 21: Service boots
- [x] `mvn -pl reporting-service spring-boot:run` boots cleanly, empty changelog applies
- [x] `mvn verify` still green across the whole reactor with the new module present
- [x] Human review before first entity slice — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 19: TrackedCompany

- [x] Task 42: TrackedCompany persistence — detalle: tasks/LEARNINGS.md (grep "## Task 42:")

- [x] Task 43: TrackedCompany API — detalle: tasks/LEARNINGS.md (grep "## Task 43:")

### Checkpoint 22: TrackedCompany CRUD works end-to-end
- [x] `mvn -pl reporting-service verify` green
- [x] Manual check: register → get → list a tracked company via curl; duplicate RUC → 409 `RPT-002` — real curl against `spring-boot:run`, not just the IT
- [x] Human review before SigersolSync slice — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 20: SigersolSync (introduces the EXCLUDE USING gist pattern)

- [x] Task 44: SigersolSync persistence — detalle: tasks/LEARNINGS.md (grep "## Task 44:")

- [x] Task 45: SigersolSync API — detalle: tasks/LEARNINGS.md (grep "## Task 45:")

- [x] Task 46: SigersolSync overlap concurrency test — detalle: tasks/LEARNINGS.md (grep "## Task 46:")

### Checkpoint 23: SigersolSync complete, exclusion-constraint pattern proven
- [x] `mvn -pl reporting-service verify` green
- [x] Manual check: register → get → list; overlapping period → 409 `RPT-006`; adjacent non-overlapping period → 201 — via real `spring-boot:run` + curl
- [x] Task 46's negative verification (constraint temporarily removed, test confirmed to fail, restored) documented in `tasks/LEARNINGS.md`
- [x] Human review before wiring event consumption — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 21: Event consumption (TracedCollectionEntry ledger)

- [x] Task 47: reporting-service RabbitMQ wiring + TracedCollectionEntry schema — detalle: tasks/LEARNINGS.md (grep "## Task 47:")

- [x] Task 48: `CollectionRegisteredEventListener` (reporting-service) — detalle: tasks/LEARNINGS.md (grep "## Task 48:")

- [x] Task 49: Event consumption end-to-end IT — detalle: tasks/LEARNINGS.md (grep "## Task 49:")

### Checkpoint 24: Event consumption wired and proven
- [x] `mvn -pl reporting-service verify` green
- [x] A `CollectionRecord` created in `collection-service` results in a new `traced_collection_entry` row here, over the real shared exchange, with zero changes to `collection-service` — confirmed by booting both real services against the shared Docker Postgres/RabbitMQ, creating a real neighbor + `CollectionRecord` via `collection-service`'s HTTP API, and querying `reporting-service`'s own `traced_collection_entry` table directly via `psql`
- [x] Redelivery proven a no-op on the sum, not just the row count
- [x] Human review before certificate issuance — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 22: Certificate issuance

- [x] Task 50: EsgCertificate + EsgCertificateLineItem persistence — detalle: tasks/LEARNINGS.md (grep "## Task 50:")

- [x] Task 51: Certificate summary preview — detalle: tasks/LEARNINGS.md (grep "## Task 51:")

- [x] Task 52: Certificate issuance — detalle: tasks/LEARNINGS.md (grep "## Task 52:")

- [x] Task 53: Certificate concurrency + immutability tests — detalle: tasks/LEARNINGS.md (grep "## Task 53:")

### Checkpoint 25: Certificate issuance complete
- [x] `mvn -pl reporting-service verify` green
- [x] Manual check: preview → issue → 409 on overlap (`RPT-004`) → 409 on missing SIGERSOL data (`RPT-005`) → immutability confirmed against a real backdated event — via real `spring-boot:run` (both `reporting-service` and `collection-service`) + curl, real RabbitMQ propagation, not just the IT
- [x] Task 53's negative verification documented in `tasks/LEARNINGS.md`
- [x] Human review before PDF/CSV export — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 23: PDF/CSV export

- [x] Task 54: CertificatePdfExporter — detalle: tasks/LEARNINGS.md (grep "## Task 54:")

- [x] Task 55: CertificateCsvExporter — detalle: tasks/LEARNINGS.md (grep "## Task 55:")

- [x] Task 56: Export HTTP endpoints — detalle: tasks/LEARNINGS.md (grep "## Task 56:")

### Checkpoint 26: Export complete
- [x] `mvn -pl reporting-service verify` green
- [x] Manual check: `curl` both `.../pdf` and `.../csv` for a real issued certificate, open/parse both — detalle: tasks/LEARNINGS.md (grep "## Checkpoint 26:")
- [x] Human review before Polish — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 24: Polish

- [x] Task 57: springdoc-openapi wiring — detalle: tasks/LEARNINGS.md (grep "## Task 57:")

### Checkpoint 27: Full reporting path complete
- [x] `mvn verify` green across the whole reactor
- [x] Manual check: full flow through real HTTP — register a `TrackedCompany` → register a `SigersolSync` → a real `collection-service` `CollectionRecord` lands as a `traced_collection_entry` here → preview → issue → download PDF and CSV — detalle: tasks/LEARNINGS.md (grep "## Checkpoint 27:")
- [x] `recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected

### Checkpoint 28: Final — ready for review (M5 module close)
- [x] `mvn verify` green across the whole reactor, RabbitMQ Testcontainer included
- [x] All Success Criteria bullets in `SPEC-reporting-service.md` re-verified line by line with evidence, same discipline as `cross-service-events`' Checkpoint 20 — detalle: tasks/LEARNINGS.md (grep "## Checkpoint 28:")
- [x] `shared-kernel`/`recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected — no destructive schema change, no existing endpoint contract changed
- [x] Human review and approval — this is the last business-logic module before `ci-pipeline`/`deployment` per the capability map's build order — ver tasks/LEARNINGS.md#checkpoint-28-human-review

## Phase 25: ci-pipeline workflow

- [x] Task 58: Add .github/workflows/ci.yml (GitHub Actions: build+test on push/PR) — detalle: tasks/LEARNINGS.md (grep "## Task 58:")

### Checkpoint 29: CI proven live (M6 module close)
- [x] Push to main triggers a real Actions run; `mvn -B verify` passes reactor-wide (shared-kernel + recycler-service + collection-service + reporting-service), including at least one Postgres IT and one RabbitMQ IT, zero repo secrets configured — detalle: tasks/LEARNINGS.md (grep "## Checkpoint 29:")
- [x] Negative check: a deliberately broken test turns the run red with that test named in the log; reverted; green again — same grep
- [x] A real PR (throwaway branch) shows the same workflow as a status check on the PR itself, not only on direct pushes to `main` — same grep
- [x] Two rapid pushes to the same branch show the earlier run cancelled (concurrency block proven live, not just present in the YAML) — same grep
- [x] `mvn verify` still green locally across the whole reactor — zero regression from adding the workflow file
- [x] Human review and approval — last module before `deployment` per the capability map's build order — ver tasks/LEARNINGS.md#checkpoint-29-human-review

## Phase 26: auth-service infra

- [x] Task 59: auth-service scaffolding (new Maven module, pom, application.yml port 8084, empty Liquibase master) — detalle: tasks/LEARNINGS.md (grep "## Task 59:")

### Checkpoint 30: Service boots
- [x] `mvn -pl auth-service spring-boot:run` boots cleanly, empty changelog applies
- [x] `mvn verify` still green across the whole reactor with the new module present
- [x] Human review before first entity slice — implicit approval: user directed `/build auto` for the whole module, automated flow per CLAUDE.md

## Phase 27: StaffUser + login core

- [x] Task 60: StaffUser persistence (Liquibase v0.1.0, unique email constraint, BCryptPasswordEncoder bean) — detalle: tasks/LEARNINGS.md (grep "## Task 60:")
- [x] Task 61: Admin bootstrap runner (SecureRandom password, logged once, gated on staff_user being empty — never a later reset) — detalle: tasks/LEARNINGS.md (grep "## Task 61:")
- [x] Task 62: shared-kernel JwtSecurityConfig (JwtDecoder, JwtAuthenticationEntryPoint/AccessDeniedHandler) + auth-service's own SecurityConfig — detalle: tasks/LEARNINGS.md (grep "## Task 62:")
- [x] Task 63: Login API (JwtIssuer via Nimbus, POST /auth/login, AUTH-001 same error for unknown email or wrong password) — detalle: tasks/LEARNINGS.md (grep "## Task 63:")

### Checkpoint 31: auth-service core proven
- [x] `mvn -pl auth-service verify` green
- [x] Manual check: boot with ADMIN_BOOTSTRAP_EMAIL set, retrieve the logged password, curl POST /auth/login, decode the returned JWT and confirm claims
- [x] Human review before wiring the other three services — implicit approval, user directed `/build auto` for the whole module

## Phase 28: Staff onboarding + self endpoint

- [x] Task 64: POST /auth/staff-users (requires a valid token; AUTH-002 on duplicate email) — detalle: tasks/LEARNINGS.md (grep "## Task 64:")
- [x] Task 65: GET /auth/me — detalle: tasks/LEARNINGS.md (grep "## Task 65:")

### Checkpoint 32: auth-service feature-complete
- [x] `mvn -pl auth-service verify` green
- [x] Manual check: login as bootstrap account → create second staff user → login as second user → GET /auth/me confirms the right identity
- [x] Human review before retrofitting recycler-service/collection-service/reporting-service — implicit approval, user directed `/build auto` for the whole module

## Phase 29: Retrofit recycler-service

- [x] Task 66: recycler-service auth retrofit — detalle: tasks/LEARNINGS.md (grep "## Task 66:")

### Checkpoint 33: recycler-service secured
- [x] `mvn -pl recycler-service verify` green — every pre-existing test still passes, zero loosened assertions (verified without .env.local, CI-like)
- [x] Human review before collection-service — approved 2026-09-25 (independent Cowork review of the real code, no findings)

## Phase 30: Retrofit collection-service

- [x] Task 67: collection-service auth retrofit — detalle: tasks/LEARNINGS.md (grep "## Task 67:")

### Checkpoint 34: collection-service secured
- [x] `mvn -pl collection-service verify` green (verified without .env.local, CI-like)
- [x] Human review before reporting-service — approved 2026-09-25 by user with the M7 gate change (see tasks/plan.md "Gate change for the rest of M7")

## Phase 31: Retrofit reporting-service

- [x] Task 68: reporting-service auth retrofit — detalle: tasks/LEARNINGS.md (grep "## Task 68:")

### Checkpoint 35: reporting-service secured
- [x] `mvn -pl reporting-service verify` green (verified without .env.local, CI-like)
- [x] Human review before the cross-service proof and polish — waived 2026-09-25 by user decision (see tasks/plan.md "Gate change for the rest of M7")

## Phase 32: Cross-service proof + polish

- [x] Task 69: cross-service token portability IT (new e2e-tests module) — detalle: tasks/LEARNINGS.md (grep "## Task 69:")
- [x] Task 70: springdoc-openapi wiring for auth-service — detalle: tasks/LEARNINGS.md (grep "## Task 70:")

### Checkpoint 36: Final — ready for review (M7 module close)
- [x] `mvn verify` green across the whole reactor (six modules incl. test-only e2e-tests — CrossServiceTokenPortabilityIT must run and pass) — clean worktree, no .env.local; see tasks/LEARNINGS.md "### Checkpoint 36"
- [x] All Success Criteria bullets in SPEC-auth-service.md re-verified line by line with evidence — 2 were uncovered by any task and fixed here (actuator, .env.local.example); see tasks/LEARNINGS.md "### Checkpoint 36"
- [x] Every pre-existing *ApiIT across all three retrofitted services still green, now sending tokens — zero lost coverage (counts: see tasks/LEARNINGS.md "### Checkpoint 36")
- [x] Swagger UI and /actuator/health remain publicly reachable with no token on all four services — proven over real HTTP against the 4 real processes (e2e) + per-service ITs; the manual compose-backed curl was blocked by Hyper-V excluded ports, see tasks/LEARNINGS.md "### Checkpoint 36"
- [x] Human review and approval — last module before `deployment` per the capability map's build order — approved 2026-09-26 (independent Cowork review of the real code, no findings; manual curl skipped by user decision, covered by e2e-tests)

## Phase 33: Production-ready configuration (M8 deployment — full detail in tasks/plan.md)

- [x] Task 71: recycler-service liveness as Render health check — ver tasks/LEARNINGS.md#task-71
- [x] Task 72: liveness on collection/reporting/auth-service — ver tasks/LEARNINGS.md#task-72
- [x] Task 73: PORT + rabbitmq vhost/ssl + auth-service AMQP excluded + .env.local.example — ver tasks/LEARNINGS.md#task-73

### Checkpoint 37: Configuration ready, zero regression
- [x] `mvn -B verify` green reactor-wide, clean worktree, no .env.local, e2e-tests included
- [x] Test counts = Checkpoint 36 totals + only the new liveness/readiness assertions
- [x] Liveness permit is the exact path in all four SecurityConfigs; no `/actuator/**` or `/actuator/health/**` anywhere

## Phase 34: Container images

- [x] Task 74: recycler-service Dockerfile + root .dockerignore, measured at 512m/0.1 CPU — ver tasks/LEARNINGS.md#task-74
- [x] Task 75: collection/reporting/auth-service Dockerfiles, all four measured at 512m/0.1 CPU — ver tasks/LEARNINGS.md#task-75

### Checkpoint 38: Images within Render free limits (go/no-go)
- [x] All four boot at 512m/0.1 CPU, liveness 200, OOMKilled=false
- [x] Measurements in LEARNINGS; startup accepted by user decision (2026-09-28): 103–126 s cold on Render, CDS/AOT unprioritized follow-up — see tasks/LEARNINGS.md#task-81
- [x] No `.env.local` / host `target/` inside any image

## Phase 35: Login rate limiting

- [x] Task 76: AUTH-004 + ClientIpResolver (provisional rule) + bounded Bucket4j LoginRateLimiter — ver tasks/LEARNINGS.md#task-76
- [x] Task 77: LoginRateLimitFilter on POST /auth/login + LoginRateLimitIT — ver tasks/LEARNINGS.md#task-77

### Checkpoint 39: Rate limit proven locally
- [x] `mvn -B verify` green reactor-wide, clean worktree, no .env.local
- [x] Pre-existing auth-service IT assertions unchanged
- [x] ClientIpResolver rule marked provisional until Task 82

## Phase 36: Deployment descriptors

- [x] Task 78: render.yaml Blueprint (4 free web services, liveness health check, checksPass, buildFilter, esg-shared) — ver tasks/LEARNINGS.md#task-78
- [x] Task 79: docs/deployment.md runbook (placeholders only; no cold-start figure until Task 81) — ver tasks/LEARNINGS.md#task-79
- [x] Task 80: .github/dependabot.yml (maven, github-actions, docker; weekly; minor+patch grouped) — ver tasks/LEARNINGS.md#task-80

### Checkpoint 40: Repo deploy-ready
- [x] `mvn -B verify` green reactor-wide, clean worktree, no .env.local
- [x] Pushed to main (confirmed with user), CI green on that commit — ac9f54e, see tasks/LEARNINGS.md "### Checkpoint 40"
- [x] Repo-wide secret sweep clean

## Phase 37: Go live (user-driven)

- [x] Task 81: go-live provisioning + runbook fixed against reality — ver tasks/LEARNINGS.md#task-81
- [x] Task 82: Render client-IP header confirmed (XFF, 1 trusted hop); ClientIpResolver finalized, temp log removed — ver tasks/LEARNINGS.md#task-82

### Checkpoint 41: Platform live
- [x] Four public URLs, same commit (read as: each service's buildFilter paths identical to main HEAD), liveness green in Render — see tasks/LEARNINGS.md "### Checkpoint 41"
- [x] ClientIpResolver rule confirmed; temporary log gone — see tasks/LEARNINGS.md "### Checkpoint 41"

## Phase 38: Production proof

- [x] Task 83: production smoke check, 6/6 green (incl. CloudAMQP event path and real 429) — ver tasks/LEARNINGS.md#task-83
- [x] Task 84: CD proof — buildFilter both ways, checksPass red→no deploy / green→deploy off main (PR #13, throwaway branch deleted), Dependabot parsed — ver tasks/LEARNINGS.md#task-84

### Checkpoint 42: Final — ready for review (M8 module close)
- [x] Every SPEC-deployment.md Success Criteria bullet re-verified with evidence — 10 met, #5 accepted gap, #11 waived by user; see tasks/LEARNINGS.md "### Checkpoint 42"
- [x] `mvn -B verify` green, clean worktree; CI green on final main commit — 8b51a52, see tasks/LEARNINGS.md "### Checkpoint 42"
- [x] Render build-minute / instance-hour usage recorded — 1.65/750 h, 0/500 min, $0.00 (2026-09-30)
- [x] CAPABILITY-MAP.md status updated
- [x] Human review and approval — hard stop, never automatic — M8 closed 2026-10-01. Final main: ccb05cb, CI green (run 36812617933). #5 accepted gap (503 DOWN not pinned; liveness 200 + Render health check proven). #11 waived by the user: personal project whose goal is to become a template; no third-party validation planned.

## Post-M8: CORS (PASO 0) + deployed-commit check

- [x] Task 85: CORS + `/actuator/info` deployed commit + `scripts/verify-deploy.sh` (d21cdc3, verified live on all four) — ver tasks/LEARNINGS.md#task-85

## Frontend

- [x] F1: Angular 21 skeleton — Preparando el sistema, Login, Empresas, interceptors/guard, proxy, Vitest + Playwright (31a9c47) — ver tasks/LEARNINGS.md#frontend
- [x] F1b: CI job for frontend-demo (path-gated: npm ci, lint, unit tests, build; Playwright deferred) — ver tasks/LEARNINGS.md#frontend
- [x] F2: Ficha de empresa — resumen del mes en curso, gráfica SVG de 12 períodos, tabla/tarjetas de certificados — ver tasks/LEARNINGS.md#frontend
- [x] F3: Detalle del certificado (elemento firma) + descargas PDF/CSV con nombre sanitizado — ver tasks/LEARNINGS.md#frontend
- [x] F4: Alta de recojo (vecino, asociación, fecha, kg, cronograma opcional) — ver tasks/LEARNINGS.md#frontend
- [x] F5: Asociaciones — absorbida por Frontend v2 / V1 (9639af6)
- [x] F6: Despliegue del frontend (https://esg-frontend-egcf.onrender.com) + `CORS_ALLOWED_ORIGINS` en esg-shared + Playwright en CI — ver tasks/LEARNINGS.md#frontend. Límite: ficha, certificado y descargas no se pudieron probar en vivo porque producción no tiene empresas; cubiertas por 40 Playwright en CI y la comprobación local de la build de producción bajo la misma CSP

## Rediseño visual: dirección A "Ticket de balanza" (elegida 2026-10-10)

- [x] R1: Tokens y tipografía (Archivo + Martian Mono autoalojadas, controles propios, un bloque de error, deshabilitado, cifras tabulares)
- [x] R2: Shell y navegación (foco ≥ 3:1 sobre la barra oscura)
- [x] R3: Login (ticket + titular centrados como conjunto; impresión una vez)
- [x] R4: Empresas y ficha con el gráfico (talones dentados, resumen dentado, tabla plana, barras legibles)
- [x] R5: Certificado (ticket dentado, h1 sin rótulo encima, impresión una vez)
- [x] R6: Registrar recojo (formulario plano)
- [x] R7: Movimiento, bundle, CSP en build de producción, docs de diseño y TOOLING.md

### Pendientes (tareas aparte)

- [x] Módulo de cobertura completa → ver "Frontend v2" abajo

- [x] Datos de demo en producción: cargados con scripts/demo-seed → ver "Handoff v2" abajo

## Frontend v2: cobertura completa (brief 2026-10-10, dirección A, sin cambios de backend)

- [x] V1 Asociaciones + regla "por vencer" (9639af6, 67169a8) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] V2 Vecinos, cronogramas, recojos, buscador, asociación recordada, bitácora (b9a8d89) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] V3 Registrar empresa (23ead4e) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] V4 SIGERSOL "ingreso manual" (b846bd1) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] Reduced-motion: 0s en transiciones, test de alto contraste estable (a1098a0) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] V5 Emitir certificado (62f5987) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] V6 Mi sesión + crear personal (f50ba20) — ver tasks/LEARNINGS.md#frontend-v2-cobertura-completa
- [x] Cierre: docs/frontend.md (matriz 27/44, reglas del frontend, limitaciones, pendientes de backend), README
- [x] Human review del módulo y aprobación (Angel y Cowork, 2026-10-10): APROBADO con 2 reservas y 2 decisiones, todas cerradas — ver tasks/LEARNINGS.md#cierre-de-v2-reservas-y-decisiones
- [x] Empresa INACTIVE: se BLOQUEA la emisión (regla del frontend, docs/frontend.md)
- [x] scripts/demo-seed: script idempotente, simulación por defecto (README en scripts/demo-seed/)
- [x] Carga de datos de demo en producción con scripts/demo-seed: 16 altas + certificado de Setiembre 2026 (66,95 kg, 85,5 %)

## Handoff v2 (2026-10-10)

### Estado de V1–V6 (todo pusheado, CI verde y verify-deploy 5/5; detalle en tasks/LEARNINGS.md#frontend-v2-cobertura-completa)

- V1 Asociaciones: `9639af6`, más la regla "por vencer" en `67169a8`
- V2 Vecinos, cronogramas, recojos, buscador, asociación recordada, bitácora: `b9a8d89`
- V3 Registrar empresa: `23ead4e`
- V4 SIGERSOL "ingreso manual": `b846bd1`
- Arreglo del test de alto contraste (reduced-motion, transiciones a 0s): `a1098a0`
- V5 Emitir certificado: `62f5987`
- V6 Mi sesión y crear cuentas del personal: `f50ba20`
- Docs de cierre (docs/frontend.md, matriz 27/44): `d9af954`
- Revisión de seguridad: CERRADA y desplegada. `bf88db8` (gitleaks, npm audit, Dependabot npm), `c419934` (AUTH-005), `171b399` (RPT-009), `87b1853` (frontend AUTH-005/RPT-009), `e941463` (checklist de seguridad), `7d261b6`, `f907739` (fix del CSV: formula injection), `c00d66f`
- Cierre de la revisión de v2: `412a7e4` (scripts/demo-seed + job de CI), `28100be` (docs), `068ded7` (empresa inactiva bloqueada + espacio de "Ingreso manual"); CI verde y verify-deploy 5/5
- Job `demo-seed` del CI: limitado a cambios en `scripts/` (o en el propio workflow), igual que el job del frontend

### Decisiones de Angel (no volver a preguntar)

- La empresa real es la tracked company de reporting-service; `/companies` de collection-service no se usa (solapamiento documentado).
- Sin rol admin: cualquier persona del personal emite certificados y crea personal; confirmación explícita antes de lo irreversible o sensible.
- SIGERSOL se rotula siempre como "ingreso manual".
- Huecos del backend: solo se documentan (docs/frontend.md).
- "Por vencer": regla del frontend, menos de 30 días en hora de Lima, constante `EXPIRING_SOON_DAYS`, rotulada en pantalla; no es estado del backend.
- V5: mes completo por defecto y rango libre como opción secundaria; BLOQUEAR períodos que no terminaron (fin >= hoy en Lima), diciendo desde qué fecha se podrá emitir; BLOQUEAR 0 kg; confirmación = casilla + botón que nombra el período.
- V6: contraseña inicial generada SIEMPRE por el navegador (crypto.getRandomValues, 16 caracteres, sin ambiguos), sin campo ni política; entrada desde Mi sesión, sin sexta sección en la nav; "vence a las HH:MM (dura X y no se renueva)" derivado de exp/iat del token; contraseña solo en memoria (nunca storage/URL/history.state/consola/bitácora/atributos), sin oferta de guardarla.
- Datos de demo: no en el módulo v2; después, desde las pantallas, ficticios y con aprobación de Angel por lista.

### Pendientes

- [x] Regla empresa inactiva: Angel decidió BLOQUEAR la emisión (regla del frontend; el backend la permite).
- [x] scripts/demo-seed: hecho; lo corre Angel en su PowerShell, nunca desde la terminal de Claude.
- [x] 2 reservas: (1) "Ingreso manual" pegado al "%" a 360 px en el borrador de emisión; (2) capturas v4/empresa-sin-sigersol-* obsoletas, rehechas (locales, gitignored).
- [x] Human review del módulo v2 y aprobación (Angel y Cowork, 2026-10-10).
- [x] Carga de los datos de demo de abajo en producción (Angel, con scripts/demo-seed): 16 altas + certificado de Setiembre 2026 emitido con 66,95 kg y 85,5 %.

### Datos de demo ficticios (lista aprobada por Angel el 2026-10-10; CARGADOS en producción)

Cargados con scripts/demo-seed (copia exacta en scripts/demo-seed/data.mjs): 16 altas y el certificado de Setiembre 2026 (66,95 kg, 85,5 %), ya emitido e irreversible. Volver a correr el script no duplica nada.

0. Lo que no tiene pantalla, para decidir cómo hacerlo:
   - Asociación: "Asociación de Recicladores Demo Norte (ficticia)", RUC 20999000011. Si ya existe una asociación en producción, se puede usar esa.
   - Certificación: "Registro municipal (demo)", del 15/01/2026 al 15/01/2027, para que salga como vigente.

Desde las pantallas:

1. Vecinos:
   - Carmen Flores (demo), Jr. Demo 101, Comas
   - Jorge Salas (demo), Av. Demo 202, Comas
   - Lucía Paz (demo), Calle Demo 303, Independencia
2. Cronogramas: Carmen el lunes a las 08:00, Jorge el miércoles a las 09:30 y Lucía el viernes a las 07:00.
3. Recojos de setiembre de 2026 (66.95 kg en total):
   - Carmen: 07/09 con 12.50 kg, 14/09 con 10.75 kg y 21/09 con 11.20 kg
   - Jorge: 09/09 con 8.40 kg y 23/09 con 9.10 kg
   - Lucía: 18/09 con 15.00 kg
4. Empresa: "Empresa Demo Envases S.A.C. (ficticia)", RUC 20999000029, de la asociación demo.
5. SIGERSOL (ingreso manual): del 01/09/2026 al 30/09/2026, cumplimiento de 85.50 %, 70.00 kg declarados, con la nota "Dato de demostración, no oficial".
6. Certificado de Setiembre 2026: saldría con 66.95 kg y 85.5 %. Es irreversible, así que pide aprobación aparte.

No se propone crear cuentas del personal de demo: la contraseña sería permanente en producción.

## v3 (brief 2026-10-10; plan y decisiones en tasks/plan.md → "v3")

Rama y PR por módulo (`v3/mN`). Freno para el OK de Angel antes del merge: M2, M3, M4, M5, M7. Freno antes de M4: Angel crea el token de Decolecta.

### M0: plan, spikes, specs (rama `v3/m0`)
- [x] V3-0.1: plan v3 en tasks/plan.md y tasks/todo.md
- [x] V3-0.2: S1, spike de SIGERSOL Municipal (ubigeo, formato por distrito, último año con datos, unidad) — resultado en tasks/plan.md → "Resultado de S1"; unidad sin resolver
- [x] V3-0.3: S2 (con permiso de Angel, una ejecución): 200 desde EE. UU. en los 3 endpoints; rama desechable borrada
- [x] V3-0.4: enmiendas "Amendment v3" en los 5 SPEC (locales: los SPEC están en .gitignore), con endpoints, campos y códigos nuevos (RPT-010..020, COL-010..013, ASO-004)
- [x] V3-0.5: 4 borradores de ADR en docs/decisiones-v3/ (respaldo no verificada, snapshot SIGERSOL, distrito en el evento, plausibilidad condicionada)
- [x] V3-0.6: PR #23 de M0, CI verde y PASS; fusionado por Angel el 2026-10-11

- [x] V3-0.7: respuestas de Angel a M0: D4 descartada, nombres de API en inglés, M5 reducido (plan, ADR y SPEC locales actualizados)
- [x] V3-0.8: migrar los 4 borradores de ADR de `docs/decisiones-v3/` a `portal/Writerside/topics/decisiones/` con la plantilla del portal (rama `portal/adr-migration`) — PR #27, fusionado por Angel el 2026-10-11

### Portal (tareas propias, fuera de la numeración V3-x.y; detalle y próximas en portal/PLAN.md)

- [x] Task 3 de portal/PLAN.md: specs OpenAPI por servicio (`OpenApiExportIT`/`OpenApiDocsIT`, determinista) + job informativo `openapi-drift` — PR #29 (rama `portal/openapi-export`), checks verdes, fusionado por Angel a mano el 2026-10-11 (antes que #28)
- [x] PR #27 (rama `portal/adr-migration`, V3-0.8): hallazgo de gitleaks en `secrets` corregido — era un falso positivo ajeno a este PR (2 UUID de test en los commits `965dec5`/`17a593a` de `v3/m2`, ya permitidos en `.gitleaks.toml` con la misma política de siempre). Checks verdes, fusionado por Angel a mano el 2026-10-11.

#### Pendiente (bloqueo de infraestructura, no de código)

- [x] `gh pr merge` fallaba en ambos PR (#27 y #29) con `403 Resource not accessible by personal access token` (confirmado por GraphQL y por REST `pulls/.../merge`), aunque el repo mostraba `admin:true` para la cuenta — era el *token* el que no tenía permiso de escritura en pull requests, no el usuario. Resuelto: Angel fusionó #27 y #29 a mano desde GitHub el 2026-10-11, sin reintento necesario.
- [ ] Siguiente en el portal (task 4 de portal/PLAN.md): glosario de estados y catálogo de errores por servicio (`errores-<servicio>.md`, `estados-<servicio>.md`), más el job informativo `catalog-drift` — mismo patrón que `openapi-drift` (informa, no bloquea, mismo filtro de disparo).

### M1: shared-kernel (rama `v3/m1`) — PR #26, fusionado por Angel el 2026-10-11
- [x] V3-1.1: value object `Ruc` (11 dígitos, prefijo 10/15/16/17/20, módulo 11); pruebas con 20131312955 válido, 20999000029 y 20999000037 inválidos
- [x] V3-1.2: catálogo ubigeo INEI (43 Lima Metropolitana + 7 Callao) desde la fuente oficial, verificado con tildes y ñ; normalizador solo para comparar
- [x] V3-1.3: `CurrentActor` (sub + email del JWT)

### M2: collection-service + frontend, vecinos (rama `v3/m2`, worktree `../esg-v3-m2`) — **freno: OK de Angel antes del merge**

Compatibilidad (parcial): el alta acepta `districtUbigeo` o, como respaldo, el `district` en texto si coincide con el catálogo (`UbigeoCatalog.findByName`); si no, COL-010. Ruptura conocida hasta que se despliegue el frontend de V3-2.6: el v2 manda `district: null` cuando el campo queda vacío, o un texto fuera del catálogo ("Surco", "Cercado de Lima"), y eso ahora es COL-010. Por eso backend y frontend de M2 se despliegan juntos.

- [x] V3-2.1: ubigeo del vecino (changeset + backfill, COL-010, filtro por código y por nombre v2) — PASS; ver tasks/LEARNINGS.md#v3-21
- [x] V3-2.2: editar vecino (PATCH, vía `update()` de Persistable) — PASS
- [x] V3-2.3: desactivar/reactivar con motivo, historial con actor del JWT, pausa de cronogramas (D8), compare-and-set contra carreras — PASS; ver tasks/LEARNINGS.md#v3-23
- [x] V3-2.4: COL-013 (recojo, cronograma nuevo o reactivado para vecino INACTIVO), FOR SHARE contra la desactivación concurrente — PASS; ver tasks/LEARNINGS.md#v3-24
- [x] V3-2.5: eliminación lógica con motivo (COL-011 con recojos, cancela cronogramas, COL-001 en adelante; las lecturas anidadas quedan como auditoría) — PASS; ver tasks/LEARNINGS.md#v3-25
- [x] V3-2.6: frontend — selector de distrito (combobox agrupado Lima/Callao con búsqueda sin tildes; catálogo TS verificado contra el CSV de shared-kernel), alta y filtro con el selector, sin "Escríbelo…"; editar, desactivar/reactivar con motivo y confirmación, eliminar, cronogramas pausados al reactivar (D8), acciones en "Inactivos" — PASS (501db2e, 6aac591); ver tasks/LEARNINGS.md#v3-26
- [x] V3-2.7: docs — docs/frontend.md (matriz, reglas, limitaciones), catálogo de errores COL-010..013 — ver tasks/LEARNINGS.md#v3-27
### Checkpoint V3-M2
- [x] `mvn -B -pl collection-service verify` en worktree limpio con compose detenido; frontend `npm test` + Playwright verdes — 88 backend (87 + `OpenApiExportIT` traído por el merge de main), 195 unit frontend, 126 Playwright, todo verde; ver tasks/LEARNINGS.md#checkpoint-v3-m2
- [ ] PR con CI verde; **freno: OK de Angel antes del merge** (migración en Neon al desplegar)

## M3: anulación de certificados (reporting-service + frontend)

- [x] V3-3.1: migración v0.2.0 (estado + datos de anulación, CHECK de coherencia, exclusión de periodo solo para ISSUED, con rollback), dominio y repositorio con UPDATE condicional — PASS; ver tasks/LEARNINGS.md#v3-31
- [x] V3-3.2: `POST …/certificates/{id}/annul` (actor del JWT, RPT-019), RPT-015 al emitir para empresa INACTIVE — PASS; ver tasks/LEARNINGS.md#v3-32
- [x] V3-3.3: PDF con pie y sello ANULADO, CSV con estado y datos de anulación — PASS; ver tasks/LEARNINGS.md#v3-33
- [x] V3-3.4: frontend — anular con motivo y confirmación, sello, etiqueta en la lista, anulados fuera de resumen/gráfica/solape; docs/frontend.md — PASS; ver tasks/LEARNINGS.md#v3-34
### Checkpoint V3-M3
- [x] `mvn -B -pl reporting-service -am verify`: 79 unit + 80 IT, 0 fallos; frontend 205 unit, 134 Playwright, lint — ver tasks/LEARNINGS.md#checkpoint-v3-m3
- [x] PR #30 con CI verde, mergeado (squash, 3964c8a); desplegados reporting-service y frontend sirviendo 3964c8a (`scripts/verify-deploy.sh`); la migración v0.2.0 se aplicó al arrancar reporting-service (liveness 200)

### Handoff M4 (corto)
- Entra: RUC local + SUNAT (puerto, adaptador, caché; proveedor Decolecta), respaldo D2, **ciclo de vida de empresa** (desactivar/eliminar, `RPT-016/017/018`; el orden de errores al emitir ya reserva `RPT-016` entre `RPT-015` y `RPT-004`), contacto, compatibilidad N/N+1 del POST.
- Hecho en M3 y reutilizable: `ReportingErrors` RPT-015/019, `reason-confirm.ts` en `shared/`, `CurrentActor.from(jwt)`, patrón de UPDATE condicional + CHECK. Hoy ninguna ruta deja una empresa en INACTIVE (las ITs lo hacen por SQL): al llegar el PATCH, cambiar esas ITs para usar la ruta.
- **Frenos antes de arrancar:** Angel crea la cuenta y el token de Decolecta; aviso antes de tocar `render.yaml`; OK de Angel antes del merge.
- Ruido conocido: las ITs de reporting muestran un intento de conexión a RabbitMQ en localhost:5672 pese a `auto-startup=false`; no falla nada.

## M4: RUC, SUNAT y ciclo de vida de empresa (reporting-service + frontend, rama `v3/m4`, sin PR) — **frenos: token de Decolecta, `render.yaml`, migración (`pre-m4` en Neon), OK de Angel antes del merge**

Rebanadas (máx. 10 commits en la rama, uno por rebanada vertical con tasks/ y docs dentro):
- [x] V3-4.1: RUC local (RPT-010) + puerto `RucLookup` + adaptador Decolecta (probado con respuestas grabadas, sin llamada real) + caché de 24 h + `RUC_LOOKUP_PROVIDER` sin default (`none` prohibido con `RENDER=true`); alta sin razón social (RPT-011/012/013, NO HABIDO con aviso), `name` tolerado e ignorado y deprecado (versión N), contacto; migración v0.3.0 con todo el esquema de M4; frontend (alta sin razón social, contacto, bloque SUNAT en la ficha, mensajes); OpenAPI del portal. **Falta:** primera llamada real a Decolecta (freno 1), migración en Neon (freno 4), variables en `render.yaml` (freno 2).
- [x] V3-4.2: respaldo D2 (`POST /unverified`, RPT-014), `sunat-refresh`, RPT-016 al emitir; ADR del respaldo pasa a "Implementada"; frontend ("No verificada", "Verificar con SUNAT", formulario de respaldo tras RPT-011, emisión bloqueada). Primera llamada real a Decolecta hecha (una, RUC 20131312955, 200) y guardada como grabación `reporting-service/src/test/resources/sunat/decolecta-ruc-20131312955.json`; `render.yaml` con `RUC_LOOKUP_PROVIDER=decolecta` y `RUC_LOOKUP_TOKEN` (`sync: false`). Agendado para V3-4.4 (hallazgos del revisor en 4.1): concurrencia de dos altas con el mismo RUC, token fuera de logs, test propio de `CachedRucLookup`.
- [x] V3-4.3: ciclo de vida (PATCH, desactivar/activar con motivo e historial, eliminar lógico, RPT-017/018) y las ITs que ponen INACTIVE por SQL pasan a usar la ruta; frontend (ficha: editar contacto, desactivar/reactivar y eliminar con `reason-confirm`). IT de concurrencia con dos altas del mismo RUC (índice parcial → RPT-002) y dos desactivaciones simultáneas. La UI edita solo el contacto (RUC y asociación solo por API). Code-reviewer PASS; límites conocidos agendados en V3-4.4 (ver SPEC): carrera issue vs delete/edit sin bloqueo, listado de certificados de empresa eliminada, ediciones simultáneas de contacto.
- [x] V3-4.4: docs (`docs/frontend.md`, SPEC), cierre y checkpoint — ver tasks/LEARNINGS.md#checkpoint-v3-m4. Cubiertas las 4 observaciones del revisor de 4.1 (RPT-002 sin gastar cuota: 4.2; concurrencia con el índice parcial: 4.3; token fuera de logs y test propio de `CachedRucLookup`: 4.4). Pendiente solo el OK de merge de Angel.

**M4 CERRADO (2026-10-12).** OK de merge de Angel; `git merge --squash` → un solo commit en `main` (`b1cf2a2`, `feat(m4): …`), push, rama `v3/m4` borrada. `scripts/verify-deploy.sh`: CI verde (2 runs) y reporting-service + frontend sirviendo `b1cf2a2` (auth, recycler y collection siguen en `8687bd4`: no los tocó). La rama tuvo 4 commits; módulos tras el squash: reporting-service 30, frontend-demo 31 (pasó el tope de 30 con la autorización de Angel). Migración v0.3.0 aplicada en Neon al desplegar (`pre-m4` existía). Límites conocidos de M4 (SPEC "As built slice 3"): `issue` sin bloqueo frente a borrado/edición concurrentes, listado de certificados de empresa eliminada, ediciones simultáneas de contacto; RPT-020 (versión N+1) en un PR posterior. Las empresas anteriores a M4 quedan "No verificada" hasta verificarlas (limpieza de la demo: M7).

## M5: SIGERSOL Municipal, origen del dato y distrito en el evento (reporting-service + collection-service + recycler-service + frontend, rama `v3/m5`, sin PR) — **única parada: OK de merge de Angel; `pre-m5` ya existe**

Rebanadas (máx. 10 commits en la rama, uno por rebanada vertical con tasks/ y docs dentro). Máximo 3 llamadas reales a SIGERSOL, solo para grabar respuestas de prueba; el CI usa solo grabaciones.
- [x] V3-5.1: distrito en el evento de recojo (D3): `districtUbigeo` en `CollectionRegisteredEvent` (collection publica desde el vecino bloqueado), `traced_collection_entry.district_ubigeo` (migración v0.4.0), reporting lo guarda (inválido = desconocido), recycler lo ignora (IT con broker real); ADR del distrito "Implementada".
- [x] V3-5.2: origen del dato en `SigersolSync` (D1): `origin` (obligatorio), quién (JWT), `reference`; filas viejas "origen no registrado"; cobertura RECORDED/UNVERIFIED en el resumen; migración v0.4.0 (changeset 2); frontend (formulario con origen y referencia, lista con columna Origen, ficha y borrador con "Origen del dato" / "No verificado"); OpenAPI del portal.
- [x] V3-5.3: puerto `SigersolMunicipalStats` (solo conteos), snapshot por año y distrito (`sigersol_district_snapshot`, migración v0.4.0), `DistrictContextService` con degradación a "no disponible" y último dato bueno, `SIGERSOL_MUNICIPAL_PROVIDER` (`none`) en `render.yaml`/docs. **Pendiente (único paso de Angel):** el adaptador HTTP real y las respuestas grabadas necesitan la URL base de SIGERSOL Municipal y un ejemplo de petición/respuesta de cada endpoint (el repositorio no los tiene; ≤ 3 llamadas reales). Al llegar el adaptador: valida los valores de `Found` al construirlos y añade una caché negativa corta (hoy un distrito sin filas repite hasta 6 llamadas por consulta; acotado porque el contexto se congela al emitir).
- [x] V3-5.4: contexto distrital en la emisión: líneas con distrito, contexto congelado en `esg_certificate_district_context` (nunca bloquea; "no disponible" si SIGERSOL no responde), PDF (varias páginas, pie en todas) y CSV con fuente y año, `districtContext` en el certificado y `districts` en el resumen; OpenAPI del portal. El ADR del snapshot queda "parcialmente implementada" hasta que exista el adaptador real. Deuda para el adaptador real (revisor): la consulta HTTP corre dentro de `@Transactional issue` (el store ya usa `REQUIRES_NEW`, así que un SQL rechazado no aborta la emisión, pero hasta 6 años de sondeo por distrito retienen la conexión): calcular el contexto antes de abrir la transacción de emisión.
- [x] V3-5.5: frontend del contexto (borrador y certificado, "no disponible") ya entró en 5.4; cierre: `verify` de todo el monorepo y frontend completos, SPEC de collection, `e2e-tests` con la variable nueva, checkpoint — ver tasks/LEARNINGS.md#checkpoint-v3-m5. **Pendiente (único paso de Angel):** la URL base de SIGERSOL Municipal y un ejemplo de petición/respuesta de `minamlistProvincia`, `minamlistDistrito` y `reporteValorizacion/listaRecicladores` para escribir el adaptador real y grabar las respuestas de prueba (≤ 3 llamadas reales). Pendiente además el OK de merge de Angel.

**M5 cerrado (2026-10-12):** squash en `main` como `d38d04c` (5 commits en la rama), ADR corregido en `00c4cc4`. reporting-service sirve `d38d04c`; recycler, collection y frontend esperan Manual Deploy en Render (el push final solo tocaba el portal). Pendiente: adaptador HTTP real de SIGERSOL (M5b).

### M6–M8
- Detalle por task al abrir cada módulo (ver la tabla de módulos en tasks/plan.md).
