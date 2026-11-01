# JPA and backend-layer refactor handoff

2026-10-27, Asia/Bangkok. **COMPLETE for the requested backend refactor.** Real provider integrations remain outside scope. No commit, push, deploy, production connection or schema/history deletion occurred. One agent performed implementation/testing/self-review directly; no Codex CLI or independent reviewer was used.

## Result and acceptance

The existing Java 21/Spring Boot 4.1.2 backend now follows:

`controller → service interface → serviceImpl → repository → JPA models/PostgreSQL`

| Layer | Actual implementation |
| --- | --- |
| `models/` | 28 JPA entities covering all V1–V6 domain tables. Composite ID classes, lazy read-only relationships, integer money, Instant timestamps, JSONB snapshots and immutable ledger/audit/receipt mappings. No cascading financial-history deletion. |
| `dto/` | 13 DTO containers separating HTTP requests/responses, validation, authenticated Principal and payment gateway contracts from entities. Existing JSON field names remain stable. |
| `service/` | 11 business interfaces. AccountService extends UserDetailsService; controllers/security/worker/test fixture depend on contracts. Bean Validation parameter constraints declared on interfaces once. |
| `serviceImpl/` | 11 implementations retaining business decisions, server authorization, ownership, idempotency and transaction orchestration. No SQL/ResultSet/JdbcTemplate calls in implementations. |
| `repository/` | 8 Spring Data JpaRepository interfaces, 11 domain repositories and 3 shared JPA projection/query helpers (22 files). No dependency on service implementations. |

JPA entity/JPQL operations implement account/role persistence and lookup, service/room CRUD, membership plan and eligibility replacement, availability/review inserts, and owned Inbox reads/read-state. Booking session/booking/grant locks return mapped entities through Spring Data native queries. PostgreSQL-specific upserts, NO KEY UPDATE/SHARE/SKIP LOCKED, atomic guarded balances/seats, immutable ledger/fulfillment and reports use parameterized JPA native queries inside repositories. This preserves critical database semantics; it is not a claim that all SQL became JPQL.

Production source no longer imports or calls JdbcTemplate, Spring JDBC APIs, java.sql or ResultSet. Explicit Spring Data JDBC starter/test-starter dependencies are removed. The JPA provider necessarily still uses the PostgreSQL JDBC driver and framework connection infrastructure. Guarded test/demo fixture setup and independent database assertions retain test-only JdbcTemplate; those fixtures are excluded from the production JAR.

## Data and behavior compatibility

- Flyway V1–V6 bytes/checksums are unchanged. No migration was rewritten or added. Hibernate validates the existing schema in dev/prod, PostgreSQL tests and the local demo; never create/update the PostgreSQL schema automatically. H2 remains a schema-free startup smoke fixture, with Flyway/DDL disabled.
- JpaTransactionManager governs business transactions. Existing @Transactional/TransactionTemplate boundaries and lock order remain. NO KEY UPDATE is explicitly retained rather than replaced with a potentially stronger ORM lock that conflicts with FK KEY SHARE locks.
- EntityManager bulk writes flush before execution and clear afterward, preventing cached entity state from overwriting guarded credit/seat/payment changes. Database row locks remain until outer transaction completion. A new rollback/context-invalidation test verifies this behavior.
- Relationship mappings are lazy and read-only over existing scalar FK fields. Public controllers accept/return DTOs, with open-in-view disabled; ownership and composite integrity remain enforced by service checks and PostgreSQL constraints. JSONB snapshots load through JPA without exposing entity graphs.
- All public routes, request/response fields, status codes, CSRF/session behavior and real-versus-simulated payment boundaries remain unchanged. Gateway calls/verification remain outside database locks. Cancellation and payment event replay behavior is preserved.
- Temporal projections normalize to Instant/LocalDate; JPA binding uses UTC, with business date logic still Asia/Bangkok. Native report parameter casting uses CAST(? AS text), which Hibernate parses correctly.

## Commands actually executed and results

