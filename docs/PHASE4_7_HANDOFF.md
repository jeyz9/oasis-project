# Phase 4–7 delivery and verification

Historical delivery evidence below predates the user-requested JPA/layer refactor. Current persistence and latest verification are in [JPA_REFACTOR_HANDOFF.md](JPA_REFACTOR_HANDOFF.md); earlier test counts remain historical.


Date: 2026-10-27 (Asia/Bangkok). **COMPLETE for the authorized local implementation, testing, self-review and documentation increment through AGENTS workflow Phase 7. Full commercial integration: PARTIAL.** Real payment, Email/LINE providers and deployment remain explicitly unconfigured/out of scope. No commit, push, merge or production mutation occurred. This was performed directly by one agent, without Codex CLI or an independent reviewer.

## Delivered behavior and acceptance

Existing Phase 1–3 code and stack were preserved: Java 21/Spring Boot 4.1.2, explicit JdbcTemplate/PostgreSQL transactions/Flyway, React/TypeScript/Vite 8 and centralized Oasis tokens. Existing JPA dependencies remain unused for domain persistence. No new runtime dependencies or stack rewrite were introduced. The workspace has no Git metadata; review compares actual source to `.local/phase7-before`, including added files.

| Acceptance criterion | Outcome and evidence |
| --- | --- |
| Server-enforced Customer/Trainer/Admin ownership | Existing role/ownership suites retained. New admin APIs reject customers; trainer reports bind self; inbox reads reject other owners; disabled sessions lose access on the next request. COMPLETE. |
| Admin manages user access safely | Unique valid roles, enabled flag, audit reason; serial protection against removing the last enabled admin, including simultaneous mutual removal. Future classes/bookings and active memberships constrain role changes. Booking and complimentary issuance serialize account eligibility. Concurrent disable/book test passes. COMPLETE. |
| Rooms and configurable cancellation | Canonical room identity/capacity/active state, database published-slot exclusion; future sessions protect room capacity/deactivation, room history protects names. New sessions snapshot configured policy; old sessions retain theirs. COMPLETE with documented legacy/implicit-room capacity assumption. |
| Booking/credits/memberships remain correct | Original last-seat, duplicate/replay, one-credit, cancellation boundary/restoration, expiry/unlimited and service-eligibility tests pass after notification hooks. Failed bookings leave no jobs. COMPLETE for documented lifecycle. |
| Payments/receipts/refunds | Previous simulation/verified-event/duplicate/refund tests pass; production capabilities remain disabled. Redirects cannot mark paid. Real card/PromptPay/provider sandbox: PARTIAL pending provider configuration. |
| Durable notifications/reminders | Booking and deduplicated jobs commit together; local inbox/read/pagination/consent, one-hour reminder/cancellation guards. Concurrent workers and expired leases cannot duplicate inbox. Retry→DEAD and audited manual recovery tested. COMPLETE for INAPP and queue/adapter foundation. Email/LINE real delivery: PARTIAL, normal transport BLOCKED; no messages sent. |
| Attendance and eligible reviews | Existing assigned-trainer attendance/completion and owned one-per-booking review tests pass. COMPLETE for documented behavior; no public moderation workflow claimed. |
| Sales/utilization/trainer commission | REAL/SIMULATED separated, accepted captures and confirmed refunds with daily/monthly Bangkok periods, midnight boundaries and invalid input tests. Completed-session utilization and fixed fee snapshots verified. Trainer sees self only. COMPLETE for these formulas; percentage commission/payout not implemented. |
| Admin diagnostics and audit | Reconciliation/queue/payment-review counters, recent queue/audit display and paginated audit API. Minimal health probe. COMPLETE for local observability; production alerting not configured. |
| Responsive UI | New Administration, Inbox, Reports and My performance views; reusable shell/tokens. Chrome E2E checks real API/DB flows and mobile overflow. Screenshots visually inspected. COMPLETE within tested views. |

## Phase 5 — commands actually run

| Command/location | Actual result |
| --- | --- |
| `oasis-api`: `$env:OASIS_PG_TEST='true'; $env:OASIS_BROWSER_TEST='true'; .\mvnw.cmd -o clean package` | PASS: **59 tests, 0 failures, 0 errors, 0 skips**. 5 smoke/unit + 54 PostgreSQL/JUnit tests; four JUnit browser tests execute five Chrome scenarios. Executable production JAR built. Final transcript `.local/phase7-release-check.log`. |
| Earlier full `.\mvnw.cmd -o test` with PG/browser opt-in | PASS: 56 tests before the final three regression/browser additions. Transcript `.local/phase7-full-tests.log`. |
| `oasis-app`: `npm.cmd run build` | PASS: TypeScript + Vite production build, 26 transformed modules. |
| `oasis-app`: `npm.cmd run lint` | PASS: exit 0, no diagnostics. |
| Root: `.\scripts\verify-local-recovery.ps1` | PASS: synthetic pg_dump restored to a newly created disposable database; source/restored result `6,0,0` = six successful migrations, zero credit mismatches, zero seat mismatches. Restored DB removed; source/demo retained. `.local/recovery-check/result.json`. |
| Fresh PostgreSQL migration test within full suite | PASS: all V1–V6 apply to an empty UUID-named isolated database and Flyway validates six checksums; new DB removed. Existing fixture had also been upgraded from V4 to V6. |
| `jar tf oasis-api/target/oasis-api-0.0.1-SNAPSHOT.jar` with fixture/H2 exclusion assertion | PASS: no local fixture package, launcher, integration/smoke test classes or H2 runtime library in production artifact. |
| `git -c core.autocrlf=false -c core.whitespace=cr-at-eol diff --no-index --check` against snapshot | No whitespace diagnostics for source/UI/E2E/docs/scripts/README. Exit 1 signifies changed files in no-index mode, not a clean HEAD. No Git repository exists. |

