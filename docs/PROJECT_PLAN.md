# Oasis Wellness Hub — Project Plan

## User-authorized architecture change — 2026-10-27

Current persistence is JPA, organized into models/DTO/repository/service/serviceImpl. The prior JdbcTemplate choice in earlier phase records is historical and superseded by this explicit request. Scope: preserve HTTP contracts and business behavior, keep PostgreSQL constraints/lock ordering, map all 28 tables, move all production persistence behind JPA repositories, retain independent test fixture SQL and rerun the full suite. See [JPA_REFACTOR_HANDOFF.md](JPA_REFACTOR_HANDOFF.md) for verification.

## Current delivery status — 2026-10-27

The original discovery/Phase 1 proposal below is historical. Phases 1–3 implementation handoffs and [PHASE4_7_HANDOFF.md](PHASE4_7_HANDOFF.md) describe current code. This turn completes the authorized local increment through AGENTS workflow Phase 7: implement → test → self-review/fix → documentation/handoff. Full commercial integration remains **PARTIAL** because no real payment, Email/LINE provider or deployment was requested/configured.

| Workflow stage | Current outcome |
| --- | --- |
| 1 Discovery | Existing React/TypeScript/Vite + Java 21/Spring Boot/PostgreSQL preserved; no Git metadata exists. |
| 2 Analysis/design | Acceptance criteria, domain ownership, lifecycles and critical transaction boundaries documented. |
| 3 Foundation | Migrations, authentication, RBAC, validation, secure session/CSRF, error format and responsive shell implemented. |
| 4 Incremental implementation | Classes/private schedules, atomic bookings/credits, memberships, simulated payment/refund pipeline, attendance/reviews, durable inbox/jobs, admin management and financial/trainer reports implemented. |
| 5 Testing | H2 smoke, isolated PostgreSQL/migration/concurrency and Chrome E2E suites; exact current evidence in final handoff. |
| 6 Review/repair | One agent's self-review, race/validation/UI repairs and affected test reruns; no independent reviewer claimed. |
| 7 Handoff | Updated architecture/plan/README, API reference, operations/recovery runbook and evidence/limitations delivered. |

Next integration increment requires choosing a payment provider and its verified sandbox protocol, Email delivery provider and trusted LINE account-linking flow. Preserve simulation separation, idempotency and consent. Resolve percentage commission/revenue attribution and tax-invoice business details before adding those features. No deployment is implied by workflow completion.


Discovery date: 2026-10-26 (Asia/Bangkok). Updated during Phase 3 adapter/simulation delivery on the same date. Platform status: **PARTIAL — booking, membership/training and payment foundation implemented; real gateway/purchases and later integrations remain unconfigured**. See [PHASE1_HANDOFF.md](PHASE1_HANDOFF.md), [PHASE2_HANDOFF.md](PHASE2_HANDOFF.md) and [PHASE3_HANDOFF.md](PHASE3_HANDOFF.md) for coverage, evidence and limitations. User explicitly selected adapter preparation/simulation without a provider. Discovery/initial verification sections below remain historical baseline.

## 1. Repository and tools at initial discovery (historical)

