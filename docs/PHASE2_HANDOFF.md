# Phase 2 — Membership lifecycle and training

Historical Phase 2 snapshot. See [PHASE3_HANDOFF.md](PHASE3_HANDOFF.md) for the later payment adapter/simulation foundation; real provider integration is still unconfigured.

Delivery date: 2026-10-26, Asia/Bangkok. This increment continues directly in chat without Codex CLI, retains the existing stack and leaves V1 migration unchanged. No commit, push, deployment, real gateway connection or external message is part of this work.

## Scope and acceptance criteria

| ID | Implemented acceptance boundary |
| --- | --- |
| P2-MEM-01 | Admin creates/updates/disables MONTH, YEAR and PACKAGE products with integer THB minor-unit price, service eligibility and CREDITS or UNLIMITED mode; invalid modes/quantities/validity/services are rejected. Public catalogue shows active plans. |
| P2-MEM-02 | Admin issues complimentary access to an enabled customer with a reason and idempotency key. A repeated identical request returns the original grant; changed payload conflicts. Concurrent identical requests create one grant/ledger effect. No paid state, transaction or receipt is fabricated. |
| P2-MEM-03 | Grant name/terms/price and service eligibility are snapshots; later plan edits do not change them. Monthly/yearly validity follows Bangkok calendar arithmetic, including month-end/leap-day clamping; expiry is exclusive. Future, suspended, revoked, expired or ineligible grants cannot book. |
| P2-MEM-04 | Credit grants preserve atomic one-credit debit/restore. Unlimited grants have no balance, use cost-zero bookings and create no debit/restore entries. Seat limits, overlaps, cancellation policy and owned history still apply. Customer sees effective status, expiry and noncancelled usage. |
| P2-MEM-05 | Admin can suspend/reactivate with a reason; revoke is permanent. No validity extension, rollover or implicit booking cancellation occurs. Existing bookings can still cancel under their normal policy, restoring only their original credit cost. |
| P2-TRAIN-01 | Trainer creates/deletes only own nonoverlapping future availability windows of up to 31 days. Private sessions require capacity one and containment in a trainer window. Deleting a window races safely with private session publication; a future assigned private session protects its window. |
| P2-TRAIN-02 | Only assigned trainer can record attendance/outcome after session end; cancelled bookings are ineligible. Status/outcome changes are audited. Completion requires every confirmed booking resolved, is replay-safe, and locks attendance. Trainer-only outcome reads include participant identity. |
| P2-REV-01 | Owning customer rates 1–5 and writes bounded text only after ATTENDED + COMPLETED. Future, cancelled, no-show and other-owner requests are denied. DB uniqueness and owner FK prevent duplicate/misattributed reviews, including concurrent requests. Customer can read own reviews. |
| P2-QA-01 | Keep Phase 1 credit/security tests; verify calendar/lifecycle/concurrency rules on actual isolated PostgreSQL, plus real Chrome membership/private-session and attendance/review journeys. Build/lint, migration validation, visual inspection and self-review are required before completion. |

The implemented boundary is membership products/lifecycle with **complimentary access**, training and owned reviews. Real paid purchase/renewal, payment integration, tax invoices, notification delivery and business reporting remain later phases. Full platform status stays PARTIAL even when this increment's tests pass.

## Explicit business defaults

- MONTH = one calendar month and YEAR = one calendar year from the selected activation instant in Asia/Bangkok. Java calendar arithmetic clamps Jan 31 to the final valid day of the next month and Feb 29 to Feb 28 in a non-leap year. PACKAGE uses 1–3660 days. Validity is `[start, end)`; grants must be valid now and at class start, as in Phase 1.
- There is no recurring billing/automatic renewal, credit rollover, freeze extension, package stacking, fair-use limit or automatic penalty beyond existing rules. Each booking selects one grant. No-shows consume their original entitlement and do not receive a refund.
- Complimentary activation is now (with a five-minute input allowance) or up to 366 days ahead, with microsecond-or-coarser timestamps. A reason is required. The price is a product snapshot, not money collected; source is explicitly ADMIN_COMPLIMENTARY. Existing SYNTHETIC_FIXTURE grants remain valid.
- Suspension affects new bookings and retains original expiry; reactivation does not reset validity. Revocation is irreversible through the API. Grant status changes retain existing reservations; cancellation is a separate policy-checked operation.
- Admin cannot cancel a session after it starts or after completion. Trainers can correct attendance/outcomes while the ended session is still PUBLISHED; completion locks them. An exceptional post-completion correction workflow is not introduced in this increment.
- Availability is concrete intervals rather than recurrence rules. Private appointments use admin-created sessions that customers book through the same schedule/capacity mechanism. There is no arbitrary customer-generated appointment slot or recurring calendar integration.
- Reviews are stored against a booking, which identifies class and trainer. They are visible to their author only in this increment; public publication/moderation/aggregates are deferred. Outcome notes are visible only to the assigned trainer.

