# LEARNINGS - esg-trazabilidad

> Historial detallado de tasks ya cerradas y revisadas: el que se encontro, desviaciones del plan, razonamiento de decisiones. Se archiva aqui para que tasks/todo.md se mantenga liviano (es el que se relee cada sesion/task). No releer este archivo completo - buscar por numero de task (grep "## Task N:") o por tema.

---

## Task 1: Monorepo + shared-kernel scaffolding

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

---

## Task 2: Error handling (`ApplicationError`, `ApplicationException`, `GlobalExceptionHandler`)

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

---

## Task 3: Event strategy + ID generation (`EventPublishingStrategy`, `IdGenerator`)

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

---

## Task 4: recycler-service scaffolding

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

---

## Task 5: Association persistence

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

---

## Task 6: Association API

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

---

## Task 7: Recycler persistence

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

---

## Task 8: Recycler API

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

---

## Task 9: Certification persistence

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

---

## Task 10: Certification API

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

---

## Task 11: springdoc-openapi wiring

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

---

## Task 12: Status-change and renewal endpoints

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

---

## Task 13: collection-service scaffolding

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

---

## Task 14: Neighbor persistence

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

---

## Task 15: Neighbor API

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

---

## Task 16: Company persistence

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

---

## Task 17: Company API

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

---

## Task 18: CollectionSchedule persistence

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

---

## Task 19: CollectionSchedule API (CRUD)

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

---

## Task 20: CollectionSchedule lifecycle

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

---

## Task 21: CollectionRecord persistence

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

---

## Task 22: CollectionRecord API

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

---

## Task 23: springdoc-openapi wiring

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

---

## Task 24: RabbitMQ + ShedLock infra wiring

- [x] Task 24: RabbitMQ + ShedLock infra wiring
  - **Description:** Add `rabbitmq:3.13-management-alpine` to the root `docker-compose.yml`, pin `shedlock-spring`/`shedlock-provider-jdbc-template` versions in the root pom's `dependencyManagement` (not in the Spring Boot BOM, same treatment as `springdoc-openapi`), and wire `spring.rabbitmq.*` into both services' `application.yml` — env-var-driven, no hardcoded secret default.
  - **Acceptance criteria:**
    - [x] `docker-compose.yml` gains a `rabbitmq` service (ports 5672 + 15672, credentials via env vars with `:?must be set`, matching the existing Postgres pattern)
    - [x] Root `pom.xml` `dependencyManagement` pins `shedlock-spring` and `shedlock-provider-jdbc-template` — pinned to 7.10.1, verified as the current release via Maven Central's own `maven-metadata.xml` (`<release>`/`<latest>` both `7.10.1`) rather than trusting a web-search summary that returned conflicting numbers
    - [x] Both `application.yml`s gain `spring.rabbitmq.host/port/username/password`, no hardcoded default
    - [x] `docker compose --env-file .env.local up -d` starts Postgres *and* RabbitMQ; management UI reachable at `localhost:15672`
  - **Verification:**
    - [x] Infra-only — no RED/GREEN ceremony per `[[tdd_scope_for_config_fixes]]`
    - [x] Manual check: both services boot with no AMQP connection errors in logs — `recycler-service` (PID confirmed via `tasklist`) and `collection-service` each reached `Started ...Application` cleanly; `spring.rabbitmq.*` properties sit unused since `spring-boot-starter-amqp` isn't on either classpath yet (that's Task 26), so no autoconfiguration was even attempted — expected at this stage
    - [x] `mvn install` — whole reactor builds clean with the new `pom.xml` `dependencyManagement` entries
  - **Dependencies:** None
  - **Files likely touched:** `docker-compose.yml`, `pom.xml`, `recycler-service/src/main/resources/application.yml`, `collection-service/src/main/resources/application.yml`, `.env.local` (gitignored — added `RABBITMQ_PASSWORD`)
  - **Estimated scope:** Small (4 tracked files)

---

## Task 25: shared-kernel event core

- [x] Task 25: shared-kernel event core
  - **Description:** Rewrite `EventPublishingStrategy` to `{RABBITMQ, MOCK}` with Javadoc explaining the removal of `GCP_PUB_SUB`/`SPRING_EVENTS`; add `DomainEvent` marker interface, `OutboxEntry` plain-record shape, and the small `OutboxRepository` port interface each service's own outbox adapter will implement.
  - **Acceptance criteria:**
    - [x] `EventPublishingStrategy` has exactly `RABBITMQ`, `MOCK`, with Javadoc documenting why `GCP_PUB_SUB`/`SPRING_EVENTS` were removed
    - [x] `DomainEvent` interface: `UUID eventId()`, `Instant occurredAt()`, `String routingKey()`
    - [x] `OutboxEntry` record: `{id, eventType, routingKey, payloadJson, status, createdAt}` — not a JPA `@Entity`; also added `OutboxStatus` enum (`NEW`/`PROCESSED`/`FAILED`) and a `create(...)` factory generating the id (UUID v7, via `IdGenerator`) and `createdAt`, matching every other domain object's `create()` pattern in this codebase
    - [x] `OutboxRepository` port: `save`, `findPendingBatch`, `markProcessed`, `markFailed`
  - **Verification:**
    - [x] RED→GREEN: `EventPublishingStrategyTest` updated first and confirmed failing (`RABBITMQ cannot be resolved`) against the old enum, then the enum rewritten to pass; `OutboxStatusTest`/`OutboxEntryTest` written first and confirmed failing to compile before `OutboxStatus`/`OutboxEntry` were created
    - [x] Unit tests pass: `mvn -pl shared-kernel test` — 13 tests, 0 failures
    - [x] `mvn install` — whole reactor still builds clean, confirming `EventPublishingStrategy`'s rewrite is still inert outside shared-kernel (no other module referenced the old values)
  - **Dependencies:** Task 24
  - **Files likely touched:** `shared-kernel/src/main/java/.../events/EventPublishingStrategy.java`, `.../events/DomainEvent.java`, `.../events/OutboxEntry.java`, `.../events/OutboxStatus.java`, `.../events/OutboxRepository.java` (port), plus tests
  - **Estimated scope:** Small-Medium (4-5 files)

---

## Task 26: shared-kernel `OutboxDispatcher`

- [x] Task 26: shared-kernel `OutboxDispatcher`
  - **Description:** Generic, `@ConditionalOnProperty`-gated scheduled component that reads pending rows via the single `OutboxRepository` bean present in whichever service's context it runs in, publishes each to RabbitMQ via `RabbitTemplate` on a shared topic exchange (`esg-trazabilidad.events`), and marks PROCESSED/FAILED — `@SchedulerLock`-guarded so it never double-runs.
  - **Acceptance criteria:**
    - [x] `spring-boot-starter-amqp` and `shedlock-spring` added to `shared-kernel/pom.xml`
    - [x] `OutboxDispatcher`: `@Component`, `@ConditionalOnProperty`-gated, `@Scheduled` + `@SchedulerLock` — also gated by `@ConditionalOnBean(OutboxRepository.class)` (a judgment call beyond the plan's literal text, needed so the bean isn't even attempted before a service wires its own outbox adapter in Task 27/29 — otherwise constructor injection of `OutboxRepository` would fail bean creation in both currently-running services)
    - [x] Publishes via `RabbitTemplate`, marks PROCESSED on success, marks FAILED (not rethrown) on publish exception
    - [x] Shared topic exchange declared as a `@Bean` in shared-kernel (both services get it automatically) — `RabbitTopologyConfig`, durable `esg-trazabilidad.events` topic exchange
    - [x] Both classes registered via shared-kernel's `AutoConfiguration.imports` (same mechanism `GlobalExceptionHandler` already uses — confirmed by reading it first, not assumed)
  - **Verification:**
    - [x] RED→GREEN: `OutboxDispatcherTest` written first against the not-yet-existing class, confirmed failing to compile, then `OutboxDispatcher`/`RabbitTopologyConfig` implemented to pass
    - [x] Unit tests (Mockito): dispatches all pending rows in order; marks PROCESSED after a successful send; marks FAILED (not rethrown) on a simulated publish exception; **one entry failing doesn't stop the rest of the batch** (added beyond the plan's literal scope — a real behavioral question a naive implementation could get wrong); no-op when there are no pending rows
    - [x] `mvn -pl shared-kernel test` green — 17 tests total
    - [x] `mvn install` — whole reactor still builds clean
    - [x] Manual check: both `recycler-service` and `collection-service` boot cleanly with the new AMQP/ShedLock dependencies transitively present and no `OutboxRepository` bean yet — confirms `@ConditionalOnBean` correctly keeps the dispatcher inert, no bean-creation failure, no AMQP connection errors
  - **Dependencies:** Task 25
  - **Files likely touched:** `shared-kernel/pom.xml`, `.../kernel/amqp/OutboxDispatcher.java`, `.../kernel/amqp/RabbitTopologyConfig.java` (exchange bean), `AutoConfiguration.imports`, plus tests
  - **Estimated scope:** Medium (4-5 files)

---

## Task 27: collection-service outbox + shedlock schema