| Command/location | Evidence |
| --- | --- |
| `oasis-api`: `.\mvnw.cmd -o test` without PG/browser opt-in | PASS: 5 smoke/unit tests passed, 54 PostgreSQL tests skipped at that point; no PostgreSQL connection used. `.local/jpa-smoke.log`. |
| PostgreSQL targeted four-test command below | PASS: 4 tests, 0 failures/errors/skips. Report/cutoff regression plus new ORM bulk rollback/context and JSON/lazy tests. `.local/jpa-targeted.log`. |
| `oasis-api`: `$env:OASIS_PG_TEST='true'; $env:OASIS_BROWSER_TEST='true'; .\mvnw.cmd -o clean package` | **PASS: 61 tests, 0 failures, 0 errors, 0 skips**, executable production JAR built. 5 smoke/unit + 56 PostgreSQL/JUnit tests, including five Chrome scenarios through four browser JUnit tests. `.local/jpa-full-check.log`. |
| `oasis-api`: `.\mvnw.cmd -o -DskipTests package` | PASS after final import cleanup, concise queue method names and typed booking repository parameter signatures. No business behavior changed after the full passing suite. `.local/jpa-final-package.log`. |
| Final affected-regression command below | PASS: 8 tests, 0 failures/errors/skips after typed booking signatures and queue method rename. `.local/jpa-final-regressions.log`. |
| `oasis-app`: `npm.cmd run build`, `npm.cmd run lint` | PASS: TypeScript/Vite production build, 26 modules; lint exit 0. Frontend source unchanged. |
| Root: `.\scripts\verify-local-recovery.ps1` | PASS: restore into fresh UUID-named isolated DB; checks `6,0,0` (six migrations, zero credit mismatches, zero seat mismatches). Restore DB removed; test/demo source data retained. `.local/jpa-recovery.log`, `.local/recovery-check/result.json`. |
| `jar tf oasis-api/target/oasis-api-0.0.1-SNAPSHOT.jar` plus exclusion assertions | PASS: no simulator/local launcher/PG test/H2 library or spring-data-jdbc library in the production artifact. JPA models and layers packaged. |
| Source boundary/migration comparison | PASS: no direct JDBC production source; no SQL in service/interface layers; no entity annotations in DTOs; all migration files byte-for-byte unchanged. `.local/jpa-review.json`. |
| `git -c core.autocrlf=false -c core.whitespace=cr-at-eol diff --no-index --check` | No whitespace diagnostics in source, pom, docs or README. Exit 1 reflects differences in no-index mode; workspace has no Git repository/HEAD. Snapshot `.local/jpa-before`. |

Targeted command (PowerShell, oasis-api directory, OASIS_PG_TEST=true):

```powershell
.\mvnw.cmd -o '-Dtest=PhaseOnePostgresTests#reportFormulasSeparateSimulatedCashAndSnapshotCommissions+salesCashFlowsUseBangkokDayCutoffsAndRejectInvalidRanges+nativeBulkWritesInvalidateManagedStateAndRollbackAtomically+mappedJsonSnapshotsAndLazyRelationsDoNotLeakThroughDtos' test
```

Final affected-regression command (same directory and PG opt-in):

```powershell
.\mvnw.cmd -o '-Dtest=PhaseOnePostgresTests#concurrentLastSeat+concurrentOneCreditAcrossSessions+concurrentIdenticalIdempotencyKeyCreatesOnce+cancelAtExactBoundaryRestoresOnceAndAllowsRebooking+concurrentRepeatedCancellationRestoresOnce+notificationOutboxInboxOwnershipAndCancellationAreDurable+notificationRetriesDeadLettersAndExplicitRetryDoNotUndoBooking+nativeBulkWritesInvalidateManagedStateAndRollbackAtomically' test
```

The full PostgreSQL suite validates all 28 mappings against the migrated schema, fresh-database V1–V6 migration, authorization/ownership, duplicate/parallel bookings and one-credit contention, exact cancellation/refund restoration, payment replays/refunds, reminder leases/retries and report formulas. New ORM tests prove JpaTransactionManager use, bulk cache invalidation/rollback, JSON snapshots and lazy relations behind DTOs. Chrome uses the unchanged real frontend against the random-port test API/PostgreSQL, including admin CRUD/inbox/report/payment/training flows; logs/screenshots remain in `.local/browser-results`.

## Self-review and repairs

The review compared source against `.local/jpa-before`, including added entities/DTOs/repositories and moved implementations, dependency/config changes and guarded fixtures. It checked SQL placement/parameter binding, mapping types/relationships, authorization/validation proxies, financial transaction order, stale persistence context, schema ownership and API compatibility.

Initial compile checks exposed moved-type imports and Instant cast precedence, corrected before runtime tests. The first full backend PostgreSQL pass had two report failures because the native positional parameter next to PostgreSQL ::text was parsed differently by Hibernate. Replaced it with standard CAST syntax; targeted report tests and the final complete 61-test suite pass. Subsequent review converted standard CRUD paths to ORM/JPQL, removed unused native wrappers, specialized scalar projections, retained typed entity locks and gave critical booking commands typed arguments. No failures were bypassed and security checks remain enabled.

Updated README, PROJECT_PLAN, ARCHITECTURE, API and OPERATIONS explain the new layers. Earlier phase handoffs retain historical test evidence and persistence decisions; the Phase 4–7 handoff points here as the current implementation record.

## Running and limitations

The local demo launcher/script and preview URL remain unchanged: http://127.0.0.1:5173, synthetic API on :8080. Restarting the API requires signing in again because sessions remain in memory. The demo's existing database/data is retained; JPA validates it without changing schema. The restarted demo reports eight JPA repository interfaces, Flyway schema up to date, API health UP and frontend HTTP 200 (`.local/jpa-demo.log`). See README/OPERATIONS for account and launch instructions.

