# Development datasource startup repair

Updated 2026-10-27.

## Current result: approved public reset and real hosted startup

The user explicitly confirmed deletion/recreation of the ten original Oasis public tables, then requested IF NOT EXISTS on every table create. This replaced the earlier private-schema proposal for this run. The verified target was active/healthy Supabase project `zixzqrsrnthgszgxqnfk` (`oasis`). Preflight confirmed the exact ten expected tables, no history, no private schema, and zero rows in all ten tables.

Implemented `scripts/reset-approved-oasis-public.sql`: explicit transaction, 5-second lock timeout, exclusive locks on the ten named tables, exact table-set and empty-data guards, one DROP TABLE statement with RESTRICT (no CASCADE), and removal of the V1 trigger function so Flyway can recreate it. Any unexpected table/data/dependency causes rollback. The public schema, Supabase-managed schemas, extension, users/credentials and other infrastructure were preserved. The approved reset was executed successfully through the connector after local rehearsal.

All 28 CREATE TABLE statements in V1–V6 now use IF NOT EXISTS. Each migration enables RLS on the tables it creates, in the same transaction. Both trigger functions now have a fixed pg_catalog search path. Business constraints, indexes, state rules and financial logic were preserved. These are user-requested revisions to the initial migration set for a fresh reset; earlier applied databases have different checksums and must not be silently repaired. IF NOT EXISTS alone neither reconciles existing definitions nor makes all ALTER/INDEX/trigger statements idempotent.

Real Flyway JDBC ran the updated V1–V6 over the existing Session pooler with TLS required, guarded host/port/database/project username checks and automatic baseline/clean disabled. Flyway itself created `public.flyway_schema_history`; all six migrations applied and validated. The approved ten-table reset did not use Flyway clean. RLS on the history table was enabled **after** Flyway returned and released its history lock. An initial local attempt to do that inside V6 hit a self-lock timeout; that statement was removed from V6 before hosted execution and the full suite rerun successfully. Source files, packaged resources and hosted checksums agree.

Independent hosted post-check: **29 public tables = 28 application tables + history, RLS enabled 29/29, versions 1–6 all successful, users=0, bookings=0, no oasis_app schema**. The default business_settings singleton is migration configuration, not a seeded customer/demo account. Existing auth/storage/platform schemas were not reset.

The built production JAR was then started locally against the actual Supabase public database with dev profile, Hibernate ddl-auto=validate, explicit public schema, TLS required, worker disabled, and an ephemeral loopback HTTP port. Results: Flyway checksum validation PASS/no pending migration, Hibernate mapping validation PASS, application started, health UP, GET services and sessions HTTP 200. No signup, booking, payment or synthetic fixture was performed remotely. Only this temporary verification process was stopped; IDE port 8080 was not occupied by it. For the user's normal run: select **dev**, Working directory `oasis-api`, and Rebuild/Restart so the IDE copies the updated migration resources. The optional private-schema profile remains unused for this hosted setup.

### Actual verification commands and evidence

- Guarded local disposable `oasis_phase1_test` public schema reset to test the revised migration set; no local demo reset.
- From `oasis-api`: `$env:OASIS_PG_TEST='true'` followed by `.\mvnw.cmd -o clean package`; browser opt-in absent. Final result: **67 discovered, 63 passed, 4 browser tests skipped, no failures/errors**. Includes PostgreSQL booking/credit/payment/review/outbox/reporting behavior, migration/constraints and a new assertion for all 28 application tables having RLS. Log `.local/public-reset-postgres-tests-final.log`. The earlier failed run is retained as `.local/public-reset-postgres-tests.log`.
- `scripts/check-java-imports.ps1`: PASS. All imports remain explicit.
- Local fresh-database rehearsal: V1 then `psql -v ON_ERROR_STOP=1 -f scripts/reset-approved-oasis-public.sql`, followed by Flyway via `.local/ApprovedPublicMigration.java`. Six migrations/history/RLS verified. Log `.local/public-reset-flyway-rehearsal.log`.
- Hosted reset: reviewed script through Supabase execute_sql after the user's explicit confirmation. Hosted migration: `java --class-path <cached dependencies> .local/ApprovedPublicMigration.java approved-zixzqrsrnthgszgxqnfk` from `oasis-api`; credentials resolved in memory from Spring configuration, not passed in argv. Log `.local/public-reset-supabase-migration.log`; outputs summary only with Flyway logging suppressed.
- Actual JAR `java -jar ... --spring.profiles.active=dev` with the public/TLS/validation/loopback/worker overrides described above; HTTP probes and process shutdown. Log `.local/supabase-public-startup.log`.
- Self-review compared all six SQL migrations against `.local/public-reset-before`, confirming only intended table-create guards, RLS and trigger search paths. Final SHA-256 manifest `.local/public-reset-migration-manifest.json`. No Git metadata is present, so snapshot diff/semantic comparison was used.