## API changes

All paths are under `/api/v1`. Existing same-origin session/CSRF and service method authorization remain active. Mutations require CSRF; complimentary issue also requires an 8–80 character `Idempotency-Key`.

| Access | Method / path |
| --- | --- |
| Public | GET `/membership-plans` |
| Admin | GET/POST `/admin/membership-plans`; PUT `/admin/membership-plans/{id}` |
| Admin | GET `/admin/customers`, `/admin/memberships`; POST `/admin/memberships/complimentary`; PUT `/admin/memberships/{id}/status` |
| Trainer, own | GET `/trainer/availability?from=...&to=...`; POST `/trainer/availability`; DELETE `/trainer/availability/{id}` |
| Trainer, assigned | PUT `/trainer/bookings/{id}/attendance`; GET `/trainer/sessions/{id}/outcomes`; POST `/trainer/sessions/{id}/complete` |
| Customer, own | GET `/me/reviews`; POST `/me/bookings/{id}/review` |

Example plan payload: `{ "name": "Monthly unlimited", "period": "MONTH", "entitlementMode": "UNLIMITED", "credits": null, "validityDays": null, "priceMinor": 150050, "active": true, "serviceIds": ["service-uuid"] }`. Currency is THB. Credit mode requires 1–10000 credits; a PACKAGE requires validityDays while MONTH/YEAR must leave it null. Unlimited requires null credits.

Complimentary payload: `{ "customerId": "...", "planId": "...", "startsAt": "2030-01-01T00:00:00Z", "reason": "Welcome access" }`. Status payload: `{ "status": "SUSPENDED", "reason": "Administrative hold" }`. Attendance payload: `{ "status": "ATTENDED", "outcome": "Improved balance" }`. Review payload: `{ "rating": 5, "body": "Helpful guidance" }`.

Customer grants add entitlementMode, nullable remainingCredits and usedSessions; status exposes NOT_STARTED/EXPIRED when applicable. Booking history adds sessionState so the UI can offer a review only after completion. Existing clients must handle null balance for unlimited mode rather than treating it as zero.

## Persistence and transaction design

`V2__membership_training_reviews.sql` adds `membership_plans`, `plan_services`, `complimentary_requests`, `trainer_availability`, `attendance_records`, `reviews`, plan snapshots/mode on grants and COMPLETED/ATTENDED/NO_SHOW states. DB constraints enforce valid credit/null combinations, valid price/period rules, interval conflicts, unique review per booking and matching review ownership. Existing ledger/audit append-only protection remains.

Complimentary issuance serializes the scoped request, then holds a plan share lock while copying terms and eligible services; grant, initial credit ledger (if applicable), idempotency result and audit event commit together. Product edits acquire the plan update lock first, so issuance sees one consistent product version. The only grant mutation route changes status with audit, never terms or balances.

Booking/cancellation retain session → booking → sorted grants lock order where applicable. Non-key mutations use `FOR NO KEY UPDATE`, preventing the lock upgrade between request FK checks and explicit row locks that was exposed by the regression suite. Keys remain immutable. This lock is compatible with KEY SHARE while serializing other non-key updates, as documented by the [PostgreSQL row-lock matrix](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS).

Private session publication and availability changes serialize on the trainer user row. Availability deletion reads assigned future private sessions before removal; session creation checks availability while holding that same trainer lock. Attendance/completion lock the assigned session first and booking next. Review creation shares the completed session and locks the owned booking; completion prevents later attendance changes, while uniqueness resolves concurrent review attempts.

