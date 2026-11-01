# Local operations and integration runbook

Updated 2026-10-27. This is a tested local handoff, not a deployment or production readiness assertion. No existing .env, real credentials, real customer records or production system was used.

## Start and verify

Prerequisites: Java 21, Node compatible with the checked-in Vite 8 release, npm, installed PostgreSQL binaries. Existing Maven/node caches allow offline builds. Normal runtime selects dev/prod profile. Dev optionally imports `.env` from the process working directory (normally `oasis-api`), or from the absolute path in runtime variable `OASIS_ENV_FILE`. Entries use Java properties syntax, without shell `export` or surrounding quotes. Runtime environment variables override file values. Prod requires explicit runtime configuration and does not import the dev file. Normal runtime creates no demo administrators. Local trust authentication is confined to the isolated loopback fixture cluster.

```powershell
# From D:\project-ai\oasis-project
.\scripts\start-test-postgres.ps1
.\scripts\run-local-demo.ps1
# Separate terminal
Set-Location oasis-app
npm run dev
```

Preview: http://127.0.0.1:5173. Demo API: http://127.0.0.1:8080/api/v1/health. Account/password and the fixture boundaries are in README. Demo restart preserves `oasis_phase1_demo`; it does not refresh old classes or reset data. Create new future sessions through Admin if demonstration dates have passed. Do not run a second backend on the same port.

```powershell
Set-Location oasis-api
# Default smoke only (no PostgreSQL/browser):
Remove-Item Env:OASIS_PG_TEST -ErrorAction SilentlyContinue
Remove-Item Env:OASIS_BROWSER_TEST -ErrorAction SilentlyContinue
.\mvnw.cmd -o test
# Full synthetic integration + browser verification:
$env:OASIS_PG_TEST='true'
$env:OASIS_BROWSER_TEST='true'
.\mvnw.cmd -o clean package
Set-Location ..\oasis-app
npm run build
npm run lint
```

Browser tests require installed Google Chrome and Playwright at `.local/browser-tools/node_modules/playwright/index.mjs`; alternatively export `OASIS_PLAYWRIGHT_MODULE` with the absolute index.mjs path. On a fresh machine install the Playwright package into that local tools directory. No browser driver is added to normal runtime. JUnit starts Vite against its random API port and runs five browser scenarios (Phase 1; Phase 2 membership and training; Phase 3 payments; final administration/inbox/reports). Each subprocess has a 120-second timeout and cleanup. Browser logs/screenshots are in `.local/browser-results`.

Tests pin `oasis_phase1_test` on 127.0.0.1:55439 and verify its exact datasource before resetting synthetic tables. They do not read the ordinary datasource configuration. The empty-database migration test creates its own `oasis_migration_check_<UUID>` and removes only that database. H2 smoke tests do not prove PostgreSQL constraints or concurrency. Do not run concurrent test instances against the same fixture DB.

## Migrations and backup/recovery

Flyway V1–V6 are executable and checksum-validated. Keep clean disabled, automatic baseline disabled and JPA ddl-auto validate. V5/V6 were exercised as additive upgrades from the previous V4 local test schema; all six also run in a newly created DB. For a previously unmanaged/nonempty database, inspect and map schema/history before adoption; never use baseline or clean to hide a mismatch. Canonical-room backfill may expose previously overlapping case-variant locations: resolve the real scheduling conflict before adopting V5. Validate physical room capacity; backfilled/implicit rooms default to 100 while existing class caps are preserved.

```powershell
# Only the guarded synthetic fixture; run after tests stop mutating it.
.\scripts\verify-local-recovery.ps1
```

The recovery script checks cluster ownership, uses pg_dump custom format on `oasis_phase1_test`, restores into a fresh UUID-named `oasis_restore_check_*`, compares migration/credit/seat invariants, writes `.local/recovery-check/result.json` and drops only that new DB. The source test DB and demo DB remain intact. This demonstrates a local restore; it is not an encrypted offsite backup, RPO/RTO guarantee or production recovery drill. For future production, back up before migration, validate restoration into an isolated target, and plan corrective forward migrations rather than deleting financial history.

## Worker and diagnostics

Main runtime defaults OASIS_NOTIFICATION_WORKER_ENABLED=true, OASIS_NOTIFICATION_WORKER_DELAY_MS=10000. Test contexts disable scheduling for deterministic manual cycles; the opted-in local demo enables it. Admin Administration shows reconciliation counts, latest 200 queue records and recent 25 audit events. Use the paginated audit API for older records. Inbox pages contain 25 owned items and poll every 10 seconds; preference drafts are not overwritten by this poll.