| Area | Evidence / current state |
| --- | --- |
| Workspace | `AGENTS.md`, `oasis-api/`, `oasis-app/`; no `.git` found at root or either application. `git status --short` fails in all three locations. No repository was initialized. |
| Frontend | React 19.3.1, React DOM, TypeScript 6.0.3, Vite 8.3.5 installed (manifest range ^8.3.4), React plugin, Oxlint 1.89.0 installed. Existing Vite counter/template UI; no routes, API client, business screens, design system, or automated frontend test runner. |
| Backend | Spring Boot parent 4.1.2, Java properties 21, Maven wrapper 3.10.0; one application class and one context-test class. Compiler release was inconsistently 17; repaired to use `java.version`. |
| Declared backend libraries | Web MVC, Security, Validation, Data JPA **and** Data JDBC, Flyway/PostgreSQL extension, PostgreSQL driver, Lombok, DevTools and Spring test starters. Dependency presence is not feature implementation. |
| Database/configuration | PostgreSQL connection comes from environment. Existing dev/prod YAML and local `.env` (contents not inspected). No entities, repositories, controllers, migration scripts, security configuration, payment adapter, or notification worker. H2 test configuration existed without its driver; added test-only H2. |
| Documentation | Generated frontend README and backend HELP; root plan/architecture/setup documentation added in this increment. `effective-pom*` and `target/` are generated outputs, not source architecture. |
| Runtime tools | Node 20.19.3, npm 11.19.0, JDK 21.0.1, system Maven 3.9.6, Git, ripgrep, Postman CLI 1.75.0, Docker CLI 28.0.4, psql CLI 18.1. |
| Service availability | Docker CLI exists but `docker info` cannot reach Docker Desktop Linux engine. psql version does not establish a running PostgreSQL server. No PostgreSQL connection attempted; no local `.env` credentials consumed by tests. |
| Other capabilities | Shell/file tools and a GitHub connector are available; no remote repository was identified or modified. Postman was version-checked only; there is no API collection or implemented business endpoint to exercise yet. |

The two existing applications are retained. This is an existing scaffold, not an empty directory and not a functioning wellness system. No dependency upgrades, stack replacement, gateway connection, deployment, commit, or push are part of this increment.

## 2. Scope and decisions

Use a modular Spring Boot monolith and PostgreSQL, with the existing React SPA. Prefer JPA for domain persistence; retain existing JDBC dependencies for now and decide whether to remove the unused starter in Phase 1. Use Flyway as the sole production schema author; never use Hibernate auto-DDL for production. See [ARCHITECTURE.md](ARCHITECTURE.md) for the proposed model, API and transaction boundaries.

Business defaults proposed for acceptance: business timezone Asia/Bangkok, THB in integer satang, UTC instants in storage/API, no waitlist, no temporary seat holds, no public role selection, and cancellation allowed when `now <= starts_at - cancellation_window`. Default window is 120 minutes, snapshotted per session. Credits must be valid both at confirmation and at class start. These are design defaults pending business validation, not implemented policy.

## 3. Testable acceptance criteria

These are full-platform acceptance criteria. Phases 1/2 implement booking, identity, membership/training and review subsets. Phase 3 implements orders, immutable snapshots/receipts, verified-event fulfillment, deduplication, reconciliation visibility and unused full refunds using an explicitly isolated simulator. Exact coverage is mapped in the handoffs; provider selection/real integration/sandbox validation, notifications and reports remain pending. Section 6 records only the earlier planning increment.

