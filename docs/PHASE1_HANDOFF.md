# Phase 1 delivery and verification

Historical Phase 1 snapshot. Phase 2 supersedes the membership/training limitations below; see [PHASE2_HANDOFF.md](PHASE2_HANDOFF.md) for current coverage and verification.

Updated 2026-10-26, Asia/Bangkok. Codex CLI was explicitly stopped at the user's request; implementation, review and verification continued directly in the chat. No commit, push, deployment, real gateway or external notification was performed.

## Delivered slice

- Database-backed registration/login/logout/current-user, BCrypt passwords, rotated server sessions, CSRF, customer-only signup, role checks at HTTP and service boundaries, ownership/assignment checks, refreshed roles/disabled status, 30-minute idle and eight-hour absolute session expiry. Authentication throttling is bounded and process-local.
- Public daily/weekly Bangkok schedules; admin create/update services and future sessions, active service checks, published/draft states, trainer/private-capacity validation and PostgreSQL exclusion constraints for conflicting trainer/location intervals.
- Explicit synthetic prepaid-credit grants and append-only usage ledger; no real membership product/order/purchase API or public credit provisioning endpoint.
- Atomic booking with idempotency identity scoped to customer, session locks, grant locks, seat/credit guards, active-booking/customer-overlap constraints and rollback on failure. Successful retry returns the original booking identity.
- Customer booking history and cancellation at the exact configurable policy boundary. One compensating credit entry, no duplicate restoration, original expiry retained. Admin session cancellation restores all bookings atomically under a capacity cap of 100 and records an audit reason.
- Trainer assigned schedules and minimal attendee lists. Customer cannot see another customer's history/grants; trainer cannot see another trainer's attendees or customer payment data.
- Responsive React screens for those flows, a CSRF-aware same-origin API client, explicit errors/loading/empty/full/cancellation-closed states, accessible labels/focus/skip link, and centralized Sage/Sand/Terracotta tokens with darker text/button colors. Desktop and 390px mobile screenshots were visually inspected.
- Server-generated request IDs in headers and safe error responses; no existing `.env` import or secret inspection. Test annotations pin disposable datasources before Flyway can run.

This completes the narrowly scoped Phase 1 synthetic-credit vertical slice. The full Oasis platform remains **PARTIAL**. The larger acceptance criteria in PROJECT_PLAN.md remain the roadmap, not a claim that every requirement is complete.

## Implemented API

All paths below are under `/api/v1`; unsafe methods require a session CSRF token from `GET /auth/csrf`. Booking confirmation also requires an `Idempotency-Key` matching `[A-Za-z0-9_-]{8,80}`. JSON unknown properties are rejected in normal runtime and PostgreSQL tests.

| Access | Method / path |
| --- | --- |
| Public | GET `/auth/csrf`; POST `/auth/register`, `/auth/login` (CSRF still required) |
| Authenticated | GET `/me`; POST `/auth/logout` |
| Public catalogue | GET `/services`, `/sessions?from=YYYY-MM-DD&to=YYYY-MM-DD`, `/sessions/{id}` |
| Customer, own resources | GET `/me/bookings`, `/me/bookings/{id}`, `/me/memberships`, `/me/credit-ledger`; POST `/me/bookings`, `/me/bookings/{id}/cancel` |
| Trainer, assigned only | GET `/trainer/sessions?from=...&to=...`, `/trainer/sessions/{id}/attendees` |
| Admin | GET/POST `/admin/services`, PUT `/admin/services/{id}`; GET `/admin/trainers`; GET/POST `/admin/sessions`, PUT `/admin/sessions/{id}`; POST `/admin/sessions/{id}/cancel` with `{ "reason": "..." }` |

Date ranges are half-open Bangkok dates, capped at 31 days. Customer lists accept offset >= 0 and limit 1..100. The first UI shows the latest 100 records; public/admin catalogue lists are bounded, with full UI pagination still deferred. Errors use stable codes (e.g. SESSION_FULL, IDEMPOTENCY_CONFLICT, INSUFFICIENT_CREDITS, CANCELLATION_WINDOW_CLOSED), HTTP status and request ID. Unauthorized ownership/assignment reads return 404.

## Schema and changed code

Migration: `oasis-api/src/main/resources/db/migration/V1__identity_catalog_membership_booking.sql`. It creates `users`, `user_roles`, `services`, `class_sessions`, `membership_grants`, `grant_services`, `bookings`, `credit_ledger`, `booking_requests`, `audit_events`, indexes, interval exclusions and append-only history triggers. `btree_gist` must be available. V1 has been verified on isolated local schemas only; existing databases need explicit inspection/adoption, with automatic baseline/clean disabled.

Backend modules: `identity/{Accounts,AuthController,AuthThrottle,SecurityConfig,SessionGuard}`, `catalog/{Catalog,CatalogController}`, `booking/{Bookings,BookingController}`, `shared/{Actor,ApiError,RequestTrace}`. Persistence uses explicit `JdbcTemplate` SQL with Spring transactions; existing JPA/JDBC dependencies and versions are preserved. POM adds the Spring Boot Maven plugin for an executable JAR. Application YAML now uses session cookies rather than unused JWT settings and does not import `.env`.

Frontend: `src/App.tsx`, `src/App.css`, `src/index.css`, `src/shared/api.ts`, `src/styles/tokens.css`, Vite proxy configuration and page title. Test additions: `PhaseOnePostgresTests`, `AuthThrottleTests`, strengthened `OasisApiApplicationTests`, opt-in test-only `LocalDemo`, and `oasis-app/e2e/phase1.mjs`. Local launch scripts are under `scripts/`. README, environment example, project plan and architecture are updated.

