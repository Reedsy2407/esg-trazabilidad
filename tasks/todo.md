# Task List: shared-kernel + recycler-service (core)

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`.

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

- [ ] Task 11: springdoc-openapi wiring
  - **Description:** Confirm Swagger UI renders all three controllers with accurate request/response schemas, including validation constraints.
  - **Acceptance criteria:**
    - [ ] Swagger UI reachable locally (default `/swagger-ui.html`) and lists `Association`, `Recycler`, `Certification` endpoints
    - [ ] Request/response schemas reflect validation annotations (e.g. RUC/DNI patterns show in the schema)
  - **Verification:**
    - [ ] Manual check: open Swagger UI in a browser against `docker compose up` + running app, exercise one endpoint from the UI
  - **Dependencies:** Task 6, Task 8, Task 10
  - **Files likely touched:** `application.yml` (springdoc config if any customization needed), possibly `@Tag`/`@Schema` annotations on existing controllers/DTOs
  - **Estimated scope:** Small (1-2 files)

### Checkpoint 6: Final — ready for review
- [ ] All Success Criteria in `SPEC-shared-kernel.md` and `SPEC-recycler-service.md` are met
- [ ] Definition of Done (Correctness + Quality sections) satisfied for every task above
- [ ] Human review and approval before moving to `collection-service` or `cross-service-events`
