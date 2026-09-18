# Implementation Plan: shared-kernel + recycler-service + collection-service + cross-service-events

> Source specs: [[SPEC-shared-kernel.md]], [[SPEC-recycler-service.md]], [[SPEC-collection-service.md]], [[SPEC-cross-service-events.md]]. Module ids per [[CAPABILITY-MAP.md]]: `shared-kernel`, `recycler-service`, `collection-service`, `cross-service-events`.
> `shared-kernel` + `recycler-service` (Tasks 1-12, Checkpoints 1-6) and `collection-service` (Tasks 13-23, Checkpoints 7-13) are complete and approved. `cross-service-events` (Tasks 24-39, Checkpoints 14-20) is planned below, not yet built — it's cross-cutting infrastructure added to all three existing modules, not a new Maven module.

## Overview

Build the first three modules of the ESG traceability monorepo: `shared-kernel` (error handling, event-strategy enum, ID generation — a dependency-light library), `recycler-service` (the first real Spring Boot service, covering `Association`, `Recycler`, and `Certification` CRUD, Liquibase-managed Postgres schema, and Docker Compose local infra), and `collection-service` (a second Spring Boot service in the same reactor, covering `Neighbor`, `Company`, `CollectionSchedule`, `CollectionRecord` CRUD against the same shared Postgres). Then `cross-service-events`: RabbitMQ-based transactional Outbox (publish) + Inbox/idempotency (consume) infrastructure wiring the two services together — `collection-service` publishes `CollectionRegisteredEvent` (consumed by `recycler-service` to increment `Association.totalKilosCollected`), and `recycler-service` publishes `CertificationExpiredEvent`/`CertificationRenewedEvent` (consumed by `collection-service` to block/unblock new `CollectionRecord` creation for an association with a lapsed certification).

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

## Task Sizing Note

Strict "≤5 files per task" isn't achievable for a full Controller→Mapper→UseCase→Service vertical slice without fragmenting a single cohesive layer. Each entity is instead split into two M/L-sized tasks (persistence, then API) rather than one XL task — each still fits one focused session and has its own acceptance criteria and tests, which is the sizing guide's actual intent.

`cross-service-events` follows the same split, extended one step further: schema/adapter tasks (24, 27, 29, 35) are separated from behavior tasks (listeners, scan job, blocking enforcement) — infra-only tasks are verified by boot + `mvn verify` + a direct-repository IT proving the DB shape, without a contrived RED step for a `CREATE TABLE`, per `[[tdd_scope_for_config_fixes]]`. Business-logic tasks get full RED→GREEN TDD. Direction A and Direction B are each proven end-to-end (Tasks 31, 38) before the other starts, rather than interleaved — mirrors how `CollectionSchedule`'s persistence/API/lifecycle tasks were sequenced rather than parallelized.

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

- [ ] Task 39: Ordering test (publish Expired then Renewed through the outbox in quick succession, assert final state unblocked; documents the residual out-of-order-after-redelivery risk rather than hiding it)

### Checkpoint 20: Final — ready for review
- [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`), RabbitMQ Testcontainer included
- [ ] All 11 Success Criteria bullets in `SPEC-cross-service-events.md` re-verified line by line with evidence
- [ ] `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected
- [ ] Human review and approval before moving to `reporting-service`

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

## Open Questions

None outstanding — all four source specs (`shared-kernel`, `recycler-service`, `collection-service`, `cross-service-events`) are fully resolved. Any new question that surfaces during implementation should be raised before the task it blocks, not worked around silently.
