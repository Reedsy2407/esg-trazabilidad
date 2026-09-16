# Task List: shared-kernel + recycler-service + collection-service + cross-service-events

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`, `SPEC-collection-service.md`, `SPEC-cross-service-events.md`.

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

- [x] Task 17: Company API
  - **Description:** Expose Company CRUD over HTTP.
  - **Acceptance criteria:**
    - [x] `POST /companies` validates RUC format, returns 409 via `COL-005` on duplicate RUC
    - [x] `GET /companies/{id}` returns 404 via `COL-004` when missing
    - [x] `GET /companies` returns paginated, filterable `PageResponse<CompanyResponse>`
    - [x] `CompanyExceptionHandler` catches the duplicate-RUC `DataIntegrityViolationException` fallback — **simplified from the plan's original wording**: `Company` has exactly one unique constraint (`ruc`), same as `Association`, so no `ConstraintViolationException.getConstraintName()` disambiguation is needed (that's only for multi-constraint controllers like `RecyclerController`). Mirrors `AssociationExceptionHandler` exactly, not `RecyclerExceptionHandler`.
    - [x] Unit test for `CompanyService` + IT test (`CompanyApiIT`) covering create → get → list, duplicate-RUC and not-found paths
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl collection-service test` (17 total)
    - [x] Integration tests pass: `mvn -pl collection-service verify` (12 total)
    - [x] Manual check: create → list a company via curl against `docker compose up` Postgres, confirmed `/companies` and `/companies/{id}` appear in `/v3/api-docs`
  - **Dependencies:** Task 16
  - **Files likely touched:** `company/adapter/in/web/{CompanyController,CompanyMapper,CompanyExceptionHandler}.java`, DTOs, `company/port/in/*UseCase.java`, `company/service/CompanyService.java`, `CompanyServiceTest.java`, `it/CompanyApiIT.java`
  - **Estimated scope:** Large (7-8 files)
  - **Note:** TDD RED→GREEN for `CompanyServiceTest`/`CompanyService` (test written and confirmed failing to compile before the service existed).

### Checkpoint 9: Company CRUD works end-to-end
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: create → get → list a company, duplicate-RUC returns 409
- [x] Human review before CollectionSchedule slice — implicit approval: user directed `/build` for Task 18 directly

## Phase 10: CollectionSchedule

