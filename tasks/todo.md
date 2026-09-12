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

- [ ] Task 2: Error handling (`ApplicationError`, `ApplicationException`, `GlobalExceptionHandler`)
  - **Description:** Implement the shared error-handling contract every service will use: a typed error interface, an exception wrapping it, and a `@RestControllerAdvice` translating it to RFC 7807 `ProblemDetail`.
  - **Acceptance criteria:**
    - [ ] `ApplicationError` interface with `getCode()`, `getMessage()`, `getStatus()` exists in `pe.esgtrazabilidad.kernel.error`
    - [ ] `ApplicationException` wraps an `ApplicationError`, exposes it via a getter
    - [ ] `GlobalExceptionHandler` catches `ApplicationException` and returns a `ProblemDetail` with the error's code/message/status
    - [ ] Unit test proves the mapping from a sample `ApplicationError` to the resulting `ProblemDetail` fields
  - **Verification:**
    - [ ] Tests pass: `mvn -pl shared-kernel test`
    - [ ] Manual check: read the test to confirm it asserts status code, error code, and message — not just "no exception thrown"
  - **Dependencies:** Task 1
  - **Files likely touched:** `shared-kernel/src/main/java/.../error/ApplicationError.java`, `.../error/ApplicationException.java`, `.../error/GlobalExceptionHandler.java`, `shared-kernel/src/test/java/.../error/GlobalExceptionHandlerTest.java`
  - **Estimated scope:** Medium (4 files)

- [ ] Task 3: Event strategy + ID generation (`EventPublishingStrategy`, `IdGenerator`)
  - **Description:** Add the pluggable event-strategy enum (no publishing logic yet — just the type every service will branch on later) and a UUID v7 generator utility.
  - **Acceptance criteria:**
    - [ ] `EventPublishingStrategy` enum with exactly `GCP_PUB_SUB`, `MOCK`, `SPRING_EVENTS`
    - [ ] `IdGenerator` produces UUID v7 values (RFC 9562 time-ordered), verified by generating N ids and asserting monotonic time-ordering
    - [ ] Unit tests for both
  - **Verification:**
    - [ ] Tests pass: `mvn -pl shared-kernel test`
  - **Dependencies:** Task 1
  - **Files likely touched:** `shared-kernel/src/main/java/.../events/EventPublishingStrategy.java`, `.../id/IdGenerator.java`, corresponding test files
  - **Estimated scope:** Small (4 files)

### Checkpoint 1: shared-kernel complete
- [ ] `mvn -pl shared-kernel install` succeeds standalone
- [ ] `mvn -pl shared-kernel test` green
- [ ] Human review before starting `recycler-service`

## Phase 2: recycler-service infra

- [ ] Task 4: recycler-service scaffolding
  - **Description:** Create the `recycler-service` Maven module (depending on `shared-kernel`), the root `docker-compose.yml` with Postgres, an empty Liquibase master changelog, `application.yml`, and the shared `PageResponse<T>` DTO used by every future list endpoint.
  - **Acceptance criteria:**
    - [ ] `recycler-service/pom.xml` depends on `shared-kernel`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, Postgres driver, Liquibase core, Testcontainers (test scope)
    - [ ] `esg-trazabilidad/docker-compose.yml` at repo root starts a Postgres container with a named volume
    - [ ] `db/changelog/db.changelog-master.yaml` exists with `includeAll` on its folder (empty folder is fine for now)
    - [ ] `PageResponse<T>` generic wrapper exists in `pe.esgtrazabilidad.recycler` (or a shared location if reused later)
    - [ ] App boots with `spring-boot:run` against the Docker Postgres with no schema errors
  - **Verification:**
    - [ ] Build succeeds: `mvn -pl recycler-service -am install`
    - [ ] Manual check: `docker compose up -d && mvn -pl recycler-service spring-boot:run` boots cleanly, `/actuator/health` (if enabled) or root context responds
  - **Dependencies:** Task 1, Task 2, Task 3 (needs `shared-kernel` installed)
  - **Files likely touched:** `recycler-service/pom.xml`, `docker-compose.yml`, `recycler-service/src/main/resources/application.yml`, `recycler-service/src/main/resources/db/changelog/db.changelog-master.yaml`, `PageResponse.java`
  - **Estimated scope:** Medium (5 files)

### Checkpoint 2: Service boots
- [ ] `docker compose up -d` starts Postgres
- [ ] `mvn -pl recycler-service spring-boot:run` boots cleanly, empty changelog applies
- [ ] Human review before first entity slice

## Phase 3: Association

