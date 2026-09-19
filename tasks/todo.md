# Task List: shared-kernel + recycler-service + collection-service + cross-service-events + reporting-service

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`, `SPEC-collection-service.md`, `SPEC-cross-service-events.md`, `SPEC-reporting-service.md`.

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

- [ ] Task 47: reporting-service RabbitMQ wiring + TracedCollectionEntry schema
  - **Description:** `TracedCollectionEntryEntity` (event_id PK, association_id, collection_date, weight_kg, received_at) — doubles as Inbox-idempotency marker and the queryable fact table, per the spec's own deliberate simplification versus `recycler-service`'s split ledger+atomic-counter design. Liquibase `v0.1.2_create_traced_collection_entry_table.yaml`. No queue/listener yet (Task 48) — this task is schema + entity + repository only, verified by boot + a direct-repository IT, no contrived RED step (per `[[tdd_scope_for_config_fixes]]`, same convention already applied to the equivalent schema-only tasks in `cross-service-events`, e.g. Tasks 27/29/35).
  - **Acceptance criteria:**
    - [ ] Liquibase creates `traced_collection_entry` with `event_id` as primary key
    - [ ] `TracedCollectionEntryRepository` exposes `findByAssociationIdAndCollectionDateBetween(...)` — the query `CertificateService` will need
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green
    - [ ] `mvn install` — whole reactor still builds
  - **Dependencies:** Task 41
  - **Files likely touched:** `events/ledger/TracedCollectionEntryEntity.java`, `events/ledger/TracedCollectionEntryJpaRepository.java`, `v0.1.2_create_traced_collection_entry_table.yaml`
  - **Estimated scope:** Small (3-4 files)

- [ ] Task 48: `CollectionRegisteredEventListener` (reporting-service)
  - **Description:** Own local `CollectionRegisteredEvent` record (structurally matching `collection-service`'s publisher, independently defined — same "never a shared Java type across services" convention as `recycler-service`'s Direction A consumer), `CollectionRegisteredEventListener` (`@RabbitListener`), `CollectionRegisteredEventProcessor` (separate `@Transactional` bean, same self-invocation reasoning as `recycler-service`'s Task 30), `CollectionRegisteredQueueConfig` (own queue `collection.registered.reporting-service`, bound to the existing exchange/routing key — zero change to `collection-service`). Unit-tested with Mockito only at this stage (real broker IT is Task 49).
  - **Acceptance criteria:**
    - [ ] Listener inserts a `TracedCollectionEntry` row keyed by `event_id`; a simulated duplicate `event_id` is caught (`DataIntegrityViolationException`) and treated as a no-op, not an error
  - **Verification:**
    - [ ] `mvn -pl reporting-service test` green (Mockito-mocked repository, same shape as `CollectionRegisteredEventListenerTest`/`CertificationStatusEventListenerTest` precedent)
  - **Dependencies:** Task 47
  - **Files likely touched:** `events/consume/CollectionRegisteredEvent.java`, `events/consume/CollectionRegisteredEventListener.java`, `events/consume/CollectionRegisteredEventProcessor.java`, `events/consume/CollectionRegisteredQueueConfig.java`
  - **Estimated scope:** Medium (4-5 files)

- [ ] Task 49: Event consumption end-to-end IT
  - **Description:** RabbitMQ Testcontainer IT proving the real broker path: publish a structurally-matching `CollectionRegisteredEvent` JSON payload directly (same "faithful stand-in for the real publisher's wire shape" convention already established and reused across every broker-flow IT in this codebase) → assert it lands in `traced_collection_entry`. Redelivery/idempotency test: the SAME event published twice → assert the period-scoped `SUM(weight_kg)` is unaffected by the duplicate, not just that the row count didn't grow (mirrors Task 40's own correction — a weaker check would not have caught that class of bug).
  - **Acceptance criteria:**
    - [ ] A published event's data appears in `traced_collection_entry` within the IT's polling window
    - [ ] Redelivering the same event is a verified no-op on the period-scoped sum
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green (RabbitMQ + Postgres Testcontainers)
  - **Dependencies:** Task 48
  - **Files likely touched:** `it/events/consume/CollectionRegisteredEventBrokerFlowIT.java`
  - **Estimated scope:** Medium (1-2 files, high test complexity)

### Checkpoint 24: Event consumption wired and proven
- [ ] `mvn -pl reporting-service verify` green
- [ ] A `CollectionRecord` created in `collection-service` results in a new `traced_collection_entry` row here, over the real shared exchange, with zero changes to `collection-service` (confirmed by exercising the real `collection-service` HTTP endpoint against the shared broker, not just this service's own faithful-stand-in test)
- [ ] Redelivery proven a no-op on the sum, not just the row count
- [ ] Human review before certificate issuance

## Phase 22: Certificate issuance

- [ ] Task 50: EsgCertificate + EsgCertificateLineItem persistence
  - **Description:** `EsgCertificate` domain (id, trackedCompanyId, associationId [snapshot], companyName [snapshot], companyRuc [snapshot], periodStart, periodEnd, kilosTrazados [snapshot], hierarchyCompliancePercent [snapshot], issuedAt) and `EsgCertificateLineItem` (certificateId FK, collectionDate, weightKg) — the frozen copy of contributing `TracedCollectionEntry` rows. Liquibase `v0.1.3_create_esg_certificate_table.yaml`: `CONSTRAINT excl_esg_certificate_company_period EXCLUDE USING gist (tracked_company_id WITH =, daterange(period_start, period_end, '[]') WITH &&)` (reuses `btree_gist`, already enabled by Task 44) — the DB-level backstop for RPT-004. `v0.1.4_create_esg_certificate_line_item_table.yaml` for the child table.
  - **Acceptance criteria:**
    - [ ] Liquibase creates both tables; `esg_certificate` carries the exclusion constraint scoped to `tracked_company_id`
    - [ ] `EsgCertificateRepository.save(certificate, lineItems)` persists both in one transaction
  - **Verification:**
    - [ ] `mvn -pl reporting-service test` green
    - [ ] A direct-repository IT proves the exclusion constraint fires on a sequential duplicate-period insert (same discipline as Task 44's equivalent)
  - **Dependencies:** Task 42 (needs `TrackedCompany` for the FK-less `tracked_company_id` reference), Task 44 (reuses `btree_gist`, already enabled)
  - **Files likely touched:** `certificate/domain/{EsgCertificate,EsgCertificateLineItem}.java`, `certificate/adapter/out/persistence/*`, `certificate/port/out/EsgCertificateRepository.java`, `v0.1.3_create_esg_certificate_table.yaml`, `v0.1.4_create_esg_certificate_line_item_table.yaml`
  - **Estimated scope:** Large (7-9 files)

- [ ] Task 51: Certificate summary preview
  - **Description:** `CertificateSummary` (plain record, not persisted — per spec's Resolved Decisions) + `PreviewCertificateSummaryUseCase` + `GET /tracked-companies/{companyId}/certificate-summary?from=...&to=...`. Computes live: `TracedCollectionEntryRepository` sum for the company's `associationId` within the period, plus `SigersolSyncRepository.findCovering(...)` for the compliance percentage (nullable in the preview if none exists yet — RPT-005 only blocks actual issuance, not the preview, so an operator can see "what would this look like" before the SIGERSOL data is even entered).
  - **Acceptance criteria:**
    - [ ] Preview returns the correct live sum and compliance % (or `null` compliance if no covering `SigersolSync` record exists) for a tracked company + period
    - [ ] Preview persists nothing
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green
  - **Dependencies:** Task 43, Task 45, Task 49
  - **Files likely touched:** `certificate/CertificateSummary.java`, `certificate/port/in/PreviewCertificateSummaryUseCase.java`, `certificate/service/CertificateService.java`, `certificate/adapter/in/web/CertificateController.java`
  - **Estimated scope:** Medium (4-5 files)

- [ ] Task 52: Certificate issuance
  - **Description:** `IssueCertificateUseCase` / `POST /tracked-companies/{companyId}/certificates`: `RPT-003` (tracked company not found), `RPT-004` (overlapping period — service check first, DB exclusion constraint as the real backstop), `RPT-005` (no covering `SigersolSync` record). On success, freezes company name/RUC/associationId, the computed sum, and the compliance % into `EsgCertificate`, and snapshots the contributing `TracedCollectionEntry` rows into `EsgCertificateLineItem` — all in one `@Transactional` method. `CertificateExceptionHandler` (`getConstraintName()` dispatch) translates the exclusion-constraint violation to `RPT-004`.
  - **Acceptance criteria:**
    - [ ] Issuing succeeds and returns 201 with the frozen certificate
    - [ ] An overlapping period for the same tracked company → 409 `RPT-004`; a non-overlapping adjacent period → 201
    - [ ] No covering `SigersolSync` record → 409 `RPT-005`
    - [ ] `GET .../certificates/{id}` and `GET .../certificates` (paginated) work
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green
  - **Dependencies:** Task 50, Task 51
  - **Files likely touched:** `certificate/port/in/IssueCertificateUseCase.java`, `certificate/service/CertificateService.java`, `certificate/adapter/in/web/{CertificateController,CertificateExceptionHandler}.java`, `ReportingErrors.java`
  - **Estimated scope:** Large (6-8 files)

- [ ] Task 53: Certificate concurrency + immutability tests
  - **Description:** The required real two-thread concurrency test for `RPT-004` (same shape and same negative-verification discipline as Task 46 — must fail without the exclusion constraint, pass with it, documented in `tasks/LEARNINGS.md`). Plus the immutability test: issue a certificate, then insert a new `TracedCollectionEntry` for the same association with a `collectionDate` inside the already-issued period, and assert re-fetching that certificate returns the exact same frozen numbers/line items as before.
  - **Acceptance criteria:**
    - [ ] Two concurrent `issue()` calls for the same tracked company + overlapping period: exactly one succeeds, the other gets `RPT-004`, exactly one `esg_certificate` row exists afterward
    - [ ] Verified to fail without the exclusion constraint (temporarily removed, confirmed both succeed, restored) — documented in `tasks/LEARNINGS.md`
    - [ ] A backdated `TracedCollectionEntry` arriving after issuance does not change an already-issued certificate's frozen `kilosTrazados`/line items
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green
  - **Dependencies:** Task 52
  - **Files likely touched:** `it/certificate/{CertificateConcurrencyIT,CertificateImmutabilityIT}.java` (or combined)
  - **Estimated scope:** Medium (1-2 files, high test complexity)

### Checkpoint 25: Certificate issuance complete
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: preview → issue → 409 on overlap (`RPT-004`) → 409 on missing SIGERSOL data (`RPT-005`) → immutability confirmed against a real backdated event
- [ ] Task 53's negative verification documented in `tasks/LEARNINGS.md`
- [ ] Human review before PDF/CSV export

## Phase 23: PDF/CSV export

- [ ] Task 54: CertificatePdfExporter
  - **Description:** `CertificatePdfExporter` (Apache PDFBox) generating a formatted certificate document (company name/RUC, period, kilos trazados, compliance %, issue date) from an in-memory `EsgCertificate`. Pure function, no Postgres/RabbitMQ dependency.
  - **Acceptance criteria:**
    - [ ] Output is a real, valid PDF
  - **Verification:**
    - [ ] Unit test parses the generated PDF back with PDFBox's own reader (`PDFTextStripper` or equivalent) and asserts the company name, period, kilos, and compliance % are all present — not just "the byte array is non-empty"
  - **Dependencies:** Task 52
  - **Files likely touched:** `certificate/export/CertificatePdfExporter.java`, `pom.xml` (PDFBox dependency)
  - **Estimated scope:** Medium (2 files)

- [ ] Task 55: CertificateCsvExporter
  - **Description:** `CertificateCsvExporter` (Apache Commons CSV) writing the certificate's frozen `EsgCertificateLineItem` rows (one row per contributing collection date/weight) plus a summary header/section.
  - **Acceptance criteria:**
    - [ ] Output is real, valid CSV, correctly escaping any field that could contain a comma/quote
  - **Verification:**
    - [ ] Unit test parses the generated CSV back with Commons CSV's own parser and asserts the line items and summary values round-trip correctly
  - **Dependencies:** Task 52
  - **Files likely touched:** `certificate/export/CertificateCsvExporter.java`, `pom.xml` (Commons CSV dependency)
  - **Estimated scope:** Small (2 files)

- [ ] Task 56: Export HTTP endpoints
  - **Description:** `GET /tracked-companies/{companyId}/certificates/{id}/pdf` and `.../csv`, streaming the exporter output directly in the response (`Content-Disposition: attachment`), no file persisted anywhere.
  - **Acceptance criteria:**
    - [ ] Both endpoints return the correct content type and a downloadable attachment
  - **Verification:**
    - [ ] IT (RestAssured) downloads both, parses the real HTTP response bytes with PDFBox's/Commons CSV's own reader, and asserts correctness — same round-trip standard as Tasks 54/55's unit tests, now proven over real HTTP
  - **Dependencies:** Task 54, Task 55
  - **Files likely touched:** `certificate/adapter/in/web/CertificateController.java`
  - **Estimated scope:** Small (1-2 files)

### Checkpoint 26: Export complete
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: `curl` both `.../pdf` and `.../csv` for a real issued certificate, open/parse both
- [ ] Human review before Polish

## Phase 24: Polish

- [ ] Task 57: springdoc-openapi wiring
  - **Description:** Same wiring as Task 11 (`recycler-service`)/Task 23 (`collection-service`).
  - **Acceptance criteria:**
    - [ ] Swagger UI reachable at `:8083`, lists every endpoint with request/response schemas
  - **Verification:**
    - [ ] `mvn -pl reporting-service verify` green
    - [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service` + `reporting-service`)
  - **Dependencies:** Tasks 43, 45, 51, 52, 56 (needs every controller to exist)
  - **Files likely touched:** `pom.xml` (springdoc dependency), `application.yml`
  - **Estimated scope:** Small (2 files)

### Checkpoint 27: Full reporting path complete
- [ ] `mvn verify` green across the whole reactor
- [ ] Manual check: full flow through real HTTP — register a `TrackedCompany` → register a `SigersolSync` → a real `collection-service` `CollectionRecord` lands as a `traced_collection_entry` here → preview → issue → download PDF and CSV
- [ ] `recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected

### Checkpoint 28: Final — ready for review (M5 module close)
- [ ] `mvn verify` green across the whole reactor, RabbitMQ Testcontainer included
- [ ] All Success Criteria bullets in `SPEC-reporting-service.md` re-verified line by line with evidence, same discipline as `cross-service-events`' Checkpoint 20
- [ ] `shared-kernel`/`recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected — no destructive schema change, no existing endpoint contract changed
- [ ] Human review and approval — this is the last business-logic module before `ci-pipeline`/`deployment` per the capability map's build order