| ID / area | Acceptance criteria and verification |
| --- | --- |
| AUTH-01 Authentication | Register a normalized unique email as CUSTOMER only; hash passwords; valid login creates a rotated server session; invalid credentials return a generic 401; logout invalidates it. Test duplicate registration, CSRF, expiry, disabled account and rate limiting. Never log passwords/session tokens. |
| AUTH-02 Authorization | Anonymous protected request returns 401; wrong role returns 403; another customer's resource returns 404; trainer accesses only assigned sessions/attendees; only admin changes roles. Test HTTP and service authorization, attempted role escalation and ownership bypass. |
| CAT-01 Catalogue | Admin creates/updates active services with name, description, category, group/private type, instructor assignment, duration, difficulty, capacity and location; invalid duration/capacity is rejected. Customer sees only published sessions of active services. Private sessions have capacity one. |
| SCH-01 Scheduling | Daily and weekly Bangkok date ranges show instructor, start/end, room, level, duration and live availability. Reject overlapping trainer/room sessions, invalid times and capacity below existing reservations. Test adjacent intervals and midnight/timezone boundaries. |
| BOOK-01 Confirmation | Eligible customer confirms once; transaction creates booking, reserves one seat and debits the selected entitlement together. Full, started, cancelled, expired, insufficient-credit or duplicate requests have no partial effects. Run concurrent PostgreSQL tests for last seat and one credit across different sessions. |
| BOOK-02 Retry and schedules | Same user/key/payload returns the original result; same key/different payload returns 409. Customer sees own bookings, trainer sees assigned sessions. Duplicate active booking is prevented even with different request keys. |
| BOOK-03 Cancellation | At exactly two hours before start cancellation succeeds; one instant later fails under the default rule. Eligible cancellation releases one seat and restores the original debit once; repeated cancellation has no additional effect. Restore does not extend expired validity. Admin session cancellation restores all eligible debits through auditable processing. Test races with confirmation and repeat requests. |
| MEM-01 Products | Admin defines monthly, yearly and prepaid products (including 10 credits), price, validity, service eligibility and explicit credit/unlimited entitlement mode. Product edits do not rewrite purchased snapshots. Customer sees own validity, status, balance and usage ledger. |
| MEM-02 Accounting | Credit balance cannot go negative, each confirmed credit booking has one debit and each refundable cancellation at most one compensating entry. Rollback changes neither seats nor credits. Unlimited bookings record entitlement use without a credit debit. Test expiry, simultaneous spend and failed confirmation. |
| PAY-01 Orders and payments | Order stores immutable item/amount/currency snapshots. Pending/failed/expired orders grant no entitlement. In a future configured provider adapter, verified server events matching order/amount/currency activate once; duplicate/out-of-order callbacks cannot double-grant or regress paid state. A browser success redirect never marks paid. Test with synthetic signed fixtures before sandbox integration. |
| PAY-02 Refunds and receipts | Verified paid transactions alone create numbered immutable receipts; unique provider IDs/event IDs prevent duplication. Refunds have separate pending/succeeded/failed records, cumulative refunded amount cannot exceed captured amount, and refund confirmation is reconciled before entitlement adjustment. Tax invoice is separately scoped. No real integration in this round. |
| NOT-01 Notifications | Confirmation/reminder jobs are created transactionally with domain changes. Email adapter and later LINE adapter honor preferences and consent. Failed delivery has attempt count, next retry, redacted error and dead-letter state; retries are deduplicated and observable. Cancelled sessions do not send reminders. Test worker crash/restart and transient/permanent failures. |
| REV-01 Reviews | Owner can rate 1–5 and review only a completed attended booking; cancelled, future, no-show and other-user bookings are rejected. One review per booking, enforced in DB. Test concurrent duplicate submission and text limits. |
| TRAIN-01 Trainer work | Trainer manages own availability, sees assigned attendee minimum fields and records attendance/outcome only for assigned eligible sessions; no customer payment data. Admin can audit corrections. Test assignment, time-window and ownership restrictions. |
| REP-01 Reports | Admin sees daily/monthly net settled package sales, attendance utilization, completed classes per trainer and commission. Trainer sees own commission only. Fixtures cover cancelled sessions, no-shows, partial refunds, Bangkok month boundaries and historical rate snapshots. Formulas are in ARCHITECTURE.md. |
| UX-01 Accessibility | Shared design tokens use Sage #6B8E23, Sand #F5F5DC and Terracotta #E07A5F with tested text contrast. Responsive schedule/booking flow supports keyboard, visible focus, labels, loading/error/empty/sold-out states. Do not use brand colors for inaccessible small text. |
| OPS-01 Foundation | Reproducible build/test instructions, isolated synthetic test DB, validated environment, migration verification, structured redacted errors/logs, health checks and secret exclusions. Test missing configuration and startup failure; no production credentials in fixtures. |

## 4. Delivery phases

| Phase | Deliverable / exit gate |
| --- | --- |
| 0 — this increment | Discovery, acceptance criteria, architecture/schema/API/RBAC design, documented risks, repair existing H2 tests/compiler/config defaults, self-review and actual verification report. No claim of business E2E coverage. |
| 1 — first complete vertical slice | Authentication and roles; admin service/session creation; customer schedule, own test entitlement, booking/cancellation and ledger; trainer assigned schedule/attendees; React screens integrated with API. Flyway migration on disposable PostgreSQL; API authorization/concurrency tests and browser E2E must pass. |
| 2 — membership lifecycle and training | Full product administration, monthly/yearly/unlimited policy, expiry/usage history, personal-training availability and conflict handling, attendance/outcomes, review eligibility. |
| 3 — payments and receipts | Select provider; obtain sandbox credentials and webhook contract; synthetic adapter tests, verified sandbox card/PromptPay flow where supported, reconciliation/refunds and receipts. Production credentials/launch are separate authorized work. |
| 4 — notifications and reports | Outbox worker, email, optional configured LINE OA, reminders, reviews UI, audited reports and commission calculations. |
| 5 — release readiness | Security/performance/accessibility review, backup/restore exercise, operational runbooks and release checks. Deployment needs explicit authorization. |

