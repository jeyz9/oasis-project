# Phase 3 — Payment adapter and simulated end-to-end flow

Date: 2026-10-26, Asia/Bangkok. User explicitly chose **no payment provider yet; prepare an adapter and test with simulation**. This increment implements that boundary. No real payment gateway, card charge, PromptPay transfer, external message, deployment, commit or push is performed. Real provider integration remains PARTIAL/unconfigured.

## Acceptance criteria and implemented boundary

| ID | Acceptance criteria |
| --- | --- |
| P3-ORDER-01 | Customer creates an owned, idempotent, one-plan/quantity-one order using server price/currency/product/eligibility snapshots. Payload cannot override amount/status/owner. Inactive/free plans are rejected for purchase; complimentary access remains a separate admin action. |
| P3-ORDER-02 | Pending orders expire after 30 minutes; cancellation and capture serialize. A success URL/query parameter never changes payment/order/member state. Other customers and trainers cannot access orders/receipts. |
| P3-PAY-01 | Gateway interface separates capabilities, checkout, verified event decoding and refund dispatch. Normal runtime supplies a disabled provider and cannot charge, verify a callback or activate membership through payment. No credentials or fake real-provider implementation are invented. |
| P3-PAY-02 | Only provider-verified server events can fulfill. Identity, amount, currency, order mode and payment reference must match. Event IDs and payment references deduplicate across retries/concurrent deliveries. Changed content under one event ID conflicts; failed/late events cannot regress paid fulfillment. |
| P3-PAY-03 | Accepted capture, membership grant, original credit ledger when applicable, order PAID and immutable receipt commit in one transaction. Unlimited fulfillment creates no fictitious credits. Extra captures or capture of cancelled/expired/ineligible orders require reconciliation and do not grant access again. |
| P3-REC-01 | Customer reads only own immutable receipt issued from accepted server transaction data. Test receipts have SIM- numbers, simulated flag and visible “no money collected” text, support browser printing and explicitly are not tax invoices. Refunds do not rewrite receipts. |
| P3-REF-01 | Admin requests only a full unused-membership refund with a reason/key. Reserve by suspending the grant under its lock; a simultaneous booking either succeeds first and blocks refund, or sees suspended access. Pending refund cannot be manually reactivated. Verified success revokes access; verified failure leaves it suspended for review. |
| P3-REF-02 | One full refund request per payment; no partial/double refund. Dispatch runs outside DB locks, using refund UUID as provider idempotency identity, and binds the returned reference. Callback must match request/payment/amount/reference. Duplicate/late failure cannot regress refund success. |
| P3-UI-01 | Customer catalogue/order/history/checkout/receipt flow and admin order/refund/event inspection work through actual APIs. Simulation is prominently labelled; no card-number input or fake real QR is provided. Read-only polling refreshes pending states and never marks paid. |
| P3-QA-01 | Real local PostgreSQL integrity/concurrency/security tests and Chrome order→confirmation→receipt→refund journey, plus prior phases, build/lint, production-JAR exclusion checks, visual inspection and self-review. |

## Payment provider boundary

`payment/PaymentGateway` is a contract, not a configured real integration. `PaymentConfiguration` defaults to UNCONFIGURED: capabilities disabled, checkout and verification fail closed. Only `src/test/java/com/jeyz9/oasisfixture/PaymentSimulationConfiguration` supplies the primary LOCAL_SIMULATOR. It is outside normal application scanning, explicitly imported for guarded PostgreSQL tests or the opted-in `LocalDemo` test-classpath launcher, and checks the exact local DB URL plus the appropriate opt-in environment variable. It is excluded from production JARs.

The simulator generates an ephemeral in-memory HMAC-SHA256 key, validates a five-minute timestamp window and constant-time signature comparison over raw bytes before decoding. Its merchant identity is fixed to a local synthetic account; it rejects unknown/malformed fields and oversized input. Generic Payment-Signature/Payment-Timestamp headers are this fixture's protocol, **not** an assertion of compatibility with a real provider. A real adapter must map its provider's documented headers, signature/timestamp rules, account identity, event semantics and supported payment methods before enabling payment.

Simulated CARD/PROMPTPAY controls exercise the shared server confirmation pipeline; they do not tokenize cards, generate a real PromptPay QR, transfer funds or prove provider support. No credentials or payment data from `.env` are read. A future real checkout should use the configured provider's hosted/tokenized flow; Oasis currently has no card-data collection form.

## Order, capture and receipt design

V3 adds `membership_orders`, `order_services`, `payments`, `payment_events`, `receipts` and `refunds`. V4 adds immutable order-term/eligibility guards, immutable paid fulfillment and composite FKs tying receipts to the matching payment/order/owner and fulfillment grant to its order owner. V1/V2 remain unchanged; V4 was added rather than rewriting the V3 already applied to the isolated test schema.

