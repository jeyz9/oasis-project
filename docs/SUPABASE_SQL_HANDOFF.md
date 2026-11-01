# Supabase SQL compatibility verification

Historical SQL-only result below. A later user-approved public reset applied the updated V1–V6 through real Flyway/JDBC and verified full Spring Boot startup, JPA validation and read-only HTTP endpoints. Public now has 28 application tables plus Flyway history, all RLS-enabled. See [current reset verification](DATASOURCE_STARTUP_REPAIR.md); the former 10-table/no-history state and old migration hashes below describe the earlier probe only.

2026-10-27 (Asia/Bangkok). **COMPLETE for the SQL-only scope selected by the user.** Project `zixzqrsrnthgszgxqnfk` (`oasis`), region ap-southeast-1, remote PostgreSQL **17.6**. This does not switch the website/API to Supabase, run Spring Boot/JPA remotely, certify concurrent hosted sessions or activate a payment provider.

## Actual remote result

The Supabase connector successfully executed the rollback-isolated SQL probe: **16 check groups passed**, applying the SQL from all **six existing migration scripts** and creating **28 application tables** inside a unique private scratch schema. This is a migration-SQL compatibility test; it did not run Flyway's JDBC engine or write migration history remotely.

[Recorded result and post-check JSON](verification/supabase-sql-result.json)

| Check | Remote result |
| --- | --- |
| `six_migration_scripts_and_28_tables` | PASS |
| `private_schema_denies_client_roles` | PASS |
| `no_key_update_and_guarded_booking_debit` | PASS |
| `full_session_guard` | PASS |
| `duplicate_booking_constraint` | PASS |
| `grant_owner_foreign_key` | PASS |
| `immutable_credit_ledger` | PASS |
| `credit_restoration_once` | PASS |
| `canonical_room_identity` | PASS |
| `room_and_trainer_interval_exclusion` | PASS |
| `unlimited_and_unique_review_storage` | PASS |
| `jsonb_integer_money_and_immutable_payment_snapshots` | PASS |
| `payment_event_deduplication_and_single_fulfillment` | PASS |
| `bangkok_cash_flow_periods` | PASS |
| `skip_locked_outbox_and_inbox_deduplication` | PASS |
| `final_credit_and_seat_reconciliation` | PASS |

These checks exercise SQL storage/constraints and the same guarded update shapes used by the application: FK ownership, interval exclusions, duplicate records/events, immutable financial history, integer minor units/JSONB, restoration/reconciliation, Bangkok period cutoffs and SKIP LOCKED query support. They do **not** assert server role authorization, cancellation-policy eligibility, webhook verification or actual multi-session race behavior; those belong to the existing local API/JPA suite.

## Isolation and preservation

A session-local `pg_temp` helper creates a uniquely named private schema inside a PL/pgSQL subtransaction. It denies PUBLIC/anon/authenticated schema access, executes the unchanged migration SQL, enables RLS on scratch tables, inserts only synthetic fixtures and runs assertions. A dedicated caught exception rolls back that entire subtransaction before returning the report. The helper is SECURITY INVOKER, not SECURITY DEFINER. No project roles/credentials, branches, API keys, external messages or application data are created permanently.

The probe requires btree_gist to be installed already, so its `CREATE EXTENSION IF NOT EXISTS` cannot introduce a new project-wide extension. Existing extension placement is not changed. It never runs the local fixture's TRUNCATE/CREATE DATABASE/DROP DATABASE operations against Supabase.

Post-check verified:

- Scratch schema absent: **true**; remaining `oasis_probe_*` schemas: **0**.
- Existing public table OIDs unchanged; public table count remains **10**, all **10** with RLS enabled.
- Existing users/services/bookings/grants/ledger counts remain **0**.
- Public Flyway history remains absent. No Supabase migration history entry was created.

The project currently has the original ten V1-style public tables, not the full current 28-table application schema. Do not start the normal migration-enabled application against this existing schema without reviewing schema adoption/version history first; automatic baseline/clean remains disabled. A future JDBC integration should use an isolated branch/private schema and explicit server-side credentials, separately authorized/configured. This test did not alter public schema or RLS policies to make assertions pass.

## Reproducible artifacts and commands actually run

- `scripts/build-supabase-probe.py` builds the self-rolling-back query from current V1–V6 files plus `oasis-api/src/test/resources/supabase/probe-checks.sql`. It only writes generated SQL/metadata locally and does not connect or read credentials.
- `python scripts/build-supabase-probe.py` generated `.local/supabase-probe.sql` and a SHA-256 migration manifest at `.local/supabase-probe-manifest.json`.
- Rehearsal: `psql -h 127.0.0.1 -p 55439 -U oasis_test -d oasis_phase1_test -v ON_ERROR_STOP=1 -At -f .local/supabase-probe-rehearsal.sql` — PASS, 16 checks, PostgreSQL 18.1. This rehearsal also creates missing synthetic anon/authenticated roles **only inside a local transaction that is rolled back**. Transcript `.local/supabase-probe-rehearsal.log`.
- Remote execution: Supabase `execute_sql`, project_id `zixzqrsrnthgszgxqnfk`, query from `.local/supabase-probe.sql` — PASS, 16 checks, PostgreSQL 17.6. The initial connector request returned an expired request-state error without a usable result; a read-only inspection confirmed no scratch schema remained, and the next execution returned the recorded PASS.
- Independent post-check query — PASS for schema cleanup/public OIDs/counts/RLS, at the timestamp recorded in the result JSON.
- `scripts/check-java-imports.ps1` — PASS. No Java source/dependency/runtime configuration changes were made in this SQL-only increment.

Existing V1–V6 migration files remain unchanged. The probe builder is a test generator, not a new Flyway/Supabase migration; it does not produce deployment migration history.

## Existing advisory findings

Security advisors before/after show the same pre-existing findings: 10 informational RLS-enabled tables without policies, one warning for mutable search_path on `public.reject_history_mutation`, and one warning for btree_gist in public. No public policy/function/extension change was made. The performance advisor also reports unindexed foreign keys and unused indexes; unused constraint indexes must not be removed merely because this empty project has no usage statistics.

## Documentation reviewed and remaining integration boundary

The Supabase skill and Postgres best-practice guidance were used. The current [Supabase changelog](https://supabase.com/changelog) was checked, including the [PostgreSQL 15.19/17.11 breaking-change notice](https://supabase.com/changelog/postgres-15-19-17-11-breaking-changes). Results here are specific to the actual 17.6 server queried; no database upgrade/reindex was performed.

For future Spring Boot/JPA connectivity, Supabase's [official Spring Boot guide](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot) specifies Direct or Session pooling rather than the Transaction pooler as the main Hibernate datasource, and recommends a private application schema. Use verified TLS with the project CA according to [SSL documentation](https://supabase.com/docs/guides/platform/ssl-enforcement). Keep Flyway ownership and ddl-auto=validate from this repository, rather than adopting tutorial ddl-auto=update. No database password was available, requested in chat or reset; no publishable/service-role key is a substitute for a JDBC password. The user explicitly selected SQL-only testing during this turn.

No commit, push, deployment, permanent schema migration, production data deletion or real gateway connection occurred.
