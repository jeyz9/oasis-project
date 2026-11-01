# Oasis Wellness Hub

React/TypeScript/Vite frontend (`oasis-app`) and Java 21/Spring Boot/PostgreSQL backend (`oasis-api`). Implemented slices cover authentication/RBAC, booking, membership/training/reviews and the payment adapter/order/receipt/refund foundation with an explicit local simulator. The local application now also includes admin user/room/policy management, durable inbox/reminder jobs, and financial/trainer reports. The authorized workflow is delivered through Phase 7; full external integration remains **PARTIAL**: real payment, Email and LINE providers are unconfigured.

- [Project discovery, acceptance criteria and delivery phases](docs/PROJECT_PLAN.md)
- [Proposed architecture, schema, transactions, API and RBAC](docs/ARCHITECTURE.md)
- [Implemented Phase 1, API, verification and limitations](docs/PHASE1_HANDOFF.md)
- [Phase 2 membership/training rules, API and verification](docs/PHASE2_HANDOFF.md)
- [Phase 3 payment adapter, simulation boundary and verification](docs/PHASE3_HANDOFF.md)
- [Final Phase 4–7 acceptance and verification](docs/PHASE4_7_HANDOFF.md)
- [Implemented API reference](docs/API.md)
- [Operations, metrics and recovery](docs/OPERATIONS.md)
- [JPA refactor, layers and verification](docs/JPA_REFACTOR_HANDOFF.md)
- [Working rules](AGENTS.md)

## Local verification (PowerShell)

Use Node 20.19.3 or a version compatible with the checked-in Vite release, npm and JDK 21. Dependencies are already installed in this workspace; on a fresh machine use `npm ci` in `oasis-app`. Maven wrapper downloads its distribution/dependencies on first use, requiring network/cache access.

```powershell
Set-Location D:\project-ai\oasis-project\oasis-app
npm ci # fresh setup only
npm run build
npm run lint
npm run dev
```

The frontend connects `/api` through Vite to `http://127.0.0.1:8080` by default. `OASIS_API_TARGET` can override the local backend target. The optional real-browser journey is launched by the guarded backend test fixture, not by a mocked frontend test.

```powershell
Set-Location D:\project-ai\oasis-project\oasis-api
.\mvnw.cmd test
.\mvnw.cmd package
# Use -o only when all required artifacts are cached.
```

Default tests use test-only H2 and test YAML, without PostgreSQL or payment credentials. Flyway is disabled in those smoke tests; opt in to the separate PostgreSQL/browser suite described in the handoff to verify migrations/concurrency and the UI. Maven needs write access to its local cache for locks. Do not treat old reports in `target/` as current evidence.

## Run the synthetic local demo

From the project root, with PostgreSQL server binaries on PATH (or pass `-PostgresBin 'D:\Program Files\PostgreSQL\18\bin'`):

```powershell
.\scripts\run-local-demo.ps1
# In a second terminal:
Set-Location oasis-app
npm run dev
```

Open http://127.0.0.1:5173. Fixture accounts are `customer@oasis.test`, `trainer@oasis.test`, and `admin@oasis.test`; their synthetic local password is `Synthetic-Test-Only-2030!`. The customer has 10 demonstration credits; signup creates a customer with no credits. The fixture adds tomorrow's classes only on first initialization and never resets existing demo data on restart.

This explicitly opted-in fixture lives on the test classpath and is excluded from the production JAR. It pins `oasis_phase1_demo` on loopback port 55439, separate from the destructive `oasis_phase1_test` test DB. Its PostgreSQL cluster uses local trust authentication and must stay loopback-only; it is a local demonstration environment. No existing `.env` is read or overwritten.

For Phase 2, sign in as admin and open **Manage studio** to create a membership plan and issue a **complimentary** entitlement with a reason. This is free access and never records a payment. Trainers add availability before an admin can publish private sessions. After a session ends, the assigned trainer records every attendee and completes it; attended customers can then submit one review under **My bookings**. Monthly/yearly validity uses Bangkok calendar arithmetic; there is no auto-renewal, rollover or freeze extension.