Order state is PENDING/PAID/CANCELLED/REVIEW; EXPIRED is derived for pending orders whose exclusive expires_at boundary has passed. Order service eligibility and product terms are snapshots. Prices use integer minor units (THB, up to the existing safe bound), never client-supplied totals. Orders created while the real provider is unavailable can remain pending, but checkout is unavailable and no payment fulfillment occurs. Switching between simulated/real modes cannot settle an order from the other mode.

Webhook body reads stop at 65,537 bytes and reject above 65,536. Verification occurs before transaction work. Provider/merchant/event identity plus payload hash detect replay and changed content. The transaction locks event then order, resolves the globally scoped provider/merchant/payment reference, and atomically fulfills once. Non-key locks remain compatible with foreign-key KEY SHARE checks. SQL exceptions roll back event/financial/entitlement effects together, allowing the same verified event to retry safely.

Success on an already paid order with a new external payment reference records a REVIEW payment/event rather than a second membership. A valid capture after cancellation/expiry or for a disabled/ineligible customer records REVIEW and grants nothing. Mismatched amounts/currency/mode/reference produce a durable REJECTED event. No “mark paid” admin endpoint exists. Review records are observable but have no unsafe manual resolution action in this increment.

Membership validity starts at accepted server confirmation time and uses the snapshotted Bangkok monthly/yearly/package rules from Phase 2. Payment source is SIMULATED_PAYMENT or VERIFIED_PAYMENT, kept separate from complimentary/fixture grants. Receipt amount/reference comes from the matching verified event and immutable order snapshot; issued_at/captured_at in this increment represent server confirmation/issuance time rather than a separately supplied provider settlement timestamp. A real adapter and reporting policy must settle provider-time/reconciliation requirements before launch.

## Conservative refund rules

- Only full accepted-payment refunds are supported, and only when the grant has no noncancelled bookings and any credit balance equals its original grant. A previously booked then eligible-cancelled package can qualify after full restoration. Partly consumed packages require a separate approved business policy.
- Request is admin-only, requires a reason/key, and locks order→payment→grant. Booking/refund races serialize on the grant. Access suspends atomically with the request; no funds are considered refunded at request or dispatch time.
- Exactly one full refund record exists per payment. Repeated identical key/payload returns that record; a second independent request conflicts, including after a failure. A future provider-specific reconciliation/retry policy is needed before allowing another refund operation.
- Dispatch calls the adapter outside DB locks with the stable refund UUID, then binds its provider reference in a transaction. Timeout/crash may repeat the dispatch, so a real adapter must honor that identity. There is no automatic dispatch worker/outbox yet; the admin action is explicit and safely retryable.
- Verified success marks refund SUCCEEDED and revokes the grant without deleting/reversing history. Failed refund remains SUSPENDED; there is no silent access restoration. Explicit admin reactivation after a failed refund is a business review action, and a later valid success still revokes access. Success is terminal against later failed notifications.
- Existing receipts and the original order PAID state remain immutable, with refund status shown separately. Revoked grants can retain a historical credit balance, which is unusable. Partial refunds, used-package calculations, tax credit notes and multiple refund attempts are pending.

## API additions

All routes are under `/api/v1`. Customer/admin unsafe methods retain CSRF; only the exact provider webhook subtree has a CSRF exemption and instead requires provider verification.

| Access | Method / path |
| --- | --- |
| Public | GET `/payments/capabilities` |
| Customer, own | POST `/me/orders` with Idempotency-Key and `{ "planId": "..." }`; GET `/me/orders?offset=0&limit=25`, `/me/orders/{id}` |
| Customer, own | POST `/me/orders/{id}/checkout`, `/me/orders/{id}/cancel`; GET `/me/receipts/{id}` |
| Verified provider | POST `/payments/webhooks/{provider}`; raw bounded body, configured provider identity and provider-verified headers |
| Admin | GET `/admin/orders`, `/admin/payment-events`, `/admin/refunds`; POST `/admin/refunds` with key and `{ "paymentId": "...", "reason": "..." }`; POST `/admin/refunds/{id}/dispatch` |
| Explicit local fixture only | POST `/local-payment-simulator/orders/{id}/confirm` (own customer; method CARD/PROMPTPAY); POST `/local-payment-simulator/refunds/{id}/confirm` (admin; succeeded boolean) |

Normal runtime has no simulator controller. The narrow security matchers alone do not create endpoints. Default smoke tests assert disabled payment capabilities/checkout; packaging checks confirm test-only simulation classes are absent.

## Testing and local use