Queue flow: PENDING/RETRY → PROCESSING (60-second token lease) → DELIVERED/BLOCKED/RETRY/DEAD/CANCELLED. Each cycle handles at most 20 jobs. Four retry delays are 30/60/120/240 seconds; the fifth failure dead-letters. Expired leases recover after a crash. Fix the underlying disabled provider before an audited manual retry; repeatedly retrying BLOCKED jobs does not enable an integration. Stable job UUIDs deduplicate inbox inserts and must be used by a future external provider. Outbound sends must timeout before lease expiry. External delivery is at least once; cancellation/opt-out racing a provider call cannot unsend a message.

Confirmation/cancellation and one-hour-before reminders are durable booking events. Reminder creation is skipped if already due when booked; cancellation clears pending reminder leases. Opting into a channel later does not retroactively create jobs. LINE linking and email verification/provider setup must precede real delivery. Current normal transport makes no external network request and reports BLOCKED. Test/demo transport only records simulated success/failure.

`/health` is a minimal DB probe. Server errors contain a request ID; error logs show request ID/class only to avoid leaking provider/customer data. Current monitoring is local diagnostics, not a hosted alerting service. Alerting, retention, dead-letter escalation and backup scheduling require a future operations decision.

## Metric definitions

- Package count: accepted SUCCEEDED payment captures in the selected interval/mode; complimentary access excluded.
- Gross: sum captured amount_minor for those captures, by captured_at. Refunds: SUCCEEDED full refunds by confirmed_at, linked to accepted payments in the same simulation mode. Net = gross − refunds; refund-day net can be negative. This is cash movement, not tax/accounting revenue recognition.
- REVIEW captures are excluded from sales and reported separately as unreconciledMinor. Pending/failed/duplicate captures do not create extra sales. REAL and SIMULATED never aggregate together; UI defaults REAL.
- Periods are [from midnight, to midnight) Asia/Bangkok; daily/monthly buckets include zeros. Partial monthly date filters include only movements within the requested dates, labelled by month start.
- Classes taught: COMPLETED sessions whose starts_at lies in the interval. Capacity summed once per completed session; attendance is ATTENDED bookings, no-shows are NO_SHOW. Utilization = attended / capacity (null for no capacity). Cancelled sessions excluded; all classes taught may include zero attendees.
- Commission: sum fixed commission_minor snapshotted on those completed sessions. Later trainer terms do not rewrite past or already assigned fees. No percentage allocation, payroll deduction or payout confirmation is implied.
- Credit mismatch: CREDITS grant remaining balance differs from sum of immutable ledger deltas. Seat mismatch: reserved_count differs from non-CANCELLED bookings. Expected both zero. Do not repair either by blindly overwriting totals: inspect booking/ledger history and use an audited corrective process.

## External release dependencies and current limits

Real card/PromptPay integration needs a chosen provider, credentials, verified raw webhook protocol, provider-side idempotency, timeout/reconciliation policy and sandbox evidence. Real Email needs verified sender, consent/verification policy, bounce handling and idempotent timeout-bounded transport. LINE needs OA credentials and authenticated account-linking; the API intentionally exposes no destination setter. Tax invoices require separate business/tax details. No secrets are invented.

Sessions and auth throttle remain in memory; multi-instance production needs shared sessions/throttle or a deliberate affinity/revocation design. Password recovery, email verification, recurring billing, percentage commissions, partial/used membership refunds, recurring trainer availability, multi-branch tenancy, retention policy, production alerting and deployment remain outside the delivered local increment. Reports are bounded synchronous queries, not benchmarked for production-scale load. Financial/admin screens expose authorized sensitive data and should be served over HTTPS with secure cookies in a future configured environment.

## JPA implementation update

Current production persistence is JPA (see JPA_REFACTOR_HANDOFF.md). Flyway/checksums are unchanged. Dev/prod and PostgreSQL tests validate the existing schema after migration; H2 smoke disables Flyway/DDL and only checks wiring/configuration. Entity relations are lazy, open-in-view is disabled, and HTTP responses remain DTOs. Native JPA bulk writes flush then clear managed state to prevent stale seat/credit data. Do not replace NO KEY UPDATE with an ORM lock that upgrades it to FOR UPDATE without rerunning the FK/concurrency regressions. Production Hibernate SQL-error detail logging is suppressed to retain redacted API error handling.