For Phase 3 local simulation, create a priced plan as admin. Customer opens **My membership → Create test order → Prepare checkout** and chooses a simulated Card/PromptPay confirmation. Server verification activates one synthetic membership and an explicitly labelled SIM- receipt. Admin can request a full refund for an unused membership, prepare dispatch, and simulate its result. No card details, real QR or funds are used. The simulator/launcher exist only in test sources and are excluded from the production JAR; normal payment capabilities are disabled.

Normal backend startup uses `mvnw spring-boot:run` or the executable JAR with configuration from `oasis-api/.env.example`. The default `dev` profile optionally imports `.env` from the process working directory as Java properties. In IntelliJ set Working directory to `D:\project-ai\oasis-project\oasis-api`; when launching elsewhere set `OASIS_ENV_FILE=D:/project-ai/oasis-project/oasis-api/.env` in the runtime environment. Use plain `KEY=value` entries without shell `export` or surrounding quotes; Java properties escaping rules apply. Exported environment variables override file values. The `prod` profile requires runtime configuration and does not import this dev file. There are no default real users/credits in normal startup. Flyway runs migrations; normal runtime must never use the disposable test DB. See [datasource startup repair](docs/DATASOURCE_STARTUP_REPAIR.md) for verification and remaining database setup boundaries.

Keep Flyway clean and automatic baseline disabled. For a nonempty database inspect schema/history before adopting migrations. Tests can use the installed PostgreSQL server binaries without Docker; H2 is only startup smoke coverage. Full PostgreSQL and Chrome test commands are in the handoff.

The user-approved Supabase reset is complete for project `zixzqrsrnthgszgxqnfk`: `public` now contains 28 application tables plus `flyway_schema_history`, with V1–V6 applied and validated through real JDBC. All 29 tables have RLS enabled. Use **Active profiles: dev** with Working directory `oasis-api`, then Rebuild/Restart in IntelliJ. A local JAR connected to this database passed JPA validation and health/services/sessions HTTP checks without seeding accounts. No server was deployed. See [the verified reset handoff](docs/DATASOURCE_STARTUP_REPAIR.md).

At the user's explicit request all 28 CREATE TABLE statements now use IF NOT EXISTS. This does not make every ALTER/INDEX/trigger statement rerunnable or reconcile different existing columns. Migration checksums changed as part of this approved fresh reset: existing local demo databases with earlier V1–V6 history need a separately reviewed upgrade/reset plan; do not automatically repair checksums or delete their data. The optional `supabase` profile and `scripts/prepare-supabase-schema.sql` remain a separate private-schema alternative and were not used for the approved public reset. Selecting that profile requires its own prepared schema; it is not the profile for the current hosted public tables.

## Run with Docker Compose

Requires Docker Engine and the Docker Compose plugin. Compose starts its own PostgreSQL 17 database, runs the backend with the `prod` profile so Flyway migrates that local database and JPA validates it, then serves the built frontend with Nginx. Nginx proxies `/api` to the backend. The Postgres data persists in the `oasis_postgres_data` named volume.

From the repository root, copy `.env.example` to `.env` and replace the sample `POSTGRES_PASSWORD` with a strong local password. This root `.env` is only for the Compose database. Do not copy `oasis-api/.env` here: that file contains the Supabase datasource settings for IDE runs.

```powershell
Copy-Item .env.example .env
# Edit .env and set POSTGRES_PASSWORD to a strong local value.
docker compose up --build -d
docker compose ps
docker compose logs -f api
```

Open http://localhost:5173. To stop the containers while retaining database data, run `docker compose down`. To remove the local database volume as well, run `docker compose down --volumes`; that permanently deletes the Compose database contents. This stack is for local development; before serving it publicly, use HTTPS and set `OASIS_COOKIE_SECURE=true` in the root `.env`.

## Integration status

The shared verified-event pipeline, order snapshots, deduplication, immutable receipts and unused full-refund lifecycle are implemented and tested with the local simulator. Real provider credentials/protocol/card/PromptPay support and sandbox validation are pending; email and LINE OA remain unconfigured. A browser redirect never marks an order paid. Deployment, production infrastructure, commit and push remain outside scope.

## Final local increment

