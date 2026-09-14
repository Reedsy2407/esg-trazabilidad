# Task List: shared-kernel + recycler-service + collection-service (core)

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`, `SPEC-collection-service.md`.

## Phase 1: Foundation (`shared-kernel`)

- [x] Task 1: Monorepo + shared-kernel scaffolding
  - **Description:** Create the root Maven aggregator POM and the `shared-kernel` module skeleton (no business logic yet) so later modules have a reactor to join.
  - **Acceptance criteria:**
    - [x] Root `pom.xml` exists with `<packaging>pom</packaging>` and `<modules><module>shared-kernel</module></modules>`
    - [x] `shared-kernel/pom.xml` exists, packaging `jar`, depends only on `spring-boot-starter` + JUnit5/AssertJ (test scope)
    - [x] `mvn install` from repo root builds the empty reactor successfully
  - **Verification:**
    - [x] Build succeeds: `mvn install`
    - [x] Manual check: `mvn -pl shared-kernel install` also succeeds standalone
  - **Dependencies:** None
  - **Files likely touched:** `pom.xml`, `shared-kernel/pom.xml`
  - **Estimated scope:** Small (2 files)

- [x] Task 2: Error handling (`ApplicationError`, `ApplicationException`, `GlobalExceptionHandler`)
  - **Description:** Implement the shared error-handling contract every service will use: a typed error interface, an exception wrapping it, and a `@RestControllerAdvice` translating it to RFC 7807 `ProblemDetail`.
  - **Acceptance criteria:**
    - [x] `ApplicationError` interface with `getCode()`, `getMessage()`, `getStatus()` exists in `pe.esgtrazabilidad.kernel.error`
    - [x] `ApplicationException` wraps an `ApplicationError`, exposes it via a getter
    - [x] `GlobalExceptionHandler` catches `ApplicationException` and returns a `ProblemDetail` with the error's code/message/status
    - [x] Unit test proves the mapping from a sample `ApplicationError` to the resulting `ProblemDetail` fields
  - **Verification:**
    - [x] Tests pass: `mvn -pl shared-kernel test`
    - [x] Manual check: read the test to confirm it asserts status code, error code, and message — not just "no exception thrown"
  - **Dependencies:** Task 1
  - **Files likely touched:** `shared-kernel/src/main/java/.../error/ApplicationError.java`, `.../error/ApplicationException.java`, `.../error/GlobalExceptionHandler.java`, `shared-kernel/src/test/java/.../error/GlobalExceptionHandlerTest.java`
  - **Estimated scope:** Medium (4 files)

- [x] Task 3: Event strategy + ID generation (`EventPublishingStrategy`, `IdGenerator`)
  - **Description:** Add the pluggable event-strategy enum (no publishing logic yet — just the type every service will branch on later) and a UUID v7 generator utility.
  - **Acceptance criteria:**
    - [x] `EventPublishingStrategy` enum with exactly `GCP_PUB_SUB`, `MOCK`, `SPRING_EVENTS`
    - [x] `IdGenerator` produces UUID v7 values (RFC 9562 time-ordered), verified by generating N ids and asserting monotonic time-ordering
    - [x] Unit tests for both
  - **Verification:**
    - [x] Tests pass: `mvn -pl shared-kernel test`
  - **Dependencies:** Task 1
  - **Files likely touched:** `shared-kernel/src/main/java/.../events/EventPublishingStrategy.java`, `.../id/IdGenerator.java`, corresponding test files
  - **Estimated scope:** Small (4 files)

### Checkpoint 1: shared-kernel complete
- [x] `mvn -pl shared-kernel install` succeeds standalone
- [x] `mvn -pl shared-kernel test` green
- [ ] Human review before starting `recycler-service`

## Phase 2: recycler-service infra

- [x] Task 4: recycler-service scaffolding
  - **Description:** Create the `recycler-service` Maven module (depending on `shared-kernel`), the root `docker-compose.yml` with Postgres, an empty Liquibase master changelog, `application.yml`, and the shared `PageResponse<T>` DTO used by every future list endpoint.
  - **Acceptance criteria:**
    - [x] `recycler-service/pom.xml` depends on `shared-kernel`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, Postgres driver, Liquibase core, Testcontainers (test scope)
    - [x] `esg-trazabilidad/docker-compose.yml` at repo root starts a Postgres container with a named volume
    - [x] `db/changelog/db.changelog-master.yaml` exists with `includeAll` on its folder (empty folder is fine for now)
    - [x] `PageResponse<T>` generic wrapper exists in `pe.esgtrazabilidad.recycler` (or a shared location if reused later)
    - [x] App boots with `spring-boot:run` against the Docker Postgres with no schema errors
  - **Verification:**
    - [x] Build succeeds: `mvn -pl recycler-service -am install`
    - [x] Manual check: `docker compose up -d && mvn -pl recycler-service spring-boot:run` boots cleanly, `/actuator/health` (if enabled) or root context responds
  - **Dependencies:** Task 1, Task 2, Task 3 (needs `shared-kernel` installed)
  - **Files likely touched:** `recycler-service/pom.xml`, `docker-compose.yml`, `recycler-service/src/main/resources/application.yml`, `recycler-service/src/main/resources/db/changelog/db.changelog-master.yaml`, `PageResponse.java`
  - **Estimated scope:** Medium (5 files)
  - **Deviations from plan:** (1) `includeAll.filter` in this Liquibase version (4.27.0) expects a Java class name, not an inline expression — changesets live in a `db/changelog/changes/` subfolder instead of alongside the master file, so no filter is needed at all. (2) Docker Postgres is published on host port **5433**, not 5432 — a pre-existing native PostgreSQL 18 Windows service on this machine already owns 5432 and silently shadowed the container for any Windows-native process (incl. `spring-boot:run`), causing password-auth failures that had nothing to do with the app's config.
  - **Verification note:** `POSTGRES_PASSWORD` (docker-compose) and `DB_PASSWORD` (app) must be exported before `docker compose up` / `spring-boot:run` — both fail fast with no default, per CLAUDE.md's no-hardcoded-secret-default rule.

### Checkpoint 2: Service boots
- [x] `docker compose up -d` starts Postgres
- [x] `mvn -pl recycler-service spring-boot:run` boots cleanly, empty changelog applies
- [ ] Human review before first entity slice

## Phase 3: Association

- [x] Task 5: Association persistence
  - **Description:** Schema, domain model, and persistence adapter for `Association` — no HTTP surface yet.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.0_create_association_table.yaml` creates the `association` table: `name`, `ruc` (unique, 11 chars), `registration_number`, `address`, `contact_email`, `contact_phone`, `status`
    - [x] `Association` domain class with a `status` of `ACTIVE`/`SUSPENDED`
    - [x] JPA entity + Spring Data repository + a `AssociationRepository` port + adapter implementing it (domain never leaks the JPA entity)
    - [x] `AssociationErrors` enum implementing `ApplicationError` (`ASO-001` not-found, `ASO-002` duplicate RUC, at minimum)
    - [x] Unit test for any domain-level validation logic on `Association`
  - **Verification:**
    - [x] Tests pass: `mvn -pl recycler-service test`
    - [x] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`docker compose up -d && mvn -pl recycler-service spring-boot:run`, confirm table exists via `psql` or a client)
  - **Dependencies:** Task 4
  - **Files likely touched:** `db/changelog/v0.1.0_create_association_table.yaml`, `association/domain/Association.java`, `association/adapter/out/persistence/{AssociationEntity,AssociationJpaRepository,AssociationRepositoryAdapter}.java`, `association/port/out/AssociationRepository.java`, `association/exception/AssociationErrors.java`
  - **Estimated scope:** Large (6 files) — persistence-only, no controller/service yet
  - **Note:** changelog lives at `db/changelog/changes/v0.1.0_create_association_table.yaml` (Task 4's `changes/` subfolder decision). Domain validation tested: RUC length invariant, and `suspend()`/`activate()` idempotency guards (not spec-required but cheap, real business rules worth protecting).

- [x] Task 6: Association API
  - **Description:** Expose Association CRUD over HTTP: mapper, request/response DTOs with validation, use case interfaces, service, controller.
  - **Acceptance criteria:**
    - [x] `POST /associations` creates an association, validates RUC format (11 digits) via `jakarta.validation`, returns 409 via `AssociationErrors.DUPLICATE_RUC` on conflict
    - [x] `GET /associations/{id}` returns 404 via `AssociationErrors.NOT_FOUND` when missing
    - [x] `GET /associations` returns a paginated `PageResponse<AssociationResponse>`, filterable by `status` via a composed `Specification<Association>` (not a monolithic lambda)
    - [x] Unit test for `AssociationService` (Mockito-mocked repository port)
    - [x] IT test (`AssociationApiIT` or similar, RestAssured + Testcontainers) covering: create → get → list, plus the duplicate-RUC and not-found error paths
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl recycler-service test`
    - [x] Integration tests pass: `mvn -pl recycler-service verify`
    - [x] Manual check: exercise all three endpoints via Swagger UI or curl against `docker compose up` Postgres
  - **Dependencies:** Task 5
  - **Files likely touched:** `association/adapter/in/web/{AssociationController,AssociationMapper}.java`, DTOs (`CreateAssociationRequest`, `AssociationResponse`), `association/port/in/*UseCase.java`, `association/service/AssociationService.java`, `AssociationServiceTest.java`, `it/AssociationApiIT.java`
  - **Estimated scope:** Large (7 files)
  - **Also required (gaps surfaced by this task, not scope creep):** shared-kernel's `GlobalExceptionHandler` now auto-registers into any consuming service via a Spring Boot `AutoConfiguration.imports` file — it existed since Task 2 but nothing wired it into component scanning, so this task's 409/404 paths would have silently 500'd otherwise. Root pom now sets `maven.compiler.parameters=true` — without it, unnamed `@PathVariable`/`@RequestParam` fail at request time with "parameter name information not available". `rest-assured` pinned to 5.5.7 (6.0.1 requires Jackson 3, incompatible with Spring Boot 3.3.5's Jackson 2.x).
  - **Verification gotcha:** a standalone `mvn -pl recycler-service spring-boot:run` (no `-am`) resolves `shared-kernel` from the already-installed `~/.m2` jar, not the reactor's fresh `target/classes` — if shared-kernel changed, run `mvn -pl shared-kernel install` first or the manual check will silently run against a stale jar.

### Checkpoint 3: Association CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green
- [x] Manual check: create → get → list an association via Swagger/curl
- [ ] Human review before Recycler slice

## Phase 4: Recycler

- [x] Task 7: Recycler persistence
  - **Description:** Schema, domain model, and persistence adapter for `Recycler`, with a required FK to `Association`.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.1_create_recycler_table.yaml` creates the `recycler` table: `full_name`, `dni` (unique, 8 chars), `phone`, `association_id` (FK, not null), `status`
    - [x] `Recycler` domain class, `status` of `ACTIVE`/`INACTIVE`
    - [x] JPA entity + repository + port + adapter, same shape as Association
    - [x] `RecyclerEntity` implements `Persistable<UUID>` (transient `isNew` flag, `true` on the creation constructor, cleared via `@PostLoad`) — same pattern as `AssociationEntity`, needed because IDs are app-assigned so Spring Data would otherwise route `save()` through `merge()` and issue a spurious SELECT before every INSERT
    - [x] `RecyclerErrors` enum: `REC-001` not-found, `REC-002` duplicate DNI, `REC-003` association-not-found (used when creating a recycler under a nonexistent association)
    - [x] Unit test covering the FK-must-exist business rule at the service/domain layer (not left to the DB's FK constraint alone, so the error is typed)
  - **Verification:**
    - [x] Tests pass: `mvn -pl recycler-service test`
  - **Dependencies:** Task 5 (Association schema and repository must exist to validate the FK)
  - **Files likely touched:** `db/changelog/v0.1.1_create_recycler_table.yaml`, `recycler/domain/Recycler.java`, `recycler/adapter/out/persistence/*.java`, `recycler/port/out/RecyclerRepository.java`, `recycler/exception/RecyclerErrors.java`
  - **Estimated scope:** Large (6 files)
  - **Note:** the FK-must-exist check landed in `RecyclerRepositoryAdapter.save()` (injecting `AssociationRepository` directly), not a service — Task 7 has no `RecyclerService` yet (that's Task 8), and the adapter is the only place in this task's scope that can reach the association's persistence. Unit-tested with Mockito (`RecyclerRepositoryAdapterTest`), matching the criterion's "not left to the DB's FK constraint alone." Manually verified the `recycler` table + FK apply cleanly against Docker Postgres.

- [x] Task 8: Recycler API
  - **Description:** Expose Recycler CRUD nested under its association.
  - **Acceptance criteria:**
    - [x] `POST /associations/{associationId}/recyclers` validates DNI format (8 digits), returns `REC-003` 404 if the association doesn't exist, `REC-002` 409 on duplicate DNI
    - [x] `GET /associations/{associationId}/recyclers/{id}` returns 404 via `REC-001` when missing
    - [x] `GET /associations/{associationId}/recyclers` returns paginated, filterable `PageResponse<RecyclerResponse>`, with `@PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)` on the `Pageable` param
    - [x] **Architecture correction applied:** `RecyclerService.create()` orchestrates the association-must-exist check (`AssociationRepository.findById()`) and the duplicate-DNI check (`findByDni()`), both before `save()`. `RecyclerRepositoryAdapter` reverted to a pure domain↔entity translator (no `AssociationRepository` dependency); `RecyclerRepositoryAdapterTest` from Task 7 deleted and replaced with a persistence-level `RecyclerRepositoryAdapterIT`.
    - [x] `RecyclerExceptionHandler` (`RecyclerController`-scoped, `@Order(HIGHEST_PRECEDENCE)`) disambiguates the two constraints Recycler can violate by inspecting `exception.getCause() instanceof ConstraintViolationException` → `getConstraintName()` (per user correction — more reliable than message text, which varies by driver/locale) rather than `getMostSpecificCause().getMessage()` as originally sketched. `fk_recycler_association` → REC-003, `recycler_dni_key` → REC-002, anything else → generic `DATA_CONFLICT` fallback.
    - [x] Unit test for `RecyclerService` (+ a fast direct unit test on `RecyclerExceptionHandler` using a hand-constructed `ConstraintViolationException`, no DB needed)
    - [x] IT test covering create → get → list, plus association-not-found and duplicate-DNI paths (`RecyclerApiIT`), plus a repository-level IT (`RecyclerRepositoryAdapterIT`) proving both real DB constraints fire with the expected names
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl recycler-service test`
    - [x] Integration tests pass: `mvn -pl recycler-service verify`
    - [x] Manual check: full create → get → list → 404 → 409 flow against `docker compose up` Postgres
  - **Dependencies:** Task 7
  - **Files likely touched:** `recycler/adapter/in/web/{RecyclerController,RecyclerMapper,RecyclerExceptionHandler}.java`, DTOs, `recycler/port/in/*UseCase.java`, `recycler/service/RecyclerService.java`, `RecyclerServiceTest.java`, `it/RecyclerApiIT.java` — plus edits to `RecyclerRepositoryAdapter.java` (revert) and removal of `RecyclerRepositoryAdapterTest.java`
  - **Estimated scope:** Large (7-8 files)

### Checkpoint 4: Recycler CRUD works end-to-end
- [x] `mvn -pl recycler-service verify` green
- [x] Manual check: creating a recycler under a nonexistent association returns a typed 404 (`REC-003`), not a raw 500
- [ ] Human review before Certification slice

## Phase 5: Certification

- [x] Task 9: Certification persistence
  - **Description:** Schema, domain model with the `isExpired()` business rule, and persistence adapter for `Certification`.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.2_create_certification_table.yaml` creates the `certification` table: `association_id` (FK, not null), `certification_type`, `issued_date`, `expiration_date`
    - [x] `Certification` domain class exposes `isExpired()` computed from `expirationDate` vs. current date — no stored, independently-updatable status column
    - [x] JPA entity + repository + port + adapter
    - [x] `CertificationEntity` implements `Persistable<UUID>` (same transient `isNew` + `@PostLoad` pattern as `AssociationEntity`/`RecyclerEntity`, for the same app-assigned-ID reason)
    - [x] `CertificationErrors` enum: `CER-001` not-found, `CER-002` association-not-found, `CER-003` invalid date range (`issuedDate` not before `expirationDate`)
    - [x] Unit tests for `isExpired()` boundary cases: expires today, already expired (yesterday), far future
  - **Verification:**
    - [x] Tests pass: `mvn -pl recycler-service test`
  - **Dependencies:** Task 5 (Association must exist to validate the FK)
  - **Files likely touched:** `db/changelog/v0.1.2_create_certification_table.yaml`, `certification/domain/Certification.java`, `certification/adapter/out/persistence/*.java`, `certification/port/out/CertificationRepository.java`, `certification/exception/CertificationErrors.java`
  - **Estimated scope:** Large (6 files)
  - **Note:** unlike Recycler (Task 7), no association-must-exist check went into the adapter this time — learned from the Task 7/8 correction. `CertificationRepositoryAdapter` is a pure translator from the start (save/findById only, no `AssociationRepository` dependency); `CER-002` isn't wired to any logic yet, that's Task 10's `CertificationService`. Domain-level `issuedDate < expirationDate` invariant also enforced in `Certification.create()` (defense in depth alongside Task 10's `CER-003` DTO validation), same pattern as Association's RUC regex and Recycler's DNI regex. `isExpired()` uses plain `LocalDate.now()` comparison (no `Clock` injection) — kept simple since boundary tests compute their own expected dates relative to `now()` at test time, no flakiness. Manually verified the `certification` table + FK apply cleanly via Liquibase against Docker Postgres.

- [x] Task 10: Certification API
  - **Description:** Expose Certification CRUD nested under its association.
  - **Acceptance criteria:**
    - [x] `POST /associations/{associationId}/certifications` validates `issuedDate < expirationDate`, returns `CER-002` 404 if the association doesn't exist, `CER-003` on invalid date range
    - [x] `GET /associations/{associationId}/certifications/{id}` returns 404 via `CER-001` when missing, response includes computed `expired: boolean`. Got the associationId path-scoping right from the start this time: `GetCertificationUseCase.getById(associationId, id)` checks `certification.getAssociationId()` matches, throwing `CER-001` on mismatch — covered by an IT test (two associations, cross-path lookup → 404).
    - [x] `GET /associations/{associationId}/certifications` returns paginated `PageResponse<CertificationResponse>`, with `@PageableDefault(size = 20, sort = "expirationDate", direction = Sort.Direction.ASC)` on the `Pageable` param
    - [x] Unit test for `CertificationService` (association-exists check, date-range check, both order and precedence over each other; get scoped to association; list delegates)
    - [x] IT test covering create → get → list, plus association-not-found (404/CER-002) and invalid-date-range (400/CER-003) paths, and one case each for expired/not-expired in the response
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl recycler-service test`
    - [x] Integration tests pass: `mvn -pl recycler-service verify`
  - **Dependencies:** Task 9
  - **Files likely touched:** `certification/adapter/in/web/{CertificationController,CertificationMapper}.java`, DTOs, `certification/port/in/*UseCase.java`, `certification/service/CertificationService.java`, `CertificationServiceTest.java`, `it/CertificationApiIT.java`
  - **Estimated scope:** Large (7 files)
  - **Note:** also added `CertificationExceptionHandler` (same TOCTOU-race fallback pattern as `RecyclerExceptionHandler`, using `ConstraintViolationException.getConstraintName()` for `fk_certification_association` → CER-002), with both a direct unit test and a repository-level IT confirming the real constraint name. `issuedDate < expirationDate` is also enforced in `Certification.create()` at the domain layer (defense in depth), consistent with Association's RUC and Recycler's DNI pattern.

### Checkpoint 5: Full CRUD path complete
- [x] `mvn verify` green across the whole reactor
- [x] Manual check: Association → Recycler → Certification chain works end-to-end through real HTTP calls

## Phase 6: Polish

- [x] Task 11: springdoc-openapi wiring
  - **Description:** Confirm Swagger UI renders all three controllers with accurate request/response schemas, including validation constraints.
  - **Acceptance criteria:**
    - [x] Swagger UI reachable locally (default `/swagger-ui.html`) and lists `Association`, `Recycler`, `Certification` endpoints
    - [x] Request/response schemas reflect validation annotations (e.g. RUC/DNI patterns show in the schema)
  - **Verification:**
    - [x] Manual check: open Swagger UI in a browser against `docker compose up` + running app, exercise one endpoint from the UI
  - **Dependencies:** Task 6, Task 8, Task 10
  - **Files likely touched:** `application.yml` (springdoc config if any customization needed), possibly `@Tag`/`@Schema` annotations on existing controllers/DTOs
  - **Estimated scope:** Small (1-2 files)
  - **Also fixed (real doc-accuracy gap, in scope for this task):** every `POST` create endpoint documented `200` instead of the actual runtime `201` — springdoc can't statically infer a programmatic `ResponseEntity.status(...)` call. Added `@ResponseStatus(HttpStatus.CREATED)` to all three controllers' `create()` methods (purely a documentation hint; Spring ignores `@ResponseStatus` when the method returns `ResponseEntity`, so no runtime behavior changed — verified via the full test suite plus a manual curl still returning 201).
  - **Gap surfaced, not fixed here (see Checkpoint 6 note):** `SPEC-recycler-service.md`'s Success Criteria calls for "update where the domain calls for it, e.g. renewing a certification" — no task in this plan ever scoped an update/PUT endpoint for anything (Association `suspend()`/`activate()`, Recycler `activate()`/`deactivate()`, or a Certification renewal), even though those domain methods exist. This is a planning-phase gap, not something skipped during `/build`.

- [x] Task 12: Status-change and renewal endpoints
  - **Description:** Close the "update where the domain calls for it" gap in `SPEC-recycler-service.md`: expose the existing domain lifecycle methods over HTTP, plus a new Certification renewal capability.
  - **Acceptance criteria:**
    - [x] `PATCH /associations/{id}/suspend` and `PATCH /associations/{id}/activate` call `Association.suspend()`/`activate()`; already-in-that-state returns 409 via a new `AssociationErrors.INVALID_STATUS_TRANSITION` (`ASO-003`), not a raw `IllegalStateException`
    - [x] `PATCH /associations/{associationId}/recyclers/{id}/activate` and `.../deactivate` call `Recycler.activate()`/`deactivate()`; same 409 pattern via a new `RecyclerErrors.INVALID_STATUS_TRANSITION` (`REC-004`) — also scoped to `associationId` from the start (mismatch → `REC-001`), same as `getById()`
    - [x] `PATCH /associations/{associationId}/certifications/{id}/renew` (body: `newExpirationDate`) extends a certification's expiration — new `Certification.renew(LocalDate)` domain method (mutates `expirationDate`, reuses the `issuedDate < expirationDate` invariant, throwing `IllegalArgumentException` → service maps to the existing `CertificationErrors.INVALID_DATE_RANGE`, no new error code needed)
    - [x] **Prerequisite correctness fix:** every `*RepositoryAdapter.save()` always constructed its JPA entity via the "new" constructor (`isNew=true`), correct for `create()` but would have silently attempted an INSERT for these updates. Added `update(...)` to all three repository ports, with an adapter-level `existing(...)` factory (`isNew=false`) so Spring Data routes through `merge()`. Verified with a repository-level IT per entity that the change is actually persisted (fetch after update, assert it stuck).
    - [x] Unit tests for the three services' new methods (happy path + the "already in that state" / "invalid renewal date" / "wrong association" conflict paths)
    - [x] IT coverage for all three endpoints' happy path and conflict paths
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl recycler-service test` (59 total)
    - [x] Integration tests pass: `mvn -pl recycler-service verify` (32 total)
    - [x] Manual check: suspended/reactivated an association, deactivated/reactivated a recycler, renewed an expired certification (confirmed `expired` flips `true`→`false`), all via curl against `docker compose up` Postgres — plus confirmed all 5 new `PATCH` endpoints appear in `/v3/api-docs`
  - **Dependencies:** Task 6, Task 8, Task 10
  - **Files likely touched:** `certification/domain/Certification.java` (new `renew()`, `expirationDate` no longer `final`), all three `*Repository`/`*RepositoryAdapter`/`*Entity` files (new `update()` path), all three `port/in`, `*Service`, `*Controller` files, corresponding tests
  - **Estimated scope:** Large (touches all three entities)

### Checkpoint 6: Final — ready for review
- [x] All Success Criteria in `SPEC-shared-kernel.md` and `SPEC-recycler-service.md` are met — re-verified line by line: shared-kernel 5/5, recycler-service 7/7 (the "update where the domain calls for it" line, previously the one gap, is now closed by Task 12).
- [x] Definition of Done (Correctness + Quality sections) satisfied for every task above — no separate DoD document exists in this repo; applying the de facto standard held throughout every task: passing unit + integration tests, a clean `mvn verify`/`mvn install` on the whole reactor, a manual end-to-end check against real Docker Postgres, and any deviation from the original plan documented in the task's own notes and commit message.
- [x] Human review and approval before moving to `collection-service` or `cross-service-events` — approved 2026-09-13; reviewed in a prior session, code confirmed solid.

## Phase 7: collection-service infra

- [x] Task 13: collection-service scaffolding
  - **Description:** Create the `collection-service` Maven module (depends on `shared-kernel` only), add it to the root reactor, `application.yml` (port 8082, same shared Postgres via `DB_URL`/`DB_PASSWORD` env vars, no hardcoded secret default), empty Liquibase master changelog, reuse the existing root `docker-compose.yml` unchanged.
  - **Acceptance criteria:**
    - [x] `collection-service/pom.xml` depends on `shared-kernel`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, Postgres driver, Liquibase core, Testcontainers (test scope)
    - [x] Root `pom.xml`'s `<modules>` gains `collection-service`
    - [x] `application.yml`: `server.port: 8082`, `spring.data.web.pageable.max-page-size: 100`
    - [x] `db/changelog/db.changelog-master.yaml` exists with `includeAll` on `changes/` (empty folder OK)
    - [x] App boots with `spring-boot:run` against the shared Docker Postgres, empty changelog applies with no errors, no collision with `recycler-service`'s tables
  - **Verification:**
    - [x] Build succeeds: `mvn -pl collection-service -am install`
    - [x] Manual check: `docker compose up -d && mvn -pl collection-service spring-boot:run` boots cleanly against the same Postgres instance `recycler-service` uses — confirmed no port/table conflict (`recycler-service` re-booted against the same volume right after: 3 changesets ran, `collection-service` ran 0, both started clean)
  - **Dependencies:** None (shared-kernel already built)
  - **Files likely touched:** `collection-service/pom.xml`, root `pom.xml`, `collection-service/src/main/resources/application.yml`, `collection-service/src/main/resources/db/changelog/db.changelog-master.yaml`
  - **Estimated scope:** Medium (4 files)
  - **Prerequisite fix (not scope creep — see commit history):** moved `PageResponse<T>` from `recycler-service`'s own package to `shared-kernel` (`pe.esgtrazabilidad.kernel.web`), since `collection-service` needs the same list-endpoint wrapper but cannot depend on `recycler-service` per the capability map. Anticipated by Task 4's original acceptance criteria wording ("... or a shared location if reused later"). Mechanical move (bare `spring-data-commons` added to `shared-kernel`), no behavior change — full reactor test suite re-run to confirm no regression (58 `recycler-service` tests still green).
  - **Environment note:** this machine runs **Rancher Desktop** (dockerd/moby mode), not Docker Desktop — verify with `docker ps`, never suggest launching `Docker Desktop.exe` (now documented in `CLAUDE.md`). Also: the local `esg_postgres_data` Docker volume from earlier sessions had an unrecorded password (by design — never logged/committed); recreated it (`docker compose down -v && up -d`) with a known dev-only password rather than guess — user confirmed this was fine since it only held local sample data, not anything worth preserving.
  - **`.env.local` follow-up (verified empirically, not from memory):** created `.env.local` at the repo root with `POSTGRES_PASSWORD`/`DB_PASSWORD`. Two different mechanisms apply, confirmed by testing each in isolation:
    - **Docker Compose does NOT auto-read `.env.local`** — its only auto-loaded file is plain `.env`. Confirmed via `docker compose config` failing against `.env.local` alone, then succeeding once renamed to `.env`. Kept the file named `.env.local` (as asked) rather than renaming — the fix is to always invoke Compose with `--env-file .env.local` (confirmed working). Documented here since it's easy to forget and silently fall back to a shell-exported value instead.
    - **Spring Boot has no built-in dotenv support either**, but it doesn't need a new dependency: added `spring.config.import: "optional:file:../.env.local[.properties]"` to both `recycler-service` and `collection-service`'s `application.yml` (the `[.properties]` suffix tells Spring Boot's config-data loader to parse the file as `.properties` syntax despite the `.local` extension; the `../` is relative to each module's own working directory when run via `mvn -pl <module> spring-boot:run`, which resolves to the repo root). `optional:` means a missing file doesn't break anything (CI, or a machine relying on real env vars instead) — verified both ways: boots clean with zero exported env vars and the file present, and still fails to start (Liquibase/Postgres auth error) with both the file and the env var absent, so the "never a silent default" guarantee holds either way.

### Checkpoint 7: Service boots
- [x] `mvn -pl collection-service spring-boot:run` boots cleanly against the shared Postgres, empty changelog applies
- [x] Human review before first entity slice — approved 2026-09-13; verified PageResponse relocation, `.env.local` + `docker compose --env-file` documentation, `pom.xml`/`application.yml`.

## Phase 8: Neighbor

- [x] Task 14: Neighbor persistence
  - **Description:** Schema, domain model, and persistence adapter for `Neighbor` — no HTTP surface yet.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.0_create_neighbor_table.yaml` creates the `neighbor` table: `full_name`, `phone`, `address`, `district`, `status`
    - [x] `Neighbor` domain class with a `status` of `ACTIVE`/`INACTIVE`
    - [x] JPA entity implementing `Persistable<UUID>` (same `isNew`/`@PostLoad` pattern as every `recycler-service` entity — app-assigned UUID v7 IDs) + Spring Data repository + `NeighborRepository` port + adapter (domain never leaks the JPA entity)
    - [x] `CollectionErrors` enum gains `NEIGHBOR_NOT_FOUND` (`COL-001`)
    - [x] Unit test for any domain-level validation logic on `Neighbor`
  - **Verification:**
    - [x] Tests pass: `mvn -pl collection-service test`
    - [x] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`Table neighbor created`, app started in 2.83s)
  - **Dependencies:** Task 13
  - **Files likely touched:** `db/changelog/changes/v0.1.0_create_neighbor_table.yaml`, `neighbor/domain/Neighbor.java`, `neighbor/adapter/out/persistence/{NeighborEntity,NeighborJpaRepository,NeighborRepositoryAdapter}.java`, `neighbor/port/out/NeighborRepository.java`, `neighbor/exception/CollectionErrors.java`
  - **Estimated scope:** Large (6 files) — persistence-only, no controller/service yet
  - **Note:** `Neighbor` has no domain-specific format invariant (unlike `Association`'s RUC or `Recycler`'s DNI regex) — all fields are free text, so there's no equivalent business rule to unit-test at the domain layer beyond `create()` producing an `ACTIVE` neighbor with a generated id (TDD RED confirmed: test failed to compile before `Neighbor` existed, GREEN after). Blank-field validation is deferred to Task 15's `jakarta.validation` DTO layer, matching `Association`'s own precedent (no non-blank domain checks there either). Also caught and fixed a real inconsistency in `SPEC-collection-service.md` itself: the Project Structure section said per-entity `NeighborErrors.java`, contradicting the Code Style block and Success Criteria's single shared `CollectionErrors` enum — fixed the spec to match the enum design actually used (placed at `pe.esgtrazabilidad.collection.exception.CollectionErrors`, not nested under `neighbor/`). No `existing()`/`update()` factory added to `NeighborEntity` — `Neighbor` has no lifecycle/update endpoint planned in this spec (unlike `CollectionSchedule`), so that machinery would be unused (YAGNI, same discipline as `Certification`'s Task 9 before Task 12 added renewal).
  - **Correction (user-caught, post-commit, two rounds):** `address`, `phone`, and `district` were all nullable with zero validation. Since `CollectionSchedule` has no address field of its own and depends entirely on `Neighbor`'s, this allowed an `ACTIVE` schedule with no physical pickup location.
    - **Round 1:** made `address` required in the domain (`IllegalArgumentException` in `Neighbor.create()` if null/blank — TDD RED→GREEN, same defense-in-depth pattern as `Association`'s RUC/`Recycler`'s DNI), and added the DB-level constraint via a **separate new changeset** (`v0.1.1_alter_neighbor_address_not_null.yaml`), following `recycler-service`'s "never edit a shipped changelog" boundary.
    - **Round 2 (user override, explicit and deliberate):** since the project has zero real data anywhere (only local dev Postgres, freely recreated), the user directed rewriting `v0.1.0_create_neighbor_table.yaml` in place (`address` now `nullable: false` directly in the original `createTable`) instead of carrying a synthetic ALTER changeset — deleted the separate `v0.1.1` file entirely, reverting the changelog numbering `v0.1.1`→`v0.1.2`→`v0.1.3` (Company/Schedule/Record) back to `v0.1.1`/`v0.1.2`/`v0.1.3` (none of those files exist yet, so pure renumbering). This is a one-time, explicitly-authorized exception to the "never edit a shipped changelog" rule — justified only because there's no real data anywhere to protect; the rule still applies once any environment beyond this local machine exists.
    - **Verified the DB constraint itself, not just the domain guard:** added `NeighborRepositoryAdapterIT.savingANeighborWithoutAnAddressViolatesTheDatabaseConstraint()`, which calls `Neighbor.reconstruct()` (bypasses the `create()` guard on purpose) and asserts `neighborRepository.save(...)` throws `DataIntegrityViolationException` — confirmed against real Postgres (`SQLState: 23502`, "violates not-null constraint"). Also recreated the shared `docker-compose` Postgres volume from scratch and re-booted both `collection-service` (confirms the rewritten `v0.1.0` applies cleanly as a single changeset, table created with the constraint baked in) and `recycler-service` (confirms no regression) against it.
    - `district` evaluated and left nullable — under the same "does it block the pickup itself, or is it just supplementary" test the user applied to `phone`, no feature in this spec actually consumes `district` yet (no zone-based routing), so requiring it now would enforce a rule with no consumer; can tighten later via a real migration once such a feature exists (by then, the "never edit a shipped changelog" rule will actually apply). `phone` unchanged (nullable, operational contact only).

- [x] Task 15: Neighbor API
  - **Description:** Expose Neighbor CRUD over HTTP: mapper, request/response DTOs with validation, use case interfaces, service, controller.
  - **Acceptance criteria:**
    - [x] `POST /neighbors` creates a neighbor via `jakarta.validation` on request fields (`@NotBlank` on `fullName` and `address` — the latter matching the domain-level requirement fixed after Task 14)
    - [x] `GET /neighbors/{id}` returns 404 via `COL-001` when missing
    - [x] `GET /neighbors` returns a paginated `PageResponse<NeighborResponse>`, filterable by `status`/`district` via a composed `Specification<Neighbor>`, `@PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)`
    - [x] Unit test for `NeighborService` (Mockito-mocked repository port)
    - [x] IT test (`NeighborApiIT`, RestAssured + Testcontainers) covering create → get → list plus the not-found path
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl collection-service test` (8 total: 4 `NeighborTest` + 4 `NeighborServiceTest`)
    - [x] Integration tests pass: `mvn -pl collection-service verify` (6 total: 5 `NeighborApiIT` + 1 `NeighborRepositoryAdapterIT`)
    - [x] Manual check: create → list → get-by-id exercised via curl against `docker compose up` Postgres, plus `/v3/api-docs` confirmed reachable and listing `/neighbors`
  - **Dependencies:** Task 14
  - **Files likely touched:** `neighbor/adapter/in/web/{NeighborController,NeighborMapper}.java`, DTOs (`CreateNeighborRequest`, `NeighborResponse`), `neighbor/port/in/*UseCase.java`, `neighbor/service/NeighborService.java`, `NeighborServiceTest.java`, `it/NeighborApiIT.java`
  - **Estimated scope:** Large (7 files)
  - **Note:** TDD RED→GREEN for `NeighborServiceTest`/`NeighborService` (test written and confirmed failing to compile before the service existed). No lifecycle endpoints (no `suspend`/`activate` equivalent) — matches the spec's Success Criteria, which only calls for create/get-by-id/list for `Neighbor`.

### Checkpoint 8: Neighbor CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a neighbor via curl
- [x] Human review before Company slice — implicit approval: user directed `/build` for Task 16 directly

## Phase 9: Company

- [x] Task 16: Company persistence
  - **Description:** Schema, domain model, and persistence adapter for `Company` — standalone, no relationship to the collection domain.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.1_create_company_table.yaml` creates the `company` table: `name`, `ruc` (unique, 11 chars), `contact_email`, `contact_phone`, `address`, `status`
    - [x] `Company` domain class, RUC regex validation (`\d{11}`, same pattern as `Association`), `status` of `ACTIVE`/`INACTIVE`
    - [x] JPA entity + repository + port + adapter, same `Persistable<UUID>` shape as `Neighbor`
    - [x] `CollectionErrors` gains `COMPANY_NOT_FOUND` (`COL-004`), `DUPLICATE_RUC` (`COL-005`)
    - [x] Unit test for RUC validation
  - **Verification:**
    - [x] Tests pass: `mvn -pl collection-service test` (12 total)
    - [x] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`Table company created`, app started in 2.66s)
  - **Dependencies:** Task 13 (not Task 14/15 — no relationship to Neighbor)
  - **Files likely touched:** `db/changelog/changes/v0.1.1_create_company_table.yaml`, `company/domain/Company.java`, `company/adapter/out/persistence/*.java`, `company/port/out/CompanyRepository.java`
  - **Estimated scope:** Large (6 files)
  - **Note:** TDD RED→GREEN for `CompanyTest`/`Company` (RUC regex validation, same pattern as `Association`). No `existing()`/`update()` factory — `Company` has no lifecycle/update endpoint planned in this spec (flat client registry, per the spec's "Neighbor/Company relationship model" decision), same YAGNI reasoning as `Neighbor`.

- [ ] Task 17: Company API
  - **Description:** Expose Company CRUD over HTTP.
  - **Acceptance criteria:**
    - [ ] `POST /companies` validates RUC format, returns 409 via `COL-005` on duplicate RUC
    - [ ] `GET /companies/{id}` returns 404 via `COL-004` when missing
    - [ ] `GET /companies` returns paginated, filterable `PageResponse<CompanyResponse>`
    - [ ] `CompanyExceptionHandler` disambiguates the duplicate-RUC `DataIntegrityViolationException` via `ConstraintViolationException.getConstraintName()`, same pattern as `recycler-service`
    - [ ] Unit test for `CompanyService` + IT test (`CompanyApiIT`) covering create → get → list, duplicate-RUC and not-found paths
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl collection-service test`
    - [ ] Integration tests pass: `mvn -pl collection-service verify`
  - **Dependencies:** Task 16
  - **Files likely touched:** `company/adapter/in/web/{CompanyController,CompanyMapper,CompanyExceptionHandler}.java`, DTOs, `company/port/in/*UseCase.java`, `company/service/CompanyService.java`, `CompanyServiceTest.java`, `it/CompanyApiIT.java`
  - **Estimated scope:** Large (7-8 files)

### Checkpoint 9: Company CRUD works end-to-end
- [ ] `mvn -pl collection-service verify` green
- [ ] Manual check: create → get → list a company, duplicate-RUC returns 409
- [ ] Human review before CollectionSchedule slice

## Phase 10: CollectionSchedule

- [ ] Task 18: CollectionSchedule persistence
  - **Description:** Schema, domain model with the `pause()`/`cancel()`/`reactivate()` state machine, and persistence adapter for `CollectionSchedule`, with a required FK to `Neighbor`.
  - **Acceptance criteria:**
    - [ ] Liquibase changelog `v0.1.2_create_collection_schedule_table.yaml` creates the `collection_schedule` table: `neighbor_id` (FK, not null), `day_of_week`, `time`, `status` — plus a **partial unique index** `(neighbor_id, day_of_week) WHERE status = 'ACTIVE'` via a raw `<sql>` changeset (Liquibase's `<createIndex>` doesn't support a `WHERE` clause portably)
    - [ ] `CollectionSchedule` domain class: `pause()` (ACTIVE→PAUSED), `cancel()` (ACTIVE or PAUSED→CANCELLED, terminal), `reactivate()` (PAUSED→ACTIVE) — every illegal transition throws `IllegalStateException`
    - [ ] JPA entity + repository + port + adapter, **including the `existing()`/`update()` path from the start** (per `persistable_update_path` memory — this entity needs lifecycle updates from day one, don't discover it after the fact like Task 12 did)
    - [ ] `CollectionErrors` gains `SCHEDULE_NOT_FOUND` (`COL-006`)
    - [ ] Unit tests: every legal transition, every illegal transition → `IllegalStateException`
  - **Verification:**
    - [ ] Tests pass: `mvn -pl collection-service test`
  - **Dependencies:** Task 14 (neighbor FK)
  - **Files likely touched:** `db/changelog/changes/v0.1.2_create_collection_schedule_table.yaml`, `schedule/domain/CollectionSchedule.java`, `schedule/adapter/out/persistence/*.java`, `schedule/port/out/CollectionScheduleRepository.java`
  - **Estimated scope:** Large (6 files)

- [ ] Task 19: CollectionSchedule API (CRUD)
  - **Description:** Expose CollectionSchedule create/get/list nested under its neighbor, with the COL-002 same-day conflict rule.
  - **Acceptance criteria:**
    - [ ] `POST /neighbors/{neighborId}/schedules` returns 409 via `COL-002` on same-day active conflict, 404 via `COL-001` if neighbor missing
    - [ ] `GET /neighbors/{neighborId}/schedules/{id}` returns 404 via `COL-006` when missing, scoped to `neighborId` (cross-neighbor lookup must 404, not leak — same path-scoping discipline as `recycler-service` Task 8/10)
    - [ ] `GET /neighbors/{neighborId}/schedules` returns paginated `PageResponse<CollectionScheduleResponse>`
    - [ ] `CollectionScheduleService` exposes a reusable `assertNoActiveConflict(neighborId, dayOfWeek, excludingScheduleId)` method — used by `create()` here and by `reactivate()` in Task 20, so the rule isn't duplicated
    - [ ] `ScheduleExceptionHandler` disambiguates the partial-unique-index violation via `ConstraintViolationException.getConstraintName()` → `COL-002`
    - [ ] Unit + IT tests: create → get → list, conflict path (COL-002), neighbor-not-found path, cross-neighbor path-scoping (404)
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl collection-service test`
    - [ ] Integration tests pass: `mvn -pl collection-service verify`
  - **Dependencies:** Task 18
  - **Files likely touched:** `schedule/adapter/in/web/{CollectionScheduleController,CollectionScheduleMapper,ScheduleExceptionHandler}.java`, DTOs, `schedule/port/in/{Create,Get,List}*UseCase.java`, `schedule/service/CollectionScheduleService.java`, `CollectionScheduleServiceTest.java`, `it/CollectionScheduleApiIT.java`
  - **Estimated scope:** Large (7-8 files)

- [ ] Task 20: CollectionSchedule lifecycle
  - **Description:** Expose `pause`/`cancel`/`reactivate` over HTTP.
  - **Acceptance criteria:**
    - [ ] `PATCH /neighbors/{neighborId}/schedules/{id}/pause`, `.../cancel`, `.../reactivate` call the domain guard methods; illegal transition → 409 via new `CollectionErrors.INVALID_SCHEDULE_TRANSITION` (`COL-008`), not a raw `IllegalStateException`
    - [ ] `reactivate()` re-runs `assertNoActiveConflict(...)` (from Task 19) before flipping back to `ACTIVE`
    - [ ] Unit tests: every legal transition, every illegal transition → `COL-008`, reactivate-into-a-new-conflict case
    - [ ] IT tests: happy path for all three endpoints + the reactivate-conflict 409 case end-to-end
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl collection-service test`
    - [ ] Integration tests pass: `mvn -pl collection-service verify`
    - [ ] Manual check: pause → reactivate → cancel a schedule via curl, confirm cancel is terminal
  - **Dependencies:** Task 19
  - **Files likely touched:** `schedule/port/in/{Pause,Cancel,Reactivate}ScheduleUseCase.java`, `schedule/service/CollectionScheduleService.java` (new methods), `schedule/adapter/in/web/CollectionScheduleController.java` (new endpoints), corresponding tests
  - **Estimated scope:** Medium (5 files)

### Checkpoint 10: CollectionSchedule complete (CRUD + full lifecycle)
- [ ] `mvn -pl collection-service verify` green
- [ ] Manual check: two schedules same neighbor different days (both succeed), same-day duplicate (409 COL-002), pause → reactivate, cancel → confirm terminal (409 COL-008 on further pause/reactivate)
- [ ] Human review before CollectionRecord slice

## Phase 11: CollectionRecord

- [ ] Task 21: CollectionRecord persistence
  - **Description:** Schema, domain model, and persistence adapter for `CollectionRecord` — an immutable historical record, no lifecycle.
  - **Acceptance criteria:**
    - [ ] Liquibase changelog `v0.1.3_create_collection_record_table.yaml` creates the `collection_record` table: `neighbor_id` (FK, not null), `schedule_id` (FK, **nullable**), `association_id` (plain `UUID` column, **no FK constraint**), `collection_date`, `weight_kg`
    - [ ] `CollectionRecord` domain class — no status field, no lifecycle methods
    - [ ] JPA entity + repository + port + adapter — `save()`/`findById()`/list only, **no `update()`** (nothing to update on an immutable record)
    - [ ] `CollectionErrors` gains `RECORD_NOT_FOUND` (`COL-007`)
    - [ ] Unit test: `weightKg` must be positive, `collectionDate` required
  - **Verification:**
    - [ ] Tests pass: `mvn -pl collection-service test`
  - **Dependencies:** Task 14 (neighbor FK), Task 18 (schedule table must exist for the nullable FK column)
  - **Files likely touched:** `db/changelog/changes/v0.1.3_create_collection_record_table.yaml`, `collectionrecord/domain/CollectionRecord.java`, `collectionrecord/adapter/out/persistence/*.java`, `collectionrecord/port/out/CollectionRecordRepository.java`
  - **Estimated scope:** Large (6 files)

- [ ] Task 22: CollectionRecord API
  - **Description:** Expose CollectionRecord create/get/list nested under its neighbor.
  - **Acceptance criteria:**
    - [ ] `POST /neighbors/{neighborId}/collection-records` returns 404 via `COL-001` if neighbor missing; `associationId` accepted and persisted with **no existence check**
    - [ ] `GET /neighbors/{neighborId}/collection-records/{id}` returns 404 via `COL-007`, scoped to `neighborId`
    - [ ] `GET /neighbors/{neighborId}/collection-records` returns paginated, filterable by date range
    - [ ] Unit + IT tests: create with a real `scheduleId`, create with `scheduleId` omitted (ad-hoc), create with a random unvalidated `associationId` (**must succeed** — proves the eventual-consistency decision holds, not just allowed by omission), neighbor-not-found path
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl collection-service test`
    - [ ] Integration tests pass: `mvn -pl collection-service verify`
    - [ ] Manual check: log a record tied to a schedule, log an ad-hoc one, confirm `associationId` isn't validated
  - **Dependencies:** Task 21
  - **Files likely touched:** `collectionrecord/adapter/in/web/{CollectionRecordController,CollectionRecordMapper}.java`, DTOs, `collectionrecord/port/in/*UseCase.java`, `collectionrecord/service/CollectionRecordService.java`, `CollectionRecordServiceTest.java`, `it/CollectionRecordApiIT.java`
  - **Estimated scope:** Large (7 files)

### Checkpoint 11: CollectionRecord complete
- [ ] `mvn -pl collection-service verify` green
- [ ] Manual check: log a collection record tied to a schedule, log an ad-hoc one (no schedule), confirm both list correctly and `associationId` isn't validated
- [ ] Human review before Polish phase

## Phase 12: Polish

- [ ] Task 23: springdoc-openapi wiring
  - **Description:** Confirm Swagger UI renders all `collection-service` controllers with accurate request/response schemas.
  - **Acceptance criteria:**
    - [ ] Swagger UI reachable and lists `Neighbor`, `Company`, `CollectionSchedule` (incl. lifecycle endpoints), `CollectionRecord`
    - [ ] All `create()` endpoints have `@ResponseStatus(HttpStatus.CREATED)` (documentation hint — springdoc can't infer `ResponseEntity.status(...)` statically, same fix as `recycler-service` Task 11)
  - **Verification:**
    - [ ] Manual check: open Swagger UI, exercise one endpoint per controller
  - **Dependencies:** Task 15, Task 17, Task 19, Task 20, Task 22
  - **Files likely touched:** `application.yml` (if any springdoc customization needed), `@ResponseStatus` annotations on existing controllers
  - **Estimated scope:** Small (1-2 files)

### Checkpoint 12: Full CRUD path complete
- [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`)
- [ ] Manual check: Neighbor → CollectionSchedule → CollectionRecord chain works end-to-end through real HTTP calls; Company independently CRUD-able; `recycler-service` unaffected

### Checkpoint 13: Final — ready for review
- [ ] All Success Criteria in `SPEC-collection-service.md` are met
- [ ] Definition of Done satisfied for every task above (Tasks 13-23)
- [ ] Human review and approval before moving to `cross-service-events` or `reporting-service`