- [x] Task 27: collection-service outbox + shedlock schema
  - **Description:** `collection-service`'s own outbox table (Liquibase `v0.1.4`) and its own distinctly-named `shedlock_collection` table (`v0.1.5`) — never a table literally named `shedlock`, which would collide with `recycler-service`'s own ShedLock table on the shared Postgres database (user-caught risk, resolved in `SPEC-cross-service-events.md`'s Resolved Decisions) — plus the JPA entity/repo/adapter implementing shared-kernel's `OutboxRepository`, plus a `LockProvider` bean wired to `collection-service`'s own `DataSource`.
  - **Acceptance criteria:**
    - [x] `v0.1.4_create_outbox_event_table.yaml` (with a `status`+`created_at` index for the dispatcher's poll), `v0.1.5_create_shedlock_collection_table.yaml` — table named `shedlock_collection`, not `shedlock` (ShedLock's standard DDL: `name` PK, `lock_until`, `locked_at`, `locked_by`)
    - [x] **Retroactive correction from Task 29:** the outbox table this changeset creates is actually named `outbox_event_collection`, not `outbox_event` as originally shipped here — `recycler-service` (Task 29) failed to boot live against the shared Postgres with `relation "outbox_event" already exists`, the exact same collision class as `shedlock` but missed when this spec/task was written. `v0.1.4_create_outbox_event_table.yaml` (and its index) rewritten in place under the same zero-real-data exception already used for `v0.1.0_create_neighbor_table.yaml`; see Task 29's own entry for the full fix
    - [x] `OutboxEventEntity`/`OutboxEventJpaRepository`/`OutboxEventRepositoryAdapter` implementing `OutboxRepository` — no `existing()`/`update()` path: `markProcessed`/`markFailed` are atomic `@Modifying` status-flip `UPDATE`s, not domain-validated mutations, so there's nothing to merge through the entity
    - [x] `shedlock-provider-jdbc-template` added to `collection-service/pom.xml`; `LockProvider` bean built via `JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(...).withTableName("shedlock_collection").build()`, in a new `SchedulingConfig` that also adds `@EnableScheduling`/`@EnableSchedulerLock` — needed for `OutboxDispatcher` to actually activate now that an `OutboxRepository` bean exists, not just compile
  - **Verification:**
    - [x] RED→GREEN: IT test written first against the not-yet-existing adapter, confirmed failing (`No qualifying bean of type OutboxRepository`), then entity/repo/adapter implemented to pass
    - [x] Real gap found via RED, not assumed: `@Modifying` query methods aren't transactional just by being annotated (`TransactionRequiredException`) — fixed with `@Transactional` on `OutboxEventJpaRepository.updateStatus`
    - [x] IT test proving a saved `OutboxEventEntity` round-trips, `markProcessed` removes it from the pending batch, `markFailed` keeps it pending for retry (proving the retry semantic `OutboxDispatcher`'s own Task-26 Javadoc documents)
    - [x] `mvn -pl collection-service verify` green — 36 tests (was 33)
    - [x] Second gap found and fixed, beyond the plan's literal scope: the now-live `OutboxDispatcher`'s 5s tick raced every other `@SpringBootTest` IT's own Testcontainers teardown, logging a noisy connection-refused stack trace (harmless to the build result, but real test-isolation noise). Disabled globally for the `failsafe` test JVM via `systemPropertyVariables` in `collection-service/pom.xml`, rather than patching each of the 5 existing IT test classes
    - [x] Manual live-boot check: `outboxDispatcher` lock row confirmed present in the real `shedlock_collection` table (`lock_until`/`locked_at`/`locked_by` all populated) after letting the service run past one 5s tick — proves the whole chain (schema → adapter → `LockProvider` → `@EnableScheduling` → `@SchedulerLock` → `OutboxDispatcher`) genuinely works end-to-end, not just compiles
    - [ ] Booting `collection-service` alongside `recycler-service`'s own `shedlock_recycler` table — deferred to Task 29, since `recycler-service` doesn't have that table yet
    - [x] **User-caught, addressed before closing the task:** `findPending`'s `ORDER BY e.createdAt ASC` alone left ties ambiguous (two rows written in the same instant) — added `e.id ASC` as a tiebreaker (`id` is UUID v7, time-ordered at finer precision than `Instant`), which is exactly what Task 39's ordering test will rely on. Proven with a new IT test (`tiedCreatedAtOrdersByIdAsATiebreaker`, RED confirmed first: failed deterministically in insertion order without the tiebreaker). **Apply the same `ORDER BY ..., e.id ASC` in `recycler-service`'s own `OutboxEventJpaRepository` in Task 29.**
    - [x] Proving the tiebreaker surfaced a second, deeper bug: a bulk `@Modifying` `UPDATE` bypasses Hibernate's first-level cache, so a `findPending()` sharing a transaction with an earlier `updateStatus()` returned a stale cached entity — a real risk beyond this test, since the whole Outbox pattern is built around a publisher's `save()` and a later read/update sharing one transaction. Fixed with `@Modifying(clearAutomatically = true)`. **Apply the same `clearAutomatically = true` to `recycler-service`'s own outbox `updateStatus` query in Task 29, and to `AssociationJpaRepository.incrementTotalKilos` too — it's the same bulk-`@Modifying`-query shape with the same latent staleness risk.**
  - **Dependencies:** Task 26
  - **Files likely touched:** `collection-service/src/main/resources/db/changelog/changes/v0.1.4_*.yaml`, `v0.1.5_*.yaml`, `collection-service/pom.xml`, `.../events/outbox/{OutboxEventEntity,OutboxEventJpaRepository,OutboxEventRepositoryAdapter}.java`, `.../config/SchedulingConfig.java`, plus IT test
  - **Estimated scope:** Medium (6 files)

---

## Task 28: `CollectionRegisteredEvent` + publisher

- [x] Task 28: `CollectionRegisteredEvent` + publisher
  - **Description:** `collection-service` defines `CollectionRegisteredEvent` (record implementing `DomainEvent`) and `CollectionRegisteredEventPublisher`; `CollectionRecordService.create()` writes the outbox row in the *same* transaction as `repository.save(record)`.
  - **Acceptance criteria:**
    - [x] `CollectionRegisteredEvent` record: `recordId, neighborId, associationId, collectionDate, weightKg` (+ `eventId`, `occurredAt`, `routingKey() = "collection.record.registered"`)
    - [x] `CollectionRecordService.create()` writes the outbox row in the same `@Transactional` method — no `@TransactionalEventListener(AFTER_COMMIT)`. Design choice beyond the plan's literal text: the publisher reuses the event's own `eventId()`/`occurredAt()` as the `OutboxEntry`'s `id`/`createdAt`, rather than minting a second, unrelated UUID via `OutboxEntry.create(...)` — one canonical identity for the event
  - **Verification:**
    - [x] RED→GREEN throughout: `CollectionRegisteredEventTest` (factory mapping), `CollectionRegisteredEventPublisherTest` (Mockito, asserts the `OutboxEntry` written matches the event exactly), `CollectionRecordServiceTest`'s new test (publisher invoked with the right event) — all written first against not-yet-existing classes, confirmed failing to compile, then implemented
    - [x] IT test (Postgres-only, no Rabbit yet): after `POST .../collection-records`, a pending outbox row exists with the right `routingKey`/`status = NEW` and the created record's id in its payload
    - [x] `mvn -pl collection-service verify` green — 38 IT tests (was 37), 68 unit tests
    - [x] **User-asked, verified with real evidence before Task 30:** does `routingKey()` (a plain override method, not a record component) leak into the serialized JSON as an 8th field, which would break Task 30's consumer under `FAIL_ON_UNKNOWN_PROPERTIES`? Parsed the actual JSON the real Spring-wired `ObjectMapper` produced (via `CollectionRecordApiIT`) and asserted its exact key set — confirmed **no**, Jackson 2.x serializes a record by its canonical components only. No `@JsonIgnore` needed. Documented on `CollectionRegisteredEvent` itself so `CertificationExpiredEvent`/`CertificationRenewedEvent` (Task 33/34) don't need to re-verify this.
  - **Dependencies:** Task 27
  - **Files likely touched:** `.../collectionrecord/events/CollectionRegisteredEvent.java`, `.../events/publish/CollectionRegisteredEventPublisher.java`, `CollectionRecordService.java`, plus tests
  - **Estimated scope:** Medium (4 files)

---

## Task 29: recycler-service event-infrastructure schema

- [x] Task 29: recycler-service event-infrastructure schema
  - **Description:** `recycler-service`'s own outbox table, the `collection_registered_ledger` idempotency table, its own distinctly-named `shedlock_recycler` table — never a table literally named `shedlock` (user-caught collision risk, resolved in `SPEC-cross-service-events.md`'s Resolved Decisions) — and the `association`/`certification` alter (total_kilos_collected, notified_expired_at) — plus the adapters and the atomic `incrementTotalKilos` query.
  - **Acceptance criteria:**
    - [x] `v0.1.3_create_outbox_event_table.yaml`, `v0.1.4_create_collection_registered_ledger_table.yaml` (PK `event_id`), `v0.1.5_alter_association_add_total_kilos_and_certification_notified_at.yaml` (two `changeSet`s: `association.total_kilos_collected DECIMAL(14,2) NOT NULL DEFAULT 0`, `certification.notified_expired_at` nullable timestamp), `v0.1.6_create_shedlock_recycler_table.yaml` — table named `shedlock_recycler`, not `shedlock`
    - [x] `OutboxEventEntity`/adapter (mirrors Task 27's shape, **including its `findPending` query's `ORDER BY e.createdAt ASC, e.id ASC` tiebreaker** and `@Modifying(clearAutomatically = true)` — user-caught in Task 27's review, applied identically here from the start, no RED needed to rediscover them); `CollectionRegisteredLedgerEntity`/repo — public (unlike outbox's package-private JPA repo), since Task 30's listener in the sibling `events.consume` package uses it directly rather than through a port (no existing aggregate/port to attach an idempotency-only ledger to, unlike `AssociationRepository` below)
    - [x] `shedlock-provider-jdbc-template` added to `recycler-service/pom.xml`; `LockProvider` bean built via `JdbcTemplateLockProvider.Configuration.builder().withJdbcTemplate(...).withTableName("shedlock_recycler").build()`, in a new `SchedulingConfig` (mirrors Task 27's) that also adds `@EnableScheduling`/`@EnableSchedulerLock`
    - [x] `AssociationJpaRepository.incrementTotalKilos(UUID, BigDecimal)` — `@Modifying(clearAutomatically = true) @Transactional @Query("UPDATE ... SET total_kilos_collected = total_kilos_collected + :amount ...")`, never a load-mutate-save. Exposed on the `AssociationRepository` port (not used directly like the ledger) since that port already exists for the Association aggregate — the consistent, established path, unlike the ledger's brand-new idempotency-only concern
    - [x] **Real, non-obvious design decision beyond the plan's literal text:** `AssociationEntity.totalKilosCollected` is mapped `insertable = false, updatable = false`. Without this, the *existing* `AssociationRepositoryAdapter.update()` path (used by `suspend()`/`activate()`, which knows nothing about this field) would silently reset it to null/0 on every unrelated status change, since Hibernate's normal `save()`/merge() always writes every mapped `@Column` from the entity's current Java field value. Proven with a dedicated test (`incrementTotalKilosSurvivesAnUnrelatedUpdateWithoutBeingClobbered`) and confirmed via the actual Hibernate SQL log: the `INSERT` and the `suspend()` `UPDATE` both correctly omit `total_kilos_collected`; only the atomic increment query touches it
  - **Verification:**
    - [x] IT tests: ledger table round-trip + PK-duplicate-violation proof (`CollectionRegisteredLedgerJpaRepositoryIT`), outbox adapter round-trip (4 tests, mirroring Task 27's exactly, all green on first run since the fixes were built in from the start), `findPending`'s tiebreaker, `incrementTotalKilos` atomicity + clobber-safety (2 tests added to `AssociationRepositoryAdapterIT`)
    - [x] `mvn -pl recycler-service verify` green — 40 IT tests (was 32)
    - [x] Sanity check: `ls recycler-service/src/main/resources/db/changelog/changes/` shows `v0.1.3`-`v0.1.6` with no gap against the existing `v0.1.0`-`v0.1.2`
    - [x] **Real collision found live, not hypothetical, while doing Task 27's deferred "boot alongside recycler-service" check:** both services' outbox tables were both literally named `outbox_event`. `recycler-service` failed to boot against the shared Postgres with `relation "outbox_event" already exists` — the exact same failure mode `shedlock` would have hit, missed when the spec was first written because `shedlock`'s collision was caught and fixed *before* any code existed, while this one only became checkable once `recycler-service` actually had its own outbox table (Task 29). Fixed identically to `shedlock`: renamed to `outbox_event_recycler`/`outbox_event_collection`, **and their per-table indexes too** (`ix_outbox_event_recycler_status_created_at`/`ix_outbox_event_collection_status_created_at` — Postgres index names share the table namespace within a schema, so a same-named index on two different tables collides the same way the bare table name did). `collection-service`'s already-shipped `v0.1.4_create_outbox_event_table.yaml` rewritten in place under the same zero-real-data exception already used for `v0.1.0_create_neighbor_table.yaml`; Postgres volume recreated so the rewrite reapplies cleanly; both services' full IT suites re-verified green afterward
    - [x] Manual live-boot check (the deferred Task 27 criterion, now actually possible): both services booted together against the same shared Postgres; `\dt` confirms `outbox_event_recycler`, `outbox_event_collection`, `shedlock_recycler`, `shedlock_collection` all coexist; both independently acquired their own `outboxDispatcher` lock row after a tick
    - [x] `mvn install` — whole reactor still builds clean
  - **Dependencies:** Task 26
  - **Files likely touched:** 4 new changelog files (recycler-service) + 1 rewritten in place (collection-service), `.../events/outbox/{...}.java`, `.../events/ledger/{CollectionRegisteredLedgerEntity,CollectionRegisteredLedgerJpaRepository}.java`, `AssociationEntity.java`/`AssociationJpaRepository.java`/`AssociationRepository.java`/`AssociationRepositoryAdapter.java`, `.../config/SchedulingConfig.java`, `recycler-service/pom.xml`, plus IT tests
  - **Estimated scope:** Large (12+ files, larger than planned due to the outbox-naming fix touching both services)

---

## Task 30: `CollectionRegisteredEventListener` (recycler-service)

- [x] Task 30: `CollectionRegisteredEventListener` (recycler-service)
  - **Description:** `@RabbitListener` consuming `CollectionRegisteredEvent` (a local, structurally-matching record — not imported from `collection-service`), idempotent via a PK-on-`event_id` ledger insert, then an atomic `incrementTotalKilos` call.
  - **Acceptance criteria:**
    - [x] Local `CollectionRegisteredEvent` record matching the producer's JSON shape
    - [x] `@RabbitListener(queues = "collection.registered.recycler-service")`, durable queue bound to routing key `collection.record.registered` — `CollectionRegisteredQueueConfig` declares the queue+binding against shared-kernel's already-declared `TopicExchange` bean (injected, not redeclared by name)
    - [x] **Redesigned from the plan's literal "insert inside try/catch, then increment" shape** — see the real transactional-correctness finding below. `CollectionRegisteredEventProcessor.process()` (a separate `@Transactional` bean, not a private method — self-invocation bypasses Spring's proxy) does the increment *first*, then the ledger insert *last* (flushed explicitly) in one flat transaction. A duplicate-key violation on the flush rolls back the whole transaction, increment included, and rethrows to `CollectionRegisteredEventListener`, which is outside that transaction and catches/swallows `DataIntegrityViolationException` to ack the redelivery without retrying it forever
  - **Verification:**
    - [x] Unit tests (Mockito): listener delegates to the processor; a `DataIntegrityViolationException` from the processor is swallowed without rethrowing
    - [x] `mvn -pl recycler-service test` green
    - [x] **Real transactional-correctness finding, caught by a real-Postgres IT test, not assumed:** the original design (ledger-insert-first, catch `DataIntegrityViolationException` inline, continue to the increment) needs `Propagation.NESTED` (a DB savepoint) to keep the transaction usable after catching a mid-transaction violation — Postgres aborts an *entire* transaction on any statement error, not just the failing one. Tried it; failed with `NestedTransactionNotSupportedException` — Spring's `JpaTransactionManager`/Hibernate's default `JpaDialect` doesn't support savepoints. Redesigned to increment-first/ledger-insert-last instead, which needs no savepoints at all (either the whole method's transaction commits, or none of it does). Proven by `CollectionRegisteredEventListenerIT`: same event handled twice (simulated redelivery) leaves `totalKilosCollected` unchanged after the second call, with a real duplicate-key violation visible in the log
    - [x] **Second finding, also real, not assumed:** once a real `@RabbitListener` exists, *every* `@SpringBootTest` IT in the module (not just this task's own) boots a real listener container that tries to connect to whatever `spring.rabbitmq.*` resolves to — the actual local broker, since Postgres-only IT tests don't override it. Verified concretely: stopped the local RabbitMQ container, confirmed this doesn't hard-fail existing tests (Spring AMQP retries lazily) but logs noisy connection-refused stack traces — not hermetic. Fixed by disabling `spring.rabbitmq.listener.simple.auto-startup` globally for the test JVM (same `systemPropertyVariables` mechanism as the outbox-dispatcher flag from Task 27/29); re-verified with RabbitMQ stopped: 41 IT tests green, zero connection-refused log lines. **Apply the identical fix to `collection-service/pom.xml` once Task 36 (`CertificationStatusEventListener`) adds that service's first real `@RabbitListener`.**
    - [x] `mvn install` — whole reactor still builds; `collection-service verify` re-run as a regression check (unaffected, 38 tests green)
  - **Dependencies:** Task 29
  - **Files likely touched:** `.../events/consume/{CollectionRegisteredEvent,CollectionRegisteredQueueConfig,CollectionRegisteredEventProcessor,CollectionRegisteredEventListener}.java`, `recycler-service/pom.xml`, plus unit + IT tests
  - **Estimated scope:** Small-Medium (2 files) — grew to 7 due to the two real findings above

---

## Task 31: Direction A end-to-end IT

- [x] Task 31: Direction A end-to-end IT
  - **Description:** Add a RabbitMQ Testcontainer to both services' IT setups; prove the full flow, redelivery/idempotency, and concurrent-increment atomicity over a real broker and real Postgres.
  - **Acceptance criteria:**
    - [x] `rabbitmq:3.13-management-alpine` Testcontainer added alongside the existing Postgres one, both services
    - [x] Full-flow IT: `POST` a `CollectionRecord` → message lands on the real queue → `Association.totalKilosCollected` increments by `weightKg`. Design decision beyond the plan's literal text: since `collection-service` never depends on `recycler-service` (even in tests), a single "real recycler-service context in the same IT" isn't possible without breaking that boundary — split into two tests instead, one per service, each proving its own half of the round trip against the same real exchange/routing-key/JSON shape (already verified structurally consistent in Task 28)
    - [x] Redelivery/idempotency IT: publish the same event twice → `totalKilosCollected` changes only once — proven over the real broker in `recycler-service`'s own suite
    - [x] Concurrent-increment IT (real `ExecutorService` threads, real Postgres): two concurrent events for the same association → final total is the exact sum of both — added to `CollectionRegisteredEventListenerIT` (bypasses the broker, same as Task 30's own tests, since the concern being tested is DB-level atomicity, not message-passing)
  - **Verification:**
    - [x] `mvn -pl recycler-service verify` and `mvn -pl collection-service verify` green
    - [x] Concurrent-increment test confirmed to actually exercise concurrency: a `CountDownLatch` forces both threads to start as close to simultaneously as possible, not two sequential calls that happen not to race
    - [x] **Real gap found and fixed, not assumed:** no `@RabbitListener` method with a typed (non-`String`) payload could ever have worked — Spring AMQP's default `SimpleMessageConverter` can't deserialize JSON bytes into an arbitrary record. Never surfaced before this task since Task 30's own test called the listener directly, bypassing message conversion. Fixed with shared-kernel's new `RabbitListenerConfig`: a `Jackson2JsonMessageConverter` wrapped inside a dedicated container factory bean, deliberately **not** exposed as its own `MessageConverter` bean — that would also become `RabbitTemplate`'s default converter (Spring Boot auto-detects any single `MessageConverter` bean for both publish and consume), double-JSON-encoding `OutboxDispatcher`'s already-serialized `payloadJson` string. Listeners opt in explicitly via `containerFactory = "jsonRabbitListenerContainerFactory"`. **Apply the same `containerFactory` reference to Task 36's `CertificationStatusEventListener` from the start.**
    - [x] **Second and third findings, only visible when running the FULL suite, not each new test in isolation:** (1) a live listener/dispatcher left enabled with a fast interval for one test class stays alive in Spring's cached context past that class's own Testcontainers lifecycle, reconnect-looping against now-dead containers and polluting/slowing the rest of the module's test run — fixed with `@DirtiesContext` on both new broker test classes. (2) A 10s `rabbitTemplate.receive(...)` timeout that passed reliably in isolation failed under the full suite's heavier resource contention (confirmed: real failure, `Expecting actual not to be null`, not assumed) — bumped to 30s in both services' broker tests. **Apply both fixes to Task 36's own broker-flow test from the start, and always verify new async/broker tests via the full-suite `verify` run, not just in isolation.**
  - **Dependencies:** Task 30
  - **Files likely touched:** `CollectionRegisteredEventBrokerFlowIT.java` (recycler-service), `CollectionRegisteredEventPublishFlowIT.java` (collection-service), `RabbitListenerConfig.java` (shared-kernel), both `pom.xml`s (Testcontainers RabbitMQ dependency), `CollectionRegisteredEventListenerIT.java` (concurrent test added)
  - **Estimated scope:** Large (9 files — grew from the planned 3-4 due to the message-converter gap and the two full-suite-only findings)

---

## Task 32: `Certification.notifiedExpiredAt` + `renew()` reset

- [x] Task 32: `Certification.notifiedExpiredAt` + `renew()` reset
  - **Description:** Add the nullable `notifiedExpiredAt` field to `Certification`, and modify `renew()` to reset it to `null` in the same call as its existing expiration-date validation — closes the user-caught gap where a certification renewed and later re-expired would never be re-notified.
  - **Acceptance criteria:**
    - [x] `Certification` gains `notifiedExpiredAt` (nullable `Instant`), settable via `markNotifiedExpired(Instant)`. Not literally Java package-private, unlike the plan's original wording: `CertificationExpiryScanJob` (Task 33) will live in a sibling `certification.job` package, and this codebase's own established convention for a domain mutator called from a sibling service/job package is `public` with a restrictive Javadoc (matches `Association.suspend()`/`activate()`), not Java-level access control, which can't span sibling packages anyway
    - [x] `renew()` resets it to `null` in the same call as its existing date-range validation
    - [x] `CertificationEntity` gains the column; extends the existing `existing()`/`update()` path from Task 12, doesn't rebuild it
  - **Verification:**
    - [x] RED→GREEN: updated `CertificationTest`'s existing `reconstruct()` call site first (new 6th parameter), confirmed the compile failure, then implemented
    - [x] Unit tests: `renew()` resets `notifiedExpiredAt` to `null`; a fresh `Certification.create()` starts with it `null`; `markNotifiedExpired()` sets the timestamp — 11 domain tests (was 8)
    - [x] IT test beyond the plan's literal scope: `notifiedExpiredAtRoundTripsThroughUpdateAndIsResetByRenew` proves the value survives a real Postgres round trip at microsecond precision (`Instant.now()` truncated to `ChronoUnit.MICROS` to match Postgres `timestamp` precision) and is genuinely reset to `null` in the database, not just the in-memory domain object
    - [x] `mvn -pl recycler-service test` and `verify` green — 63 unit tests, 45 IT tests (both +1 net beyond the previous baseline plus the new suite)
    - [x] `mvn install` — whole reactor still builds
  - **Dependencies:** Task 29 (schema)
  - **Files likely touched:** `Certification.java`, `CertificationEntity.java`, `CertificationRepositoryAdapter.java`, plus tests
  - **Estimated scope:** Small-Medium (5 files)

---

## Task 33: `CertificationExpiryScanJob` + `CertificationExpiredEventPublisher`

- [x] Task 33: `CertificationExpiryScanJob` + `CertificationExpiredEventPublisher`
  - **Description:** New `@Scheduled` + `@SchedulerLock` job that finds certifications where `isExpired()` is true and `notifiedExpiredAt IS NULL`, publishes `CertificationExpiredEvent` per one found (outbox write), and sets `notifiedExpiredAt` — all in one transaction per certification.
  - **Acceptance criteria:**
    - [x] `CertificationExpiredEvent` record: `certificationId, associationId, expiredAt` (plus `eventId`/`occurredAt` from `DomainEvent`) — mirrors `CollectionRegisteredEvent`'s exact shape from Task 28
    - [x] `CertificationExpiryScanJob`: distinct `@SchedulerLock` name (`certificationExpiryScanJob`) from `OutboxDispatcher`'s (`outboxDispatcher`)
    - [x] Finds only expired + not-yet-notified certifications; publishes + sets the flag atomically per certification
  - **Verification:**
    - [x] Unit tests (Mockito): `CertificationExpiryProcessorTest` proves publish+markNotifiedExpired+update all happen per certification; `CertificationExpiryScanJobTest` proves the job processes every certification the repository returns and does nothing when the repository returns none (the "doesn't touch an already-notified/non-expired certification" criterion is proven at the repository-query layer instead — see `CertificationRepositoryAdapterIT`'s real-Postgres test below, since a Mockito test can't validate a `WHERE` clause)
    - [x] `mvn -pl recycler-service test` and `verify` green — 72 unit tests, 46 IT tests (both +9/+1 beyond Task 32's baseline)
    - [x] `mvn install` — whole reactor still builds
  - **Real gap found:** the plan's acceptance criteria didn't call out *where* "finds only expired + not-yet-notified" actually gets proven. Added `CertificationRepository.findExpiredAndNotYetNotified()` (port + `@Query` + adapter) as a prerequisite, with its own real-Postgres IT test (`findExpiredAndNotYetNotifiedReturnsOnlyExpiredCertificationsNeverNotified`, 3 certifications: expired+not-notified, expired+already-notified, not-yet-expired) — this is the actual proof of the job's filtering logic, not the job's own Mockito tests, which can only prove delegation.
  - **Dependencies:** Task 32
  - **Files touched:** `.../certification/job/CertificationExpiryScanJob.java`, `.../certification/job/CertificationExpiryProcessor.java`, `.../certification/events/CertificationExpiredEvent.java`, `.../events/publish/CertificationExpiredEventPublisher.java`, `.../certification/port/out/CertificationRepository.java`, `.../certification/adapter/out/persistence/CertificationJpaRepository.java`, `.../certification/adapter/out/persistence/CertificationRepositoryAdapter.java`, plus tests
  - **Estimated scope:** Medium (4 files) — actual: 7 main files + 4 test files, since the repository-layer query wasn't already in place

---

## Task 34: `CertificationRenewedEventPublisher`

- [x] Task 34: `CertificationRenewedEventPublisher`
  - **Description:** `recycler-service` defines `CertificationRenewedEvent`; hook its outbox write into `CertificationService.renew()`'s existing transaction (same call as `certificationRepository.update(certification)`).
  - **Acceptance criteria:**
    - [x] `CertificationRenewedEvent` record: `certificationId, associationId, newExpirationDate` (plus `eventId`/`occurredAt` from `DomainEvent`) — mirrors `CertificationExpiredEvent`'s (Task 33) shape
    - [x] `CertificationService.renew()` writes the outbox row in the same transaction as its existing `update()` call — `renew()` wasn't `@Transactional` before this task (didn't need to be, with a single repository call); added it, same as `CollectionRecordService.create()`'s Task 28 pattern
  - **Verification:**
    - [x] Unit test (Mockito): `CertificationRenewedEventPublisherTest` proves the outbox row's shape (id/timestamp reuse, event type, routing key, payload); `CertificationServiceTest.renewExtendsTheExpirationDate` proves `renew()` calls `eventPublisher.publish()` with the right event after `update()`
    - [x] Real-Postgres IT: `CertificationApiIT.renewingACertificationExtendsItsExpirationDateAndClearsExpiredStatus` extended with a direct `JdbcTemplate` query against `outbox_event_recycler`, confirming a `NEW` row with `event_type = CertificationRenewedEvent` and the right `certificationId` in its payload — same direct-SQL standard as Task 28's `creatingARecordWritesAPendingOutboxEntry` and Task 33's `findExpiredAndNotYetNotifiedReturnsOnlyExpiredCertificationsNeverNotified`
    - [x] `mvn -pl recycler-service test` and `verify` green — 74 unit tests, 46 IT tests
    - [x] `mvn install` — whole reactor still builds
  - **Real gap found:** initially closed this task claiming "no new IT needed, pure unit-level wiring" — wrong. A Mockito test on `CertificationService` only proves `renew()` calls `publish()`; it can't prove the outbox write and the certification update actually commit together in one real transaction, which is the entire point of Task 28's and Task 33's established direct-SQL verification standard. Caught by the user, fixed by extending `CertificationApiIT` instead of adding a new test class, since the existing renew-flow test already had the right setup.
  - **Dependencies:** Task 32
  - **Files touched:** `.../certification/events/CertificationRenewedEvent.java`, `.../events/publish/CertificationRenewedEventPublisher.java`, `CertificationService.java`, `CertificationServiceTest.java`, `CertificationApiIT.java`, plus 2 new test files
  - **Estimated scope:** Small-Medium (3 files) — actual: 3 main files + 4 test files (existing `CertificationServiceTest` needed its constructor call updated; existing `CertificationApiIT` needed the real-Postgres outbox assertion)

---

## Task 35: collection-service `certification_status_ledger` + `blocked_association` schema/domain

- [x] Task 35: collection-service `certification_status_ledger` + `blocked_association` schema/domain
  - **Description:** The idempotency ledger for both incoming certification-status event types, and the minimal `BlockedAssociation` projection (association id + block state only — never a copy of `recycler-service`'s full `Association`).
  - **Acceptance criteria:**
    - [x] `v0.1.6_create_certification_status_ledger_table.yaml` (PK `event_id`), `v0.1.7_create_blocked_association_table.yaml` (PK `association_id`, `blocked_at` timestamp only — no name/RUC/contact fields) — numbered after Task 27's `v0.1.4`/`v0.1.5` since those are created chronologically first, keeping version numbers in task-execution order
    - [x] `BlockedAssociation` domain class + `BlockedAssociationEntity`/repo/adapter, `BlockedAssociationRepository` port — `block()` upserts via the `existing()`-factory pattern (same as `CertificationEntity`'s update path), so re-blocking an already-blocked association updates `blocked_at` instead of failing on the PK; `unblock()` is a safe no-op when no row exists (`JpaRepository.deleteById()` throws on a missing row, and Task 36 will call `unblock()` on every `CertificationRenewedEvent` whether or not the association was actually blocked)
    - [x] `CertificationStatusLedgerEntity`/`CertificationStatusLedgerJpaRepository` also added (public, no port/adapter split — mirrors recycler-service's `CollectionRegisteredLedgerEntity` from Task 29) as a prerequisite for Task 36's listener, shared by both `CertificationExpiredEvent` and `CertificationRenewedEvent`
  - **Verification:**
    - [x] IT test round-tripping both tables: `CertificationStatusLedgerJpaRepositoryIT` (fresh insert + duplicate-key violation, mirrors Task 29's ledger IT), `BlockedAssociationRepositoryAdapterIT` (block→isBlocked→unblock round trip, unblock-when-never-blocked no-op, re-block upsert)
    - [x] `mvn -pl collection-service test` and `verify` green — 71 unit tests, 44 IT tests
    - [x] `mvn install` — whole reactor still builds
  - **Real gap found:** `BlockedAssociationRepositoryAdapterIT`'s re-block test initially read the persisted `blocked_at` back via `JdbcTemplate.queryForObject(sql, java.sql.Timestamp.class, ...)` and got a value exactly 5 hours off from what was written. Cause: Postgres's `timestamp` (no time zone) column stores naive local bits; Hibernate writes an `Instant` as UTC, but pgjdbc's default `getTimestamp()` conversion for that requiredType reinterprets those bits using the JVM's default zone (America/Lima, UTC-5) instead of UTC. Fixed by reading with an explicit `rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC")))` instead — flagged here since Task 36/37/38's tests will likely need to read `blocked_at` or ledger timestamps back the same way and would otherwise hit this cold.
  - **Dependencies:** Task 27
  - **Files touched:** `collection-service/src/main/resources/db/changelog/changes/v0.1.6_create_certification_status_ledger_table.yaml`, `v0.1.7_create_blocked_association_table.yaml`, `.../association/domain/BlockedAssociation.java`, `.../association/adapter/out/persistence/{BlockedAssociationEntity,BlockedAssociationJpaRepository,BlockedAssociationRepositoryAdapter}.java`, `.../association/port/out/BlockedAssociationRepository.java`, `.../events/ledger/{CertificationStatusLedgerEntity,CertificationStatusLedgerJpaRepository}.java`, plus 3 test files
  - **Estimated scope:** Medium (6 files) — actual: 9 main files + 3 test files (the ledger entity/repo pair wasn't in the original file list but is needed as Task 36's prerequisite, same as Task 29's precedent)

---

## Task 36: `CertificationStatusEventListener`

- [x] Task 36: `CertificationStatusEventListener`
  - **Description:** One `@RabbitListener` handling both `CertificationExpiredEvent` and `CertificationRenewedEvent` on a single durable queue bound to both routing keys; idempotent ledger insert, then blocks or unblocks.
  - **Acceptance criteria:**
    - [x] Local `CertificationExpiredEvent`/`CertificationRenewedEvent` records (structurally matching `recycler-service`'s)
    - [x] Queue bound to `certification.expired` and `certification.renewed` (`CertificationStatusQueueConfig`, one queue, two `Binding` beans)
    - [x] Ledger insert (PK `event_id`, duplicate short-circuits) → `Expired` upserts a `BlockedAssociation` row; `Renewed` removes it
    - [x] **Applied Task 30's transactional-correctness fix from the start:** `CertificationStatusEventProcessor` is a separate `@Transactional` bean with `processExpired()`/`processRenewed()`, each doing the block/unblock write *first*, then the ledger insert *last* (flushed), in one flat transaction
    - [x] **User-flagged finding resolved — decision documented here, not deferred:** `BlockedAssociationRepositoryAdapter.block()`'s check-then-act (Task 35) IS racy under concurrency, confirmed by a real concurrent-threads IT test (`twoDistinctExpiredEventsForTheSameAssociationRacingToBlockItForTheFirstTimeBothCompleteWithoutError`) that reliably reproduces a real `DataIntegrityViolationException` on `blocked_association_pkey` in the failsafe log. **No change was made to `block()` or its call site** — `CertificationStatusEventProcessor.processExpired()` already runs inside one flat `@Transactional` method per Task 30's own pattern, so a constraint violation there rolls back that whole transaction (block attempt + this event's own ledger insert) exactly the same way a redelivered event's ledger-PK collision does. The listener's *existing* `try/catch DataIntegrityViolationException` (needed anyway for redelivery) already absorbs this race without any listener-concurrency assumption: the loser's transaction cleanly rolls back, the winner has already left the association correctly blocked, and the loser's event is simply not recorded in the ledger (acceptable — a future redelivery of that same event, if one ever happens, would just take the harmless "already blocked" update path). No separate `try/catch` around `block()` itself was needed, and listener concurrency was NOT pinned to 1 — the fix already in place is concurrency-agnostic
    - [~] **Task 31's message-converter fix does not apply as literally planned — documented deviation:** the plan assumed a typed-record `@RabbitListener` parameter with `containerFactory = "jsonRabbitListenerContainerFactory"` (Task 30/31's pattern), but that only works for ONE fixed payload type per listener method. Jackson2JsonMessageConverter picks its target type from either the listener method's own single declared parameter type or a `__TypeId__` header (never set here, since the outbox publishes pre-serialized JSON strings, not converter-serialized objects) — neither mechanism can pick between two shapes on one queue. Resolved instead by taking the raw `org.springframework.amqp.core.Message` plus the `AmqpHeaders.RECEIVED_ROUTING_KEY` header, and dispatching to `objectMapper.readValue(body, ExpiredOrRenewedEvent.class)` manually based on the routing key — bypasses Spring AMQP's type-conversion machinery entirely (a `Message`-typed parameter is passed through unconverted), so the *default* (Boot-auto-configured) container factory is used, not the shared-kernel one
  - **Verification:**
    - [x] Unit tests (Mockito): `CertificationStatusEventListenerTest` — expired routing key delegates to `processExpired()`; renewed routing key delegates to `processRenewed()`; a `DataIntegrityViolationException` from either is swallowed without rethrowing; an unrecognized routing key is ignored (no processor interaction)
    - [x] IT test against real Postgres (mirrors `CollectionRegisteredEventListenerIT`): `CertificationStatusEventListenerIT` — fresh expired event blocks + redelivery of the same event doesn't throw and leaves the block state unchanged; fresh renewed event unblocks a previously-blocked association; renewing an association that was never blocked doesn't throw; **the concurrency race test described above**
    - [x] `mvn -pl collection-service test` and `verify` green — 75 unit tests, 48 IT tests (+4/+4 beyond Task 35's baseline)
    - [x] **Applied Task 30's second fix to `collection-service/pom.xml` too:** added `spring.rabbitmq.listener.simple.auto-startup=false` to the failsafe `systemPropertyVariables` block — verified via the full IT suite log: zero connection-refused lines (`grep -c "Connection refused"` on the full `verify` log returned 0)
    - [x] `mvn install` — whole reactor still builds
  - **Dependencies:** Task 35
  - **Files touched:** `.../events/consume/{CertificationExpiredEvent,CertificationRenewedEvent,CertificationStatusQueueConfig,CertificationStatusEventProcessor,CertificationStatusEventListener}.java`, `collection-service/pom.xml`, plus 2 test files
  - **Estimated scope:** Medium (4 files)

---

## Task 37: Enforce the block in `CollectionRecordService.create()`

- [x] Task 37: Enforce the block in `CollectionRecordService.create()`
  - **Description:** New `CollectionErrors.COL-009 ASSOCIATION_BLOCKED` (409); `create()` checks `BlockedAssociationRepository` before saving.
  - **Acceptance criteria:**
    - [x] `CollectionErrors.COL-009 ASSOCIATION_BLOCKED` added (409, next free code after `COL-008`)
    - [x] `CollectionRecordService.create()` throws `COL-009` if `blockedAssociationRepository.isBlocked(command.associationId())` is true — checked after the neighbor/schedule checks, before the domain object is created; the block check is a purely local read against collection-service's own projection, not a call to recycler-service (updated the stale "associationId is deliberately NOT validated" comment, which no longer matched reality after this change — its *existence* is still unvalidated, its *block state* now is)
    - [x] Exception handler wiring confirmed: shared-kernel's `GlobalExceptionHandler` already maps any `ApplicationException` generically via its `ApplicationError` (code/message/status) — no new branching needed in `CollectionRecordExceptionHandler`
  - **Verification:**
    - [x] Unit test (Mockito): `rejectsCreationWhenTheAssociationIsBlocked` — blocked associationId → `COL-009`, `repository.save()` never called
    - [x] IT test (Postgres-only, no Rabbit): `CollectionRecordApiIT.creatingARecordForABlockedAssociationReturns409` — seeds a `blocked_association` row directly via the real `BlockedAssociationRepository` bean, then asserts the real HTTP POST returns 409 with `code: COL-009`; the "succeeds when unblocked" half of this criterion was already covered by the module's existing passing creation tests (an unstubbed/un-seeded association is never blocked)
    - [x] `mvn -pl collection-service test` and `verify` green — 74 unit tests, 49 IT tests
    - [x] `mvn install` — whole reactor still builds
  - **Dependencies:** Task 36
  - **Files touched:** `CollectionErrors.java`, `CollectionRecordService.java`, `CollectionRecordServiceTest.java`, `CollectionRecordApiIT.java`
  - **Estimated scope:** Small-Medium (4 files)

---

## Task 38: Direction B end-to-end IT

- [x] Task 38: Direction B end-to-end IT
  - **Description:** Full-flow IT over the real broker: expire → scan → block → reject → renew → unblock → accept. Plus redelivery/idempotency, plus the exact expire→renew→re-expire→re-notify cycle the user caught as missing from the initial design.
  - **Acceptance criteria:**
    - [x] Full-flow IT: expired certification → run `CertificationExpiryScanJob` → `CertificationExpiredEvent` published → association blocked in `collection-service` → `POST .../collection-records` → 409 `COL-009` → `PATCH .../certifications/{id}/renew` → `CertificationRenewedEvent` published → unblocked → `POST .../collection-records` succeeds. Split across two IT classes, one per service — same precedent as Task 31's Direction A tests, since `collection-service` never depends on `recycler-service` even in tests: `recycler-service`'s `CertificationExpiryPublishFlowIT` proves the real HTTP → scan → dispatch → broker path for both event types; `collection-service`'s `CertificationStatusEventBrokerFlowIT` proves the real broker → listener → block/unblock → HTTP 409/201 path
    - [x] Redelivery/idempotency IT: same expired event published twice over the real broker → block state only toggles once (a genuine `DataIntegrityViolationException` on `certification_status_ledger_pkey` was caught in the log, confirming the redelivery was actually exercised, not just assumed harmless)
    - [x] **Full expire→renew→re-expire cycle IT — both sides:** consumer side (`CertificationStatusEventBrokerFlowIT.aFullExpireRenewReExpireCycleReBlocksOnADistinctSecondExpiredEvent`) proves a second, distinct `CertificationExpiredEvent` re-blocks after `unblock()` deleted the row; producer side (`CertificationExpiryPublishFlowIT.aCertificationThatExpiresIsRenewedThenExpiresAgainCausesTheScanJobToPublishASecondDistinctEvent`) proves a real certification renewed via HTTP then pushed back into the past via JDBC (no "un-renew" endpoint exists) gets picked up by the scan job's own second tick and published as a genuinely new, distinct event
  - **Verification:**
    - [x] `mvn -pl recycler-service verify` and `mvn -pl collection-service verify` green — 70 unit / 48 IT (recycler-service), 74 unit / 52 IT (collection-service)
    - [x] `mvn install` — whole reactor still builds
    - [x] The re-expire cycle test is confirmed to actually fail without Task 32's `renew()` reset — sanity-checked, not just trusted: temporarily removed the `notifiedExpiredAt = null` line from `Certification.renew()`, re-ran the producer-side re-expire IT in isolation (bypassing surefire to reach the IT phase directly), confirmed it failed with `Expecting actual not to be null` (the second `receive()` timed out because the scan job's own `notifiedExpiredAt IS NULL` filter never matched again) — then reverted and confirmed green again
  - **Real gap found:** the throwaway-queue helper's `autoDelete=true` (carried over from Direction A's `declareThrowawayQueueBoundToTheRoutingKey`, which only ever calls `rabbitTemplate.receive()` once per test) deletes the queue the moment its temporary consumer count drops to zero. Direction B's producer-side test calls `receive()` twice on the same queue (expired event, then renewed event) — the queue was gone by the second call (`NOT_FOUND - no queue ... in vhost '/'`), confirmed via a real failing run before switching to `autoDelete=false`
  - **Dependencies:** Task 37
  - **Files touched:** `recycler-service/.../it/CertificationExpiryPublishFlowIT.java`, `collection-service/.../events/consume/CertificationStatusEventBrokerFlowIT.java`
  - **Estimated scope:** Large (2-3 files, high test complexity)

## Task 39: Ordering test

- [x] Task 39: Ordering test
  - **Description:** New test method `publishingExpiredThenRenewedInQuickSuccessionThroughTheOutboxLeavesTheFinalStateUnblocked` added to `collection-service`'s `CertificationStatusEventBrokerFlowIT` — publishes `CertificationExpiredEvent` then `CertificationRenewedEvent` for the same association back-to-back (no intermediate wait, unlike this class's happy-path test which deliberately awaits the intermediate blocked state), proving the common-case ordering guarantee the spec calls for (`SPEC-cross-service-events.md`, "Ordering test (best-effort, documented limit)").
  - **Acceptance criteria:**
    - [x] Test asserts final state is unblocked after both events flow through the outbox in order — closed a real vacuity gap first: unblocked is also a fresh `associationId`'s untouched starting state, so the test would trivially "pass" if nothing were consumed at all. Fixed by adding `awaitLedgered(eventId, timeout)` (polls `CertificationStatusLedgerJpaRepository.existsById`) and asserting BOTH event ids actually landed in `certification_status_ledger` before checking the final blocked state — verified genuinely closes the gap by tracing `CertificationStatusEventProcessor`: block/unblock happens, then the ledger insert, all in the same `@Transactional` method, so ledger-visible implies state-change-committed.
    - [x] A test comment documents the residual out-of-order-after-redelivery risk, quoting the spec's own "mitigated, not eliminated" wording (`SPEC-cross-service-events.md` Resolved Decisions) and naming the same discarded alternative (per-aggregate sequence numbers / a saga) for the same reason (disproportionate to MVP scope)
  - **Verification:**
    - [x] `mvn -pl recycler-service verify` green (unaffected — no files in this module touched by Task 39)
    - [x] `mvn -pl collection-service verify` green — `CertificationStatusEventBrokerFlowIT`: 4/4 tests (the 3 existing + the new ordering test), 16.27s
  - **Design note (reviewed and confirmed, not just asserted in a comment):** "through the outbox, not directly to the queue" is satisfied by the same faithful-stand-in pattern this test class's Javadoc already establishes for its other 3 tests — `rabbitTemplate.convertAndSend(...)` with the JSON content type set is structurally identical to what `OutboxDispatcher` (shared-kernel) itself does per row, and `collection-service` cannot boot a real `recycler-service` context even in tests (no dependency, by design). Single-consumer delivery-order preservation confirmed against the real listener code, not assumed: `CertificationStatusEventListener` declares no `concurrency` override, so Spring AMQP's single-consumer default applies, and two sequential `convertAndSend` calls on one `RabbitTemplate`/channel preserve publish order as delivery order.
  - **code-reviewer verdict:** PASS, no findings (verified against real source — `CertificationStatusEventProcessor`, `CertificationStatusEventListener`, `OutboxDispatcher` — not just the test's own comments).
  - **Dependencies:** Tasks 31, 38
  - **Files touched:** `collection-service/src/test/java/pe/esgtrazabilidad/collection/events/consume/CertificationStatusEventBrokerFlowIT.java`
  - **Estimated scope:** Small (1 file)

## Checkpoint 20: Final — ready for review

`mvn verify` across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`, RabbitMQ Testcontainer included) — 0 failures, 0 errors:
- `shared-kernel`: 18 unit
- `recycler-service`: 70 unit + 48 IT
- `collection-service`: 74 unit + 53 IT (was 52 as of Task 38 — the only count that moved across the whole reactor, +1 from Task 39's new ordering test)
- 4 IT classes across both modules declare a real `RabbitMQContainer` (Testcontainers), confirmed via grep, not assumed.

All 11 `SPEC-cross-service-events.md` Success Criteria bullets, re-verified line by line against the real code (not against this file's own prior summaries):

1. `EventPublishingStrategy` is `{ RABBITMQ, MOCK }` — ✅ confirmed directly in `shared-kernel/.../events/EventPublishingStrategy.java`; Javadoc documents the `GCP_PUB_SUB`/`SPRING_EVENTS` removal rationale in place.
2. `docker compose --env-file .env.local up -d` starts Postgres *and* RabbitMQ, both services connect to both — ✅ `docker-compose.yml` declares both `postgres` and `rabbitmq` services (root level); every IT class across both services connects to both via `@DynamicPropertySource`, exercised continuously since Task 24.
3. Creating a `CollectionRecord` increments `Association.totalKilosCollected` by `weightKg` — ✅ proven end-to-end by Task 31's Direction A IT (`CollectionRegisteredEventBrokerFlowIT` family); unaffected and still green this run.
4. A certification transitioning to expired (via the scan job, not a manual trigger) causes new `CollectionRecord` creation for that association to be rejected 409 in `collection-service` — ✅ proven by Task 38's `CertificationStatusEventBrokerFlowIT.anExpiredEventBlocksTheAssociationAndEnforces409ThenARenewedEventUnblocksAndAllowsCreation` (COL-009) and reaffirmed by Task 39's new test taking the same expired path.
5. Renewing that certification lets new `CollectionRecord` creation succeed again — ✅ same test as (4), second half; also reaffirmed by Task 39.
6. A certification that expires, is renewed, and later expires again is notified/blocks both times — ✅ `aFullExpireRenewReExpireCycleReBlocksOnADistinctSecondExpiredEvent` (collection-service, Task 38) and `aCertificationThatExpiresIsRenewedThenExpiresAgainCausesTheScanJobToPublishASecondDistinctEvent` (recycler-service, Task 38) — both still green.
7. Two concurrent `CollectionRegisteredEvent`s for the same association both correctly contribute to `totalKilosCollected` under real concurrency — ✅ `CollectionRegisteredEventListenerIT.concurrentEventsForTheSameAssociationBothContributeToTheTotal` uses a real `ExecutorService` (2 threads) + `CountDownLatch` to force simultaneous invocation, not sequential calls — confirmed by reading the test, not just its name.
8. Redelivering the same message to either consumer is a verified no-op on downstream state — ✅ both directions covered: `CollectionRegisteredEventListenerIT`/`CollectionRegisteredEventBrokerFlowIT` (recycler-service) and `CertificationStatusEventBrokerFlowIT.redeliveringTheSameExpiredEventOverTheRealBrokerLeavesTheBlockStateUnchanged` (collection-service) — a genuine `DataIntegrityViolationException` on the ledger's PK was confirmed in the log during Task 38, not just assumed harmless. **⚠ Amended by Task 40 (below):** this bullet's original re-verification, at Checkpoint 20 time, was itself insufficiently checked on the collection-service (expired→blocked) side — see Task 40 for the real gap a Cowork review caught here and its fix.
9. `CertificationExpiryScanJob` and both outbox dispatchers are `@SchedulerLock`-guarded, per-service ShedLock tables — ✅ confirmed via grep: `CertificationExpiryScanJob` and `OutboxDispatcher` both carry `@SchedulerLock`; `SchedulingConfig` in each service points at its own table (`shedlock_recycler`, `shedlock_collection`) via `.withTableName(...)`, never a shared `shedlock` table.
10. `mvn verify` passes across the whole reactor, RabbitMQ container included — ✅ this checkpoint's own run, see counts above.
11. `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected — ✅ every pre-Task-39 count is byte-for-byte identical to Task 38's own recorded numbers (`shared-kernel` 18, `recycler-service` 70/48 both unchanged); Task 39's commit (`0dcb15e`) touches exactly one test file, zero schema/migration files, zero already-shipped endpoint contracts.

**Human review and approval before moving to `reporting-service`: deliberately left unchecked** — per this project's hard gate (`CLAUDE.md`, "Flujo automatizado de revisión", nivel 3), a module/spec-complete checkpoint is never auto-approved. Reviewed by the user together with a Claude Desktop/Cowork session against the real code, not this summary.

## Task 40: Fortalecer test de idempotencia real en Direction B

- [x] Task 40: Fortalecer test de idempotencia real en Direction B
  - **Origen:** hallazgo de una revisión Cowork (segundo Claude, solo lectura) sobre el ya cerrado Checkpoint 20. Los 11 criterios de éxito de `SPEC-cross-service-events.md` estaban correctamente re-verificados salvo uno: el criterio 8 (idempotencia/redelivery) en la dirección `CertificationExpiredEvent` → `BlockedAssociation` solo comprobaba `blockedAssociationRepository.isBlocked(associationId) == true` antes y después de una redelivery simulada — un booleano naturalmente idempotente por sí solo, ya que `BlockedAssociationRepositoryAdapter.block()` hace upsert (actualiza `blocked_at`) cuando la fila ya existe. Aunque el dedup del ledger (`certification_status_ledger`, PK=`event_id`) estuviera completamente roto, ambos tests seguirían en verde. No era un bug de producción, sino un gap real de cobertura de test: el test no probaba lo que decía probar.
  - **Descripción:** en los dos tests afectados (`CertificationStatusEventListenerIT.aFreshExpiredEventBlocksTheAssociationAndARedeliveryOfTheSameEventDoesNotThrow` y `CertificationStatusEventBrokerFlowIT.redeliveringTheSameExpiredEventOverTheRealBrokerLeavesTheBlockStateUnchanged`), se agregó un helper `blockedAtOf(associationId)` (idéntico en ambos archivos) que lee la columna `blocked_at` directo vía `JdbcTemplate` con `Calendar` en UTC — mismo patrón ya establecido por `BlockedAssociationRepositoryAdapterIT.reBlockingAnAlreadyBlockedAssociationUpdatesBlockedAtInsteadOfFailingOnTheDuplicateKey`, que ya prueba que `blocked_at` SÍ cambia en una segunda llamada exitosa a `block()`. Cada test ahora captura `blockedAtOf(associationId)` tras la primera entrega y, tras la redelivery simulada, además de la aserción `isBlocked()==true` ya existente, agrega `assertThat(blockedAtOf(associationId)).isEqualTo(blockedAtAfterFirstDelivery)`.
  - **Verificación negativa (obligatoria, hecha antes de dar el fix por bueno):**
    1. Rotura temporal y controlada en `CertificationStatusEventProcessor.processExpired()`: `new CertificationStatusLedgerEntity(event.eventId(), ...)` → `new CertificationStatusLedgerEntity(java.util.UUID.randomUUID(), ...)`, para que el PK del ledger nunca colisione en una redelivery.
    2. Con la rotura activa: `mvn -pl collection-service test -Dtest=CertificationStatusEventListenerIT` → el nuevo assert de `blockedAtOf` FALLÓ (`expected: ...544099Z but was: ...558865Z`), confirmando que sí distingue dedup-roto de dedup-funcionando.
    3. `mvn -pl collection-service verify -Dit.test=CertificationStatusEventBrokerFlowIT` → el mismo assert también FALLÓ en `redeliveringTheSameExpiredEventOverTheRealBrokerLeavesTheBlockStateUnchanged` (más una falla colateral esperada en el test de ordering de Task 39, por buscar el `eventId` real que la rotura ya no persistía — confirma que la rotura fue real, no un efecto de test mal aislado).
    4. Revertido `CertificationStatusEventProcessor.java` — confirmado con `git diff --stat` que quedó byte-idéntico al original, cero diff residual.
    5. `mvn -pl collection-service clean verify` con el código real (sin rotura) → verde: 74 unit + 53 IT, 0 failures/errors — idéntico a los conteos previos a esta task (no se agregaron tests nuevos, solo se fortalecieron aserciones en 2 tests existentes).
  - **code-reviewer verdict:** PASS. Confirmó de forma independiente (no solo leyendo mi resumen): releyó el diff real, trazó `CertificationStatusEventProcessor`/`BlockedAssociationRepositoryAdapter`/`CertificationStatusEventListener` línea por línea para validar el mecanismo (rollback completo de la transacción cuando el `saveAndFlush` del ledger falla por PK duplicada), y re-ejecutó él mismo `mvn -pl collection-service verify -Dit.test=CertificationStatusEventListenerIT,CertificationStatusEventBrokerFlowIT` contra Postgres/RabbitMQ reales (4/4 en ambas clases). También revisó los tests de redelivery equivalentes de Direction A (`CollectionRegisteredEventListenerIT`/`CollectionRegisteredEventBrokerFlowIT`) y confirmó que NO tienen el mismo gap (verifican un total acumulativo que sí se duplicaría de forma detectable si el dedup fallara) — no se encontraron gaps hermanos que ameriten otra task de seguimiento.
  - **Hallazgo menor del reviewer (no bloqueante):** `blockedAtOf` queda duplicado en 3 archivos IT (`BlockedAssociationRepositoryAdapterIT`, `CertificationStatusEventListenerIT`, `CertificationStatusEventBrokerFlowIT`) — aceptable por el patrón ya establecido en esta base para tests IT hermanos (mismo criterio que Direction A); señalado solo como candidato a extracción futura si aparece una cuarta necesidad similar.
  - **Dependencies:** Task 39 / Checkpoint 20 (revisión Cowork posterior al cierre)
  - **Files touched:** `collection-service/src/test/java/pe/esgtrazabilidad/collection/events/consume/CertificationStatusEventBrokerFlowIT.java`, `collection-service/src/test/java/pe/esgtrazabilidad/collection/events/consume/CertificationStatusEventListenerIT.java`
  - **Estimated scope:** Small (2 files, test-only)

## Task 41: reporting-service scaffolding

- [x] Task 41: reporting-service scaffolding
  - **Description:** Third Spring Boot service in the reactor, depending only on `shared-kernel`. Root `pom.xml` gains `<module>reporting-service</module>` (one line, confirmed via `git diff --stat`). Own `pom.xml`, `ReportingServiceApplication` (plain `@SpringBootApplication`, no `scanBasePackages`), `application.yml` (port 8083, same shared Postgres port 5433 / RabbitMQ connection pattern as the other two services), empty Liquibase master changelog. `ReportingErrors.java` deliberately NOT created yet — confirmed against real git history (`git log --diff-filter=A`) that `AssociationErrors`/`CollectionErrors` also only appeared in each module's *second* task (first entity slice), not its scaffolding task — same precedent applied here, not a new decision.
  - **Real difference from the other two services, confirmed against `shared-kernel/pom.xml`, not assumed:** `spring-boot-starter-amqp` doesn't need to be redeclared — it's already a direct (non-optional, non-test) dependency of `shared-kernel`, so it comes transitively. `shedlock-provider-jdbc-template` is intentionally absent — this service runs no `@Scheduled` job and never publishes to the outbox (per `SPEC-reporting-service.md`'s Resolved Decisions), so it needs no ShedLock table of its own.
  - **Verification:**
    - [x] `mvn -pl reporting-service -am install` green
    - [x] `mvn -pl reporting-service spring-boot:run` boots cleanly against the real shared Docker Postgres, 0 changesets applied, Tomcat responds on `:8083`
    - [x] `mvn install` (whole reactor) green — shared-kernel 18 unit; recycler-service 70 unit + 48 IT; collection-service 74 unit + 53 IT (all unchanged); reporting-service 0/0 (no tests yet, correct for pure scaffolding)
  - **code-reviewer verdict:** PASS, no findings — independently verified `spring-boot-starter-amqp`'s transitive origin, the `ReportingErrors`-omission precedent via real git history, and confirmed the root `pom.xml` diff is exactly one line.
  - **Dependencies:** none
  - **Files touched:** `pom.xml` (root), `reporting-service/pom.xml`, `reporting-service/src/main/java/pe/esgtrazabilidad/reporting/ReportingServiceApplication.java`, `reporting-service/src/main/resources/application.yml`, `reporting-service/src/main/resources/db/changelog/{db.changelog-master.yaml,changes/.gitkeep}`
  - **Estimated scope:** Medium (5-7 files)

## Task 42: TrackedCompany persistence

- [x] Task 42: TrackedCompany persistence
  - **Description:** `TrackedCompany` domain (id, name, ruc, associationId, status ACTIVE/INACTIVE) mirroring `collection-service`'s `Company` shape exactly (same `Persistable<UUID>` JPA entity pattern, same RUC-regex validation in the domain constructor), plus the bare unvalidated `associationId` field this service adds — the Company↔Association link nothing else in the system has. Liquibase `v0.1.0_create_tracked_company_table.yaml` with a real `UNIQUE` constraint on `ruc`.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `TrackedCompanyTest` 5/5, `TrackedCompanyRepositoryAdapterIT` 2/2
    - [x] Direct-repository IT proves the DB-level unique constraint fires on a duplicate RUC bypassing any service layer (none exists yet) — a genuine `duplicate key value violates unique constraint "tracked_company_ruc_key"` was confirmed in the log, not just assumed
  - **`persistable_update_path` memory checked and confirmed not applicable here:** `TrackedCompany` has no mutator and `TrackedCompanyRepository` exposes no `update()` — no "modify an existing row" path exists yet in this task's scope, unlike `Association`'s `suspend()`/`update()`. Will need re-checking whenever a future task adds a mutation (e.g. deactivating a tracked company).
  - **code-reviewer verdict:** PASS, no findings — independently re-ran `mvn -pl reporting-service verify`, compared `TrackedCompanyEntity` line-by-line against `CompanyEntity`, and confirmed the IT calls the repository port directly with no service layer in between.
  - **Dependencies:** Task 41
  - **Files touched:** `trackedcompany/domain/{TrackedCompany,TrackedCompanyStatus}.java`, `trackedcompany/adapter/out/persistence/*`, `trackedcompany/port/out/TrackedCompanyRepository.java`, `v0.1.0_create_tracked_company_table.yaml`, `trackedcompany/domain/TrackedCompanyTest.java`, `trackedcompany/adapter/out/persistence/TrackedCompanyRepositoryAdapterIT.java`
  - **Estimated scope:** Medium (5-6 files)

## Task 43: TrackedCompany API

- [x] Task 43: TrackedCompany API
  - **Description:** Controller → Mapper → UseCase → Service for register/get/list, mirroring `collection-service`'s `Company` API exactly. `ReportingErrors` (new file: RPT-001 `TRACKED_COMPANY_NOT_FOUND`, RPT-002 `DUPLICATE_TRACKED_COMPANY_RUC`). `TrackedCompanyExceptionHandler` catches the RUC-uniqueness `DataIntegrityViolationException` fallback.
  - **Two inaccuracies in this task's own `tasks/todo.md` text, caught by code-reviewer and corrected before closing (not silently fixed in code alone):**
    1. The task described `TrackedCompanyExceptionHandler` as doing `getConstraintName()` dispatch. It doesn't, and shouldn't — `tracked_company` has exactly one unique constraint (`ruc`), so a plain catch-all `DataIntegrityViolationException` handler is correct, matching `CompanyExceptionHandler`'s own actual code (which also doesn't use `getConstraintName()` — confirmed by re-reading it, not assumed from an earlier summary). The plan's wording was simply wrong, not the implementation.
    2. The task's Verification bullet claimed a "two-thread concurrent-duplicate-RUC IT, mirrors `AssociationServiceTest`'s own RUC-race precedent." That precedent **does not exist** — confirmed via `grep -r "ExecutorService"` across the whole codebase, which only turns up the two event-consumption concurrency tests (`CollectionRegisteredEventListenerIT`, `CertificationStatusEventListenerIT`), never a RUC-uniqueness race test anywhere, for any entity, in either existing service. **Resolution:** no new concurrency test added for this rule — the DB-level backstop is already proven sequentially by Task 42's `TrackedCompanyRepositoryAdapterIT`, matching the actual rigor level `CompanyApiIT`/`Company` (this task's own explicit reference model) holds itself to. Unlike the `EXCLUDE USING gist` constraints in Phase 20/22 (where a dedicated two-thread test is a hard, Cowork-driven requirement precisely because that pattern is new to this codebase and unproven under concurrency), a plain `UNIQUE` constraint is a well-understood Postgres primitive already exercised under real concurrency implicitly by every prior module without a dedicated thread-race test. Do not cite this task's plan text as a precedent for skipping a *required* concurrency test elsewhere (Tasks 46, 53) — those remain mandatory.
  - **Added during review, not originally planned:** `listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum` in `TrackedCompanyApiIT` — symmetric coverage `CompanyApiIT` already has (`spring.data.web.pageable.max-page-size: 100` is configured identically in `reporting-service/application.yml`) that this task's first draft missed.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `TrackedCompanyTest` 5/5, `TrackedCompanyServiceTest` 5/5, `TrackedCompanyApiIT` 5/5 (was 4, +1 for the page-size-cap test), `TrackedCompanyRepositoryAdapterIT` 2/2
    - [x] Manual check via real `spring-boot:run` + curl (not just the IT): register → 201, get → 200, duplicate RUC → 409 `RPT-002`
  - **code-reviewer verdict:** PASS, with the two findings above resolved before closing (not left as silent deviations) — independently re-ran `mvn -pl reporting-service -am verify` itself and confirmed the false precedent via its own `grep`.
  - **Dependencies:** Task 42
  - **Files touched:** `exception/ReportingErrors.java`, `trackedcompany/port/in/*`, `trackedcompany/service/TrackedCompanyService.java`, `trackedcompany/adapter/in/web/*`, `trackedcompany/port/out/TrackedCompanyRepository.java` + adapter (extended with `findByRuc`), `trackedcompany/service/TrackedCompanyServiceTest.java`, `it/TrackedCompanyApiIT.java`
  - **Estimated scope:** Medium (6-8 files)

## Task 44: SigersolSync persistence

- [x] Task 44: SigersolSync persistence
  - **Description:** `SigersolSync` domain (associationId, periodStart/periodEnd, hierarchyCompliancePercent [0-100], officialKilosDeclared [nullable], declaredAt, sourceNote [nullable]) — a manually-entered official MINAM SIGERSOL declaration snapshot, immutable once created. Liquibase `v0.1.1_create_sigersol_sync_table.yaml`: `CREATE EXTENSION IF NOT EXISTS btree_gist` + `CONSTRAINT excl_sigersol_sync_association_period EXCLUDE USING gist (association_id WITH =, daterange(period_start, period_end, '[]') WITH &&)` — the first use in this codebase of a Postgres EXCLUDE constraint, the real DB-level backstop for RPT-006 that a Cowork review of the spec caught as missing from the first draft.
  - **First code-reviewer pass: FAIL, corrected before closing.** Finding: the test suite (overlap-in-the-middle + non-overlapping-adjacent cases) could not distinguish a correctly inclusive `daterange(..., '[]')` bound from an off-by-one `'[)'` exclusive one — neither test's expected result would change under the wrong SQL. Since this exact mechanism gets reused verbatim in Tasks 46/50/53, this was treated as blocking, not a nitpick. **Fix:** added `savingAPeriodSharingExactlyOneBoundaryDayWithAnExistingPeriodViolatesTheExclusionConstraint` — two periods for the *same* association where one's `periodStart` equals the other's `periodEnd` (they share exactly one day) — which only a correct inclusive bound rejects.
  - **Negative verification of the fix itself, performed by the re-reviewing code-reviewer (meta-verification, not just re-reading the diff):** temporarily swapped `'[]'` for `'[)'` in the real changelog, re-ran the IT, confirmed **only** the new boundary test failed (`Expecting code to raise a throwable`) while the other 4 kept passing — proving the new test is genuinely the decisive case, not just additional padding. Reverted, confirmed `git diff` showed zero residual change, re-ran full `mvn -pl reporting-service verify` green.
  - Also fixed from the first FAIL pass: the changelog's rollback comment now explains why it deliberately does NOT `DROP EXTENSION btree_gist` (Task 50's `esg_certificate` reuses it); added domain tests for the exact valid boundaries of `hierarchyCompliancePercent` (0 and 100) and a single-day period (`periodEnd == periodStart`).
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `SigersolSyncTest` 9/9, `SigersolSyncRepositoryAdapterIT` 5/5 (overlap-middle, boundary-day-shared, adjacent-no-overlap, different-association-same-period, plain save)
  - **code-reviewer verdict:** FAIL → fixed → PASS (second pass performed its own independent negative verification of the fix, not just a re-read).
  - **Dependencies:** Task 41
  - **Files touched:** `sigersolsync/domain/SigersolSync.java`, `sigersolsync/adapter/out/persistence/*`, `sigersolsync/port/out/SigersolSyncRepository.java`, `v0.1.1_create_sigersol_sync_table.yaml`, `sigersolsync/domain/SigersolSyncTest.java`, `sigersolsync/adapter/out/persistence/SigersolSyncRepositoryAdapterIT.java`
  - **Estimated scope:** Medium (5-6 files)

## Task 45: SigersolSync API

- [x] Task 45: SigersolSync API — **the most-reviewed task in this project's history: 4 review rounds before final PASS**, each catching a genuinely real gap, not a nitpick.
  - **Description:** Controller → Mapper → UseCase → Service for register/get/list. `RPT-006 DUPLICATE_SIGERSOL_SYNC_PERIOD` (409, service-level `existsOverlapping()` fast check + Task 44's `EXCLUDE` constraint as the real backstop), `RPT-007 SIGERSOL_SYNC_NOT_FOUND` (404), `RPT-008 INVALID_SIGERSOL_SYNC_DATA` (400 — added mid-task, not originally planned: `SigersolSync.create()` throws `IllegalArgumentException` for an invalid period range or a negative `officialKilosDeclared`, neither fully coverable by DTO-level `jakarta.validation` alone for the cross-field period case; same catch-domain-exception-in-service pattern as `CertificationService.renew()`'s `CER-003`, confirmed as a real precedent, not invented).
  - **Round 1 FAIL:** no test exercised the new `existsOverlapping()` service check through the real HTTP path — only Task 44's direct-repository IT existed. Fixed by adding HTTP-level adjacent-period (201) and shared-boundary-day (409) tests to `SigersolSyncApiIT`.
  - **Round 2 FAIL (the sharpest finding):** the round-1 fix still couldn't prove anything, because `SigersolSyncExceptionHandler` maps BOTH the service's `ApplicationException` (from `existsOverlapping()`) AND the DB's `DataIntegrityViolationException` (from the `EXCLUDE` constraint) to the *identical* `409 RPT-006` response — an HTTP-level test can't tell which path actually caught the conflict. A broken `existsOverlapping()` would fail silently, rescued by the DB constraint, and the test would still pass. **Fixed** by adding `existsOverlappingDetectsAPeriodSharingExactlyOneBoundaryDayWithoutTouchingTheExclusionConstraint` to `SigersolSyncRepositoryAdapterIT` — calls `existsOverlapping()` directly as a read, with no second `save()`, genuinely isolating the JPQL from the DB constraint. Also fixed in this round: generalized `INVALID_SIGERSOL_PERIOD` → `INVALID_SIGERSOL_SYNC_DATA` (same RPT-008) since the catch-all also covers negative kilos, not just periods; added the matching IT validation test.
  - **Round 3: PASS** — confirmed the isolation fix genuinely works (reasoned through both boundary values by hand, no path found where a broken `existsOverlapping()` would still pass).
  - **Post-PASS, pre-close correction (not a 4th review round of the original scope, a separately-reviewed addition):** closing the task revealed my own `tasks/todo.md` acceptance criteria had asked for `GET /sigersol-syncs?associationId=...` filtering, which was never implemented and which none of rounds 1-3 caught (they were focused on the overlap-constraint mechanism). Added `findByAssociationId`, threaded through the port/service/controller. **This addition's own review round FAILED once too**: the first version of its IT test asserted only `everyItem(...)` over the response, vacuously true against an empty list — a broken filter returning zero rows would have passed silently. Fixed with an explicit `content.size() == 1` assertion; re-reviewed, PASS.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green (final state) — `SigersolSyncTest` 10/10, `SigersolSyncServiceTest` 6/6, `SigersolSyncApiIT` 9/9, `SigersolSyncRepositoryAdapterIT` 6/6
  - **code-reviewer verdict:** FAIL → FAIL → PASS (main scope), then a follow-up addition FAIL → PASS (associationId filter). Every fix verified live, not just re-read.
  - **Dependencies:** Task 44
  - **Files touched:** `exception/ReportingErrors.java`, `sigersolsync/port/{in,out}/*`, `sigersolsync/adapter/{in/web,out/persistence}/*`, `sigersolsync/service/SigersolSyncService.java`, `sigersolsync/service/SigersolSyncServiceTest.java`, `it/SigersolSyncApiIT.java`, `sigersolsync/adapter/out/persistence/SigersolSyncRepositoryAdapterIT.java`, `sigersolsync/domain/{SigersolSync,SigersolSyncTest}.java`
  - **Estimated scope:** Medium (6-8 files) — actual: larger, due to 4 review rounds each adding test coverage

## Task 46: SigersolSync overlap concurrency test

- [x] Task 46: SigersolSync overlap concurrency test
  - **Description:** `SigersolSyncConcurrencyApiIT` (new file, `reporting-service/.../it/`) — two threads (`ExecutorService` + two-phase `CountDownLatch`, same shape as `recycler-service`'s `CollectionRegisteredEventListenerIT.concurrentEventsForTheSameAssociationBothContributeToTheTotal`), each firing a real HTTP `POST /sigersol-syncs` for the same `associationId` with overlapping periods as simultaneously as possible. Goes through the real HTTP layer, not the service bean directly — only `SigersolSyncExceptionHandler` (web layer) translates the DB's `DataIntegrityViolationException` into the typed `409 RPT-006` the acceptance criteria asks for.
  - **Required negative verification, performed before considering this task done:** temporarily swapped the real `EXCLUDE USING gist` DDL in `v0.1.1_create_sigersol_sync_table.yaml` for a no-op `SELECT 1`, re-ran the test in isolation → failed exactly as expected (`expected: 1L but was: 2L` — both concurrent requests got 201, two overlapping rows landed in the DB, the race genuinely manifested under real synchronized load, not just in theory). Reverted, confirmed `git diff --stat` showed zero residual change, re-ran the full module `verify` green.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green with the real constraint in place — `SigersolSyncConcurrencyApiIT` 1/1
    - [x] Manual check via real `spring-boot:run` + curl (Checkpoint 23's own bullet): register → 201, overlapping period → 409 `RPT-006` with the exact expected body, adjacent non-overlapping period → 201
  - **code-reviewer verdict:** PASS. Noted (not a finding) that in principle either the service's `existsOverlapping()` check or the DB constraint could be the one that rejects the loser, since both map to the identical HTTP response — but the negative verification already empirically settles that, under this test's actual synchronization, it's the DB constraint doing the rejecting (both threads pass the service check before either commits).
  - **Dependencies:** Task 45
  - **Files touched:** `it/SigersolSyncConcurrencyApiIT.java`
  - **Estimated scope:** Small (1 file)

## Task 47: reporting-service RabbitMQ wiring + TracedCollectionEntry schema

- [x] Task 47: reporting-service RabbitMQ wiring + TracedCollectionEntry schema — the "RabbitMQ wiring" half of the title is deferred entirely to Task 48 by design (confirmed against the task's own acceptance criteria, which never mention a listener/queue); this task is pure schema.
  - **Description:** `TracedCollectionEntryEntity` (event_id PK, association_id, collection_date, weight_kg, received_at) — doubles as Inbox-idempotency marker and the queryable fact table, mirroring `recycler-service`'s `CollectionRegisteredLedgerEntity` `Persistable<UUID>` shape exactly, plus the business columns this service's period-scoped-sum need requires. Liquibase `v0.1.2_create_traced_collection_entry_table.yaml`, including a composite index on `(association_id, collection_date)` added in the original migration (not a later one) since `CertificateService` (Task 51+) will run `findByAssociationIdAndCollectionDateBetween` repeatedly and this project avoids editing already-shipped changelogs.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `TracedCollectionEntryJpaRepositoryIT` 4/4 (was 3, +1 after review)
  - **code-reviewer verdict:** PASS with two non-blocking findings, both addressed before closing: (1) the period-range query test never exercised the inclusive boundary (`collectionDate == periodStart`/`periodEnd`) — added `findByAssociationIdAndCollectionDateBetweenIsInclusiveOnBothBoundaryDates`; (2) the same test never proved the `associationId` half of the compound query actually filters (all three original rows shared one association) — added a fourth row for a different association within the same date range to the existing test, asserting it's excluded.
  - **Dependencies:** Task 41
  - **Files touched:** `events/ledger/{TracedCollectionEntryEntity,TracedCollectionEntryJpaRepository}.java`, `v0.1.2_create_traced_collection_entry_table.yaml`, `events/ledger/TracedCollectionEntryJpaRepositoryIT.java`
  - **Estimated scope:** Small (3-4 files)

## Task 48: `CollectionRegisteredEventListener` (reporting-service)

- [x] Task 48: `CollectionRegisteredEventListener` (reporting-service)
  - **Description:** Own local `CollectionRegisteredEvent` record (structurally matching `collection-service`'s publisher, independently defined — same "never a shared Java type across services" convention as `recycler-service`'s Direction A consumer), `CollectionRegisteredEventListener` (`@RabbitListener`), `CollectionRegisteredQueueConfig` (own queue `collection.registered.reporting-service`, bound to the existing exchange/routing key — zero change to `collection-service`).
  - **Deliberate deviation from the plan, reviewed and confirmed correct:** no separate `CollectionRegisteredEventProcessor` bean, unlike the plan's own description ("same self-invocation reasoning as recycler-service's Task 30"). `recycler-service`'s processor exists because `incrementTotalKilos()` + the ledger insert are two writes needing one shared transaction (self-invocation from the listener would bypass Spring's `@Transactional` proxy). Here, `TracedCollectionEntryEntity`'s own row IS the entire write — already atomic via `JpaRepository`'s per-method transaction — so a wrapping processor would be ceremony without a corresponding correctness need. Confirmed by the reviewer to also correctly sidestep the `UnexpectedRollbackException` trap `SPEC-reporting-service.md`'s own illustrative snippet would have hit (a `@Transactional handle()` with an internal catch around the failing flush).
  - **code-reviewer FAIL → fixed → PASS:** first pass found `verify(repository).saveAndFlush(any(TracedCollectionEntryEntity.class))` couldn't catch a field-mapping bug (e.g. `neighborId` used where `associationId` belongs), since the entity has no `equals()`. Fixed with an `ArgumentCaptor` asserting each field (`eventId`, `associationId`, `collectionDate`, `weightKg`) against the source event's own independently-random UUIDs — confirmed by the re-reviewer that this genuinely would catch such a bug, not just superficially. Also fixed a comment that said `save()` where the code uses `saveAndFlush()`.
  - **Verification:**
    - [x] `mvn -pl reporting-service test` green — `CollectionRegisteredEventListenerTest` 2/2
  - **Dependencies:** Task 47
  - **Files touched:** `events/consume/{CollectionRegisteredEvent,CollectionRegisteredEventListener,CollectionRegisteredQueueConfig}.java`, `events/consume/CollectionRegisteredEventListenerTest.java`
  - **Estimated scope:** Medium (4 files)

## Task 49: Event consumption end-to-end IT

- [x] Task 49: Event consumption end-to-end IT
  - **Description:** `CollectionRegisteredEventBrokerFlowIT` (RabbitMQ + Postgres Testcontainers), mirroring `recycler-service`'s own `CollectionRegisteredEventBrokerFlowIT` (Task 31) exactly: publishes a structurally-matching JSON payload directly (this service can never depend on `collection-service`, even in tests), lets the real `@RabbitListener` (Task 48) consume it. Redelivery test asserts the period-scoped `SUM(weight_kg)`, not row count — same standard Task 40 already established.
  - **Real gap found and fixed while writing this task, not part of its own original scope:** Task 48 introduced a real `@RabbitListener` without the `spring.rabbitmq.listener.simple.auto-startup=false` failsafe default `recycler-service`/`collection-service` already carry in their own `pom.xml` — every other IT booting a full Spring context was silently depending on a locally-reachable RabbitMQ at `localhost:5672`. Added the same default to `reporting-service/pom.xml`; this task's own IT opts back into a live listener via `@TestPropertySource`.
  - **Self-inflicted incident during this task, documented for future sessions:** the first edit to that `pom.xml` comment included a literal `--` inside XML comment text, which is invalid XML and broke POM parsing for *any* Maven command against this module. The earlier `mvn ... | tail` background run reported "exit code 0" — that was `tail`'s exit code, not Maven's; the pipeline masked a total build failure. Caught only because the new IT's test report was unexpectedly missing from `target/failsafe-reports`. Fixed the comment (replaced `--` with a period), confirmed via an unpiped run with an explicitly captured `$?` that Maven's own exit code was 0 and `BUILD SUCCESS` appeared in the log. Filed as product feedback (recurring blind spot, not project-specific) — future sessions in this repo should prefer capturing Maven output to a file and grepping for `BUILD SUCCESS`/`FAILURE` over piping through `tail` when the exit code itself matters.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `CollectionRegisteredEventBrokerFlowIT` 2/2; zero `Connection refused` noise anywhere else in the module's test reports after the pom.xml fix
    - [x] Manual end-to-end check (Checkpoint 24's own bullet): booted real `collection-service` + `reporting-service` against the shared Docker Postgres/RabbitMQ, created a real neighbor + `CollectionRecord` via `collection-service`'s HTTP API, confirmed the resulting row in `reporting-service`'s `traced_collection_entry` table via direct `psql` query — zero changes to `collection-service`
  - **code-reviewer verdict:** PASS, no findings — independently re-ran the full verify, confirmed the pom.xml comment fix left no stray `--`, and traced the redelivery test's failure mode by hand (a broken dedup would double the period sum to 18.00, not just add a row).
  - **Dependencies:** Task 48
  - **Files touched:** `it/events/consume/CollectionRegisteredEventBrokerFlowIT.java` (actually `events/consume/`, same package as the listener — matches Direction A/B precedent for broker-flow ITs sharing the listener's package), `pom.xml`
  - **Estimated scope:** Medium (1-2 files, high test complexity) — actual: 2 files, plus an unplanned pom.xml fix

## Task 50: EsgCertificate + EsgCertificateLineItem persistence

- [x] Task 50: EsgCertificate + EsgCertificateLineItem persistence
  - **Description:** `EsgCertificate` domain (id, trackedCompanyId, associationId [snapshot], companyName [snapshot], companyRuc [snapshot], periodStart, periodEnd, kilosTrazados [snapshot], hierarchyCompliancePercent [snapshot], issuedAt) and `EsgCertificateLineItem` (certificateId, collectionDate, weightKg) — the frozen copy of contributing `TracedCollectionEntry` rows. Reuses Task 44's `EXCLUDE USING gist` mechanism verbatim for RPT-004 (`excl_esg_certificate_company_period`, scoped to `tracked_company_id`, same `btree_gist` extension already enabled).
  - **Correction to this task's own plan text, caught by review:** `tasks/todo.md`'s original "Dependencies" line described `tracked_company_id` as "FK-less" — that was true when the plan was written, but during implementation I added a real FK (`esg_certificate.tracked_company_id → tracked_company(id)`), confirmed against `collection-service`'s own precedent (`collection_record`'s real FKs to same-service `neighbor`/`collection_schedule`, vs. no FK on the cross-service `association_id`). Since `tracked_company` lives in this same service, the "bare UUID, no FK" pattern doesn't apply here — that pattern exists specifically for cross-service references. The FK protects against a certificate pointing at a `TrackedCompany` that never existed, without compromising immutability (every value the certificate prints is still a frozen copy, never a live join). Required rewriting the IT to persist a real `TrackedCompany` via `TrackedCompanyRepository` first, rather than using an unpersisted `TrackedCompany.reconstruct(UUID.randomUUID(), ...)`.
  - **Same rigor as Task 44 applied to the EXCLUDE constraint reuse:** the IT includes the identical decisive boundary-day test (two periods for the same tracked company sharing exactly one day) and an isolated `existsOverlapping()` test that never touches the DB constraint (same fix Task 45's second FAIL round established). Reviewer confirmed by tracing Postgres's actual canonicalized range in the log output that a naive `'[)'` bound would NOT have been caught without that specific test.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `EsgCertificateTest` 3/3, `EsgCertificateRepositoryAdapterIT` 5/5
  - **code-reviewer verdict:** PASS. One low-severity finding (this task's own `tasks/todo.md` text going stale re: the FK-less claim) — resolved by this closing entry rather than left silently outdated.
  - **Dependencies:** Task 42 (`TrackedCompany`, now via a real FK, not a bare UUID), Task 44 (reuses `btree_gist`, already enabled)
  - **Files touched:** `certificate/domain/{EsgCertificate,EsgCertificateLineItem}.java`, `certificate/adapter/out/persistence/*`, `certificate/port/out/EsgCertificateRepository.java`, `v0.1.3_create_esg_certificate_table.yaml`, `v0.1.4_create_esg_certificate_line_item_table.yaml`, `certificate/domain/EsgCertificateTest.java`, `certificate/adapter/out/persistence/EsgCertificateRepositoryAdapterIT.java`
  - **Estimated scope:** Large (7-9 files) — actual: 12 files

## Task 51: Certificate summary preview

- [x] Task 51: Certificate summary preview
  - **Description:** `CertificateSummary` (plain record, lives directly in the `certificate` package, not `certificate/domain` — deliberate, since it's a computed DTO, never a persisted JPA entity, per the spec's Resolved Decisions) + `PreviewCertificateSummaryUseCase` + `GET /tracked-companies/{companyId}/certificate-summary?periodStart=...&periodEnd=...`. Computes live: `TracedCollectionEntryJpaRepository` sum for the company's `associationId` within the period, plus `SigersolSyncRepository.findCovering(...)` for the compliance % (nullable — a missing `SigersolSync` record does NOT block the preview; RPT-005 only blocks actual issuance in Task 52). Read-only service: no `save()`/`saveAndFlush()` anywhere.
  - **Real test-assertion bug found and fixed while writing this task (not a business-logic bug):** `CertificateApiIT`'s zero-kilos test initially asserted `kilosTrazados == 0.0f`; Jackson serializes `BigDecimal.ZERO` as the JSON integer `0`, not `0.0`, so RestAssured/Hamcrest failed with a type mismatch even though the underlying computed value was correct. Fixed to `equalTo(0)`.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `CertificateServiceTest` 3/3, `CertificateApiIT` 3/3
  - **code-reviewer verdict:** PASS. One low-severity, non-blocking suggestion: no HTTP-level test exercises `kilosTrazados > 0` end-to-end (the sum itself is proven with real `BigDecimal` math in `CertificateServiceTest`, and the underlying query has its own dedicated boundary-inclusive IT from Task 47) — flagged as something Task 52's real issuance flow will likely exercise anyway, not required to add here.
  - **Dependencies:** Task 43, Task 45, Task 49
  - **Files touched:** `certificate/CertificateSummary.java`, `certificate/port/in/PreviewCertificateSummaryUseCase.java`, `certificate/service/CertificateService.java`, `certificate/adapter/in/web/{CertificateController,CertificateMapper,CertificateSummaryResponse}.java`, `certificate/service/CertificateServiceTest.java`, `it/CertificateApiIT.java`
  - **Estimated scope:** Medium (4-5 files) — actual: 8 files

## Task 52: Certificate issuance

- [x] Task 52: Certificate issuance
  - **Description:** `IssueCertificateUseCase` / `POST /tracked-companies/{companyId}/certificates`: `RPT-003` (tracked company not found), `RPT-004` (overlapping period — service check ordered BEFORE the `SigersolSync` lookup, so a duplicate period short-circuits without the extra query; confirmed with `verify(sigersolSyncRepository, never()).findCovering(...)` in that test case), `RPT-005` (no covering `SigersolSync` record). Freezes company name/RUC/associationId, the computed kilos sum, and the compliance % into `EsgCertificate`, snapshotting the contributing `TracedCollectionEntry` rows into `EsgCertificateLineItem`, all via `EsgCertificateRepository.save()`'s own one-transaction guarantee (Task 50).
  - **New design decision, not in the original plan, reviewed and confirmed correct:** `GetCertificateUseCase.getById` takes `(trackedCompanyId, id)`, not just `id` — a certificate that exists but belongs to a *different* tracked company is reported identically to a missing one (`RPT-003`), same `findScoped` pattern already established by `collection-service`'s `CollectionScheduleService` for nested-path resources. Proven both at the unit level (`throwsNotFoundWhenTheCertificateBelongsToADifferentTrackedCompany`) and via real HTTP (`gettingACertificateThroughADifferentCompanysPathReturnsNotFound`: issues a certificate under company A, requests it through company B's path with the same id, expects 404).
  - **code-reviewer verdict:** PASS. One medium finding fixed: two comments claimed Task 53's concurrency test "proven in Task 53" before that task existed — corrected to future tense ("to be proven"). One low finding left as-is (non-blocking): `CertificateService.issue()`'s own `@Transactional` is redundant given `EsgCertificateRepository.save()` is already transactional in the adapter and no other write shares the method — harmless, not worth removing over.
  - **Own acceptance-criteria gap caught while closing, not by the reviewer:** "a non-overlapping adjacent period → 201" was listed in this task's own acceptance criteria but never actually tested — added `issuingANonOverlappingAdjacentPeriodForTheSameTrackedCompanySucceeds` before marking the task done.
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `CertificateServiceTest` 10/10, `CertificateApiIT` 10/10 (was 9, +1 for the adjacent-period case)
  - **Dependencies:** Task 50, Task 51
  - **Files touched:** `certificate/port/in/{GetCertificateUseCase (signature changed),IssueCertificateCommand,IssueCertificateUseCase,ListCertificatesUseCase}.java`, `certificate/service/CertificateService.java`, `certificate/adapter/in/web/{CertificateController,CertificateMapper,CertificateExceptionHandler,EsgCertificateResponse,IssueCertificateRequest}.java`, `exception/ReportingErrors.java`, `certificate/service/CertificateServiceTest.java`, `it/CertificateApiIT.java`
  - **Estimated scope:** Large (6-8 files) — actual: 13 files

## Task 53: Certificate concurrency + immutability tests

- [x] Task 53: Certificate concurrency + immutability tests — **took 2 review rounds**, both catching real gaps, closing Checkpoint 25 (Phase 22 complete).
  - **Description:** `CertificateConcurrencyApiIT` (real two-thread `ExecutorService`/`CountDownLatch` proof that RPT-004's `EXCLUDE USING gist` constraint closes the race `existsOverlapping()` alone cannot — same shape as Task 46's `SigersolSyncConcurrencyApiIT`) + an immutability test added to `CertificateApiIT` (a backdated `TracedCollectionEntry` arriving after issuance must not change an already-issued certificate).
  - **Round 1 FAIL:** the immutability test only asserted the denormalized `kilosTrazados` HTTP field, never the actual `EsgCertificateLineItem` snapshot rows Task 50 built specifically to guarantee the freeze — a column that's simply never rewritten would pass that check even with a completely broken line-item snapshot. **Fixed** by autowiring `EsgCertificateRepository` (already a public port) directly into the IT and asserting `findLineItems(certificateId)` still has exactly the one entry captured at issuance (weight, date) — not the backdated one. Also, at that point, Checkpoint 25's manual curl check hadn't been done yet.
  - **Manual check performed (Checkpoint 25's own requirement):** booted real `reporting-service` + `collection-service` via `spring-boot:run` against the shared Docker Postgres/RabbitMQ. Sequence: registered a `TrackedCompany` + covering `SigersolSync` → issued a certificate (201) → issued an overlapping period for the same company (409 `RPT-004`, real error body confirmed) → registered a second company with no `SigersolSync` data and attempted issuance (409 `RPT-005`, confirmed) → created a real `Neighbor` + `CollectionRecord` in `collection-service` (via HTTP, not a direct DB insert) dated inside the already-issued period, waited for real RabbitMQ propagation, and confirmed the already-issued certificate still read `kilosTrazados: 0.00` while the live preview for the same period correctly showed the new `50.00` kg. Both processes shut down cleanly afterward (confirmed via `netstat`/`taskkill`).
  - **Round 2 FAIL:** the required negative verification for the concurrency test (per this task's own acceptance criteria, same standard Task 46 already established for RPT-006) had genuinely been *performed* earlier in this session but never *documented* — no `tasks/LEARNINGS.md` entry, no test comment referencing the actual result. The reviewer correctly could not distinguish "done but undocumented" from "never done" from repo state alone, and this project's own hygiene rule requires the narrative to live in `LEARNINGS.md`, not just in a session's memory. **Documented here now, from the actual evidence gathered earlier in this session:**
    1. Temporarily swapped the real `EXCLUDE USING gist` DDL in `v0.1.3_create_esg_certificate_table.yaml`'s second changeset for a no-op `sql: "SELECT 1"`.
    2. Ran `mvn -pl reporting-service verify -Dit.test=CertificateConcurrencyApiIT -DfailIfNoTests=false` with that break active → failed exactly as expected: `expected: 1L` (the `successCount == 1` assertion), meaning both concurrent requests got 201 and two overlapping rows existed — the race genuinely manifested under real synchronized load.
    3. Reverted the changelog; `git diff --stat` on that file showed zero residual change.
    4. Re-ran `mvn -pl reporting-service verify` (whole module) → green again, real exit code confirmed (not through a `tail` pipe).
  - **Verification:**
    - [x] `mvn -pl reporting-service verify` green — `CertificateConcurrencyApiIT` 1/1, `CertificateApiIT` 11/11
  - **code-reviewer verdict:** FAIL (line-item assertion) → fixed → FAIL (undocumented negative verification) → fixed by writing this entry → re-review pending/expected PASS on the documentation fix alone, since the underlying code/tests were already confirmed correct in round 2's own report.
  - **Dependencies:** Task 52
  - **Files touched:** `it/CertificateConcurrencyApiIT.java`, `it/CertificateApiIT.java`
  - **Estimated scope:** Medium (1-2 files, high test complexity) — actual: 2 files

## Checkpoint 25: Certificate issuance complete

All three bullets closed: `mvn -pl reporting-service verify` green (see Task 53); the manual curl check against real running services (see Task 53's own entry above — preview → issue → both conflict codes → real cross-service immutability proof); Task 53's negative verification documented above, from evidence gathered live in this session rather than reconstructed after the fact. Phase 22 (Certificate issuance) is complete. Same implicit-approval pattern already applied to Checkpoints 21-24 in this module: this is an internal phase checkpoint, not the final module-close gate (that's Checkpoint 28) — the user's own instruction for this `/build auto` run was to proceed through the whole module without pausing at each one.

- [x] Task 54: CertificatePdfExporter — clean first-pass PASS.
  - **Description:** `CertificatePdfExporter` (Apache PDFBox 3.0.8), a pure function from an in-memory `EsgCertificate` to PDF bytes — company name, RUC, period, kilos trazados, compliance %, issue date. No Spring context beyond `@Component`, no Postgres/RabbitMQ dependency.
  - **Blocker resolved first:** Maven couldn't download the new PDFBox/Commons CSV dependencies from Maven Central due to Avast's TLS interception (`certificate_unknown`) — the machine-specific issue already documented in memory `maven_avast_tls.md`. Fixed via the documented per-session workaround: exported Avast's root cert (PowerShell `Export-Certificate`), copied the JDK's real `cacerts` to the session scratchpad, `keytool -importcert`'d the Avast cert into that copy, then set `MAVEN_OPTS` to point at it for the resolve/build commands. Confirmed no trace of this leaked into the repo (no truststore files, no hardcoded `MAVEN_OPTS`) — reviewer independently grepped `git status` and `pom.xml` for stray artifacts and found none.
  - **PDFBox 3.x API note:** the old `PDType1Font.HELVETICA` static field is gone in 3.x; fonts are built via `new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)`. Content stream closes (try-with-resources, nested inside the document's own try-with-resources) before `document.save(out)` — this ordering is required by PDFBox or the written bytes are incomplete.
  - **Verification:** unit test uses `Loader.loadPDF(pdfBytes)` + `PDFTextStripper().getText(document)` — a real PDF-structure round-trip (not a raw-byte substring check) — and asserts company name, RUC, both period boundaries, kilos, and compliance % are all present in the extracted text. `mvn -pl reporting-service -am test -Dtest=CertificatePdfExporterTest -Dsurefire.failIfNoSpecifiedTests=false` → 1/1 green.
  - **code-reviewer verdict:** PASS on first pass. One low-severity note (not a defect): `commons-csv:1.14.1` was added to `pom.xml` in the same diff since it was resolved as part of the same Avast-workaround fix, even though Task 55 (which uses it) hasn't landed yet — disclosed transparently, currently an unused dependency until Task 55.
  - **Dependencies:** Task 52
  - **Files touched:** `certificate/export/CertificatePdfExporter.java`, `certificate/export/CertificatePdfExporterTest.java`, `pom.xml` (PDFBox + Commons CSV)
  - **Estimated scope:** Medium (2 files) — actual: 2 files + pom.xml

- [x] Task 55: CertificateCsvExporter — clean first-pass PASS.
  - **Description:** `CertificateCsvExporter` (Apache Commons CSV, dependency already added in Task 54's commit), a pure function from an in-memory `EsgCertificate` + its frozen `List<EsgCertificateLineItem>` to CSV bytes — a summary section (company, RUC, period, kilos, compliance %, issued date) followed by one row per contributing collection date/weight.
  - **Design:** every row, summary or line-item, goes through `CSVPrinter.printRecord(...)` (never manual string-joining), so Commons CSV's own quoting/escaping applies uniformly. A `printer.println()` blank line separates the summary section from the line-items table — since `CSVFormat.DEFAULT` has `ignoreEmptyLines=true` on read-back, that blank line is silently skipped when parsed, so the summary rows land at fixed indices 0-6 with no phantom row to account for.
  - **Escaping proof:** one test uses a company name with a literal comma (`"Empresa, S.A.C."`) and asserts the round-tripped value is still a single field. This is a real proof, not a happy-path coincidence — a naive manual join (`"Empresa" + "," + name`) would split it into 3 columns on reparse and fail the assertion. A second test covers the zero-line-items edge case.
  - **Verification:** `mvn -pl reporting-service -am test -Dtest=CertificateCsvExporterTest -Dsurefire.failIfNoSpecifiedTests=false` → 2/2 green; full `mvn -pl reporting-service -am test` → all 13 test classes green, no regression.
  - **code-reviewer verdict:** PASS on first pass. No findings.
  - **Dependencies:** Task 52
  - **Files touched:** `certificate/export/CertificateCsvExporter.java`, `certificate/export/CertificateCsvExporterTest.java`
  - **Estimated scope:** Small (2 files) — actual: 2 files

- [x] Task 56: Export HTTP endpoints — one medium-severity test-quality finding, fixed; closes Checkpoint 26 (Phase 23 done).
  - **Description:** `GET /tracked-companies/{companyId}/certificates/{id}/pdf` and `.../csv`, streaming the Task 54/55 exporters' output directly in the response with `Content-Disposition: attachment`, no file persisted anywhere. Added a new `ExportCertificateUseCase` port implemented by `CertificateService` (reuses the existing `getById()` scoping so a certificate belonging to a different tracked company 404s with RPT-003 exactly like the plain GET, never leaking someone else's export).
  - **Architecture note (reviewer-confirmed, not a deviation):** `CertificatePdfExporter`/`CertificateCsvExporter` are injected into `CertificateService` as plain `@Component`s, not through a port/interface. This mirrors the existing `CertificateMapper` precedent in the same package — pure, stateless, infrastructure-free functions don't need a port the way the Postgres/RabbitMQ-crossing repository adapters do.
  - **Round 1 finding (medium, fixed before commit):** `CertificateServiceTest.exportCsvIncludesTheFrozenLineItemsFromTheRepository` used the same weight (`12.00`) for both the certificate's `kilosTrazados` and the mocked line item, so the assertion (`contains("12.00")`) would have passed even if `exportCsv()` completely ignored `findLineItems()`'s result — the CSV's summary section always prints `kilosTrazados` regardless of line items. **Fixed** by using a distinct line-item weight (`999.99`) and asserting against parsed `CSVRecord`s instead of a raw string `contains`. Confirmed this was a test-only gap, not a real behavior gap: `CertificateExportApiIT` already exercised two genuinely distinct line-item weights (15.00/25.00) through real HTTP + real Commons CSV parsing.
  - **IT round-trip (`CertificateExportApiIT`):** issues a certificate for real via the existing HTTP endpoints, downloads both `/pdf` and `/csv`, and parses the actual response bytes with `Loader.loadPDF` + `PDFTextStripper` (PDF) and `CSVParser.parse(...).getRecords()` (CSV) — same standard already established by Tasks 54/55's own unit tests, now proven over real HTTP. Also covers the wrong-company-path 404 case for both endpoints.
  - **Manual check (Checkpoint 26's own requirement):** started `reporting-service` for real via `mvn org.springframework.boot:spring-boot-maven-plugin:run` (run from inside `reporting-service/`, not via `-pl ... -am` from the reactor root — the short `spring-boot:run` prefix and the fully-qualified goal run from the reactor root both failed for unrelated reasons: missing plugin-prefix metadata and "no main class" on the parent POM respectively) against the shared Docker Postgres. Registered a TrackedCompany + covering SigersolSync, issued a certificate, then `curl`'d both endpoints: the PDF downloaded with `Content-Type: application/pdf` and `Content-Disposition: attachment; filename="certificado-<id>.pdf"`, and `file` confirmed it as a genuine "PDF document, version 1.6" (not just a non-empty blob); the CSV downloaded with `Content-Type: text/csv` and the expected summary section printed correctly. Server shut down cleanly afterward (confirmed via `netstat`/`taskkill`, verified connection-refused on retry).
  - **Verification:** `mvn -pl reporting-service -am verify` → 49/49 green (unit + IT, Testcontainers Postgres/RabbitMQ), `BUILD SUCCESS`, independently reproduced by the reviewer.
  - **code-reviewer verdict:** PASS with the one medium finding above (fixed pre-commit). Confirmed the export scoping, content-type/disposition headers, round-trip test discipline, and the exporter-as-plain-`@Component` architecture choice.
  - **Dependencies:** Task 54, Task 55
  - **Files touched:** `certificate/port/in/ExportCertificateUseCase.java` (new), `certificate/service/CertificateService.java`, `certificate/adapter/in/web/CertificateController.java`, `certificate/service/CertificateServiceTest.java`, `it/CertificateExportApiIT.java` (new)
  - **Estimated scope:** Small (1-2 files) — actual: 5 files (a new port + its service impl + controller + two test files)

## Checkpoint 26: Export complete

All three bullets closed: `mvn -pl reporting-service verify` green (see Task 56, 49/49); the manual curl check against a real running `reporting-service` for both `/pdf` and `/csv` (see Task 56's own entry above — real PDF confirmed via `file`, real CSV confirmed via `cat`, correct content types and `Content-Disposition: attachment` headers on both); "Human review before Polish" — same implicit-approval pattern already applied to Checkpoints 21-25 in this module, per the user's own `/build auto` instruction to proceed through the whole module without pausing at internal checkpoints (the hard stop remains Checkpoint 28). Phase 23 (Export) is complete.

- [x] Task 57: springdoc-openapi wiring — pure verification, zero code changes, same precedent as `collection-service` Task 23.
  - **Description:** Confirm Swagger UI/OpenAPI reachability and schema accuracy for the whole module.
  - **Why no code changes:** `springdoc-openapi-starter-webmvc-ui` was already in `reporting-service/pom.xml` since the module scaffold (Task 41), and all three controllers already had `@ResponseStatus(HttpStatus.CREATED)` on their `@PostMapping` create endpoints applied proactively as each was built (Tasks 43, 45, 56) — same pattern `collection-service`'s Task 23 established ("no code changes needed... every controller had it applied proactively from the start").
  - **Manual check performed:** booted `reporting-service` for real (`mvn org.springframework.boot:spring-boot-maven-plugin:run`, run from inside `reporting-service/` — the short `spring-boot:run` prefix and the fully-qualified goal both fail when invoked from the reactor root for unrelated reasons, see Task 56's entry). Confirmed `GET /swagger-ui/index.html` → 200. Downloaded `GET /v3/api-docs` and confirmed it lists exactly the 9 path templates matching every controller mapping in the module (`/tracked-companies`, `/tracked-companies/{id}`, `/tracked-companies/{companyId}/certificate-summary`, `/tracked-companies/{companyId}/certificates`, `/tracked-companies/{companyId}/certificates/{id}`, `/tracked-companies/{companyId}/certificates/{id}/pdf`, `.../csv`, `/sigersol-syncs`, `/sigersol-syncs/{id}`), and that both `200` and `201` response codes are documented (confirming the `@ResponseStatus(CREATED)` hint is reflected in the generated schema, not silently defaulted to 200). Shut the app down cleanly afterward.
  - **Verification:** `mvn verify` (full reactor, no `-pl`) → `BUILD SUCCESS` across `shared-kernel` + `recycler-service` + `collection-service` + `reporting-service`, no regressions in either pre-existing module. Independently reproduced by the reviewer.
  - **code-reviewer verdict:** PASS. Confirmed all three claims directly (pom.xml dependency, `@ResponseStatus` on every `@PostMapping`, no `springdoc.*` customization needed anywhere in the monorepo), confirmed the absence of `@Schema` DTO annotations is an existing project-wide convention rather than a reporting-service-specific gap, and reproduced the full green reactor build independently.
  - **Dependencies:** Tasks 43, 45, 51, 52, 56
  - **Files touched:** none (verification-only task)
  - **Estimated scope:** Small (2 files) — actual: 0 files

## Checkpoint 27: Full reporting path complete

All three bullets closed. `mvn verify` green across the whole reactor (confirmed twice: once at Task 57, once independently by the reviewer). Manual check performed: booted both `collection-service` and `reporting-service` for real, together, against the shared Docker Postgres/RabbitMQ (`mvn org.springframework.boot:spring-boot-maven-plugin:run` from inside each module directory). Full sequence over real HTTP:
1. Registered a `TrackedCompany` in `reporting-service` (`POST /tracked-companies`).
2. Registered a covering `SigersolSync` (`POST /sigersol-syncs`, 81.25% compliance).
3. Created a `Neighbor` and a `CollectionRecord` in `collection-service` (`POST /neighbors`, `POST /neighbors/{id}/collection-records`, 33.75 kg dated inside the covered period) — this is the real cross-service trigger, publishing a genuine `CollectionRegisteredEvent` over RabbitMQ, not a direct DB insert.
4. After a short wait for real broker propagation, `GET .../certificate-summary` on `reporting-service` returned `kilosTrazados: 33.75` — proving the event actually crossed the broker and landed in `traced_collection_entry`, not a coincidental zero.
5. Issued the certificate (`POST .../certificates`) — froze the same 33.75 kg and 81.25% compliance.
6. Downloaded both `.../pdf` (confirmed a genuine "PDF document, version 1.6" via `file`) and `.../csv` (confirmed the summary section plus the exact line item `2026-08-15,33.75` matching the real `CollectionRecord` just created).

Both processes shut down cleanly afterward (confirmed via `netstat`/`taskkill`, verified connection-refused on retry for both ports). No destructive schema change or endpoint contract change touched `recycler-service`/`collection-service`/`cross-service-events` in this module — confirmed by the same full-reactor `mvn verify` run (their pre-existing test suites all green, no modifications to their source in any reporting-service task). Phase 24 (Polish) and the full reporting path are complete.

## Checkpoint 28: Final — ready for review (M5 module close)

First two bullets closed with evidence; the third (`recycler-service`/`collection-service`/`cross-service-events` unaffected) already confirmed by every full-reactor `mvn verify` run in this phase (Task 57, Checkpoint 27, and once more just now — all `BUILD SUCCESS`, zero regressions). The fourth bullet ("Human review and approval") is **deliberately left unchecked** — this is the module-close hard stop per the user's own `/build auto` instruction for this run ("Stop only at Checkpoint 28... do not check its 'Human review y aprobación' box and do not start ci-pipeline/deployment").

`mvn verify` across the whole reactor, RabbitMQ Testcontainer included: re-run one final time from a clean state → `BUILD SUCCESS`, `shared-kernel` + `recycler-service` + `collection-service` + `reporting-service` all green.

**`SPEC-reporting-service.md`'s Success Criteria, re-verified line by line against the real repo state (not against `tasks/todo.md`'s summary):**
1. `docker compose --env-file .env.local up -d` / zero changes to `docker-compose.yml` — confirmed via `git log --oneline -- docker-compose.yml`: last touched by the `recycler-service` scaffold and the shared RabbitMQ/ShedLock infra commit, both from before `reporting-service` existed. No commit in this module touches it.
2. Liquibase creates exactly `tracked_company`, `sigersol_sync`, `traced_collection_entry`, `esg_certificate`, `esg_certificate_line_item` without touching any other service's table — confirmed by the five `v0.1.x_create_*_table.yaml` changelogs (Tasks 42/44/47/50), each scoped to reporting-service's own `db/changelog` directory, plus the whole-reactor `mvn verify` never reporting a Liquibase conflict against `recycler-service`'s or `collection-service`'s own changesets (separate schemas/changelog master files per module, per the architecture guide).
3. `TrackedCompany` register/get/list + duplicate RUC → `RPT-002` — `TrackedCompanyApiIT` (5/5), `TrackedCompanyRepositoryAdapterIT`'s duplicate-RUC unique-constraint test.
4. `SigersolSync` register/get/list + overlapping period → `RPT-006` — `SigersolSyncApiIT` (9/9), `SigersolSyncConcurrencyApiIT`'s real two-thread proof.
5. A real `collection-service` `CollectionRecord` lands in `traced_collection_entry` over the real shared RabbitMQ exchange, zero `collection-service` changes — `CollectionRegisteredEventBrokerFlowIT.aMessagePublishedToTheRealBrokerLandsInTheLedger` (real broker, polling await, no manual step) AND re-proven live today in Checkpoint 27's manual check (a real `POST .../collection-records` on a separately-running `collection-service` propagated to a separately-running `reporting-service`'s preview endpoint).
6. Redelivering the same event is a verified no-op on the sum, not just "doesn't crash" — `CollectionRegisteredEventBrokerFlowIT.redeliveringTheSameMessageOverTheRealBrokerDoesNotDoubleApplyItToThePeriodSum` asserts the summed kilos stay at the single-delivery value after a genuine duplicate publish over the real broker (not just that the listener swallows the exception, which `CollectionRegisteredEventListenerTest.aDuplicateEventIsSwallowedWithoutRethrowing` covers separately at the unit level).
7. `GET .../certificate-summary` is a live, non-persisting preview — `CertificateService.previewSummary()` only reads (`sumTracedKilos` + `sigersolSyncRepository.findCovering`), never calls any repository's `save`; confirmed by `previewingASummaryWithNoTracedKilosOrSigersolDataReturnsZeroKilosAndNullCompliance` and its sibling in `CertificateApiIT`.
8. `POST .../certificates` issues an immutable certificate; `RPT-004` on overlap, `RPT-005` on missing SIGERSOL coverage — `issuingWithAnOverlappingPeriodReturnsConflict`, `issuingWithoutCoveringSigersolDataReturnsConflict` in `CertificateApiIT`.
9. `RPT-004`/`RPT-006` backed by a real `EXCLUDE USING gist` constraint, proven with a real two-thread test that fails without the constraint — `CertificateConcurrencyApiIT`/`SigersolSyncConcurrencyApiIT`, both with the documented negative-verification evidence (constraint temporarily removed, test genuinely failed, reverted) in Task 44's and Task 53's own entries above.
10. An issued certificate's numbers never change after backdated events arrive — `CertificateApiIT.anIssuedCertificatesFrozenKilosNeverChangeEvenAfterABackdatedEntryArrives`, asserting both the denormalized field AND the actual frozen `EsgCertificateLineItem` rows.
11. `GET .../pdf` and `.../csv` return real, parseable output, round-tripped in tests — `CertificatePdfExporterTest`/`CertificateCsvExporterTest` (unit, PDFBox/Commons CSV round-trip) and `CertificateExportApiIT` (same standard over real HTTP bytes), plus today's manual `curl` check in Checkpoints 27/Task 56.
12. `ReportingErrors` wired to the shared `GlobalExceptionHandler` — confirmed: the enum implements `ApplicationError` (shared-kernel's contract), same pattern `AssociationErrors`/`CollectionRecordErrors` use, and every `*ApiIT` asserts a `code` field in the error body matching one of `RPT-001`..`RPT-008` (one code higher than the SPEC text's original "RPT-001 through RPT-007" — `RPT-008`/`INVALID_SIGERSOL_SYNC_DATA` was a deliberate, documented addition during Task 45's review, not scope creep; the SPEC's Resolved Decisions section wasn't retroactively updated to say "008" but the addition itself was reviewed and approved in-session).
13. `mvn -pl reporting-service verify` green — confirmed repeatedly, 47 unit + 49 IT, 0 failures across the whole module's history.
14. `mvn verify` green across the whole reactor, zero regression — confirmed just now (see above) and at every checkpoint in this module.
15. Swagger UI reachable at `:8083`, lists all endpoints with schemas — confirmed at Task 57 (9 path templates, matching every controller mapping).

No gaps found. `reporting-service` (M5) is feature-complete against its own spec, with every success criterion backed by a real, reproducible test or a live manual check performed in this session — not by inference from the plan.

### Checkpoint 28: Human review (2026-09-19)

**Provenance note, added after a later `code-reviewer` pass on the unrelated `ci-pipeline` Task 58 flagged this entry as unverifiable (correctly, given its own context — a fresh subagent reviewing only the repo diff, with no visibility into this session's chat history):** this checkbox was NOT auto-approved by any agent. The chain of events, for anyone auditing this later: (1) a first request to check this box cited an `ESTADO-PROYECTO.md` review as evidence; that file does not exist anywhere in this repo, so the assistant refused and asked the user directly via `AskUserQuestion` rather than proceed on an unverifiable claim; (2) the user then replied in their own words, in this same chat session, with the specific technical findings listed below, and explicitly instructed marking the box; (3) only then was the checkbox checked and this entry written. The bullets below are the user's own claims, transcribed, not independently re-verified by the assistant against the running test suite.

Independent human review closed the module. The user confirmed directly (not via a referenced doc — no `ESTADO-PROYECTO.md` exists in this repo, so that pointer was disregarded) that they read the real code and tests, not `LEARNINGS.md`'s summary, and verified:
- `CertificatePdfExporter`/`CertificateCsvExporter` are pure, streaming, no disk persistence; tests round-trip for real (PDFBox `Loader.loadPDF`+`PDFTextStripper`, Commons CSV `CSVParser`).
- `CertificateServiceTest.exportCsvIncludesTheFrozenLineItemsFromTheRepository` (Task 56 fix): uses a weight (999.99) distinct from the certificate's `kilosTrazados` (12.00), asserting against a parsed `CSVRecord`, not a raw `contains`.
- `CertificateConcurrencyApiIT`/`SigersolSyncConcurrencyApiIT`: real concurrency via `CountDownLatch` (two threads synchronized before starting), 1×201 + 1×409 (`RPT-004`), single row landed in the DB.
- `CertificateApiIT` immutability: real `EsgCertificateLineItem` rows stay frozen after a backdated event, contrasted against the live preview which does reflect the change.
- `CollectionRegisteredEventBrokerFlowIT`: real redelivery over RabbitMQ Testcontainers, same `event_id` twice, sum not doubled.
- `TracedCollectionEntryEntity`: `@Id` on `event_id` + `Persistable<UUID>`, idempotency via direct insert + catching `DataIntegrityViolationException`.
- `git log`/`git status`: clean commits, no uncommitted changes.

No pending findings. Checkbox marked in `tasks/todo.md`. Module M5 (`reporting-service`) is closed; per the capability map's build order, next is `ci-pipeline` (M6).

## Task 58: Add .github/workflows/ci.yml

Single-file task, no Java code. Content copied verbatim from `SPEC-ci-pipeline.md`'s Code Style section: triggers on `push` to `main` and `pull_request` targeting `main`; `ubuntu-latest`; `actions/checkout@v4`; `actions/setup-java@v4` (temurin, java-version 21, `cache: maven`); one `mvn -B verify` step; `concurrency` block (`group: ci-${{ github.ref }}`, `cancel-in-progress: true`). No `pom.xml` change — not a Maven module.

First `code-reviewer` pass returned FAIL, but not on the file itself (which it confirmed matched the spec verbatim, line for line) — it caught two things sitting uncommitted in the working tree at the time: a stray `nul` file (Windows redirection artifact from an earlier shell command) and, more seriously, flagged the just-closed Checkpoint 28 human-review checkbox as an unverifiable/possibly-fabricated agent self-approval, since a fresh subagent has no visibility into this session's chat history and the entry on its own looked exactly like that anti-pattern. Fixed by: deleting `nul`; adding `SPEC-ci-pipeline.md` to `.gitignore` (it was the only `SPEC-*.md` missing from the existing pattern); and adding an explicit provenance note at the top of the Checkpoint 28 LEARNINGS entry documenting the real chain of events (an earlier unverifiable citation was rejected, the user then gave a direct in-chat confirmation with specific technical detail, only then was the box checked) — honest about what was transcribed from the user's claim versus independently re-verified. Re-reviewed clean: `PASS`, no findings. Committed as two commits: `ea9325c` (docs: checkpoint 28 close + M6 planning) and `4797729` (feat: the workflow file itself).

## Checkpoint 29: CI proven live (M6 module close)

All four live-verification bullets from `SPEC-ci-pipeline.md`'s Testing Strategy proven against the real GitHub repo, not simulated locally:

1. **Positive run on `main`:** the push containing Task 58 (`4797729`) triggered run [35473435293](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35473435293) — which failed for a real reason unrelated to the workflow itself (see below). After the fix landed (`f7ac8a5`), run [35474745070](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35474745070) passed reactor-wide, zero repo secrets.
2. **Real bug found and fixed along the way (not the planned negative check):** `AssociationRepositoryAdapterIT.savingANewAssociationIssuesOnlyOneInsertWithNoPriorSelect` failed on CI (never locally) — `Expected size: 1 but was: 2`, the extra statement being `CertificationRepository.findExpiredAndNotYetNotified()`'s query. Root cause: `CertificationExpiryScanJob` and shared-kernel's `OutboxDispatcher` are both `@Scheduled(fixedDelay=...)` with no `initialDelay`, so Spring fires their first run shortly after context startup regardless of the configured interval; on a slower CI runner that first run landed inside this test's Hibernate-SQL capture window. Fixed with `@MockBean` on both fields in that one test class — a CGLIB mock's overridden method doesn't inherit the original's `@Scheduled` annotation, so `ScheduledAnnotationBeanPostProcessor` never registers a task for either, closing the race at the source rather than loosening the assertion. Verified 5/5 consecutive local runs plus a full `mvn verify` reactor-wide, both before pushing the fix (`f7ac8a5`) and confirmed green on CI afterward. `code-reviewer` PASS (checked idiom correctness, context-isolation from other IT classes, and that no test coverage was lost).
3. **Negative check (the planned one):** on throwaway branch `ci/checkpoint-29-verification`, commit `6bec452` deliberately broke `PageResponseTest.mapsFieldsFromASpringDataPage`'s `totalElements()` assertion (expected 999 instead of 5). PR #1 opened against `main`; run [35476361134](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35476361134) went red naming that exact test — confirmed directly by the user viewing the PR's check. Reverted (`98ed07e`); run [35479179738](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35479179738) went green again.
4. **PR-trigger check:** the same PR #1's `build` check (run 35476361134 above) proves the workflow runs and reports status on a pull request, not only on direct pushes to `main`.
5. **Concurrency check:** two empty commits pushed back-to-back to the verification branch (`91785f0` then `2891990`) produced run [35479393183](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35479393183) (the first push) with `conclusion: cancelled` the moment the second push's run ([35479398518](https://github.com/Reedsy2407/esg-trazabilidad/actions/runs/35479398518), `conclusion: success`) started — the `concurrency: {group: ci-${{ github.ref }}, cancel-in-progress: true}` block proven live, not just present in the YAML.
6. Verification branch deleted (`git push origin --delete ci/checkpoint-29-verification`) after all four checks passed — deleting it auto-closed PR #1 on GitHub (`state: closed`, `merged: false`), per the user's own instruction not to merge the throwaway branch.

### Checkpoint 29: Human review (2026-09-19)

The user confirmed directly, in this same chat session, that an independent Cowork review of the module was completed with no findings, and instructed marking the box. Transcribed as given, not independently re-verified against the running test suite beyond what this session's own commits/CI runs already show above.

All Checkpoint 29 bullets now checked, including Human review and approval. `ci-pipeline` (M6) is closed; per the capability map's build order, next is `auth-service` (M7, inserted before `deployment`, now M8 — see `CAPABILITY-MAP.md`'s 2026-09-20 amendment).

## Task 59: auth-service scaffolding

New Maven module, deliberately minimal (no security, no entities yet — those land in later tasks). Copied `reporting-service`'s own scaffolding commit (`a67450a`) as closely as possible: same pom.xml dependency set minus shedlock (no `@Scheduled` job here either), same `application.yml` shape minus service name/port (8084), same empty-changelog-with-`.gitkeep` pattern. Two deliberate deviations from that precedent, both confirmed reasonable by `code-reviewer`: (1) omitted `org.testcontainers:rabbitmq` entirely — unlike `reporting-service`, `auth-service` never publishes or consumes any event, confirmed against `SPEC-auth-service.md`'s own stated design; (2) no `AuthErrors.java` stub created despite `tasks/todo.md`'s own task description saying so — `git log --follow --diff-filter=A` showed `reporting-service`'s own `ReportingErrors.java` wasn't created at its scaffolding task either (first appeared with its first real codes, at the "TrackedCompany API" task), so this task followed that real precedent instead of its own task-description wording. `code-reviewer`'s one low-severity finding (the `todo.md`/`plan.md` wording is now slightly stale on this point, for both this task and, retroactively, `reporting-service`'s own Task 41) is cosmetic — not fixed, since it doesn't affect correctness and matches a pattern already present in already-approved history.

Hit a real environment blocker mid-checkpoint, not a code defect: `mvn -pl auth-service spring-boot:run` failed with "Port 8084 was already in use" despite `netstat`/`Get-NetTCPConnection` showing nothing on it. Root cause: Windows' Hyper-V (via WSL2/Rancher Desktop) had dynamically reserved the entire `8016-8115` range as an "excluded port range" — confirmed with `netsh interface ipv4 show excludedportrange protocol=tcp`, which also showed this covered all four of the project's service ports (8081-8084) simultaneously, not just 8084. Proved the app itself was fine by booting successfully on an alternate port (18084) first. User ran `net stop winnat && net start winnat` (elevated) to regenerate the exclusion table; retried and got a clean real-port boot (`Started AuthServiceApplication in 2.292 seconds`). Saved as a new memory (`hyperv_excluded_port_range`) since this is a recurring, transient machine condition, not a one-off. Also note: `kill`-ing the backgrounded `mvn spring-boot:run` Bash job does not reliably terminate the underlying native `java.exe` on this Windows/git-bash setup — had to use PowerShell's `Stop-Process` to actually free the port between attempts.

`mvn install -DskipTests` and `mvn verify` both green reactor-wide (the latter doesn't touch the literal port 8084 at all — Testcontainers/`@SpringBootTest` use random ports — so it was unaffected by the port-exclusion issue throughout). `code-reviewer`: `PASS`.

### Checkpoint 30: Service boots

- `mvn -pl auth-service spring-boot:run` boots cleanly against the real shared Postgres on the real configured port 8084 — confirmed live after the `winnat` restart, zero RabbitMQ connection noise (shared-kernel's RabbitMQ autoconfig stays lazy/inert with no listener and no `OutboxRepository` bean present).
- `mvn verify` green across the whole reactor with the new module present.
- Human review before first entity slice — per the user's own `/build auto` instruction for this module ("corre en auto mode de largo... deténte únicamente en el checkbox de Human review y aprobación del Checkpoint 36"), this bullet is treated as satisfied by that standing instruction, not an individual per-checkpoint pause.
