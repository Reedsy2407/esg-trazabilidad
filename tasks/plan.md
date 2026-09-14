# Implementation Plan: shared-kernel + recycler-service + collection-service (core)

> Source specs: [[SPEC-shared-kernel.md]], [[SPEC-recycler-service.md]], [[SPEC-collection-service.md]]. Module ids per [[CAPABILITY-MAP.md]]: `shared-kernel`, `recycler-service`, `collection-service`.
> `shared-kernel` + `recycler-service` (Tasks 1-12, Checkpoints 1-6) are complete and approved (2026-09-13). `collection-service` (Tasks 13-23, Checkpoints 7-13) is planned below, not yet built — repo is greenfield for that module only.

## Overview

Build the first three modules of the ESG traceability monorepo: `shared-kernel` (error handling, event-strategy enum, ID generation — a dependency-light library), `recycler-service` (the first real Spring Boot service, covering `Association`, `Recycler`, and `Certification` CRUD, Liquibase-managed Postgres schema, and Docker Compose local infra), and `collection-service` (a second Spring Boot service in the same reactor, covering `Neighbor`, `Company`, `CollectionSchedule`, `CollectionRecord` CRUD against the same shared Postgres). No event publishing/consumption in this scope for either service — that's the `cross-service-events` module, specced separately.

## Architecture Decisions

- **Maven multi-module reactor**, root aggregator POM created in Task 1 — every later module (`collection-service`, `reporting-service`) will slot into this same reactor.
- **One root-level `docker-compose.yml`** (confirmed decision in SPEC-recycler-service.md) — Postgres now, RabbitMQ added later when `cross-service-events` needs it.
- **No auth wired into any endpoint in this scope** (confirmed decision) — flagged, not silently stubbed.
- **Association → Recycler → Certification build order** inside `recycler-service`, because `Recycler.associationId` and `Certification.associationId` are required FKs — Association must exist first, both in schema and in code, or the other two have nothing to reference.
- Each entity slice is split into a **persistence task** (schema + domain + JPA adapter + typed errors) and an **API task** (mapper + DTOs + use cases + service + controller + tests) rather than one large task, to keep each task within a single focused session — see Task Sizing note below.
- **`collection-service` depends only on `shared-kernel`, never `recycler-service`** (confirmed in `SPEC-collection-service.md`'s "Association reference" decision) — `CollectionRecord.associationId` is a bare unvalidated `UUID`, not a cross-service FK or synchronous HTTP call. This keeps the capability map's dependency direction intact; real validation is deferred to `cross-service-events`.
- **`Neighbor`/`Company` have no relationship to each other** in `collection-service` — `CollectionSchedule`/`CollectionRecord` reference `Neighbor` only; `Company` is a flat client registry (see spec's "Neighbor/Company relationship model" decision).
- **`CollectionSchedule` gets a third task** (lifecycle: pause/cancel/reactivate) beyond the persistence/API split, because its 3-state machine (`ACTIVE`⇄`PAUSED`, both → `CANCELLED` terminal) is a distinct vertical slice — same reasoning that made status-change endpoints their own Task 12 in `recycler-service` rather than folded into Tasks 6/8/10.

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

## Task Sizing Note

Strict "≤5 files per task" isn't achievable for a full Controller→Mapper→UseCase→Service vertical slice without fragmenting a single cohesive layer. Each entity is instead split into two M/L-sized tasks (persistence, then API) rather than one XL task — each still fits one focused session and has its own acceptance criteria and tests, which is the sizing guide's actual intent.

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
- [ ] Task 22: CollectionRecord API (mapper, DTOs, use cases, `CollectionRecordService`, controller nested under `/neighbors/{neighborId}/collection-records`, unit + IT tests incl. unvalidated-`associationId` proof)

### Checkpoint 11: CollectionRecord complete
- [ ] `mvn -pl collection-service verify` green
- [ ] Manual check: log a record tied to a schedule, log an ad-hoc one (no schedule), confirm `associationId` isn't validated
- [ ] Human review before Polish phase

### Phase 12: Polish

- [ ] Task 23: springdoc-openapi wiring for all `collection-service` controllers (incl. lifecycle endpoints)

### Checkpoint 12: Full CRUD path complete
- [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`)
- [ ] Manual check: Neighbor → CollectionSchedule → CollectionRecord chain end-to-end; Company independently CRUD-able; `recycler-service` unaffected

### Checkpoint 13: Final — ready for review
- [ ] All Success Criteria in `SPEC-collection-service.md` are met
- [ ] Definition of Done satisfied for every task above (Tasks 13-23)
- [ ] Human review and approval before moving to `cross-service-events` or `reporting-service`

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

## Open Questions

None outstanding — all three source specs (`shared-kernel`, `recycler-service`, `collection-service`) are fully resolved. Any new question that surfaces during implementation should be raised before the task it blocks, not worked around silently.