Security advisors after the reset show no RLS-disabled errors and no mutable-function-search-path warning. RLS-without-policy informational notices are expected for this server-owned JDBC application: anon/authenticated get no row policies, and browser users use Spring authorization, not direct Data API access. The existing [btree_gist-in-public warning](https://supabase.com/docs/guides/database/database-linter?lint=0014_extension_in_public) remains; moving the extension was outside the approved reset. The JDBC migration owner remains privileged; a separate least-privilege runtime role is future hardening.

No commit, push, deployment, real gateway integration, secret output, credential reset, or data import occurred. Do not edit the newly applied migrations further for future changes: use new versioned migrations. Local demo databases with old V1–V6 checksums remain untouched and may require a separately reviewed plan before restarting that fixture.

All earlier sections below describe prior stages and proposals; this verified public reset supersedes their pending-migration status.

## Follow-up: nonempty public schema without history at 17:30

The supplied trace now successfully connects and fails at the intended Flyway guard: `Found non-empty schema(s) "public" but no schema history table`. This is a schema/version ownership issue; changing passwords or enabling auto-baseline does not verify that the existing schema matches V1. Read-only hosted inspection confirms 10 public tables, no Flyway history, btree_gist already installed, and no existing `oasis_app` schema.

### Prepared solution and approval boundary

Use a new private `oasis_app` schema with fresh V1–V6 history. Existing public tables/data remain separate. This follows the private-schema direction in [Supabase's Spring Boot guide](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot); this project retains Flyway and Hibernate validation rather than the guide's automatic DDL examples.

Prepared files:

- `scripts/prepare-supabase-schema.sql`: transactionally checks that the schema is absent and btree_gist installed, creates the empty schema under the migration owner, and revokes schema access from PUBLIC and existing anon/authenticated roles. It fails rather than adopting an existing schema. It does not create users, reset passwords or alter public tables.
- `oasis-api/src/main/resources/application-supabase.yml`: explicitly selected profile; imports the existing environment file; uses JDBC currentSchema `oasis_app,public`, Hibernate default_schema `oasis_app`, and Flyway default/schema `oasis_app`. Requires TLS, validates entities, refuses automatic schema creation and baseline, and keeps clean disabled. It does not expose the schema through Supabase's Data API.
- Two config-data tests added to the existing DevEnvironmentConfigurationTests cover schema alignment, TLS and migration safety settings. All Java imports remain explicit.

Hosted execution is **pending approval** under AGENTS.md's externally consequential action boundary. After approval, the exact intended sequence is:

1. Recheck project `zixzqrsrnthgszgxqnfk`, schema absence, extension placement and public table OIDs/counts.
2. Run the reviewed preparation SQL once as the existing migration owner.
3. Start the backend with explicit active profile `supabase` (IntelliJ Active profiles), from `oasis-api`. Existing Session pooler credentials stay in `.env`; do not put passwords on the command line. Flyway applies the unchanged V1–V6 scripts into the private schema.
4. Verify six successful SQL migrations, 28 application tables plus the Flyway history table, JPA validation, HTTP health, private-schema access restrictions, and unchanged public tables. No synthetic fixture accounts/data will be created on Supabase.

No baseline of public, public data transfer, cleanup, deletion, real payment or deployment is part of this proposal. A failed later migration may leave earlier successful versions committed; inspect the history and repair deliberately, never run clean/drop as a recovery shortcut. The currently supplied postgres migration owner is not a production least-privilege runtime role; separate runtime-role provisioning remains future hardening. Do not add the private schema to Data API exposed schemas. Explicitly selecting the new profile is required: dev still targets the existing default schema and will continue to report the public/history error.

### Completed local rehearsal

Created a new dedicated loopback-only database whose name is recorded in `.local/private-schema-db.txt`, in the verified fixture cluster `127.0.0.1:55439`. Applied V1 to public to reproduce the hosted starting point, then ran the preparation script. Started the built production JAR with profile `supabase` and explicit local URL/user/empty fixture password, missing synthetic env file, ephemeral loopback HTTP port and notification worker disabled. SSL was disabled only for this local trust fixture via a command-line override; the checked-in hosted profile requires SSL.

Actual results: V1–V6 applied in the private schema; Hibernate validated and started; HTTP health UP; CSRF and customer registration passed. Post-check: public still 10 tables and 0 users, private 29 tables including history and exactly 1 synthetic registered user, 6 successful SQL migrations. Stopped only the temporary local backend process. Evidence: `.local/private-schema-startup.log`, `.local/private-schema-public-seed.log`, `.local/private-schema-prepare.log`.

Commands actually executed: `scripts/start-test-postgres.ps1`; local `createdb`; `psql -h 127.0.0.1 -p 55439 -U oasis_test -d <new-rehearsal-db> -v ON_ERROR_STOP=1 -f oasis-api/src/main/resources/db/migration/V1__identity_catalog_membership_booking.sql`; the same psql invocation with `-f scripts/prepare-supabase-schema.sql`; `java -jar oasis-api/target/oasis-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=supabase` with the guarded local overrides described above; HTTP health/CSRF/register and SQL post-checks. From `oasis-api`, `.\mvnw.cmd -o clean package` passed: **11 passed, 56 opt-in PostgreSQL tests skipped**, 67 discovered, zero failures/errors. Build evidence: `.local/private-schema-build.log`. `scripts/check-java-imports.ps1` passed. This is a local rehearsal, not a claim of full hosted application startup or the complete PostgreSQL business suite.

No Supabase mutation, baseline or migration was executed in this increment. No existing migration files, `.env`, production config, application business logic or frontend files were changed. No commit/push/deploy.

## Follow-up: password authentication failure at 17:26

The next supplied trace ends in PostgreSQL SQL state **28P01**, `password authentication failed`. The server was now reachable; this was a credential rejection, separate from the earlier connection timeout. The configured pooler username had the expected project suffix and the port was 5432. The user corrected the database password directly in `.env` and explicitly requested another connection attempt. No password was exposed, reset remotely, or added to source.

Using the actual Spring config-data loader (without an application context), a standalone Java JDBC probe connected with the newly supplied credentials. It opened a read-only transaction, queried `SELECT 1`, table count and Flyway history existence, then rolled back and closed the connection. **PASS: JDBC authentication and read-only query; 10 public tables; no public Flyway history**. This is direct JDBC evidence, superseding the earlier TCP-only and connector-only checks. Probe source/log: `.local/JdbcCredentialsCheck.java`, `.local/jdbc-credentials-probe.log`.

Also removed `oasis-api/src/main/java/com/jeyz9/oasisapi/config/WebConfig.java`, a newly added custom Flyway factory that hardcoded `baselineOnMigrate(true)` and immediately invoked `migrate()`. It overrode the YAML setting `spring.flyway.baseline-on-migrate: false` and bypassed Boot's configured Flyway initialization. Spring Boot now owns Flyway configuration again. Auto-baseline can mark a nonempty schema without verifying equivalence to V1, so it must not substitute for a schema adoption plan; see [Flyway baseline-on-migrate documentation](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

Verification from `oasis-api`: `.\mvnw.cmd -o clean package` succeeded, with 9 tests passed and 56 opt-in PostgreSQL tests skipped (zero failures/errors). From the root: `.\scripts\check-java-imports.ps1` passed. The deleted source was reviewed against `.local/auth-fix-before`; clean packaging removes its stale bytecode. No other application code, migrations or datasource settings were changed in this increment. The current user-edited dev `ddl-auto: none` was preserved; it does not validate schema compatibility.

Full Supabase-backed Spring Boot startup is still unverified: schema adoption remains required before allowing Flyway to apply V1–V6. No remote migration, baseline, schema change, commit, push or deployment was performed. The previous verification statements below describe earlier stages only.

## Follow-up: pooler connection timeout at 17:19

The new trace reaches Flyway's database connection step and ends in `java.net.SocketTimeoutException: Connect timed out` (SQL state 08001). This is a different failure from the earlier unresolved datasource URL. Inspection found port **6453** in the local Supabase pooler URL. The documented shared session-pooler port is **5432**; transaction pooling uses **6543**, not 6453. Session pooling supports prepared statements and persistent backends; see [Supabase connection documentation](https://supabase.com/docs/guides/database/connecting-to-postgres).

Verification with Python `socket.create_connection((host, port), timeout=5)` from this machine: 6453 timed out; 5432 was reachable. Updated only the URL's port in `oasis-api/.env` from 6453 to 5432. An in-memory byte comparison confirmed all other entries, including username and password, were preserved; no credential copy or output was created. This local environment change is separate from tracked application configuration. Restart the IDE run process after correcting any stale datasource environment override.

During final verification an external edit briefly changed the port to 6543. The user explicitly selected Session pooler 5432 in response to clarification. Final inspection found 5432 configured and a repeat TCP probe succeeded. No conflicting external edit was overwritten.

A read-only connector query (`SELECT 1`, table count, Flyway history existence) succeeded: 10 public tables and no `public.flyway_schema_history`. This confirms server availability through the connector, not successful JDBC authentication from the application. No Spring Boot launch or Flyway migration was attempted against Supabase. Existing schema adoption still needs a deliberate plan; do not enable automatic baseline or Hibernate schema updates to bypass that step.

Reran `.\mvnw.cmd -o '-Dtest=DevEnvironmentConfigurationTests' test` from `oasis-api`: 4 passed, no failures/errors. Evidence: `.local/pooler-port-config-tests.log`. There were no Java code changes or dependency changes for this fix. Reviewed the one-field environment edit and this documentation update; no commit, push or deployment.

The sections below record the earlier JDBC URL configuration repair and its verification at that time.

## Diagnosis and change

The supplied startup trace fails with `IllegalArgumentException: 'url' must start with "jdbc"`. The dev datasource references `${SPRING_DATASOURCE_URL}`, but Spring was not configured to import the existing `.env`. The inspected process environment had no datasource variables. A redacted inspection found an unquoted PostgreSQL JDBC URL in the existing file; its contents are not reproduced here.

`oasis-api/src/main/resources/application-dev.yml` now imports `optional:file:${OASIS_ENV_FILE:./.env}[.properties]`. This uses Spring's supported extension hint for an extensionless properties file: [externalized configuration documentation](https://docs.spring.io/spring-boot/reference/features/external-config.html). Only dev imports this file. Production configuration and database migrations are unchanged.

In IntelliJ select dev (the existing default) and set Working directory to `D:\project-ai\oasis-project\oasis-api`. For another working directory set runtime variable `OASIS_ENV_FILE=D:/project-ai/oasis-project/oasis-api/.env`. Environment variables take precedence over file values, so remove or correct stale datasource overrides in the run configuration. Use plain Java properties entries, without surrounding quotes or shell `export`; properties escaping rules apply. A missing optional file still requires datasource values from the runtime environment; there is no fallback database.

The previous duplicate service bean issue is documented separately in `STARTUP_REPAIR.md`. Rebuild the project after package moves to discard stale class files.

## Verification

Commands executed from `oasis-api`:

```powershell
.\mvnw.cmd -o '-Dtest=DevEnvironmentConfigurationTests' test
.\mvnw.cmd -o clean package
```

The four new config tests use temporary synthetic files and ConfigDataEnvironmentPostProcessor only: no datasource or application context is created by these tests. They cover URL/credentials, a runtime override, prod isolation, and a missing optional file. All four passed. The complete default build reports 65 discovered tests: 9 passed, 56 opt-in PostgreSQL tests skipped, zero failures/errors. The application context smoke tests use test-only H2 configuration. Maven initially could not acquire a cache lock within the filesystem sandbox; the build was rerun with cache access.

From the repository root, `scripts/check-java-imports.ps1` passed. Changes were self-reviewed using snapshot diffs and inspection of the new test. A SHA-256 comparison confirmed the existing `.env` was unchanged. No credentials were added to source or documentation.

## Boundaries

This verifies development configuration loading with synthetic data and the default build. It does not verify a JDBC connection to Supabase or a full startup against that database. No remote connection, migration, baseline, schema modification, commit, push or deployment was performed. The previously inspected Supabase public schema had no Flyway history and requires an explicit schema adoption plan before normal application startup can safely run its migrations; see `SUPABASE_SQL_HANDOFF.md`.