Admin opens **Administration** to manage roles/enabled accounts, rooms/capacity, the new-session cancellation policy, fixed trainer teaching fees, notification retries, reconciliation counters and recent audit events. **Reports** separates real verified cash from simulation and shows daily/monthly sales, completed teaching sessions, utilization and fee summaries. Trainers see their own **My performance**. Customers receive booking confirmations/cancellations and one-hour reminders in **Inbox** when the worker runs; inbox ownership and read status are server-enforced.

Email and LINE preferences do not configure a provider. Those jobs remain BLOCKED in normal runtime; a LINE destination must come from a future trusted linking flow. The local test/demo transport simulates external outcomes without sending messages. Do not interpret a simulated receipt, notification or report as real funds/delivery.

```powershell
# From the project root: isolated synthetic database only.
.\scripts\start-test-postgres.ps1
Set-Location oasis-api
$env:OASIS_PG_TEST='true'
$env:OASIS_BROWSER_TEST='true' # installed Chrome + local Playwright required; see OPERATIONS.md
.\mvnw.cmd -o test
Set-Location ..
.\scripts\verify-local-recovery.ps1
```

The PostgreSQL suite resets only its exact pinned `oasis_phase1_test` database; never point normal runtime there. Fresh-schema migration and backup/restore checks create and remove only their own UUID-named databases. The demo database is separate and is not reset. See the final handoff for commands actually executed and remaining integration limits.

## Backend layers and JPA

Backend code is organized into `config`, `controller`, `models`, `dto`, `repository`, `service` and `service/impl` under `oasis-api/src/main/java/com/jeyz9/oasisapi`. All nine production controllers live in `controller/`, keep their existing routes and depend on service interfaces. Spring Data JPA/EntityManager replaces application JdbcTemplate access; PostgreSQL-specific locks, upserts and reports remain parameterized JPA native queries inside repositories. See the architecture and JPA handoff for examples.

Flyway V1–V6 own the schema. Their current source includes the approved table-creation guards and RLS settings documented in the reset handoff; existing databases that used earlier migration checksums need a separately reviewed upgrade plan. Hibernate validates the PostgreSQL schema (`ddl-auto=validate`) in prod and integration tests; do not switch to create/update. H2 smoke tests disable schema creation, while the opted-in PostgreSQL suite verifies the actual mappings, constraints and concurrency. Only guarded test/demo fixtures retain direct SQL setup/assertions on the test classpath.

Runtime configuration classes (SecurityConfig, PaymentConfiguration and NotificationConfiguration) live in `config/`, alongside the package-private SessionGuard security helper. Profile YAML files retain Spring Boot’s conventional resource locations. See [configuration handoff](docs/CONFIG_HANDOFF.md).

## Java import convention

Use explicit imports for classes, annotations and static methods/fields in both main and test code. Wildcard imports are prohibited by AGENTS.md. Check from the project root with `./scripts/check-java-imports.ps1`. Unused legacy package/resource folders and unreferenced frontend template assets have been removed; retained domain folders contain active integration/security contracts.

[Unused-folder cleanup and explicit-import verification](docs/CLEANUP_HANDOFF.md)

## Supabase SQL verification

Project `zixzqrsrnthgszgxqnfk` runs V1–V6 in its public schema: 28 application tables plus Flyway history, with RLS enabled on all 29 tables. A real JDBC startup verified Flyway checksums, JPA mappings and read-only API endpoints. Earlier rollback-only SQL compatibility checks are recorded separately in [Supabase SQL verification and limits](docs/SUPABASE_SQL_HANDOFF.md). `python scripts/build-supabase-probe.py` generates the rollback-only test SQL without reading credentials.

## Startup after a Java package move

The current service implementation package is `com.jeyz9.oasisapi.service.impl` (`service/impl/`). After renaming/moving Java packages, clean generated classes before restarting:

```powershell
Set-Location D:\project-ai\oasis-project\oasis-api
.\mvnw.cmd -o clean test
# Then run OasisApiApplication in the IDE, or use explicit datasource environment settings:
.\mvnw.cmd spring-boot:run
```

In IntelliJ, stop the old run and use **Build → Rebuild Project** before running again. An incremental build can retain `target/classes/.../serviceImpl` beside the new `service/impl`, causing duplicate Spring bean names even though only one source file exists. The local demo launcher now performs Maven clean before test-run as well. [Startup repair evidence](docs/STARTUP_REPAIR.md)