Chrome scenarios cover original booking/credits/trainer/admin/mobile flow; Phase 2 membership and training/review journeys; simulated order/card/PromptPay/refund/receipt; final admin room/policy/access changes, queue delivery, real-versus-simulated reports, owned inbox/read and trainer performance. Logs/screenshots: `.local/browser-results`; final new evidence `phase7.log`, `phase7-inbox.png`, `phase7-reports-mobile.png`.

These are synthetic local integration tests, not provider sandbox certification, production load/security auditing, tax compliance or a real-message delivery check. Framework development/Mockito-agent warnings remain informational.

## Phase 6 — self-review and fixes

Review covered existing-file diffs and added source/migrations/UI/E2E/recovery script: server authorization, user access races, room identity/capacity, integer money, reporting period/cancellation formulas, booking/ledger invariants, queue transaction boundaries/leases/consent, simulation isolation, mobile UI and documentation consistency.

- First backend verification found a test variable shadowing a customer UUID with an HTTP browser object in a SQL argument. Corrected the test fixture reference; production behavior tests otherwise passed. An early test setup also avoided double-registering the simulator controller.
- Review found a disable-account versus booking/complimentary-issuance race. Added user SHARE locks and eligibility rechecks under transaction; the concurrent regression and existing booking races now pass. Admin removal is serialized and rechecks actor authority to prevent two admins removing each other to zero.
- Review fixed stale financial totals while changing report filters, avoided overwriting unsaved notification preference drafts on polling, reused one cancellation data read, and mapped missing report parameters to safe 400 responses. Added Bangkok midnight cash-flow/refund tests.
- Normalized incidental line endings back to existing source conventions and checked additions/diffs. Final clean package and frontend checks passed after behavioral fixes. No independent review is claimed.

## Phase 7 — handoff and remaining work

Read [API.md](API.md) for new endpoint contracts/RBAC and earlier handoffs for prior slices. Read [OPERATIONS.md](OPERATIONS.md) for setup/test commands, migration adoption, queue handling, recovery procedure, metric formulas and external release dependencies. README, PROJECT_PLAN and ARCHITECTURE now reflect actual implementation, with old planning sections explicitly historical.

Local preview remains http://127.0.0.1:5173 with the guarded synthetic demo API on :8080. Admin uses Administration/Reports; customer uses Inbox; trainer uses My performance. Demo restart applies additive V5/V6 and preserves earlier data; fixture dates are not reset on restart.

Main limitations: real payment/Email/LINE adapters and trusted account linking/verification are pending; fixed per-class commission only, no payouts/tax invoices; no used/partial refunds, automatic renewals or recurring availability; in-memory sessions/throttling need a production scaling decision; implicit/backfilled rooms default to capacity 100 and need operator validation; no production deployment, scheduled backups/alerting, provider credentials or production-scale benchmark. External delivery is at least once, and an already-started provider call cannot be unsent after cancellation. Retention/privacy policy remains a business decision.

## Changed files in this increment

- `README.md`
- `docs/API.md`
- `docs/ARCHITECTURE.md`
- `docs/OPERATIONS.md`
- `docs/PHASE4_7_HANDOFF.md`
- `docs/PROJECT_PLAN.md`
- `oasis-api/.env.example`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/admin/AdminController.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/admin/Administration.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/booking/Bookings.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/catalog/Catalog.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/identity/SecurityConfig.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/membership/Memberships.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/NotificationConfiguration.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/NotificationController.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/NotificationTransport.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/notification/Notifications.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/reporting/ReportController.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/reporting/Reports.java`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/shared/ApiError.java`
- `oasis-api/src/main/resources/application.yml`
- `oasis-api/src/main/resources/db/migration/V5__administration_rooms_commissions.sql`
- `oasis-api/src/main/resources/db/migration/V6__notification_outbox.sql`
- `oasis-api/src/test/java/com/jeyz9/oasisapi/LocalDemo.java`
- `oasis-api/src/test/java/com/jeyz9/oasisapi/OasisApiApplicationTests.java`
- `oasis-api/src/test/java/com/jeyz9/oasisapi/PhaseOnePostgresTests.java`
- `oasis-api/src/test/java/com/jeyz9/oasisfixture/PaymentSimulationConfiguration.java`
- `oasis-app/e2e/phase7.mjs`
- `oasis-app/src/App.css`
- `oasis-app/src/App.tsx`
- `oasis-app/src/features/Administration.tsx`
- `oasis-app/src/features/Notifications.tsx`
- `oasis-app/src/features/Reports.tsx`
- `oasis-app/src/shared/api.ts`
- `scripts/verify-local-recovery.ps1`