Persistence remains PostgreSQL-specific where exclusion constraints, JSONB, exact lock modes, upserts and report SQL are required; H2 only proves wiring. Repositories intentionally include native JPA queries, so future changes to those queries still require PostgreSQL concurrency tests. This increment does not add distributed sessions, real payment/Email/LINE adapters, tax invoices, deployment or production load certification. Existing integration/business limits remain documented in earlier handoffs.

## Changed-file manifest

Grouped changes: 11 former combined business classes replaced by service contracts/implementations plus repositories/DTOs; new 28-entity model; controllers/security/worker/gateway/test fixtures retargeted to contracts; pom/test/runtime JPA configuration updated; two ORM regression tests added. Full path/status manifest also saved as `.local/jpa-changes.json`.

- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/admin/AdminController.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/admin/Administration.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/booking/BookingController.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/booking/Bookings.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/catalog/Catalog.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/catalog/CatalogController.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/AccountDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/AdministrationDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/AuthDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/BookingDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/CatalogDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/MembershipDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/NotificationDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/OrderDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/PaymentGatewayDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/PaymentProcessingDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/RefundDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/ReportDto.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/dto/TrainingDto.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/identity/Accounts.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/identity/AuthController.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/identity/SecurityConfig.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/identity/SessionGuard.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/membership/MembershipController.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/membership/Memberships.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/AttendanceRecord.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/AuditEvent.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Booking.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/BookingRequest.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/BusinessSettings.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/ClassSession.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/ComplimentaryRequest.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/CreditLedgerEntry.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/GrantService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/MembershipGrant.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/MembershipOrder.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/MembershipPlan.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Notification.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/NotificationJob.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/NotificationPreference.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/OrderServiceLink.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Payment.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/PaymentEvent.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/PaymentReceipt.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/PlanService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Refund.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Review.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/Room.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/TrainerAvailability.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/TrainerTerm.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/User.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/UserRole.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/models/WellnessService.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/NotificationConfiguration.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/NotificationController.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/Notifications.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/Orders.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/PaymentConfiguration.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/PaymentController.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/PaymentGateway.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/PaymentProcessing.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/payment/Refunds.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/reporting/ReportController.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/reporting/Reports.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/AccountRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/AdministrationRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/BookingEntityRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/BookingRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/CatalogRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/ClassSessionRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/JpaNativeQueries.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/MembershipGrantRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/MembershipPlanRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/MembershipRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/NotificationRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/OrderRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/PaymentProcessingRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/ProjectionMapper.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/ProjectionRow.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/RefundRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/ReportRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/RoomRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/TrainingRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/UserRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/UserRoleRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/repository/WellnessServiceRepository.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/AccountService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/AdministrationService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/BookingService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/CatalogService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/MembershipService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/NotificationService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/OrderService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/PaymentProcessingService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/RefundService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/ReportService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/service/TrainingService.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/AccountServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/AdministrationServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/BookingServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/CatalogServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/MembershipServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/NotificationServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/OrderServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/PaymentProcessingServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/RefundServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/ReportServiceImpl.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/serviceImpl/TrainingServiceImpl.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/shared/Actor.java`
- ADDED `oasis-api/src/main/java/com/jeyz9/oasisapi/shared/TimeValues.java`
- REMOVED `oasis-api/src/main/java/com/jeyz9/oasisapi/training/Training.java`
- MODIFIED `oasis-api/src/main/java/com/jeyz9/oasisapi/training/TrainingController.java`
- MODIFIED `oasis-api/src/main/resources/application-dev.yml`
- MODIFIED `oasis-api/src/main/resources/application-prod.yml`
- MODIFIED `oasis-api/src/main/resources/application.yml`
- MODIFIED `oasis-api/src/test/java/com/jeyz9/oasisapi/LocalDemo.java`
- MODIFIED `oasis-api/src/test/java/com/jeyz9/oasisapi/PhaseOnePostgresTests.java`
- MODIFIED `oasis-api/src/test/java/com/jeyz9/oasisfixture/PaymentSimulationConfiguration.java`
- MODIFIED `oasis-api/src/test/resources/application-pg-test.yml`
- MODIFIED `oasis-api/src/test/resources/application.yml`
- MODIFIED `oasis-api/pom.xml`
- MODIFIED `docs/API.md`
- MODIFIED `docs/ARCHITECTURE.md`
- MODIFIED `docs/OPERATIONS.md`
- MODIFIED `docs/PHASE4_7_HANDOFF.md`
- MODIFIED `docs/PROJECT_PLAN.md`
- MODIFIED `README.md`
- ADDED `docs/JPA_REFACTOR_HANDOFF.md`

## Controller package follow-up

All nine production controllers now reside in `com.jeyz9.oasisapi.controller`. Their routes, DTOs, service contracts and security boundaries are unchanged. The simulator remains in the guarded test fixture. See [controller handoff](CONTROLLER_HANDOFF.md) for move-specific verification.