## Verification commands and evidence

Use the existing isolated local PostgreSQL setup; never use a normal/production datasource for fixture resets. Both H2 and PostgreSQL datasource settings are pinned in test annotations. The test DB is still deliberately named `oasis_phase1_test`; it now contains V1 + V2. Test fixture reset includes membership_plans to prevent data accumulation.

```powershell
# Root
.\scripts\start-test-postgres.ps1
Set-Location oasis-api
$env:OASIS_PG_TEST = 'true'
$env:OASIS_BROWSER_TEST = 'true'
.\mvnw.cmd -o clean package

Set-Location ../oasis-app
npm run build
npm run lint
```

Offline Maven requires cached dependencies; omit `-o` on a fresh setup. Browser tooling uses the previously installed local Playwright and Chrome described in the Phase 1 handoff. No application dependency upgrades were needed.

Initial results: the backend rules passed except a calendar test whose clock advancement expired its original session; the fixture now logs in again after time jumps. Both new browser stages passed. A full regression run then exposed an intermittent one-credit booking lock-upgrade deadlock (409 instead of expected 422); the row lock was repaired and a targeted four-case race/calendar retest passed. Stronger assertions now require SESSION_FULL/INSUFFICIENT_CREDITS, so a generic transaction conflict cannot disguise a concurrency defect.

Final results actually executed:

| Command / check | Result |
| --- | --- |
| `mvnw.cmd -o clean package` with both test opt-ins | PASS — 32 tests, 0 failures/errors/skips: 2 H2 smoke, 1 throttle and 29 PostgreSQL cases including both browser journeys. Executable Boot JAR generated. |
| Targeted race/calendar retest | PASS — 4 tests after lock repair; final combined run also includes private-publication/deletion race and future-grant lifecycle cases. |
| `npm.cmd run build` | PASS — TypeScript + Vite production build. |
| `npm.cmd run lint` | PASS — exit 0, no diagnostics. |
| Existing local demo restart | PASS — V1→V2 migration applied to the separate demo DB; existing data retained and server started on 127.0.0.1:8080. |
| Local preview/proxy | PASS — frontend and new public membership catalogue returned HTTP 200 with request ID on API response. The demo catalogue starts empty until admin creates a plan. |
| Source diff / whitespace / visual self-review | Completed against `.local/phase2-before` with `git diff --no-index`; changed source/e2e/docs whitespace checks passed. Desktop membership/trainer and 390px review screenshots visually inspected. |

Logs: `.local/phase2-final-build.log`; browser evidence: `.local/browser-results/run.log`, `phase2-membership.log`, `phase2-training.log` and Phase 2 desktop/mobile PNGs. These are ignored local artifacts. Source snapshots for self-review are in `.local/phase2-before`; no Git repository or HEAD diff is assumed. Mockito/JDK dynamic-agent warnings remain nonfatal; no independent review is claimed.

The scoped Phase 2 increment is **COMPLETE**: product/lifecycle management with complimentary access, private availability, attendance/outcomes and owned review eligibility. Full Oasis remains **PARTIAL**, with paid activation and the remaining integrations below outstanding. No payment gateway, deployment, commit or push occurred.

## Changed source and pending work

New backend modules: `membership/{Memberships,MembershipController}` and `training/{Training,TrainingController}`. Modified booking DTOs/transactions, catalog private-session validation/completed-state guards, security route policy and the shared PostgreSQL fixture/regression suite. Frontend adds `features/{MembershipAdmin,PlanCatalogue,TrainerTools,CustomerReviews}`, extends API types/messages and integrates them into existing screens; `e2e/phase2.mjs` supplies real browser journeys. README/project plan/architecture are updated.

Pending integration/business work: verified paid purchase and renewal/refund rules, payment gateway/receipts, email/LINE/outbox, public review moderation/visibility, admin post-completion attendance corrections, recurring trainer availability and booking buffers, dedicated room IDs, full list pagination, role administration, analytics/commissions and production operations. Existing process-local session/throttle limitations remain. Do not interpret catalogue prices or complimentary access as verified sales.