- [ ] Task 5: Association persistence
  - **Description:** Schema, domain model, and persistence adapter for `Association` — no HTTP surface yet.
  - **Acceptance criteria:**
    - [ ] Liquibase changelog `v0.1.0_create_association_table.yaml` creates the `association` table: `name`, `ruc` (unique, 11 chars), `registration_number`, `address`, `contact_email`, `contact_phone`, `status`
    - [ ] `Association` domain class with a `status` of `ACTIVE`/`SUSPENDED`
    - [ ] JPA entity + Spring Data repository + a `AssociationRepository` port + adapter implementing it (domain never leaks the JPA entity)
    - [ ] `AssociationErrors` enum implementing `ApplicationError` (`ASO-001` not-found, `ASO-002` duplicate RUC, at minimum)
    - [ ] Unit test for any domain-level validation logic on `Association`
  - **Verification:**
    - [ ] Tests pass: `mvn -pl recycler-service test`
    - [ ] Manual check: Liquibase changelog applies cleanly against the Docker Postgres (`docker compose up -d && mvn -pl recycler-service spring-boot:run`, confirm table exists via `psql` or a client)
  - **Dependencies:** Task 4
  - **Files likely touched:** `db/changelog/v0.1.0_create_association_table.yaml`, `association/domain/Association.java`, `association/adapter/out/persistence/{AssociationEntity,AssociationJpaRepository,AssociationRepositoryAdapter}.java`, `association/port/out/AssociationRepository.java`, `association/exception/AssociationErrors.java`
  - **Estimated scope:** Large (6 files) — persistence-only, no controller/service yet