- [x] Task 18: CollectionSchedule persistence
  - **Description:** Schema, domain model with the `pause()`/`cancel()`/`reactivate()` state machine, and persistence adapter for `CollectionSchedule`, with a required FK to `Neighbor`.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.2_create_collection_schedule_table.yaml` creates the `collection_schedule` table: `neighbor_id` (FK, not null), `day_of_week`, `pickup_time` (column named to avoid the `time` reserved-type ambiguity), `status` — plus a **partial unique index** `(neighbor_id, day_of_week) WHERE status = 'ACTIVE'` via a raw `<sql>` changeset (Liquibase's `<createIndex>` doesn't support a `WHERE` clause portably)
    - [x] `CollectionSchedule` domain class: `pause()` (ACTIVE→PAUSED), `cancel()` (ACTIVE or PAUSED→CANCELLED, terminal), `reactivate()` (PAUSED→ACTIVE) — every illegal transition throws `IllegalStateException`
    - [x] JPA entity + repository + port + adapter, **including the `existing()`/`update()` path from the start** (per `persistable_update_path` memory)
    - [x] `CollectionErrors` gains `SCHEDULE_NOT_FOUND` (`COL-006`)
    - [x] Unit tests: every legal transition, every illegal transition → `IllegalStateException` (11 tests total)
  - **Verification:**
    - [x] Tests pass: `mvn -pl collection-service test` (28 total)
    - [x] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`Table collection_schedule created`, both changesets ran, app boots); inspected the live table via `psql \d collection_schedule` and confirmed the partial index exists exactly as designed (`UNIQUE, btree (neighbor_id, day_of_week) WHERE status::text = 'ACTIVE'::text`) plus the FK to `neighbor`; proved it live by inserting two `ACTIVE` rows for the same neighbor+day and confirming Postgres rejects the second with `duplicate key value violates unique constraint "ux_collection_schedule_neighbor_day_active"` (multi-statement `psql -c` batch rolled back cleanly on the error, no stray data left)
  - **Dependencies:** Task 14 (neighbor FK)
  - **Files likely touched:** `db/changelog/changes/v0.1.2_create_collection_schedule_table.yaml`, `schedule/domain/CollectionSchedule.java`, `schedule/adapter/out/persistence/*.java`, `schedule/port/out/CollectionScheduleRepository.java`
  - **Estimated scope:** Large (6 files)
  - **Note:** used `java.time.DayOfWeek` directly for the domain field instead of a redundant custom enum — JPA maps it via `@Enumerated(EnumType.STRING)` like any other enum. TDD RED→GREEN for the full state machine (test file written and confirmed failing to compile before `CollectionSchedule` existed).

- [x] Task 19: CollectionSchedule API (CRUD)
  - **Description:** Expose CollectionSchedule create/get/list nested under its neighbor, with the COL-002 same-day conflict rule.
  - **Acceptance criteria:**
    - [x] `POST /neighbors/{neighborId}/schedules` returns 409 via `COL-002` on same-day active conflict, 404 via `COL-001` if neighbor missing
    - [x] `GET /neighbors/{neighborId}/schedules/{id}` returns 404 via `COL-006` when missing, scoped to `neighborId` (cross-neighbor lookup must 404, not leak — same path-scoping discipline as `recycler-service` Task 8/10)
    - [x] `GET /neighbors/{neighborId}/schedules` returns paginated `PageResponse<CollectionScheduleResponse>`
    - [x] `CollectionScheduleService` exposes a reusable `assertNoActiveConflict(neighborId, dayOfWeek, excludingScheduleId)` package-private method — used by `create()` here, will be reused by `reactivate()` in Task 20
    - [x] `ScheduleExceptionHandler` disambiguates via `ConstraintViolationException.getConstraintName()` — `ux_collection_schedule_neighbor_day_active` → `COL-002`, `fk_collection_schedule_neighbor` → `COL-001` (two real constraints on this table, unlike `Neighbor`/`Company`'s one each, so this mirrors `RecyclerExceptionHandler`'s multi-constraint pattern, not `AssociationExceptionHandler`'s)
    - [x] `ScheduleExceptionHandlerTest` (`@WebMvcTest(CollectionScheduleController.class)`, user-caught gap — added after initial Task 19 completion): covers all three branches (`ux_..._active` → 409 COL-002, `fk_..._neighbor` → 404 COL-001, unrecognized constraint → generic `DATA_CONFLICT`). Verified it actually detects a swapped mapping, not just a present one: temporarily swapped the two constraint-name constants, confirmed 2 of 3 tests failed with the exact wrong status codes, then reverted and re-confirmed green.
    - [x] Unit + IT tests: create → get → list, conflict path (COL-002), neighbor-not-found path, cross-neighbor path-scoping (404), plus a same-neighbor-different-days-both-succeed case proving COL-002 doesn't over-reach
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl collection-service test` (35 total)
    - [x] Integration tests pass: `mvn -pl collection-service verify` (18 total)
    - [x] Manual check: create → conflict (409 COL-002) via curl against `docker compose up` Postgres; confirmed `/neighbors/{neighborId}/schedules` and `.../{id}` in `/v3/api-docs`
  - **Dependencies:** Task 18
  - **Files likely touched:** `schedule/adapter/in/web/{CollectionScheduleController,CollectionScheduleMapper,ScheduleExceptionHandler}.java`, DTOs, `schedule/port/in/{Create,Get,List}*UseCase.java`, `schedule/service/CollectionScheduleService.java`, `CollectionScheduleServiceTest.java`, `it/CollectionScheduleApiIT.java`
  - **Estimated scope:** Large (7-8 files)
  - **Note:** TDD RED→GREEN for `CollectionScheduleService` (7 unit tests: create happy path, neighbor-not-found, conflict, get scoped, get-not-found, cross-neighbor-scoping, list-delegates). Neighbor-existence check lives in the service (`NeighborRepository.findById()`), not the persistence adapter — same architecture lesson from `recycler-service` Task 7/8's correction, applied correctly from the start this time.

- [x] Task 20: CollectionSchedule lifecycle
  - **Description:** Expose `pause`/`cancel`/`reactivate` over HTTP.
  - **Acceptance criteria:**
    - [x] `PATCH /neighbors/{neighborId}/schedules/{id}/pause`, `.../cancel`, `.../reactivate` call the domain guard methods; illegal transition → 409 via new `CollectionErrors.INVALID_SCHEDULE_TRANSITION` (`COL-008`), not a raw `IllegalStateException`
    - [x] `reactivate()` re-runs `assertNoActiveConflict(...)` (from Task 19) before flipping back to `ACTIVE`
    - [x] Unit tests: every legal transition, every illegal transition → `COL-008`, reactivate-into-a-new-conflict case
    - [x] IT tests: happy path for all three endpoints + the reactivate-conflict 409 case end-to-end
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl collection-service test` (46 total)
    - [x] Integration tests pass: `mvn -pl collection-service verify` (22 total)
    - [x] Manual check: pause → reactivate → cancel a schedule via curl against `docker compose up` Postgres, confirmed cancel is terminal (pause-after-cancel → 409 COL-008)
  - **Dependencies:** Task 19
  - **Files likely touched:** `schedule/port/in/{Pause,Cancel,Reactivate}ScheduleUseCase.java`, `schedule/service/CollectionScheduleService.java` (new methods), `schedule/adapter/in/web/CollectionScheduleController.java` (new endpoints), corresponding tests
  - **Estimated scope:** Medium (5 files)
  - **Note:** TDD RED→GREEN for the 8 new service tests (pause/cancel/reactivate happy paths, illegal-transition → COL-008 for each, reactivate-into-a-new-conflict → COL-002 not COL-008). `reactivate()` calls `schedule.reactivate()` (validates PAUSED→ACTIVE, mutates in-memory) before `assertNoActiveConflict()` — safe since nothing persists until `repository.update()`, which only runs if both checks pass. Adding the three new controller constructor params required updating `ScheduleExceptionHandlerTest`'s `@MockBean`s too (caught immediately by a compile error, not silently).

### Checkpoint 10: CollectionSchedule complete (CRUD + full lifecycle)
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: two schedules same neighbor different days (both succeed), same-day duplicate (409 COL-002), pause → reactivate, cancel → confirm terminal (409 COL-008 on further pause/reactivate)
- [x] Human review before CollectionRecord slice — implicit approval: user directed `/build` for Task 21 directly

## Phase 11: CollectionRecord

- [x] Task 21: CollectionRecord persistence
  - **Description:** Schema, domain model, and persistence adapter for `CollectionRecord` — an immutable historical record, no lifecycle.
  - **Acceptance criteria:**
    - [x] Liquibase changelog `v0.1.3_create_collection_record_table.yaml` creates the `collection_record` table: `neighbor_id` (FK, not null), `schedule_id` (FK, **nullable**), `association_id` (plain `UUID` column, **no FK constraint**), `collection_date`, `weight_kg`
    - [x] `CollectionRecord` domain class — no status field, no lifecycle methods
    - [x] JPA entity + repository + port + adapter — `save()`/`findById()`/list only, **no `update()`** (nothing to update on an immutable record)
    - [x] `CollectionErrors` gains `RECORD_NOT_FOUND` (`COL-007`)
    - [x] Unit test: `weightKg` must be positive, `collectionDate` required
  - **Verification:**
    - [x] Tests pass: `mvn -pl collection-service test` (52 total)
    - [x] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`Table collection_record created`, app boots); inspected the live table via `psql \d` and confirmed `neighbor_id`/`schedule_id` have real FKs while `association_id` is a bare `UUID NOT NULL` with **no FK** — exactly the spec's eventual-consistency decision, not an oversight
  - **Dependencies:** Task 14 (neighbor FK), Task 18 (schedule table must exist for the nullable FK column)
  - **Files likely touched:** `db/changelog/changes/v0.1.3_create_collection_record_table.yaml`, `collectionrecord/domain/CollectionRecord.java`, `collectionrecord/adapter/out/persistence/*.java`, `collectionrecord/port/out/CollectionRecordRepository.java`
  - **Estimated scope:** Large (6 files)
  - **Note:** TDD RED→GREEN (6 tests: create happy path, schedule-linked variant, zero/negative weight rejected, null date rejected, reconstruct). `weightKg` uses `BigDecimal` (not `double`), matching money/quantity-precision convention; DB column `numeric(10,2)`.

- [x] Task 22: CollectionRecord API
  - **Description:** Expose CollectionRecord create/get/list nested under its neighbor.
  - **Acceptance criteria:**
    - [x] `POST /neighbors/{neighborId}/collection-records` returns 404 via `COL-001` if neighbor missing; `associationId` accepted and persisted with **no existence check**
    - [x] `GET /neighbors/{neighborId}/collection-records/{id}` returns 404 via `COL-007`, scoped to `neighborId`
    - [x] `GET /neighbors/{neighborId}/collection-records` returns paginated, filterable by date range (`?from=&to=`, `Specification`-composed, same pattern as `Neighbor`'s status/district filters)
    - [x] Unit + IT tests: create with a real `scheduleId`, create with `scheduleId` omitted (ad-hoc), create with a random unvalidated `associationId` (**must succeed** — proves the eventual-consistency decision holds, not just allowed by omission), neighbor-not-found path
  - **Verification:**
    - [x] Unit tests pass: `mvn -pl collection-service test` (63 total)
    - [x] Integration tests pass: `mvn -pl collection-service verify` (31 total)
    - [x] Manual check: logged an ad-hoc record via curl against `docker compose up` Postgres with a freshly-generated random `associationId`, confirmed it's accepted and returned unchanged (no existence check); confirmed `/neighbors/{neighborId}/collection-records` and `.../{id}` in `/v3/api-docs`
  - **Dependencies:** Task 21
  - **Files likely touched:** `collectionrecord/adapter/in/web/{CollectionRecordController,CollectionRecordMapper}.java`, DTOs, `collectionrecord/port/in/*UseCase.java`, `collectionrecord/service/CollectionRecordService.java`, `CollectionRecordServiceTest.java`, `it/CollectionRecordApiIT.java`
  - **Estimated scope:** Large (7 files)
  - **Note:** TDD RED→GREEN for `CollectionRecordService` (8 tests). Added `CollectionRecordExceptionHandler` (two FK constraints: neighbor, schedule) **with its `@WebMvcTest`-based test built in from the start** — applying the lesson from `ScheduleExceptionHandler`'s gap immediately rather than waiting to be asked again. List defaults to `sort=collectionDate,DESC` (most recent pickups first), unlike every other list endpoint's ascending-by-natural-key default — a deliberate choice for a historical log, not an oversight.
  - **Environment note (this session):** Rancher Desktop had been restarted since the prior session; the shared `docker-compose` Postgres container had exited (`docker ps` empty) and a first `mvn verify` attempt hung indefinitely at Testcontainers' npipe strategy negotiation (stuck 4+ minutes with zero log progress, confirmed via `tasklist`/log tail — not just slow, since a fresh `docker ps` worked instantly in a new shell). Killed the hung `java.exe` processes, restarted the shared Postgres via `docker compose --env-file .env.local up -d`, and re-ran clean in a fresh shell — succeeded immediately.
  - **Correction (user-caught, post-commit, two findings):**
    1. **`scheduleId` ownership not checked:** `create()` only relied on the DB FK, which guarantees the schedule exists somewhere, not that it belongs to `command.neighborId()`. Fixed: when `scheduleId` is present, `CollectionRecordService` now fetches the `CollectionSchedule` (via `CollectionScheduleRepository`, injected the same way `NeighborRepository` already was) and compares `getNeighborId()`; a mismatch throws the **same** `CollectionErrors.SCHEDULE_NOT_FOUND` (`COL-006`) as a genuinely missing schedule, deliberately not leaking that the schedule exists under a different neighbor. Added 2 new unit tests (cross-neighbor mismatch, genuinely-missing schedule) and 1 IT test (`creatingWithAnotherNeighborsScheduleIdReturnsNotFound` — two real neighbors, each with their own real schedule, confirms cross-use fails) — plus a live manual curl proof (two neighbors, B's schedule attempted against A → 404 COL-006).
    2. **shared-kernel gap now concretely exploitable:** `GlobalExceptionHandler` never handled `MethodArgumentTypeMismatchException` (malformed path/query param — e.g. `?from=`/`?to=` on this endpoint's date-range filter) or `HttpMessageNotReadableException` (malformed JSON body — e.g. `associationId` not a valid UUID string), both of which fail before `@Valid` runs. Fixed in `shared-kernel/GlobalExceptionHandler.java` — benefits `recycler-service` and `collection-service` at once, not just this endpoint. Both map to `VALIDATION_ERROR` (400), same `ProblemDetail` shape as the existing `MethodArgumentNotValidException` handler. TDD RED→GREEN (2 new tests, plain-unit style matching the existing suite). Verified all three modules still compile and pass after the shared-kernel change: `shared-kernel` 10/10, `recycler-service` 58/58, `collection-service` 65 unit + 32 IT.

### Checkpoint 11: CollectionRecord complete
- [x] `mvn -pl collection-service verify` green
- [x] Manual check: log a collection record tied to a schedule, log an ad-hoc one (no schedule), confirm both list correctly and `associationId` isn't validated
- [ ] Human review before Polish phase

## Phase 12: Polish

- [x] Task 23: springdoc-openapi wiring
  - **Description:** Confirm Swagger UI renders all `collection-service` controllers with accurate request/response schemas.
  - **Acceptance criteria:**
    - [x] Swagger UI reachable and lists `Neighbor`, `Company`, `CollectionSchedule` (incl. lifecycle endpoints), `CollectionRecord`
    - [x] All `create()` endpoints have `@ResponseStatus(HttpStatus.CREATED)` (documentation hint — springdoc can't infer `ResponseEntity.status(...)` statically, same fix as `recycler-service` Task 11)
  - **Verification:**
    - [x] Manual check: booted against `docker compose up` Postgres, confirmed `swagger-ui.html` reachable (302 redirect) and `/v3/api-docs` lists all 9 expected paths — `/neighbors`, `/neighbors/{id}`, `/companies`, `/companies/{id}`, `/neighbors/{neighborId}/schedules`, `.../{id}`, `.../{id}/pause`, `.../{id}/cancel`, `.../{id}/reactivate`, `/neighbors/{neighborId}/collection-records`, `.../{id}`; confirmed 4× `"201"` (one per `create()`) and validation constraints rendering correctly (`required` fields per DTO, RUC `\d{11}` pattern, `weightKg` minimum)
  - **Dependencies:** Task 15, Task 17, Task 19, Task 20, Task 22
  - **Files likely touched:** `application.yml` (if any springdoc customization needed), `@ResponseStatus` annotations on existing controllers
  - **Estimated scope:** Small (1-2 files)
  - **Note:** no code changes needed — unlike `recycler-service`'s Task 11 (which had to retrofit `@ResponseStatus(CREATED)` after the fact), every `collection-service` controller had it applied proactively from the start, copied directly from the post-Task-11 pattern each time. This task was pure verification.

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

- [ ] Task 24: RabbitMQ + ShedLock infra wiring
  - **Description:** Add `rabbitmq:3.13-management-alpine` to the root `docker-compose.yml`, pin `shedlock-spring`/`shedlock-provider-jdbc-template` versions in the root pom's `dependencyManagement` (not in the Spring Boot BOM, same treatment as `springdoc-openapi`), and wire `spring.rabbitmq.*` into both services' `application.yml` — env-var-driven, no hardcoded secret default.
  - **Acceptance criteria:**
    - [ ] `docker-compose.yml` gains a `rabbitmq` service (ports 5672 + 15672, credentials via env vars with `:?must be set`, matching the existing Postgres pattern)
    - [ ] Root `pom.xml` `dependencyManagement` pins `shedlock-spring` and `shedlock-provider-jdbc-template`
    - [ ] Both `application.yml`s gain `spring.rabbitmq.host/port/username/password`, no hardcoded default
    - [ ] `docker compose --env-file .env.local up -d` starts Postgres *and* RabbitMQ; management UI reachable at `localhost:15672`
  - **Verification:**
    - [ ] Infra-only — no RED/GREEN ceremony per `[[tdd_scope_for_config_fixes]]`
    - [ ] Manual check: both services boot with no AMQP connection errors in logs
  - **Dependencies:** None
  - **Files likely touched:** `docker-compose.yml`, `pom.xml`, `recycler-service/src/main/resources/application.yml`, `collection-service/src/main/resources/application.yml`
  - **Estimated scope:** Small (4 files)

- [ ] Task 25: shared-kernel event core
  - **Description:** Rewrite `EventPublishingStrategy` to `{RABBITMQ, MOCK}` with Javadoc explaining the removal of `GCP_PUB_SUB`/`SPRING_EVENTS`; add `DomainEvent` marker interface, `OutboxEntry` plain-record shape, and the small `OutboxRepository` port interface each service's own outbox adapter will implement.
  - **Acceptance criteria:**
    - [ ] `EventPublishingStrategy` has exactly `RABBITMQ`, `MOCK`, with Javadoc documenting why `GCP_PUB_SUB`/`SPRING_EVENTS` were removed
    - [ ] `DomainEvent` interface: `UUID eventId()`, `Instant occurredAt()`, `String routingKey()`
    - [ ] `OutboxEntry` record: `{id, eventType, routingKey, payloadJson, status, createdAt}` — not a JPA `@Entity`
    - [ ] `OutboxRepository` port: `save`, `findPendingBatch`, `markProcessed`, `markFailed`
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl shared-kernel test` — `EventPublishingStrategy` has exactly the two expected values; `OutboxEntry` construction/shape
  - **Dependencies:** Task 24
  - **Files likely touched:** `shared-kernel/src/main/java/.../events/EventPublishingStrategy.java`, `.../events/DomainEvent.java`, `.../events/OutboxEntry.java`, `.../events/OutboxRepository.java` (port), plus tests
  - **Estimated scope:** Small-Medium (4-5 files)

- [ ] Task 26: shared-kernel `OutboxDispatcher`
  - **Description:** Generic, `@ConditionalOnProperty`-gated scheduled component that reads pending rows via the single `OutboxRepository` bean present in whichever service's context it runs in, publishes each to RabbitMQ via `RabbitTemplate` on a shared topic exchange (`esg-trazabilidad.events`), and marks PROCESSED/FAILED — `@SchedulerLock`-guarded so it never double-runs.
  - **Acceptance criteria:**
    - [ ] `spring-boot-starter-amqp` and `shedlock-spring` added to `shared-kernel/pom.xml`
    - [ ] `OutboxDispatcher`: `@Component`, `@ConditionalOnProperty`-gated, `@Scheduled` + `@SchedulerLock`
    - [ ] Publishes via `RabbitTemplate`, marks PROCESSED on success, marks FAILED (not rethrown) on publish exception
    - [ ] Shared topic exchange declared as a `@Bean` in shared-kernel (both services get it automatically)
  - **Verification:**
    - [ ] Unit tests (Mockito): dispatches all pending rows in order; marks PROCESSED after a successful send; marks FAILED (not rethrown) on a simulated publish exception; no-op when there are no pending rows
    - [ ] `mvn -pl shared-kernel test` green
  - **Dependencies:** Task 25
  - **Files likely touched:** `shared-kernel/pom.xml`, `.../kernel/amqp/OutboxDispatcher.java`, `.../kernel/amqp/RabbitTopologyConfig.java` (exchange bean), plus tests
  - **Estimated scope:** Medium (4-5 files)

### Checkpoint 14: Shared event infra ready
- [ ] `mvn -pl shared-kernel test` green
- [ ] `mvn install` — whole reactor still builds, `recycler-service`/`collection-service` unaffected (nothing references the new code yet)
- [ ] Human review before wiring either direction's business logic

## Phase 15: Direction A (collection-service → recycler-service, kilos total)

- [ ] Task 27: collection-service outbox + shedlock schema
  - **Description:** `collection-service`'s own outbox table (Liquibase `v0.1.4`) and `shedlock` table (`v0.1.7`), plus the JPA entity/repo/adapter implementing shared-kernel's `OutboxRepository`, plus a `LockProvider` bean wired to `collection-service`'s own `DataSource`.
  - **Acceptance criteria:**
    - [ ] `v0.1.4_create_outbox_event_table.yaml`, `v0.1.7_create_shedlock_table.yaml` (ShedLock's standard DDL: `name` PK, `lock_until`, `locked_at`, `locked_by`)
    - [ ] `OutboxEventEntity`/`OutboxEventJpaRepository`/`OutboxEventRepositoryAdapter` implementing `OutboxRepository`
    - [ ] `shedlock-provider-jdbc-template` added to `collection-service/pom.xml`; `LockProvider` bean
  - **Verification:**
    - [ ] IT test proving a saved `OutboxEventEntity` round-trips
    - [ ] `mvn -pl collection-service verify` green
  - **Dependencies:** Task 26
  - **Files likely touched:** `collection-service/src/main/resources/db/changelog/changes/v0.1.4_*.yaml`, `v0.1.7_*.yaml`, `collection-service/pom.xml`, `.../events/outbox/{OutboxEventEntity,OutboxEventJpaRepository,OutboxEventRepositoryAdapter}.java`, plus IT test
  - **Estimated scope:** Medium (6 files)

- [ ] Task 28: `CollectionRegisteredEvent` + publisher
  - **Description:** `collection-service` defines `CollectionRegisteredEvent` (record implementing `DomainEvent`) and `CollectionRegisteredEventPublisher`; `CollectionRecordService.create()` writes the outbox row in the *same* transaction as `repository.save(record)`.
  - **Acceptance criteria:**
    - [ ] `CollectionRegisteredEvent` record: `recordId, neighborId, associationId, collectionDate, weightKg` (+ `eventId`, `occurredAt`, `routingKey() = "collection.record.registered"`)
    - [ ] `CollectionRecordService.create()` writes the outbox row in the same `@Transactional` method — no `@TransactionalEventListener(AFTER_COMMIT)`
  - **Verification:**
    - [ ] Unit test (Mockito): `create()` calls the outbox save with the right payload
    - [ ] IT test (Postgres-only, no Rabbit yet): after `POST .../collection-records`, an outbox row exists with `status = NEW`
    - [ ] `mvn -pl collection-service verify` green
  - **Dependencies:** Task 27
  - **Files likely touched:** `.../collectionrecord/events/CollectionRegisteredEvent.java`, `.../events/publish/CollectionRegisteredEventPublisher.java`, `CollectionRecordService.java`, plus tests
  - **Estimated scope:** Medium (4 files)

- [ ] Task 29: recycler-service event-infrastructure schema
  - **Description:** `recycler-service`'s own outbox table, the `collection_registered_ledger` idempotency table, the `shedlock` table, and the `association`/`certification` alter (total_kilos_collected, notified_expired_at) — plus the adapters and the atomic `incrementTotalKilos` query.
  - **Acceptance criteria:**
    - [ ] `v0.1.3_create_outbox_event_table.yaml`, `v0.1.4_create_collection_registered_ledger_table.yaml` (PK `event_id`), `v0.1.5_alter_association_add_total_kilos_and_certification_notified_at.yaml` (two `changeSet`s: `association.total_kilos_collected DECIMAL NOT NULL DEFAULT 0`, `certification.notified_expired_at` nullable timestamp), `v0.1.6_create_shedlock_table.yaml`
    - [ ] `OutboxEventEntity`/adapter (mirrors Task 27's shape); `CollectionRegisteredLedgerEntity`/repo
    - [ ] `shedlock-provider-jdbc-template` added to `recycler-service/pom.xml`; `LockProvider` bean
    - [ ] `AssociationJpaRepository.incrementTotalKilos(UUID, BigDecimal)` — `@Modifying @Query("UPDATE ... SET total_kilos_collected = total_kilos_collected + :amount ...")`, never a load-mutate-save
  - **Verification:**
    - [ ] IT tests: ledger table round-trip, outbox adapter round-trip
    - [ ] `mvn -pl recycler-service verify` green
    - [ ] Sanity check: `ls recycler-service/src/main/resources/db/changelog/changes/` shows `v0.1.3`-`v0.1.6` with no gap or collision against the existing `v0.1.0`-`v0.1.2`
  - **Dependencies:** Task 26
  - **Files likely touched:** 4 new changelog files, `.../events/outbox/{...}.java`, `.../events/ledger/{CollectionRegisteredLedgerEntity,...}.java`, `AssociationJpaRepository.java`, `recycler-service/pom.xml`, plus IT tests
  - **Estimated scope:** Large (8-9 files)

- [ ] Task 30: `CollectionRegisteredEventListener` (recycler-service)
  - **Description:** `@RabbitListener` consuming `CollectionRegisteredEvent` (a local, structurally-matching record — not imported from `collection-service`), idempotent via a PK-on-`event_id` ledger insert, then an atomic `incrementTotalKilos` call.
  - **Acceptance criteria:**
    - [ ] Local `CollectionRegisteredEvent` record matching the producer's JSON shape
    - [ ] `@RabbitListener(queues = "collection.registered.recycler-service")`, durable queue bound to routing key `collection.record.registered`
    - [ ] Handler: ledger insert inside `try/catch DataIntegrityViolationException` → duplicate short-circuits (return, no error) → on success, call `incrementTotalKilos`
  - **Verification:**
    - [ ] Unit tests (Mockito): happy path (ledger insert then increment, in that order); duplicate delivery short-circuits before the increment call
    - [ ] `mvn -pl recycler-service test` green
  - **Dependencies:** Task 29
  - **Files likely touched:** `.../events/consume/CollectionRegisteredEventListener.java`, plus test
  - **Estimated scope:** Small-Medium (2 files)

### Checkpoint 15: Direction A wired (unit-level)
- [ ] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [ ] Human review before the end-to-end IT proves it over real RabbitMQ

- [ ] Task 31: Direction A end-to-end IT
  - **Description:** Add a RabbitMQ Testcontainer to both services' IT setups; prove the full flow, redelivery/idempotency, and concurrent-increment atomicity over a real broker and real Postgres.
  - **Acceptance criteria:**
    - [ ] `rabbitmq:3.13-management-alpine` Testcontainer added alongside the existing Postgres one, both services
    - [ ] Full-flow IT: `POST` a `CollectionRecord` → message lands on the real queue → `Association.totalKilosCollected` increments by `weightKg`
    - [ ] Redelivery/idempotency IT: publish the same event twice → `totalKilosCollected` changes only once
    - [ ] Concurrent-increment IT (real `ExecutorService` threads, real Postgres): two concurrent events for the same association → final total is the exact sum of both
  - **Verification:**
    - [ ] `mvn -pl recycler-service verify` and `mvn -pl collection-service verify` green
    - [ ] Concurrent-increment test is confirmed to actually exercise concurrency (not two sequential calls that happen not to race)
  - **Dependencies:** Task 30
  - **Files likely touched:** `.../it/CollectionRegisteredEventFlowIT.java` (or similar, both services), Testcontainers config updates
  - **Estimated scope:** Large (3-4 files, high test complexity)

### Checkpoint 16: Direction A complete and proven end-to-end
- [ ] All of Direction A's Success Criteria bullets in `SPEC-cross-service-events.md` verified with real evidence
- [ ] Human review before starting Direction B

## Phase 16: Direction B (recycler-service → collection-service, blocking)

- [ ] Task 32: `Certification.notifiedExpiredAt` + `renew()` reset
  - **Description:** Add the nullable `notifiedExpiredAt` field to `Certification`, and modify `renew()` to reset it to `null` in the same call as its existing expiration-date validation — closes the user-caught gap where a certification renewed and later re-expired would never be re-notified.
  - **Acceptance criteria:**
    - [ ] `Certification` gains `notifiedExpiredAt` (nullable `Instant`), settable only via a package-visible method used by the scan job
    - [ ] `renew()` resets it to `null` in the same call as its existing date-range validation
    - [ ] `CertificationEntity` gains the column; extends the existing `existing()`/`update()` path from Task 12, doesn't rebuild it
  - **Verification:**
    - [ ] Unit tests: `renew()` resets `notifiedExpiredAt` to `null`; a fresh `Certification.create()` starts with it `null`
    - [ ] `mvn -pl recycler-service test` green
  - **Dependencies:** Task 29 (schema)
  - **Files likely touched:** `Certification.java`, `CertificationEntity.java`, `CertificationRepositoryAdapter.java`, plus tests
  - **Estimated scope:** Small-Medium (4 files)

- [ ] Task 33: `CertificationExpiryScanJob` + `CertificationExpiredEventPublisher`
  - **Description:** New `@Scheduled` + `@SchedulerLock` job that finds certifications where `isExpired()` is true and `notifiedExpiredAt IS NULL`, publishes `CertificationExpiredEvent` per one found (outbox write), and sets `notifiedExpiredAt` — all in one transaction per certification.
  - **Acceptance criteria:**
    - [ ] `CertificationExpiredEvent` record: `associationId, certificationId, expiredAt`
    - [ ] `CertificationExpiryScanJob`: distinct `@SchedulerLock` name from `OutboxDispatcher`'s
    - [ ] Finds only expired + not-yet-notified certifications; publishes + sets the flag atomically per certification
  - **Verification:**
    - [ ] Unit tests (Mockito): finds only the right certifications; sets the flag; doesn't touch an already-notified expired certification; doesn't touch a non-expired one
    - [ ] `mvn -pl recycler-service test` green
  - **Dependencies:** Task 32
  - **Files likely touched:** `.../certification/job/CertificationExpiryScanJob.java`, `.../certification/events/CertificationExpiredEvent.java`, `.../events/publish/CertificationExpiredEventPublisher.java`, plus tests
  - **Estimated scope:** Medium (4 files)

- [ ] Task 34: `CertificationRenewedEventPublisher`
  - **Description:** `recycler-service` defines `CertificationRenewedEvent`; hook its outbox write into `CertificationService.renew()`'s existing transaction (same call as `certificationRepository.update(certification)`).
  - **Acceptance criteria:**
    - [ ] `CertificationRenewedEvent` record: `associationId, certificationId, newExpirationDate`
    - [ ] `CertificationService.renew()` writes the outbox row in the same transaction as its existing `update()` call
  - **Verification:**
    - [ ] Unit test (Mockito): `renew()` also writes the outbox row with the right payload
    - [ ] `mvn -pl recycler-service test` green
  - **Dependencies:** Task 32
  - **Files likely touched:** `.../certification/events/CertificationRenewedEvent.java`, `.../events/publish/CertificationRenewedEventPublisher.java`, `CertificationService.java`, plus test
  - **Estimated scope:** Small-Medium (3 files)

### Checkpoint 17: recycler-service publishing side complete
- [ ] `mvn -pl recycler-service verify` green
- [ ] Human review before wiring collection-service's consumption side

- [ ] Task 35: collection-service `certification_status_ledger` + `blocked_association` schema/domain
  - **Description:** The idempotency ledger for both incoming certification-status event types, and the minimal `BlockedAssociation` projection (association id + block state only — never a copy of `recycler-service`'s full `Association`).
  - **Acceptance criteria:**
    - [ ] `v0.1.5_create_certification_status_ledger_table.yaml` (PK `event_id`), `v0.1.6_create_blocked_association_table.yaml` (PK `association_id`, `blocked_at` timestamp only — no name/RUC/contact fields)
    - [ ] `BlockedAssociation` domain class + `BlockedAssociationEntity`/repo/adapter, `BlockedAssociationRepository` port
  - **Verification:**
    - [ ] IT test round-tripping both tables
    - [ ] `mvn -pl collection-service verify` green
  - **Dependencies:** Task 27
  - **Files likely touched:** `collection-service/src/main/resources/db/changelog/changes/v0.1.5_*.yaml`, `v0.1.6_*.yaml`, `.../association/domain/BlockedAssociation.java`, `.../association/adapter/out/persistence/{...}.java`, `.../association/port/out/BlockedAssociationRepository.java`, plus IT test
  - **Estimated scope:** Medium (6 files)

- [ ] Task 36: `CertificationStatusEventListener`
  - **Description:** One `@RabbitListener` handling both `CertificationExpiredEvent` and `CertificationRenewedEvent` on a single durable queue bound to both routing keys; idempotent ledger insert, then blocks or unblocks.
  - **Acceptance criteria:**
    - [ ] Local `CertificationExpiredEvent`/`CertificationRenewedEvent` records (structurally matching `recycler-service`'s)
    - [ ] Queue bound to `certification.expired` and `certification.renewed`
    - [ ] Ledger insert (PK `event_id`, duplicate short-circuits) → `Expired` upserts a `BlockedAssociation` row; `Renewed` removes it
  - **Verification:**
    - [ ] Unit tests (Mockito): expired event blocks; renewed event unblocks; duplicate of either short-circuits before the block-state change
    - [ ] `mvn -pl collection-service test` green
  - **Dependencies:** Task 35
  - **Files likely touched:** `.../events/consume/CertificationStatusEventListener.java`, event record classes, plus tests
  - **Estimated scope:** Medium (4 files)

- [ ] Task 37: Enforce the block in `CollectionRecordService.create()`
  - **Description:** New `CollectionErrors.COL-009 ASSOCIATION_BLOCKED` (409); `create()` checks `BlockedAssociationRepository` before saving.
  - **Acceptance criteria:**
    - [ ] `CollectionErrors.COL-009 ASSOCIATION_BLOCKED` added
    - [ ] `CollectionRecordService.create()` throws `COL-009` if a `BlockedAssociation` row exists for `command.associationId()`
    - [ ] Exception handler wiring confirmed (extend `CollectionRecordExceptionHandler`'s mapping, or confirm the existing generic `ApplicationException` path already covers it with no new branching)
  - **Verification:**
    - [ ] Unit test: blocked associationId → `COL-009`
    - [ ] IT test (Postgres-only, no Rabbit): create succeeds when unblocked, 409 `COL-009` when blocked
    - [ ] `mvn -pl collection-service verify` green
  - **Dependencies:** Task 36
  - **Files likely touched:** `CollectionErrors.java`, `CollectionRecordService.java`, `CollectionRecordExceptionHandler.java` (if needed), plus tests
  - **Estimated scope:** Small-Medium (4 files)

### Checkpoint 18: Direction B wired (unit-level)
- [ ] `mvn -pl recycler-service test` and `mvn -pl collection-service test` green
- [ ] Human review before the end-to-end IT

- [ ] Task 38: Direction B end-to-end IT
  - **Description:** Full-flow IT over the real broker: expire → scan → block → reject → renew → unblock → accept. Plus redelivery/idempotency, plus the exact expire→renew→re-expire→re-notify cycle the user caught as missing from the initial design.
  - **Acceptance criteria:**
    - [ ] Full-flow IT: expired certification → run `CertificationExpiryScanJob` → `CertificationExpiredEvent` published → association blocked in `collection-service` → `POST .../collection-records` → 409 `COL-009` → `PATCH .../certifications/{id}/renew` → `CertificationRenewedEvent` published → unblocked → `POST .../collection-records` succeeds
    - [ ] Redelivery/idempotency IT: same expired/renewed event published twice → block state only toggles once
    - [ ] **Full expire→renew→re-expire cycle IT:** expire → scan → notified+blocked → renew → `notifiedExpiredAt` null + unblocked → push expiration into the past again → scan a second time → a *second*, distinct `CertificationExpiredEvent` published, association blocked again
  - **Verification:**
    - [ ] `mvn -pl recycler-service verify` and `mvn -pl collection-service verify` green
    - [ ] The re-expire cycle test is confirmed to actually fail without Task 32's `renew()` reset (sanity-checked, not just trusted)
  - **Dependencies:** Task 37
  - **Files likely touched:** `.../it/CertificationStatusEventFlowIT.java` (or similar, both services)
  - **Estimated scope:** Large (2-3 files, high test complexity)

### Checkpoint 19: Direction B complete and proven end-to-end
- [ ] All of Direction B's Success Criteria bullets in `SPEC-cross-service-events.md` verified with real evidence
- [ ] Human review before the final ordering test + reactor-wide checkpoint

## Phase 17: Ordering test + final verification

- [ ] Task 39: Ordering test
  - **Description:** Publish `CertificationExpiredEvent` then `CertificationRenewedEvent` for the same association in quick succession *through the outbox* (not directly to the queue) — proves the dispatcher's strict insertion-order processing holds for the common case, while documenting (not hiding) the residual out-of-order-after-redelivery risk.
  - **Acceptance criteria:**
    - [ ] Test asserts final state is unblocked after both events flow through the outbox in order
    - [ ] A test comment documents that true out-of-order delivery after broker-level redelivery remains an accepted, undismissed residual risk (matches the spec's own wording)
  - **Verification:**
    - [ ] `mvn -pl recycler-service verify` and `mvn -pl collection-service verify` green
  - **Dependencies:** Tasks 31, 38
  - **Files likely touched:** IT test file(s) from Task 38, or a new dedicated ordering test
  - **Estimated scope:** Small (1-2 files)

### Checkpoint 20: Final — ready for review
- [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`), RabbitMQ Testcontainer included
- [ ] All 11 Success Criteria bullets in `SPEC-cross-service-events.md` re-verified line by line with evidence
- [ ] `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected — no destructive schema change, no existing endpoint contract changed
- [ ] Human review and approval before moving to `reporting-service`