## Reproduce checks

Requirements: JDK 21, Node compatible with the installed Vite, npm, PostgreSQL 18 server binaries and Chrome for the optional browser journey. Docker is not required. No payment credentials are used.

```powershell
# Root; add PostgreSQL server bin to PATH or pass -PostgresBin explicitly.
.\scripts\start-test-postgres.ps1

# Optional real-browser tooling, stored outside application dependencies:
npm install --prefix .local/browser-tools --save-exact playwright@1.64.1

Set-Location oasis-api
$env:OASIS_PG_TEST = 'true'
$env:OASIS_BROWSER_TEST = 'true'
.\mvnw.cmd clean package

Set-Location ../oasis-app
npm run build
npm run lint
```

Only when explicitly opted in does the PostgreSQL suite reset synthetic data in `oasis_phase1_test` at `127.0.0.1:55439`. URL/driver/user are pinned above ordinary environment variables; a metadata guard verifies the expected DB before fixture truncation. Each test resets both users and services so fixtures cannot accumulate. Browser tests start their own Vite instance on 5179, proxy it to the random-port test API, use synthetic users and a fixed 2030 browser/server clock, then stop the browser/Vite. Default `mvnw test` runs only H2 startup/isolation and throttle coverage; PostgreSQL/browser cases are intentionally skipped without opt-in.

Actual results from direct-chat verification:

| Check | Actual result |
| --- | --- |
| First backend rerun after handoff | Failed before tests: isolated PostgreSQL was not reachable. Restart/verification was moved into the same local setup path. No production DB attempted. |
| First combined browser run | Backend rules passed; browser journey exposed the admin service selector label, and fixture inspection exposed accumulated service rows. Both were repaired. |
| Targeted browser retest | PASS — 1 real PostgreSQL/browser test. |
| Final `mvnw clean package` with both opt-ins | PASS — 21 tests: 2 H2 smoke, 1 throttle, 18 PostgreSQL including browser journey; 0 failures, errors or skips. Executable Boot JAR generated. |
| `npm.cmd run build` | PASS — TypeScript and Vite production build. |
| `npm.cmd run lint` | PASS — exit 0, no diagnostics after repairs. |
| `scripts/run-local-demo.ps1` and Vite dev preview | PASS after allowing Maven's extra launcher arguments while ignoring all incoming datasource overrides and disabling DevTools restart. Separate demo DB seeded once, API/proxy returned 200 and server-generated request ID. |

The browser journey verifies signup, login/logout, confirmation and cancellation, balance 10→9→10, trainer attendees, admin service/session creation, public visibility and mobile overflow. HTTP/database cases additionally verify CSRF, ownership, disabled users/session rotation/expiry, exact cancellation boundary and one nanosecond late, grant expiry/service eligibility, invalid input, trainer/location conflicts, append-only accounting, concurrent last seat, one credit across sessions, identical-key replay and repeated cancellation.

Final build evidence: `.local/phase1-final-build.log`, JUnit reports under `oasis-api/target/surefire-reports`, browser log `.local/browser-results/run.log` and screenshots in the same directory. Those are ignored local artifacts. Maven emitted nonfatal metadata 401 warnings for inherited optional repositories, and Mockito/JDK emitted dynamic-agent warnings; the final build still succeeded. No independent reviewer is claimed.

Frontend formatting used a pinned temporary Prettier 3.6.2 tool in `.local/npm-cache`; no application dependency or lockfile version was changed. Build/lint were rerun successfully after formatting. The final test-only launcher adjustment was verified through actual PostgreSQL migration/fixture startup and a live proxied API response; it does not alter the production JAR's domain code.

## Local demonstration

`scripts/run-local-demo.ps1` starts the guarded local PostgreSQL setup, then uses Spring Boot `test-run` with `LocalDemo` on the test classpath. This entry point requires `OASIS_LOCAL_FIXTURE=true`, ignores incoming application overrides and pins DB/host/port/cookie settings. It is not packaged in the application JAR. It uses separate DB `oasis_phase1_demo`, never resets a populated DB, and only seeds on first initialization. Starting normal runtime never grants credits or creates privileged accounts. The launcher disables DevTools restart so startup failures are visible to Maven.

Run the frontend with `npm run dev`, then visit `http://127.0.0.1:5173`. The synthetic local password is `Synthetic-Test-Only-2030!` for customer/trainer/admin emails documented in README. The local cluster uses trust authentication on loopback only and is unsuitable for shared/production use. Stop foreground frontend/backend with Ctrl+C. To stop the project-local DB: `pg_ctl stop -D .local/phase1-pg -m fast` from the root. No local data is deleted by stopping it.

## Self-review and remaining work

Reviewed all domain source, migration, tests and changed frontend/setup/docs. Since no Git repository exists, compared changed files against CLI's pre-implementation snapshots in `.local/phase1-before`, reviewed new files directly, and used `git diff --no-index`/whitespace checks rather than claiming a HEAD diff. Repairs included complete fixture isolation, pinning datasource before migration, idempotent UI keys, persistent trainer attendee display, reactive cancellation deadlines, explicit select labels and safe test-only demo startup.

Remaining work: real membership plans/purchases/monthly/yearly/unlimited semantics, payment verification/refunds/receipts, email/LINE/outbox, attendance/reviews, trainer availability, dedicated rooms/branches, user/role administration, analytics/commissions, UI pagination, recovery/email verification, distributed auth throttling/session storage and deployment operations. Locations currently conflict by exact trimmed text, so canonical room IDs are needed before real multi-room operation. Idempotency rows currently have no retention cleanup. No reminder is scheduled or sent, and no order can be marked paid. These limits keep full-platform status PARTIAL.
