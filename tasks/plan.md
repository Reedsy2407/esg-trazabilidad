# Implementation Plan: shared-kernel + recycler-service + collection-service + cross-service-events + reporting-service + ci-pipeline

> Source specs: [[SPEC-shared-kernel.md]], [[SPEC-recycler-service.md]], [[SPEC-collection-service.md]], [[SPEC-cross-service-events.md]], [[SPEC-reporting-service.md]], [[SPEC-ci-pipeline.md]]. Module ids per [[CAPABILITY-MAP.md]]: `shared-kernel`, `recycler-service`, `collection-service`, `cross-service-events`, `reporting-service`, `ci-pipeline`.
> `shared-kernel` + `recycler-service` (Tasks 1-12, Checkpoints 1-6), `collection-service` (Tasks 13-23, Checkpoints 7-13), `cross-service-events` (Tasks 24-40, Checkpoints 14-20), and `reporting-service` (Tasks 41-57, Checkpoints 21-28) are complete and approved. `ci-pipeline` (Task 58, Checkpoint 29) is planned below, not yet built — the last module before `deployment` per the capability map's build order.

## Overview

Build the first three modules of the ESG traceability monorepo: `shared-kernel` (error handling, event-strategy enum, ID generation — a dependency-light library), `recycler-service` (the first real Spring Boot service, covering `Association`, `Recycler`, and `Certification` CRUD, Liquibase-managed Postgres schema, and Docker Compose local infra), and `collection-service` (a second Spring Boot service in the same reactor, covering `Neighbor`, `Company`, `CollectionSchedule`, `CollectionRecord` CRUD against the same shared Postgres). Then `cross-service-events`: RabbitMQ-based transactional Outbox (publish) + Inbox/idempotency (consume) infrastructure wiring the two services together — `collection-service` publishes `CollectionRegisteredEvent` (consumed by `recycler-service` to increment `Association.totalKilosCollected`), and `recycler-service` publishes `CertificationExpiredEvent`/`CertificationRenewedEvent` (consumed by `collection-service` to block/unblock new `CollectionRecord` creation for an association with a lapsed certification). Finally `reporting-service`: a third Spring Boot service, pure consumer of the already-existing `CollectionRegisteredEvent`, that turns the platform's own data plus a manually-entered official SIGERSOL/MINAM data point into an immutable, exportable (PDF/CSV) ESG traceability certificate for a B2B client. Then `ci-pipeline`: a single GitHub Actions workflow that runs `mvn -B verify` against the whole reactor on every push to `main` and every pull request, closing the gap where a regression could otherwise reach `main` without anyone running the full test suite by hand first.

## Architecture Decisions