### Phase 1 implementation sequence and demonstration

1. Start a dedicated disposable PostgreSQL instance once available; add migrations and a test PostgreSQL harness (e.g. Testcontainers after Docker works). Verify empty-schema migration and upgrade migration. H2 smoke tests do not prove PostgreSQL concurrency behavior.
2. Implement user/roles, password storage, same-origin session/CSRF, error format, request validation and authorization tests. Customer signup cannot assign TRAINER/ADMIN. Seed privileged accounts only in an explicitly local/test fixture.
3. Add service/trainer/room/session APIs with constraints and admin screens; add public date-range schedule and error/loading states.
4. Add immutable credit ledger and a **test-only** fixture granting synthetic credits; provide no public grant endpoint and no paid order simulation in normal runtime.
5. Implement transactional idempotent booking/cancellation and owned history; integrate customer and trainer views. Add centralized tokens as part of the first real UI rather than recoloring the Vite template now.
6. Demonstrate with synthetic users: admin publishes Yoga capacity 1; customer A with 10 credits books (balance 9, seats 0); customer B cannot book; trainer sees A; B cannot read A's booking; A cancels at the valid boundary (balance 10, seats 1); repeat cancel does nothing. Replay requests and race two customers for one seat in integration tests.
7. Run backend unit/API/PostgreSQL integration suites, frontend build/lint, and browser E2E covering that demonstration. Self-review diff and repair defects before marking Phase 1 COMPLETE.

Phase 1 excludes actual orders/payment settlement, external messages, production data and deployment. This sequence was the approved implementation target; current delivery and verification are recorded in PHASE1_HANDOFF.md.

## 5. Risks, assumptions and pending decisions

| Risk | Control / validation required |
| --- | --- |
| Overbooking and overspending | Database transaction, consistent row lock order, conditional seat/balance updates, unique active booking and ledger constraints; parallel PostgreSQL tests. Process-local locks or frontend availability are insufficient. |
| Duplicate/reordered requests | Scoped idempotency keys + request fingerprint; unique provider event/payment references; monotonic transitions and durable outbox. Reconcile failures rather than blind replay of financial side effects. |
| Cancellation race/expiry | Inject a Clock; compare instants at exact boundary; snapshot policy; serialize cancellation with booking. Restore original debit once without extending entitlement validity. |
| Membership semantics unclear | Decide rollover, freeze, stacking/selection, unlimited fair use, yearly calendar rules, no-show penalty and service eligibility. Proposed first slice uses prepaid credits only. |
| Trainer/room conflicts | Database interval exclusion constraints for published active sessions; private training uses capacity 1 and the same booking rules. Define room sharing and trainer buffers before implementation. |
| Fraudulent callbacks/refunds | Provider selection and verified signature/amount/currency/order checks remain prerequisites; no frontend payment authority. Do not restore spendable credits while refund entitlement handling is unresolved. |
| Existing configuration | `.env` exists; do not print/use it for discovery tests. Baseline-on-migrate was enabled; now disabled. Any existing nonempty DB requires explicit inspection and migration adoption before connecting. Do not run clean. |
| Runtime/test gaps | Docker engine unavailable; no PostgreSQL integration or browser E2E yet. Current passing H2 smoke test is narrow. Frontend lint currently has only template source. |
| Privacy / privilege | Server ownership checks, minimal trainer data, redacted logs, admin audit trail; decide retention/erasure and notification consent with business stakeholders. |
| Reliability / operations | Retryable jobs, dead-letter inspection, request IDs, reconciliation jobs, backup/restore and migration runbooks before release. |