Use the existing guarded local PostgreSQL cluster, separate `oasis_phase1_test` / `oasis_phase1_demo` DBs and synthetic accounts. No production database or real customer transaction is used.

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

Omit offline `-o` on a fresh Maven cache. Existing local Playwright/Chrome tooling is reused; runtime application dependencies and versions are unchanged. In default tests, PostgreSQL/browser cases remain intentionally skipped without opt-in; that is not the full verification command above.

For local demo run `scripts/run-local-demo.ps1`, then `npm run dev` in oasis-app. Admin first creates a priced plan in Manage studio. Customer opens My membership, creates a **test** order, prepares checkout, and chooses a simulated confirmation. The signed server event activates the simulated grant and receipt. Admin can request an unused full refund, prepare dispatch and simulate confirmation; buyer sees refund status and revoked access. Existing demo records are preserved during additive migration/restart.

Verification history: default smoke compilation passed. An initial imported fixture registered its nested controller twice; the redundant bean was removed. Backend payment regressions then passed, and the targeted real-browser/dispatch retest passed. V4 added integrity guards without changing applied V3. Self-review checked every new payment/fixture module, migration, changed security/lifecycle boundary and UI/test flow; no independent reviewer is claimed.

Final commands/checks actually executed:

| Command / check | Result |
| --- | --- |
| `mvnw.cmd -o clean package` with OASIS_PG_TEST=true and OASIS_BROWSER_TEST=true | PASS — 46 tests, 0 failures/errors/skips: 3 H2/default-context smoke tests, 1 throttle test, 42 PostgreSQL cases including real Chrome journeys for all three phases. Executable Boot JAR generated. |
| Targeted Phase 3 browser + refund dispatch test | PASS — 2 tests before final combined run. |
| `npm.cmd run build` | PASS — TypeScript and Vite production build. |
| `npm.cmd run lint` | PASS — exit 0, no diagnostics. |
| `jar tf oasis-api/target/oasis-api-0.0.1-SNAPSHOT.jar` exclusion assertions | PASS — no oasisfixture simulator, LocalDemo, integration tests or H2; payment core interface is present. |
| Demo restart and V2→V4 upgrade | PASS — additive V3/V4 migrated only the isolated demo DB and retained existing data. |
| GET local preview `/api/v1/payments/capabilities` | HTTP 200 with request ID; provider LOCAL_SIMULATOR, simulated=true. No real provider was contacted. |
| Self-review | Compared source against `.local/phase3-before` with git diff --no-index (no repository/HEAD exists); changed-source/e2e/docs whitespace checks passed. Desktop simulated receipt and 390px order/refund history screenshots visually inspected. |

Coverage includes tampered/expired signatures, merchant/amount/currency/mode/reference mismatch, bounded body, duplicate/concurrent callbacks and orders, immutable snapshots/receipts, late/excess captures, capture/cancel and booking/refund races, full-refund reservation/dispatch/replay, refund binding and monotonic results, role/ownership restrictions, and a browser success-URL forgery that leaves the order pending. The Chrome flow exercises simulated Card/PromptPay, receipt labels and refund results; it is not a real provider sandbox test.

Evidence is under `.local/phase3-final-build.log`, `.local/phase3-demo.log`, JUnit surefire reports and `.local/browser-results/phase3.log`/PNGs. These are ignored local artifacts. Existing Mockito/JDK dynamic-agent warnings are nonfatal. Temporary formatter tooling reused the local cache and did not add runtime dependencies.

Status: **COMPLETE for the explicitly requested adapter + local simulation increment**. Overall Phase 3 real integration and the full platform remain **PARTIAL** until a chosen provider is configured and its sandbox flows are actually verified. No payment credentials, real funds, deployment, commit or push were used.

## Files and remaining integrations

New backend module `payment/{PaymentGateway,PaymentConfiguration,Orders,PaymentProcessing,Refunds,PaymentController}`; migrations V3/V4; test-only `oasisfixture/PaymentSimulationConfiguration`; extended local launcher, smoke and PostgreSQL/Chrome tests. Frontend adds `features/Payments.tsx`, order creation in PlanCatalogue, simulation-source text, error messages and receipt/mobile/print styles, plus `e2e/phase3.mjs`. Security routes and membership status checks are updated; README/architecture/project plan/handoffs are maintained.

Remaining before real payments: choose a provider, obtain approved sandbox credentials, implement the real adapter and exact callback protocol, verify card/PromptPay support with that provider, test sandbox captures/refunds/reconciliation, supply required business/receipt/tax data, define used-package refund/renewal rules, add operational retry/reconciliation workflows, and separately authorize production configuration/deployment. Tax invoices, recurring billing and financial reports are not delivered here. No claim of a functional real gateway or successful real payment is made.