- **Maven multi-module reactor**, root aggregator POM created in Task 1 — every later module (`collection-service`, `reporting-service`) will slot into this same reactor.
- **One root-level `docker-compose.yml`** (confirmed decision in SPEC-recycler-service.md) — Postgres now, RabbitMQ added later when `cross-service-events` needs it.
- **No auth wired into any endpoint in this scope** (confirmed decision) — flagged, not silently stubbed.
- **Association → Recycler → Certification build order** inside `recycler-service`, because `Recycler.associationId` and `Certification.associationId` are required FKs — Association must exist first, both in schema and in code, or the other two have nothing to reference.
- Each entity slice is split into a **persistence task** (schema + domain + JPA adapter + typed errors) and an **API task** (mapper + DTOs + use cases + service + controller + tests) rather than one large task, to keep each task within a single focused session — see Task Sizing note below.
- **`collection-service` depends only on `shared-kernel`, never `recycler-service`** (confirmed in `SPEC-collection-service.md`'s "Association reference" decision) — `CollectionRecord.associationId` is a bare unvalidated `UUID`, not a cross-service FK or synchronous HTTP call. This keeps the capability map's dependency direction intact; real validation is deferred to `cross-service-events`.
- **`Neighbor`/`Company` have no relationship to each other** in `collection-service` — `CollectionSchedule`/`CollectionRecord` reference `Neighbor` only; `Company` is a flat client registry (see spec's "Neighbor/Company relationship model" decision).
- **`CollectionSchedule` gets a third task** (lifecycle: pause/cancel/reactivate) beyond the persistence/API split, because its 3-state machine (`ACTIVE`⇄`PAUSED`, both → `CANCELLED` terminal) is a distinct vertical slice — same reasoning that made status-change endpoints their own Task 12 in `recycler-service` rather than folded into Tasks 6/8/10.
- **`cross-service-events` is cross-cutting infrastructure, not a new Maven module** — additions to `shared-kernel` (generic `OutboxDispatcher`, `DomainEvent`/`OutboxEntry`/`OutboxRepository`, `EventPublishingStrategy` rewritten to `{RABBITMQ, MOCK}`), `recycler-service` (its own outbox + a `collection_registered_ledger` consumer table + the new `CertificationExpiryScanJob`), and `collection-service` (its own outbox + a `certification_status_ledger` consumer table + a new minimal `BlockedAssociation` projection).
- **Corrections made to `SPEC-cross-service-events.md` before/during task-breakdown, all now reflected in the spec itself:** `recycler-service`'s new Liquibase changelogs start at `v0.1.3` (not `v0.1.4` — that number was copied from `collection-service`'s block without adjusting for `recycler-service`'s shorter existing sequence, which only reaches `v0.1.2`); a ShedLock-table changelog is added to both services, even though none was listed anywhere in the spec, because Success Criteria explicitly requires one; `collection-service`'s changelog numbers are ordered to match task-execution order (`v0.1.4`/`v0.1.5` in Task 27, `v0.1.6`/`v0.1.7` in Task 35 — not the spec's original `v0.1.4`-`v0.1.6` grouping, which put a later-task file before an earlier-task one).
- **Each service's ShedLock table is distinctly named — `shedlock_recycler` and `shedlock_collection` — never a single shared `shedlock` table.** User-caught risk: both services share one physical Postgres database with no per-service schema separation, so a literal `shedlock` table created by each service's own Liquibase changeset would collide the moment the second service boots. Three options evaluated in `SPEC-cross-service-events.md`'s Resolved Decisions (one shared table owned by one service and referenced by the other; per-service-named tables; per-service Postgres schemas) — per-service-named tables chosen, since it needs no new dependency (`JdbcTemplateLockProvider.Configuration.withTableName(...)` already exists in `shedlock-provider-jdbc-template`), introduces no deployment-order coupling, and matches the "every table has exactly one owning service" principle already applied everywhere else in this project.
- **Event payload records are independently defined per service, never shared as a Java type** — the producing service's event `record` (implementing `shared-kernel`'s `DomainEvent`) and the consuming service's structurally-matching local record are two separate classes deserialized by JSON field-name match (`Jackson2JsonMessageConverter`), preserving the existing boundary that `collection-service` never depends on `recycler-service` and vice versa.
- **New error code `CollectionErrors.COL-009 ASSOCIATION_BLOCKED`** (409) — not pinned to a number in the spec; assigned as the next free code after `COL-008`.
- **Direction A (kilos) and Direction B (blocking) are independent of each other** and share only the Phase 13 shared-kernel infrastructure — built as two separate vertical slices (Phase 15, Phase 16) rather than interleaved task-by-task, so each direction is fully provable end-to-end before starting the other.
- **`reporting-service` is a new Maven module** (unlike `cross-service-events`, which was cross-cutting infra added to existing modules) — its own `pom.xml`, port 8083, own Liquibase changelog sequence, joining the reactor alongside `recycler-service`/`collection-service`.
- **Entity build order for `reporting-service`: `TrackedCompany` → `SigersolSync` → event consumption (`TracedCollectionEntry`) → `EsgCertificate`/export**, not the guide's own listed order (`ESGCertificate`, `SigersolSync`, `CertificateSummary`). `TrackedCompany` goes first because nothing else in the module can exist meaningfully without the Company↔Association link. `SigersolSync` goes second, ahead of event consumption, specifically to introduce and prove the `EXCLUDE USING gist`/`btree_gist` pattern (Cowork's Checkpoint-20-era finding on the spec) on the simpler of its two uses before reusing it for `EsgCertificate`'s own period-overlap constraint.
- **`SigersolSync` and `EsgCertificate` each get their own dedicated concurrency-test task** (Tasks 46, 53) rather than folding the concurrency proof into their API/issuance tasks — same reasoning `cross-service-events` already applied to keeping its own concurrency/redelivery proofs (Tasks 31, 38, 40) as distinct, individually-reviewable tasks rather than bundled into the business-logic task that introduces the rule.
- **PDF and CSV export are split into three tasks** (pure-function exporters, Tasks 54/55, then the HTTP endpoints wiring them, Task 56) rather than one — the exporters need zero Postgres/RabbitMQ and are fully unit-testable in isolation (round-trip: generate, then parse the output back with the same library's own reader), while the endpoint task is what actually needs Testcontainers Postgres for a real issued certificate to export.
- **No new error-handling infra needed for the generic `DataIntegrityViolationException` fallback** — `shared-kernel`'s `GlobalExceptionHandler` already covers it generically; each new controller-scoped `*ExceptionHandler` (mirrors `ScheduleExceptionHandler`/`CompanyExceptionHandler`) only needs to add the specific `getConstraintName()` branches this module's own constraints introduce.
- **`ci-pipeline` is a single GitHub Actions workflow file, not a new Maven module** — no addition to the root `pom.xml`'s `<modules>` list. It runs `mvn -B verify` at the reactor root on `ubuntu-latest`, needing zero repository secrets because every `@SpringBootTest`/`@Testcontainers` class in the reactor already overrides `spring.datasource.*`/`spring.rabbitmq.*` via `@DynamicPropertySource` at higher precedence than `application.yml`'s `${RABBITMQ_PASSWORD}`-style placeholders (confirmed by reading every such test class before writing `SPEC-ci-pipeline.md`).

## Dependency Graph (shared-kernel + recycler-service)

```
Task 1: Monorepo + shared-kernel scaffolding
    │
    ├── Task 2: ApplicationError / ApplicationException / GlobalExceptionHandler
    └── Task 3: EventPublishingStrategy + IdGenerator (UUID v7)
            │
            ▼
Task 4: recycler-service scaffolding (pom, docker-compose.yml, Liquibase master, PageResponse<T>)
            │
            ▼
Task 5: Association persistence (changelog, domain, JPA adapter, AssociationErrors)
            │
            ▼
Task 6: Association API (mapper, DTOs, use cases, service, controller, tests)
            │
            ▼
Task 7: Recycler persistence (changelog, domain, JPA adapter, RecyclerErrors) ── depends on Task 5/6 (Association must exist to validate FK)
            │
            ▼
Task 8: Recycler API (mapper, DTOs, use cases, service, controller, tests)
            │
            ▼
Task 9: Certification persistence (changelog, domain incl. isExpired(), JPA adapter, CertificationErrors) ── depends on Task 5/6
            │
            ▼
Task 10: Certification API (mapper, DTOs, use cases, service, controller, tests)
            │
            ▼
Task 11: springdoc-openapi wiring (cross-cutting, needs at least one controller to render)
```

## Dependency Graph (collection-service)

```
Task 13: collection-service scaffolding (pom, application.yml port 8082, Liquibase master)
    │
    ├── Task 14: Neighbor persistence (changelog v0.1.0, domain, JPA adapter, errors)
    │       │
    │       ▼
    │   Task 15: Neighbor API (mapper, DTOs, use cases, service, controller, tests)
    │       │
    │       ├──────────────────────────────┐
    │       ▼                              ▼
    │   Task 18: CollectionSchedule    Task 21: CollectionRecord persistence
    │   persistence (v0.1.2, FK to     (v0.1.3, FK to neighbor + nullable FK
    │   neighbor, partial unique       to schedule, associationId bare UUID)
    │   index for COL-002)                 │  ▲
    │       │                              │  │ (needs collection_schedule table
    │       ▼                              │  │  for the nullable scheduleId FK)
    │   Task 19: CollectionSchedule API    │  │
    │   (create/get/list, COL-002)         │  │
    │       │                              │  │
    │       ▼                              │  │
    │   Task 20: CollectionSchedule    ────┘  │
    │   lifecycle (pause/cancel/reactivate,   │
    │   COL-008)                              │
    │                                         ▼
    │                                     Task 22: CollectionRecord API
    │                                     (create/get/list, no update — immutable)
    │
    └── Task 16: Company persistence (v0.1.1, standalone, no FK)
            │
            ▼
        Task 17: Company API (create/get/list)

Task 23: springdoc-openapi wiring — depends on Tasks 15, 17, 19, 20, 22 (needs every controller to exist)
```

Task 21 depends on both Task 14 (neighbor FK) and Task 18 (schedule table must exist for the nullable `schedule_id` FK column) — sequenced after `CollectionSchedule` for that reason, even though `scheduleId` is optional per record.

Task 16 (Company) only needs Task 13's scaffolding — no dependency on Neighbor. Sequenced after Neighbor purely for build-session convenience (same convention the original plan used for Recycler-before-Certification), not a hard requirement.

## Dependency Graph (cross-service-events)

```
Task 24: RabbitMQ + ShedLock infra (docker-compose, application.yml, root pom dependencyManagement)
    │
    ▼
Task 25: shared-kernel — EventPublishingStrategy rewrite, DomainEvent, OutboxEntry, OutboxRepository port
    │
    ▼
Task 26: shared-kernel — OutboxDispatcher (generic, ShedLock-guarded, RabbitTemplate) + shared topic-exchange bean
    │
    ├─────────────────────────────────────────┐
    ▼                                          ▼
Direction A (Phase 15)                    Direction B (Phase 16)
collection→recycler, kilos                recycler→collection, blocking

Task 27: collection-service outbox+       Task 29: recycler-service outbox+ledger+
shedlock tables/adapter                   shedlock tables/adapter + association+
    │                                     certification schema alter (shared
    ▼                                     prerequisite for both A's listener and
Task 28: CollectionRegisteredEvent +      B's scan job/publishers)
publisher, hooked into                        │
CollectionRecordService.create()              ├──────────────┐
    │                                          ▼              ▼
    │                                     Task 30:       Task 32: Certification.
    │                                     CollectionReg-  notifiedExpiredAt +
    │                                     isteredEvent-   renew() reset
    │                                     Listener +           │
    │                                     incrementTotalKilos  ▼
    │                                          │          Task 33: CertificationExpiry
    │                                          │          ScanJob + CertificationExpired
    │                                          │          EventPublisher
    │                                          │               │
    ▼                                          ▼               ▼
Task 31: Direction A end-to-end IT ◄──────────┘          Task 34: CertificationRenewed
(RabbitMQ Testcontainer, redelivery,                      EventPublisher (hook into renew())
concurrent-increment test)                                     │
                                                                ▼
                                                           Task 35: collection-service
                                                           certification_status_ledger +
                                                           blocked_association schema/domain
                                                                │
                                                                ▼
                                                           Task 36: CertificationStatusEvent
                                                           Listener (both event types)
                                                                │
                                                                ▼
                                                           Task 37: enforce block in
                                                           CollectionRecordService.create()
                                                           (COL-009)
                                                                │
                                                                ▼
                                                           Task 38: Direction B end-to-end IT

Task 39: Ordering test (outbox insertion-order, best-effort) — depends on Tasks 31, 38
    │
    ▼
Checkpoint 20: Full reactor verify, all Success Criteria re-checked
```

## Dependency Graph (reporting-service)

```
Task 41: reporting-service scaffolding (pom module, port 8083, Liquibase master, ReportingErrors stub)
    │
    ├── Task 42: TrackedCompany persistence (v0.1.0, unique RUC constraint)
    │       │
    │       ▼
    │   Task 43: TrackedCompany API (RPT-001/RPT-002)
    │       │
    │       ├─────────────────────────────────────────┐
    │       │                                          │
    ├── Task 44: SigersolSync persistence               │
    │   (v0.1.1, btree_gist + EXCLUDE constraint)       │
    │       │                                           │
    │       ▼                                           │
    │   Task 45: SigersolSync API (RPT-006/RPT-007)     │
    │       │                                           │
    │       ▼                                           │
    │   Task 46: SigersolSync overlap concurrency test  │
    │   (proves the exclusion constraint closes the     │
    │   race — required fail-then-pass verification)    │
    │                                                    │
    ├── Task 47: RabbitMQ wiring + TracedCollectionEntry │
    │   schema (v0.1.2)                                  │
    │       │                                            │
    │       ▼                                            │
    │   Task 48: CollectionRegisteredEventListener       │
    │   (reporting-service's own consumer, unit-level)   │
    │       │                                            │
    │       ▼                                            │
    │   Task 49: Event consumption end-to-end IT         │
    │   (RabbitMQ Testcontainer, redelivery/idempotency) │
    │       │                                            │
    │       ▼                                            ▼
    └───────────────────────────────────────────► Task 50: EsgCertificate +
                                                    EsgCertificateLineItem persistence
                                                    (v0.1.3 w/ EXCLUDE constraint, v0.1.4)
                                                        │
                                                        ▼
                                                    Task 51: Certificate summary preview
                                                    (GET .../certificate-summary)
                                                        │
                                                        ▼
                                                    Task 52: Certificate issuance
                                                    (RPT-003/004/005, freezes numbers
                                                    + line items in one transaction)
                                                        │
                                                        ▼
                                                    Task 53: Certificate concurrency +
                                                    immutability tests
                                                        │
                                                        ├──────────────┐
                                                        ▼              ▼
                                                    Task 54:       Task 55:
                                                    PDF exporter   CSV exporter
                                                    (PDFBox)       (Commons CSV)
                                                        │              │
                                                        └──────┬───────┘
                                                               ▼
                                                    Task 56: Export HTTP endpoints
                                                        │
                                                        ▼
                                                    Task 57: springdoc-openapi wiring
                                                        │
                                                        ▼
                                                    Checkpoint 28: Full reactor verify,
                                                    all Success Criteria re-checked
```

Task 50 depends on both Task 42 (`TrackedCompany` must exist for the FK-less `tracked_company_id` reference) and Task 44 (reuses the `btree_gist` extension Task 44 already enables — Liquibase's `CREATE EXTENSION IF NOT EXISTS` makes re-declaring it in Task 50's own changelog harmless, but Task 44 is what proves the pattern works first). Task 49 doesn't block Task 50 structurally, but is sequenced first so the ledger `CertificateService` reads from is already proven correct before certificate issuance is built on top of it.

## Task Sizing Note

Strict "≤5 files per task" isn't achievable for a full Controller→Mapper→UseCase→Service vertical slice without fragmenting a single cohesive layer. Each entity is instead split into two M/L-sized tasks (persistence, then API) rather than one XL task — each still fits one focused session and has its own acceptance criteria and tests, which is the sizing guide's actual intent.

`cross-service-events` follows the same split, extended one step further: schema/adapter tasks (24, 27, 29, 35) are separated from behavior tasks (listeners, scan job, blocking enforcement) — infra-only tasks are verified by boot + `mvn verify` + a direct-repository IT proving the DB shape, without a contrived RED step for a `CREATE TABLE`, per `[[tdd_scope_for_config_fixes]]`. Business-logic tasks get full RED→GREEN TDD. Direction A and Direction B are each proven end-to-end (Tasks 31, 38) before the other starts, rather than interleaved — mirrors how `CollectionSchedule`'s persistence/API/lifecycle tasks were sequenced rather than parallelized.

`reporting-service` follows the same schema/behavior split (Tasks 42/44/47/50 are schema-only, verified by boot + direct-repository IT, no contrived RED step), plus a **third category** neither prior module needed: dedicated concurrency-proof tasks (46, 53) for its two `EXCLUDE USING gist` constraints, each required to empirically fail without the constraint and pass with it — same negative-verification discipline `cross-service-events`' Task 40 established after a Cowork-caught gap, applied here proactively (caught during spec review, before any code exists) rather than reactively.

## Task List

### Phase 1: Foundation (`shared-kernel`)

- [x] Task 1: Monorepo + shared-kernel scaffolding
- [x] Task 2: Error handling (`ApplicationError`, `ApplicationException`, `GlobalExceptionHandler`)
- [x] Task 3: Event strategy + ID generation (`EventPublishingStrategy`, `IdGenerator`)

### Checkpoint 1: shared-kernel complete
- [x] `mvn -pl shared-kernel install` succeeds standalone
- [x] `mvn -pl shared-kernel test` green — all three classes from Tasks 2-3 unit-tested
- [ ] Human review before starting `recycler-service`

### Phase 2: recycler-service infra

- [x] Task 4: recycler-service scaffolding (pom, docker-compose.yml, Liquibase master changelog, `PageResponse<T>`)

### Checkpoint 2: Service boots
- [x] `docker compose up -d` starts Postgres
- [x] `mvn -pl recycler-service spring-boot:run` boots cleanly against it, empty Liquibase changelog applies with no errors
- [ ] Human review before first entity slice

### Phase 3: Association

- [x] Task 5: Association persistence (Liquibase `v0.1.0`, domain, JPA entity/repo, repository port+adapter, `AssociationErrors`)
- [x] Task 6: Association API (mapper, DTOs incl. RUC validation, use cases, `AssociationService`, `AssociationController`, unit + IT tests)

### Checkpoint 3: Association CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green (unit + Testcontainers IT)
- [x] Manual check: create → get → list an association via Swagger/curl against `docker compose up` Postgres
- [ ] Human review before Recycler slice

### Phase 4: Recycler

- [x] Task 7: Recycler persistence (Liquibase `v0.1.1`, domain, JPA entity/repo, repository port+adapter, `RecyclerErrors`)
- [x] Task 8: Recycler API (mapper, DTOs incl. DNI validation, use cases, `RecyclerService`, `RecyclerController` nested under `/associations/{id}/recyclers`, unit + IT tests incl. "association not found" path)

### Checkpoint 4: Recycler CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green
- [x] Manual check: creating a recycler under a nonexistent association returns `REC-*` typed 404, not a raw 500
- [ ] Human review before Certification slice

### Phase 5: Certification

- [x] Task 9: Certification persistence (Liquibase `v0.1.2`, domain incl. `isExpired()`, JPA entity/repo, repository port+adapter, `CertificationErrors`)
- [x] Task 10: Certification API (mapper, DTOs incl. `issuedDate < expirationDate` validation, use cases, `CertificationService`, `CertificationController` nested under `/associations/{id}/certifications`, unit + IT tests incl. `isExpired()` boundary cases)

### Checkpoint 5: Full CRUD path complete
- [x] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service`)
- [x] Manual check: Association → Recycler → Certification chain works end-to-end through real HTTP calls

### Phase 6: Polish

- [ ] Task 11: springdoc-openapi wiring, Swagger UI verified for all three controllers

### Checkpoint 6: Final — ready for review
- [x] All Success Criteria in both specs are met
- [x] Definition of Done (Correctness + Quality sections) satisfied for every task
- [x] Human review and approval before moving to `collection-service` or `cross-service-events` — approved 2026-09-13

### Phase 7: collection-service infra

- [x] Task 13: collection-service scaffolding (pom, root reactor module entry, `application.yml` port 8082, Liquibase master, reuse shared `docker-compose.yml`)

### Checkpoint 7: Service boots
- [x] `mvn -pl collection-service spring-boot:run` boots cleanly against the shared Postgres, empty changelog applies
- [x] Human review before first entity slice — approved 2026-09-13

### Phase 8: Neighbor

- [x] Task 14: Neighbor persistence (Liquibase `v0.1.0`, domain, JPA entity/repo, repository port+adapter, errors)
- [x] Task 15: Neighbor API (mapper, DTOs, use cases, `NeighborService`, `NeighborController`, unit + IT tests)

### Checkpoint 8: Neighbor CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a neighbor via curl
- [x] Human review before Company slice — implicit approval

### Phase 9: Company

- [x] Task 16: Company persistence (Liquibase `v0.1.1`, domain incl. RUC validation, JPA entity/repo, repository port+adapter, errors)
- [x] Task 17: Company API (mapper, DTOs, use cases, `CompanyService`, `CompanyController`, unit + IT tests)

### Checkpoint 9: Company CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a company, duplicate-RUC returns 409
- [x] Human review before CollectionSchedule slice — implicit approval

### Phase 10: CollectionSchedule

- [x] Task 18: CollectionSchedule persistence (Liquibase `v0.1.2` incl. partial unique index, domain with `pause()`/`cancel()`/`reactivate()` guards, JPA entity/repo incl. `existing()`/`update()` from the start, errors)
- [x] Task 19: CollectionSchedule API — CRUD (mapper, DTOs, use cases, `CollectionScheduleService` incl. shared conflict-check method, controller nested under `/neighbors/{neighborId}/schedules`, unit + IT tests)
- [x] Task 20: CollectionSchedule lifecycle (`pause`/`cancel`/`reactivate` endpoints, `COL-008` invalid-transition handling, reactivate re-runs the conflict check, unit + IT tests)

### Checkpoint 10: CollectionSchedule complete (CRUD + full lifecycle)
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: two schedules same neighbor different days (both succeed), same-day duplicate (409 COL-002), pause → reactivate, cancel → confirm terminal (409 COL-008 on further pause/reactivate)
- [x] Human review before CollectionRecord slice — implicit approval

### Phase 11: CollectionRecord

- [x] Task 21: CollectionRecord persistence (Liquibase `v0.1.3`, domain, JPA entity/repo incl. nullable `scheduleId` FK and bare unvalidated `associationId`, errors)
- [x] Task 22: CollectionRecord API (mapper, DTOs, use cases, `CollectionRecordService`, controller nested under `/neighbors/{neighborId}/collection-records`, unit + IT tests incl. unvalidated-`associationId` proof)

### Checkpoint 11: CollectionRecord complete
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: log a record tied to a schedule, log an ad-hoc one (no schedule), confirm `associationId` isn't validated
- [ ] Human review before Polish phase

### Phase 12: Polish

- [x] Task 23: springdoc-openapi wiring for all `collection-service` controllers (incl. lifecycle endpoints)

### Checkpoint 12: Full CRUD path complete
- [x] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`)
- [x] Manual check: Neighbor → CollectionSchedule → CollectionRecord chain end-to-end; Company independently CRUD-able; `recycler-service` unaffected

### Checkpoint 13: Final — ready for review
- [x] All Success Criteria in `SPEC-collection-service.md` are met — re-verified 10/10, closed one real test-coverage gap along the way (see tasks/todo.md)
- [x] Definition of Done satisfied for every task above (Tasks 13-23)
- [x] Human review and approval before moving to `cross-service-events` or `reporting-service` — approved 2026-09-14. `collection-service` is complete.

### Phase 13: Shared event infrastructure

- [x] Task 24: RabbitMQ + ShedLock infra wiring (docker-compose `rabbitmq` service, root pom `dependencyManagement` for ShedLock 7.10.1, `spring.rabbitmq.*` in both `application.yml`s — no RED/GREEN ceremony, infra-only)
- [x] Task 25: shared-kernel event core (`EventPublishingStrategy` rewritten to `{RABBITMQ, MOCK}`, `DomainEvent`, `OutboxEntry`/`OutboxStatus`, `OutboxRepository` port)
- [x] Task 26: shared-kernel `OutboxDispatcher` (generic, `@ConditionalOnBean(OutboxRepository.class)` + `@ConditionalOnProperty`-gated, ShedLock-guarded, `RabbitTemplate`-publishing) + shared topic-exchange bean

### Checkpoint 14: Shared event infra ready
- [x] `mvn -pl shared-kernel test` green; `mvn install` — whole reactor still builds, `recycler-service`/`collection-service` unaffected (verified live via boot on both)
- [x] Human review before wiring either direction's business logic — approved 2026-09-16; content-type gap on published outbox messages caught during review, fixed immediately (see tasks/todo.md)

### Phase 15: Direction A (collection-service → recycler-service, kilos total)

- [x] Task 27: collection-service outbox + shedlock schema (Liquibase `v0.1.4` outbox, `v0.1.5` `shedlock_collection`; `OutboxEventEntity`/adapter implementing shared-kernel's `OutboxRepository`; `LockProvider` bean configured with `withTableName("shedlock_collection")`; `SchedulingConfig` activates `@EnableScheduling`/`@EnableSchedulerLock`, verified live against the real table; user-caught follow-up: `findPending`'s `ORDER BY` needed an `id` tiebreaker for Task 39's ordering guarantee, which also surfaced a stale-first-level-cache bug fixed with `@Modifying(clearAutomatically = true)` — both mirrored in Task 29; **retroactive correction from Task 29**: the outbox table itself is actually named `outbox_event_collection`, not the originally-shipped `outbox_event` — see Task 29's own note)
- [x] Task 28: `CollectionRegisteredEvent` + publisher, hooked into `CollectionRecordService.create()`, now `@Transactional` so the outbox write shares the record's own save transaction (unit + IT)
- [x] Task 29: recycler-service event-infrastructure schema (Liquibase `v0.1.3` outbox, `v0.1.4` ledger, `v0.1.5` alter association+certification, `v0.1.6` `shedlock_recycler`; outbox adapter; ledger entity/repo; `AssociationJpaRepository.incrementTotalKilos` atomic `UPDATE`; `LockProvider` bean configured with `withTableName("shedlock_recycler")`; **real collision found live, not hypothetical**: both services' outbox tables were both literally named `outbox_event` — `recycler-service` failed to boot against the shared Postgres with `relation "outbox_event" already exists` the first time both services' schemas were verified together (the exact scenario Task 27 explicitly deferred this check to). Fixed the same way as `shedlock`: renamed to `outbox_event_recycler`/`outbox_event_collection`, including their per-table indexes (Postgres index names share the table namespace too). `collection-service`'s already-shipped `v0.1.4` changelog rewritten in place under the same zero-real-data exception used for `v0.1.0_create_neighbor_table.yaml`. Verified live: both services boot together against the shared Postgres, `\dt` shows both distinct tables, both independently acquire their own `outboxDispatcher` lock)
- [x] Task 30: `CollectionRegisteredEventListener` (recycler-service) — idempotent, atomic increment; redesigned from the plan's literal shape after `Propagation.NESTED` proved unsupported by `JpaTransactionManager`/Hibernate (caught live via IT test): increment-first, ledger-insert-last in one flat transaction, no savepoints needed. Also found and fixed: every IT test now boots a real listener container that tried to reach the local broker — disabled via `auto-startup=false` for the test JVM, same fix needed again in Task 36

### Checkpoint 15: Direction A wired (unit-level)
- [x] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [x] Human review before the end-to-end IT proves it over real RabbitMQ — approved 2026-09-17

- [x] Task 31: Direction A end-to-end IT (RabbitMQ Testcontainer in both services; full-flow, redelivery/idempotency, and concurrent-increment tests, split one test per service since `collection-service` never depends on `recycler-service` even in tests). Found and fixed: no typed `@RabbitListener` payload could ever have worked without a `Jackson2JsonMessageConverter` (shared-kernel's new `RabbitListenerConfig`, kept off `RabbitTemplate`'s default to avoid double-encoding the outbox's already-serialized JSON); a live listener/dispatcher left running past its own test class pollutes the rest of the suite (`@DirtiesContext`); a 10s timeout that passed in isolation failed under the full suite's contention (bumped to 30s) — all three flagged for Task 36 to apply from the start

### Checkpoint 16: Direction A complete and proven end-to-end
- [x] All of Direction A's Success Criteria bullets verified with real evidence
- [ ] Human review before starting Direction B

### Phase 16: Direction B (recycler-service → collection-service, blocking)

- [x] Task 32: `Certification.notifiedExpiredAt` field + `renew()` resets it to `null` (unit tests + a real-Postgres IT test proving the round trip at microsecond precision). Mutator is `public` (`markNotifiedExpired`), not literally package-private as first worded — `CertificationExpiryScanJob` lives in a sibling package, so Java package-private access can't apply; matches the established `Association.suspend()`/`activate()` convention instead
- [x] Task 33: `CertificationExpiryScanJob` (ShedLock-guarded `@Scheduled`, lock name `certificationExpiryScanJob`) + `CertificationExpiredEventPublisher` + `CertificationExpiryProcessor` (separate `@Transactional` bean, same self-invocation reasoning as `CollectionRegisteredEventProcessor` from Task 30) + `CertificationRepository.findExpiredAndNotYetNotified()` (proven against real Postgres, not just Mockito, since it's the query's `WHERE` clause that matters)
- [x] Task 34: `CertificationRenewedEventPublisher`, hooked into `CertificationService.renew()`'s existing transaction (`@Transactional` added to `renew()`, mirrors `CollectionRecordService.create()`'s Task 28 pattern)

### Checkpoint 17: recycler-service publishing side complete
- [x] `mvn -pl recycler-service verify` green
- [ ] Human review before wiring collection-service's consumption side

- [x] Task 35: collection-service `certification_status_ledger` + `blocked_association` schema/domain (Liquibase `v0.1.6`, `v0.1.7`; `BlockedAssociation` minimal projection domain + adapter). Real gap caught: reading a Postgres `timestamp` column back via `JdbcTemplate` with `java.sql.Timestamp` as the requiredType lets the driver reinterpret UTC bits using the JVM's default zone (America/Lima, UTC-5) -- fixed by reading with an explicit UTC `Calendar`
- [x] Task 36: `CertificationStatusEventListener` — one listener, both event types, idempotent ledger insert then block/unblock; applied Task 30's block-first/ledger-insert-last pattern and `auto-startup=false` test fix (confirmed zero connection-refused lines). Task 31's `jsonRabbitListenerContainerFactory` pattern doesn't apply as literally planned — one queue with two payload shapes has no single fixed type for Jackson2JsonMessageConverter to infer, so the listener takes the raw AMQP `Message` and dispatches on the routing key header instead, using the default container factory. User-flagged concurrency finding resolved with no code change: the listener's own `try/catch DataIntegrityViolationException` (needed anyway for redelivery) already absorbs the `BlockedAssociationRepositoryAdapter.block()` race, since `CertificationStatusEventProcessor.processExpired()` runs in one flat transaction per Task 30's pattern — confirmed with a real concurrent-threads IT test that reproduces the actual constraint violation
- [x] Task 37: Enforce the block in `CollectionRecordService.create()` — new `CollectionErrors.COL-009 ASSOCIATION_BLOCKED` (409) (unit + IT, real Postgres seeding a `blocked_association` row directly). No new exception-handler wiring needed — shared-kernel's `GlobalExceptionHandler` already maps any `ApplicationException` generically

### Checkpoint 18: Direction B wired (unit-level)
- [x] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [ ] Human review before the end-to-end IT

- [ ] Task 38: Direction B end-to-end IT (full expire→block→renew→unblock flow, redelivery/idempotency, and the full expire→renew→re-expire→re-notify cycle test)

### Checkpoint 19: Direction B complete and proven end-to-end
- [x] All of Direction B's Success Criteria bullets verified with real evidence (Task 38's two IT classes)
- [ ] Human review before the final ordering test + reactor-wide checkpoint

### Phase 17: Ordering test + final verification

- [x] Task 39: Ordering test (publish Expired then Renewed through the outbox in quick succession, assert final state unblocked; documents the residual out-of-order-after-redelivery risk rather than hiding it)

### Checkpoint 20: Final — ready for review
- [x] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`), RabbitMQ Testcontainer included
- [x] All 11 Success Criteria bullets in `SPEC-cross-service-events.md` re-verified line by line with evidence
- [x] `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected
- [x] Task 40: Fortalecer test de idempotencia real en Direction B — closed a real vacuity gap a Cowork review caught in criterion 8's redelivery coverage (`isBlocked()` alone can't distinguish real dedup from broken dedup); fixed with a direct `blocked_at` read, verified with the required negative check (temporarily broke dedup, confirmed the new assert failed, reverted)
- [x] Human review and approval before moving to `reporting-service` — approved 2026-09-19