- [ ] Task 6: Association API
  - **Description:** Expose Association CRUD over HTTP: mapper, request/response DTOs with validation, use case interfaces, service, controller.
  - **Acceptance criteria:**
    - [ ] `POST /associations` creates an association, validates RUC format (11 digits) via `jakarta.validation`, returns 409 via `AssociationErrors.DUPLICATE_RUC` on conflict
    - [ ] `GET /associations/{id}` returns 404 via `AssociationErrors.NOT_FOUND` when missing
    - [ ] `GET /associations` returns a paginated `PageResponse<AssociationResponse>`, filterable by `status` via a composed `Specification<Association>` (not a monolithic lambda)
    - [ ] Unit test for `AssociationService` (Mockito-mocked repository port)
    - [ ] IT test (`AssociationApiIT` or similar, RestAssured + Testcontainers) covering: create → get → list, plus the duplicate-RUC and not-found error paths
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl recycler-service test`
    - [ ] Integration tests pass: `mvn -pl recycler-service verify`
    - [ ] Manual check: exercise all three endpoints via Swagger UI or curl against `docker compose up` Postgres
  - **Dependencies:** Task 5
  - **Files likely touched:** `association/adapter/in/web/{AssociationController,AssociationMapper}.java`, DTOs (`CreateAssociationRequest`, `AssociationResponse`), `association/port/in/*UseCase.java`, `association/service/AssociationService.java`, `AssociationServiceTest.java`, `it/AssociationApiIT.java`
  - **Estimated scope:** Large (7 files)

### Checkpoint 3: Association CRUD works end-to-end
- [ ] `mvn -pl recycler-service verify` green
- [ ] Manual check: create → get → list an association via Swagger/curl
- [ ] Human review before Recycler slice

## Phase 4: Recycler

- [ ] Task 7: Recycler persistence
  - **Description:** Schema, domain model, and persistence adapter for `Recycler`, with a required FK to `Association`.
  - **Acceptance criteria:**
    - [ ] Liquibase changelog `v0.1.1_create_recycler_table.yaml` creates the `recycler` table: `full_name`, `dni` (unique, 8 chars), `phone`, `association_id` (FK, not null), `status`
    - [ ] `Recycler` domain class, `status` of `ACTIVE`/`INACTIVE`
    - [ ] JPA entity + repository + port + adapter, same shape as Association
    - [ ] `RecyclerErrors` enum: `REC-001` not-found, `REC-002` duplicate DNI, `REC-003` association-not-found (used when creating a recycler under a nonexistent association)
    - [ ] Unit test covering the FK-must-exist business rule at the service/domain layer (not left to the DB's FK constraint alone, so the error is typed)
  - **Verification:**
    - [ ] Tests pass: `mvn -pl recycler-service test`
  - **Dependencies:** Task 5 (Association schema and repository must exist to validate the FK)
  - **Files likely touched:** `db/changelog/v0.1.1_create_recycler_table.yaml`, `recycler/domain/Recycler.java`, `recycler/adapter/out/persistence/*.java`, `recycler/port/out/RecyclerRepository.java`, `recycler/exception/RecyclerErrors.java`
  - **Estimated scope:** Large (6 files)

- [ ] Task 8: Recycler API
  - **Description:** Expose Recycler CRUD nested under its association.
  - **Acceptance criteria:**
    - [ ] `POST /associations/{associationId}/recyclers` validates DNI format (8 digits), returns `REC-003` 404 if the association doesn't exist, `REC-002` 409 on duplicate DNI
    - [ ] `GET /associations/{associationId}/recyclers/{id}` returns 404 via `REC-001` when missing
    - [ ] `GET /associations/{associationId}/recyclers` returns paginated, filterable `PageResponse<RecyclerResponse>`
    - [ ] Unit test for `RecyclerService`
    - [ ] IT test covering create → get → list, plus association-not-found and duplicate-DNI paths
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl recycler-service test`
    - [ ] Integration tests pass: `mvn -pl recycler-service verify`
  - **Dependencies:** Task 7
  - **Files likely touched:** `recycler/adapter/in/web/{RecyclerController,RecyclerMapper}.java`, DTOs, `recycler/port/in/*UseCase.java`, `recycler/service/RecyclerService.java`, `RecyclerServiceTest.java`, `it/RecyclerApiIT.java`
  - **Estimated scope:** Large (7 files)

### Checkpoint 4: Recycler CRUD works end-to-end
- [ ] `mvn -pl recycler-service verify` green
- [ ] Manual check: creating a recycler under a nonexistent association returns a typed 404 (`REC-003`), not a raw 500
- [ ] Human review before Certification slice

## Phase 5: Certification

- [ ] Task 9: Certification persistence
  - **Description:** Schema, domain model with the `isExpired()` business rule, and persistence adapter for `Certification`.
  - **Acceptance criteria:**
    - [ ] Liquibase changelog `v0.1.2_create_certification_table.yaml` creates the `certification` table: `association_id` (FK, not null), `certification_type`, `issued_date`, `expiration_date`
    - [ ] `Certification` domain class exposes `isExpired()` computed from `expirationDate` vs. current date — no stored, independently-updatable status column
    - [ ] JPA entity + repository + port + adapter
    - [ ] `CertificationErrors` enum: `CER-001` not-found, `CER-002` association-not-found, `CER-003` invalid date range (`issuedDate` not before `expirationDate`)
    - [ ] Unit tests for `isExpired()` boundary cases: expires today, already expired (yesterday), far future
  - **Verification:**
    - [ ] Tests pass: `mvn -pl recycler-service test`
  - **Dependencies:** Task 5 (Association must exist to validate the FK)
  - **Files likely touched:** `db/changelog/v0.1.2_create_certification_table.yaml`, `certification/domain/Certification.java`, `certification/adapter/out/persistence/*.java`, `certification/port/out/CertificationRepository.java`, `certification/exception/CertificationErrors.java`
  - **Estimated scope:** Large (6 files)

- [ ] Task 10: Certification API
  - **Description:** Expose Certification CRUD nested under its association.
  - **Acceptance criteria:**
    - [ ] `POST /associations/{associationId}/certifications` validates `issuedDate < expirationDate`, returns `CER-002` 404 if the association doesn't exist, `CER-003` on invalid date range
    - [ ] `GET /associations/{associationId}/certifications/{id}` returns 404 via `CER-001` when missing, response includes computed `expired: boolean`
    - [ ] `GET /associations/{associationId}/certifications` returns paginated `PageResponse<CertificationResponse>`
    - [ ] Unit test for `CertificationService`
    - [ ] IT test covering create → get → list, plus association-not-found and invalid-date-range paths, and one case each for expired/not-expired in the response
  - **Verification:**
    - [ ] Unit tests pass: `mvn -pl recycler-service test`
    - [ ] Integration tests pass: `mvn -pl recycler-service verify`
  - **Dependencies:** Task 9
  - **Files likely touched:** `certification/adapter/in/web/{CertificationController,CertificationMapper}.java`, DTOs, `certification/port/in/*UseCase.java`, `certification/service/CertificationService.java`, `CertificationServiceTest.java`, `it/CertificationApiIT.java`
  - **Estimated scope:** Large (7 files)

### Checkpoint 5: Full CRUD path complete
- [ ] `mvn verify` green across the whole reactor
- [ ] Manual check: Association → Recycler → Certification chain works end-to-end through real HTTP calls

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