Unresolved business decisions: payment provider/fees/refund rules; membership activation date and calendar-month/year end behavior; cancellation overrides/no-show/late-cancel penalties; trainer commissions and revenue attribution; customer rescheduling; LINE OA/email providers and credentials; tax-invoice requirements; multiple branches and retention policy. These do not block documentation but must be settled before the affected feature is COMPLETE.

## 6. Initial planning increment verification (historical, before Phase 1)

Implemented: added H2 as a test dependency; aligned compiler target with Java 21; strengthened context tests to verify actual H2 connection and disabled Flyway; disabled automatic baseline and dev Flyway clean; disabled dev SQL/bind trace logging; added root/API secret/generated-output exclusions and a placeholder `.env.example`; added root setup guide and these design documents. Existing application code/UI and dependency versions are retained.

Initial commands: `git status --short` at root and `git -C oasis-api status --short` / `git -C oasis-app status --short` all reported no Git repository. Frontend `npm run build` passed; `npm run lint` exited 0. Initial `./mvnw.cmd -o test` first hit sandbox access to Maven cache lock files; a permitted rerun reached the actual test and failed with `Cannot load driver class: org.h2.Driver` (1 test error). This is the failure repaired here.

Final commands actually executed:

| Working directory / command | Result |
| --- | --- |
| `oasis-api`: `./mvnw.cmd -o test` after repair | PASS — 2 tests, 0 failures/errors/skips; real in-memory H2 connection checked. |
| `oasis-api`: `./mvnw.cmd -o package` | Initially failed: Maven JAR-plugin dependencies were not cached. Tests still passed. |
| `oasis-api`: `./mvnw.cmd clean package` | PASS after downloading missing dependencies; 2 tests, 0 failures/errors/skips and regular JAR generated. No DB migration or production connection. |
| Root: `javap -verbose oasis-api/target/classes/com/jeyz9/oasisapi/OasisApiApplication.class` | Verified class major version 65 (Java 21). Clean build was necessary: an incremental build had retained the old Java 17 class. |
| `oasis-app`: `npm.cmd run build` | PASS — TypeScript and Vite production build. |
| `oasis-app`: `npm.cmd run lint` | Exit 0; no diagnostics emitted. Not a frontend behavior test. |
| Root: `git diff --no-index` and `git diff --no-index --check` | Inspected code/config changes and new document files. No Git history exists: before-text for changed-file comparison was reconstructed from the pre-edit discovery reads, not a HEAD diff. Documentation whitespace checks found no errors (only LF/CRLF warnings). |

Self-review checked all five edited existing files and all five new files. Repair cycle 1 addressed missing H2/compiler/config safety; verification cycle 2 found stale Java 17 bytecode and incomplete offline package cache, resolved with the successful clean build. Reviewed schema ownership constraints, transaction/lock ordering, idempotency, cancellation boundary, gateway trust, and explicit separation of planned versus implemented features. No independent reviewer or business E2E test is claimed.

Changed existing files: `oasis-api/pom.xml`, `oasis-api/.gitignore`, `oasis-api/src/main/resources/application.yml`, `oasis-api/src/main/resources/application-dev.yml`, `oasis-api/src/test/java/com/jeyz9/oasisapi/OasisApiApplicationTests.java`. New files: root `.gitignore`, root `README.md`, `oasis-api/.env.example`, `docs/PROJECT_PLAN.md`, `docs/ARCHITECTURE.md`. Local generated outputs/logs are excluded; the final clean-build transcript is `oasis-api/foundation-verification.log` and JUnit results are under `oasis-api/target/surefire-reports/`.

This discovery/planning increment is **COMPLETE** within its stated scope. The application and Phase 1 remain **PARTIAL / not implemented**. Remaining limits: Docker engine unavailable, no PostgreSQL migration/concurrency checks, no authentication/RBAC/business endpoints, no frontend behavior/E2E tests, and no external integrations. Build logs include framework development-security and Mockito dynamic-agent warnings; do not treat generated Spring Security development credentials as application authentication. No commit, push, deployment, gateway connection or production mutation occurred.