### Phase 18: reporting-service infra

- [ ] Task 41: reporting-service scaffolding (new Maven module, pom, `application.yml` port 8083, empty Liquibase master changelog, `ReportingErrors` stub) — no RabbitMQ queue/listener yet

### Checkpoint 21: Service boots
- [ ] `mvn -pl reporting-service spring-boot:run` boots cleanly against the shared Postgres, empty changelog applies
- [ ] `mvn verify` still green across the whole reactor with the new module present
- [ ] Human review before first entity slice

### Phase 19: TrackedCompany

- [ ] Task 42: TrackedCompany persistence (Liquibase `v0.1.0`, domain, JPA adapter, real `UNIQUE` constraint on `ruc`)
- [ ] Task 43: TrackedCompany API (mapper, DTOs, use cases, service, controller, `RPT-001`/`RPT-002`, `TrackedCompanyExceptionHandler`)

### Checkpoint 22: TrackedCompany CRUD works end-to-end
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: register → get → list; duplicate RUC → 409 `RPT-002`
- [ ] Human review before SigersolSync slice

### Phase 20: SigersolSync (introduces the EXCLUDE USING gist pattern)

- [ ] Task 44: SigersolSync persistence (Liquibase `v0.1.1`, `btree_gist` extension + `excl_sigersol_sync_association_period` exclusion constraint, domain, JPA adapter)
- [ ] Task 45: SigersolSync API (mapper, DTOs, use cases, service, controller, `RPT-006`/`RPT-007`, `SigersolSyncExceptionHandler`, `findCovering(...)` query)
- [ ] Task 46: SigersolSync overlap concurrency test (real two-thread `ExecutorService`/`CountDownLatch` IT; required to fail without the exclusion constraint and pass with it, documented in `tasks/LEARNINGS.md`)

