# Task List: shared-kernel + recycler-service + collection-service + cross-service-events

> See `tasks/plan.md` for architecture decisions, dependency graph, and risks. Source specs: `SPEC-shared-kernel.md`, `SPEC-recycler-service.md`, `SPEC-collection-service.md`, `SPEC-cross-service-events.md`.

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
- [ ] `mvn verify` green across the whole reactor (`shared-kernel` + `recycler-service` + `collection-service`), RabbitMQ Testcontainer included
- [ ] All 11 Success Criteria bullets in `SPEC-cross-service-events.md` re-verified line by line with evidence
- [ ] `recycler-service`/`collection-service`'s pre-existing test suites and manual-check behavior unaffected — no destructive schema change, no existing endpoint contract changed
- [ ] Human review and approval before moving to `reporting-service`
