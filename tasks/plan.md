# Implementation Plan: shared-kernel + recycler-service (core)

> Source specs: [[SPEC-shared-kernel.md]], [[SPEC-recycler-service.md]]. Module ids per [[CAPABILITY-MAP.md]]: `shared-kernel`, `recycler-service`.
> Repo is greenfield — no Maven modules, no source, no docker-compose.yml exist yet.

## Overview

Build the first two modules of the ESG traceability monorepo: `shared-kernel` (error handling, event-strategy enum, ID generation — a dependency-light library) and `recycler-service` (the first real Spring Boot service, covering `Association`, `Recycler`, and `Certification` CRUD, Liquibase-managed Postgres schema, and Docker Compose local infra). No event publishing/consumption in this scope — that's the `cross-service-events` module, specced separately.

## Architecture Decisions

- **Maven multi-module reactor**, root aggregator POM created in Task 1 — every later module (`collection-service`, `reporting-service`) will slot into this same reactor.
- **One root-level `docker-compose.yml`** (confirmed decision in SPEC-recycler-service.md) — Postgres now, RabbitMQ added later when `cross-service-events` needs it.
- **No auth wired into any endpoint in this scope** (confirmed decision) — flagged, not silently stubbed.
- **Association → Recycler → Certification build order** inside `recycler-service`, because `Recycler.associationId` and `Certification.associationId` are required FKs — Association must exist first, both in schema and in code, or the other two have nothing to reference.
- Each entity slice is split into a **persistence task** (schema + domain + JPA adapter + typed errors) and an **API task** (mapper + DTOs + use cases + service + controller + tests) rather than one large task, to keep each task within a single focused session — see Task Sizing note below.

## Dependency Graph

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
- [ ] All Success Criteria in both specs are met
- [ ] Definition of Done (Correctness + Quality sections) satisfied for every task
- [ ] Human review and approval before moving to `collection-service` or `cross-service-events`

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Testcontainers requires Docker running in dev/CI environment | Medium — IT tests fail to start without it | Confirm Docker Desktop (or CI's Docker-in-Docker) is available before Task 6's IT test; document in README if missing |
| No-auth endpoints could be forgotten and shipped as-is | Medium — security gap if `deployment` module starts before auth is added | Capability map already tracks auth as a deferred, explicit module; Checkpoint 6 and any pre-deploy checklist must re-surface it |
| FK validation between Recycler/Certification and Association done ad hoc per slice | Low — inconsistent error handling if not careful | `RecyclerErrors`/`CertificationErrors` both get an explicit `ASSOCIATION_NOT_FOUND`-style case in Tasks 7/9, not just a generic exception |
| Liquibase changelog naming/order drift as more changelogs are added later (`collection-service`, `cross-service-events`) | Low | `db.changelog-master.yaml` already committed to `includeAll` + alphabetical `vX.Y.Z_` naming from Task 4 onward |

## Open Questions

None outstanding — both source specs are fully resolved. Any new question that surfaces during implementation should be raised before the task it blocks, not worked around silently.