### Checkpoint 23: SigersolSync complete, exclusion-constraint pattern proven
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: register → get → list; overlapping period → 409 `RPT-006`; adjacent non-overlapping period → 201
- [ ] Task 46's negative verification documented in `tasks/LEARNINGS.md`
- [ ] Human review before wiring event consumption

### Phase 21: Event consumption (TracedCollectionEntry ledger)

- [ ] Task 47: reporting-service RabbitMQ wiring + `TracedCollectionEntry` schema (Liquibase `v0.1.2` — doubles as Inbox-idempotency marker and queryable fact table, no separate atomic counter, per spec's own deliberate simplification)
- [ ] Task 48: `CollectionRegisteredEventListener` (reporting-service's own local event record, listener, processor, queue config — own queue `collection.registered.reporting-service`, zero change to `collection-service`; unit-level only)
- [ ] Task 49: Event consumption end-to-end IT (RabbitMQ Testcontainer, faithful-stand-in JSON publish, redelivery/idempotency proving the period-scoped `SUM` is unaffected, not just row count)

### Checkpoint 24: Event consumption wired and proven
- [ ] `mvn -pl reporting-service verify` green
- [ ] A `CollectionRecord` created in `collection-service` results in a new `traced_collection_entry` row here, over the real shared exchange, with zero changes to `collection-service`
- [ ] Redelivery proven a no-op on the sum, not just the row count
- [ ] Human review before certificate issuance

### Phase 22: Certificate issuance

- [ ] Task 50: `EsgCertificate` + `EsgCertificateLineItem` persistence (Liquibase `v0.1.3` w/ `excl_esg_certificate_company_period` exclusion constraint scoped to `tracked_company_id`, `v0.1.4` for line items, domain, JPA adapters)
- [ ] Task 51: Certificate summary preview (`CertificateSummary` computed DTO, `GET .../certificate-summary`, live sum + compliance % lookup, persists nothing)
- [ ] Task 52: Certificate issuance (`RPT-003`/`004`/`005`, freezes company/kilos/compliance % + line items in one transaction, `CertificateExceptionHandler`)
- [ ] Task 53: Certificate concurrency + immutability tests (same fail-then-pass discipline as Task 46 for `RPT-004`; a backdated `TracedCollectionEntry` after issuance proven not to change an already-issued certificate)

### Checkpoint 25: Certificate issuance complete
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: preview → issue → 409 on overlap (`RPT-004`) → 409 on missing SIGERSOL data (`RPT-005`) → immutability confirmed against a real backdated event
- [ ] Task 53's negative verification documented in `tasks/LEARNINGS.md`
- [ ] Human review before PDF/CSV export

### Phase 23: PDF/CSV export

- [ ] Task 54: `CertificatePdfExporter` (Apache PDFBox, pure function, unit round-trip test parsing its own output back)
- [ ] Task 55: `CertificateCsvExporter` (Apache Commons CSV, pure function, unit round-trip test)
- [ ] Task 56: Export HTTP endpoints (`GET .../pdf`, `.../csv`, streamed response, no file persisted; IT round-trips the real HTTP response bytes)

### Checkpoint 26: Export complete
- [ ] `mvn -pl reporting-service verify` green
- [ ] Manual check: `curl` both `.../pdf` and `.../csv` for a real issued certificate, open/parse both
- [ ] Human review before Polish

### Phase 24: Polish

- [ ] Task 57: springdoc-openapi wiring for all `reporting-service` controllers

### Checkpoint 27: Full reporting path complete
- [ ] `mvn verify` green across the whole reactor
- [ ] Manual check: full flow through real HTTP — register `TrackedCompany` → register `SigersolSync` → a real `collection-service` `CollectionRecord` lands as a `traced_collection_entry` here → preview → issue → download PDF and CSV
- [ ] `recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected

### Checkpoint 28: Final — ready for review (M5 module close)
- [x] `mvn verify` green across the whole reactor, RabbitMQ Testcontainer included
- [x] All Success Criteria bullets in `SPEC-reporting-service.md` re-verified line by line with evidence, same discipline as `cross-service-events`' Checkpoint 20
- [x] `shared-kernel`/`recycler-service`/`collection-service`/`cross-service-events`'s pre-existing test suites and manual-check behavior unaffected
- [x] Human review and approval — last business-logic module before `ci-pipeline`/`deployment` (closed 2026-09-19, see `tasks/LEARNINGS.md`'s Checkpoint 28 entry)

## Dependency Graph (ci-pipeline)

```
Task 58: .github/workflows/ci.yml
(build+test on push/PR, no code dependency — gated only on the
reactor existing, i.e. every module built so far)
```

### Phase 25: ci-pipeline workflow

- [ ] Task 58: Add `.github/workflows/ci.yml` — triggers on `push` to `main` and `pull_request` targeting `main`; `ubuntu-latest`; `actions/checkout@v4`; `actions/setup-java@v4` (temurin, java-version 21, `cache: maven`); one step running `mvn -B verify`; `concurrency` block (`group: ci-${{ github.ref }}`, `cancel-in-progress: true`) — content per `SPEC-ci-pipeline.md`'s Code Style section verbatim. No `pom.xml` change.

### Checkpoint 29: CI proven live (M6 module close)
- [ ] Push to `main` triggers a real Actions run; `mvn -B verify` passes reactor-wide (`shared-kernel` + `recycler-service` + `collection-service` + `reporting-service`), including at least one Postgres IT and one RabbitMQ IT, zero repository secrets configured
- [ ] Negative check: a deliberately broken test turns the run red with that test named in the log; reverted; green again
- [ ] A real PR (throwaway branch) shows the same workflow as a status check on the PR itself, not only on direct pushes to `main`
- [ ] Two rapid pushes to the same branch show the earlier run cancelled (`concurrency` block proven live, not just present in the YAML)
- [ ] `mvn verify` still green locally across the whole reactor — zero regression from adding the workflow file
- [ ] Human review and approval — last module before `deployment` per the capability map's build order

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Testcontainers requires Docker running in dev/CI environment | Medium — IT tests fail to start without it | Confirm Docker Desktop (or CI's Docker-in-Docker) is available before Task 6's IT test; document in README if missing |
| No-auth endpoints could be forgotten and shipped as-is | Medium — security gap if `deployment` module starts before auth is added | Capability map already tracks auth as a deferred, explicit module; Checkpoint 6 and any pre-deploy checklist must re-surface it |
| FK validation between Recycler/Certification and Association done ad hoc per slice | Low — inconsistent error handling if not careful | `RecyclerErrors`/`CertificationErrors` both get an explicit `ASSOCIATION_NOT_FOUND`-style case in Tasks 7/9, not just a generic exception |
| Liquibase changelog naming/order drift as more changelogs are added later (`collection-service`, `cross-service-events`) | Low | `db.changelog-master.yaml` already committed to `includeAll` + alphabetical `vX.Y.Z_` naming from Task 4 onward |
| Partial unique index (`WHERE status = 'ACTIVE'`) for `COL-002` isn't expressible via Liquibase's `<createIndex>` | Medium — could silently degrade to app-only checking (TOCTOU race, same class of bug already fixed once for RUC) | Use a raw `<sql>` changeset for this one index; an IT test proves the DB-level constraint fires even when the service-level check is bypassed (mirrors `AssociationRepositoryAdapterIT`'s direct-repository race test) |
| `reactivate()`'s conflict re-check (Task 20) and `create()`'s conflict check (Task 19) drift apart if implemented as separate logic | Low-medium — a schedule could reactivate into a silent duplicate | `CollectionScheduleService` exposes one shared conflict-check method used by both call sites, called out explicitly in both tasks |
| `Persistable<UUID>` + `existing()`/`update()` factory forgotten again for `CollectionSchedule` (the exact bug Task 12 found and fixed after the fact for the other three entities) | Medium if missed — silent `persist()` instead of `merge()` on pause/cancel/reactivate | Task 18 explicitly builds the `existing()`/`update()` path from the start — see `persistable_update_path` memory |
| `CollectionRecord`'s unvalidated `associationId` looks like a bug to a future reviewer who didn't see this session's reasoning | Low | `SPEC-collection-service.md`'s "Resolved Decisions" documents the trade-off explicitly; Task 22 requires a test that *proves* an unvalidated UUID succeeds |
| Running `recycler-service` (port 8081) and `collection-service` (port 8082) against the same shared Postgres simultaneously during manual checks | Low | Distinct ports, disjoint table sets (separate Liquibase changelog masters) — verify once in Checkpoint 7 |
| RabbitMQ Testcontainer adds real startup latency to every IT run in both services | Low-medium — slower feedback loop | Accepted cost, same as Postgres Testcontainers already; no mitigation needed beyond what's already standard in this codebase |
| `OutboxDispatcher` being generic (shared-kernel) but each service having exactly one `OutboxRepository` bean could break if a service ever needs two outboxes | Low | Not a real risk at this scope — each service publishes events for exactly one aggregate direction; documented as a known single-outbox-per-service assumption if it ever needs revisiting |
| Consumer and producer event records drifting out of structural sync (field renamed on one side, not the other) since they're independently-defined per service | Medium — a silent deserialization failure or null field, not caught by either service's own unit tests | Task 31/38's end-to-end IT tests are the actual contract test — they exercise real JSON over a real queue, not mocks, so a drift fails loudly there |
| `CertificationExpiryScanJob` and the `OutboxDispatcher` running on the same `@Scheduled` cadence in `recycler-service` could contend for the same ShedLock table without issue (different lock names) but should be verified | Low | Each `@SchedulerLock` uses a distinct `name` parameter, now only needing to be unique within `recycler-service`'s own `shedlock_recycler` table; confirmed as an explicit acceptance-criteria check in Task 33 |
| Off-by-one changelog numbering (see Architecture Decisions) if not caught before `/build` | Medium — would collide with `collection-service`'s already-used `v0.1.4` naming or just be cosmetically wrong | Corrected in this plan and in `SPEC-cross-service-events.md` itself before any task starts; verify against `ls recycler-service/.../changes/` at Task 29 time as a sanity check |
| Both services sharing one physical Postgres database (no per-service schema) means any identically-named table created independently by each service's own Liquibase changeset collides on the second boot | Medium-high — would surface as a hard `relation already exists` failure the first time both services' migrations actually ran against the shared DB, not caught by either service's own isolated test suite | User-caught before `/build`; resolved by giving each service's ShedLock table a distinct name (`shedlock_recycler`, `shedlock_collection`) — see `SPEC-cross-service-events.md`'s Resolved Decisions for the full reasoning and the two rejected alternatives |
| `EXCLUDE USING gist` requires the `btree_gist` extension, which may not be enabled by default on every hosted Postgres free tier (Supabase/Neon) the same way it is on the local Docker image | Medium — a changelog that works locally could fail on first deploy | `CREATE EXTENSION IF NOT EXISTS btree_gist` is part of the changelog itself (Task 44), not a manual DBA step assumed to have already happened; both Supabase and Neon are known to allow `btree_gist` for a non-superuser role, but this should be confirmed for real against whichever free tier is actually provisioned before `deployment`, not assumed |
| A naive `assertNoOverlappingCertificate`/`SigersolSync`-equivalent service check could look sufficient in every sequential test and only fail under real concurrency — exactly the class of bug this project has already been burned by once (Cowork's Checkpoint-20 finding on `cross-service-events`) | Medium if skipped | Tasks 46 and 53 are dedicated, required tasks (not folded into the API/issuance tasks) specifically so the concurrency proof can't be quietly deprioritized or forgotten; both require the documented fail-without-constraint/pass-with-constraint negative verification before being considered done |
| `reporting-service` consuming `CollectionRegisteredEvent` independently from `recycler-service`'s own Direction A consumer means the event's JSON shape now has two independent consumers that must both stay in sync with `collection-service`'s publisher | Medium — a future field rename in `collection-service`'s event could silently break one consumer's deserialization without the other's tests catching it | Same mitigation already accepted for the existing two-consumer risk on `CertificationExpiredEvent`/`CertificationRenewedEvent`: each consumer's own broker-flow IT (Task 49) is the real contract test, exercising real JSON over a real queue — a drift fails loudly there, per consumer, independently |
| `EsgCertificateLineItem` snapshotting could grow the table indefinitely with no archival/retention policy | Low at pilot scale (2-3 companies, MVP) | Explicitly out of scope per `SPEC-reporting-service.md` — revisit only if real client volume makes it a real storage concern, same YAGNI discipline applied throughout this project |
| Checkpoint 29's live-verification bullets (real push, real PR, real concurrent-push cancellation) can't be proven by any local command — they require pushing to the actual GitHub remote under the user's own identity | Low-medium — a "hard-to-reverse / visible to others" class of action per this session's own operating guidance | `/build` confirms with the user before each push/PR in Checkpoint 29, and prefers a throwaway branch (not `main`) for the deliberately-broken-test negative check specifically, per `SPEC-ci-pipeline.md`'s Testing Strategy |

## Open Questions

None outstanding — all six source specs (`shared-kernel`, `recycler-service`, `collection-service`, `cross-service-events`, `reporting-service`, `ci-pipeline`) are fully resolved. `SPEC-reporting-service.md`'s own three Open Questions (real SIGERSOL integration timing, PDF template/branding, whether `TrackedCompany` should ever track more than one association) are deliberately deferred, not blocking — none of Tasks 41-57 depend on resolving them. `SPEC-ci-pipeline.md`'s own three Open Questions (README CI badge, Dependabot, branch protection) are likewise deliberately deferred, not blocking — none of Task 58/Checkpoint 29 depend on resolving them. Any new question that surfaces during implementation should be raised before the task it blocks, not worked around silently.
