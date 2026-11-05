# Implemented API reference

Persistence update: controllers now consume service interfaces/DTOs and persistence uses JPA repositories. Paths, validation, JSON shapes, roles, ownership and HTTP status semantics are unchanged. See [JPA refactor handoff](JPA_REFACTOR_HANDOFF.md).


Base path: `/api/v1`. JSON requests reject unknown properties. UUIDs identify resources; instants use ISO-8601 UTC, date periods use Asia/Bangkok. Money is integer THB minor units. Errors use `application/problem+json` with HTTP status, `code` and server-generated `requestId`; no SQL, credentials or provider payload is returned.

## Swagger UI and OpenAPI

Start the API and open `http://localhost:8080/swagger-ui.html`. The machine-readable documents are available at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml` (YAML). Swagger UI and these two documents are permitted by Spring Security; business endpoints keep their normal role and ownership checks. Set `OASIS_SWAGGER_ENABLED=false` to turn off the UI and generated documents.

The OpenAPI operations include request/response DTO schemas, validation constraints, a short summary, an explanation, the required role, and session/CSRF requirements. Swagger UI runs on the same origin as the API so the browser can retain the HttpOnly session cookie after login. Before any POST/PUT/DELETE, call `GET /api/v1/auth/csrf` and send the returned token under the returned `headerName` (currently `X-CSRF-TOKEN`). Webhook operations instead require provider signature headers and do not use browser CSRF.

### Complete endpoint index

`Public` means no login is required. `Customer`, `Trainer`, and `Admin` refer to server-enforced roles. `Signed provider` is the payment webhook authentication path. All mutating browser requests require the session cookie when applicable and the CSRF header.

#### Authentication and account

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Public | Get the current CSRF token and header name. |
| POST | `/api/v1/auth/register` | Public + CSRF | Register a customer account; roles are assigned by the server. |
| POST | `/api/v1/auth/login` | Public + CSRF | Authenticate and create a rotated HttpOnly session. |
| POST | `/api/v1/auth/logout` | Authenticated + CSRF | Invalidate the current session. |
| GET | `/api/v1/me` | Authenticated | Get the current account and roles. |

#### Catalog and schedule

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/services` | Public | List active group classes and private services. |
| GET | `/api/v1/sessions?from=&to=` | Public | List published sessions in a date range. |
| GET | `/api/v1/sessions/{id}` | Public | Get one session, its trainer, capacity, and availability. |
| GET | `/api/v1/admin/services` | Admin | List all services, including inactive ones. |
| POST | `/api/v1/admin/services` | Admin + CSRF | Create a service. |
| PUT | `/api/v1/admin/services/{id}` | Admin + CSRF | Replace a service's editable details. |
| GET | `/api/v1/admin/trainers` | Admin | List trainers available for assignment. |
| GET | `/api/v1/admin/sessions?from=&to=` | Admin | List sessions, including draft sessions. |
| POST | `/api/v1/admin/sessions` | Admin + CSRF | Create and assign a session. |
| PUT | `/api/v1/admin/sessions/{id}` | Admin + CSRF | Update a session after server-side booking checks. |
| GET | `/api/v1/trainer/sessions?from=&to=` | Trainer | List sessions assigned to the signed-in trainer. |

#### Booking and membership credits

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| POST | `/api/v1/me/bookings` | Customer + CSRF + Idempotency-Key | Book a session using an eligible membership grant. |
| GET | `/api/v1/me/bookings?offset=&limit=` | Customer | List the signed-in customer's bookings. |
| GET | `/api/v1/me/bookings/{id}` | Customer | Get one owned booking. |
| POST | `/api/v1/me/bookings/{id}/cancel` | Customer + CSRF | Cancel an eligible booking and restore credit when policy allows. |
| GET | `/api/v1/me/memberships?offset=&limit=` | Customer | List own grants and remaining credits. |
| GET | `/api/v1/me/credit-ledger?offset=&limit=` | Customer | Read own credit transactions. |
| GET | `/api/v1/trainer/sessions/{id}/attendees` | Assigned trainer | View attendee names and booking states for an assigned session. |
| POST | `/api/v1/admin/sessions/{id}/cancel` | Admin + CSRF | Cancel a whole session and apply the credit restoration rules. |
| GET | `/api/v1/membership-plans` | Public | List active plans available for purchase. |
| GET | `/api/v1/admin/membership-plans` | Admin | List active and inactive plans. |
| POST | `/api/v1/admin/membership-plans` | Admin + CSRF | Create a monthly, yearly, or credit package plan. |
| PUT | `/api/v1/admin/membership-plans/{id}` | Admin + CSRF | Update a plan; existing orders retain their price snapshot. |
| GET | `/api/v1/admin/customers` | Admin | List customers for membership administration. |
| GET | `/api/v1/admin/memberships` | Admin | Review grants across customers. |
| POST | `/api/v1/admin/memberships/complimentary` | Admin + CSRF + Idempotency-Key | Issue a complimentary grant with a reason. |
| PUT | `/api/v1/admin/memberships/{id}/status` | Admin + CSRF | Activate, suspend, or revoke a grant with a reason. |

#### Payments and receipts

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/payments/capabilities` | Public | Check provider, enabled/simulated state, and supported methods. |
| POST | `/api/v1/me/orders` | Customer + CSRF + Idempotency-Key | Create an order for a plan. |
| GET | `/api/v1/me/orders?offset=&limit=` | Customer | List own orders. |
| GET | `/api/v1/me/orders/{id}` | Customer | Get one owned order. |
| POST | `/api/v1/me/orders/{id}/checkout` | Customer + CSRF | Start checkout for an unpaid order. |
| POST | `/api/v1/me/orders/{id}/cancel` | Customer + CSRF | Cancel an order that has not been paid. |
| GET | `/api/v1/me/receipts/{id}` | Customer | Get an owned receipt created from verified payment data. |
| GET | `/api/v1/admin/orders` | Admin | Review orders and payment states. |
| GET | `/api/v1/admin/payment-events` | Admin | Review processed and rejected provider events. |
| GET | `/api/v1/admin/refunds` | Admin | List refund requests and states. |
| POST | `/api/v1/admin/refunds` | Admin + CSRF + Idempotency-Key | Request a refund with a reason. |
| POST | `/api/v1/admin/refunds/{id}/dispatch` | Admin + CSRF | Dispatch a refund through the configured adapter. |
| POST | `/api/v1/payments/webhooks/{provider}` | Signed provider | Process a verified provider event idempotently; browser redirects cannot mark an order paid. |

#### Trainer tools and reviews

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/trainer/availability?from=&to=` | Trainer | List own available time ranges. |
| POST | `/api/v1/trainer/availability` | Trainer + CSRF | Add an available time range. |
| DELETE | `/api/v1/trainer/availability/{id}` | Trainer + CSRF | Remove own availability. |
| PUT | `/api/v1/trainer/bookings/{id}/attendance` | Assigned trainer + CSRF | Record attendance/no-show and an outcome. |
| GET | `/api/v1/trainer/sessions/{id}/outcomes` | Assigned trainer | Review outcomes for an assigned session. |
| POST | `/api/v1/trainer/sessions/{id}/complete` | Assigned trainer + CSRF | Mark an assigned session complete. |
| GET | `/api/v1/me/reviews` | Customer | List own submitted reviews. |
| POST | `/api/v1/me/bookings/{id}/review` | Customer + CSRF | Review an eligible completed booking once. |

#### Notifications

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/me/notifications?offset=&limit=` | Authenticated | Read own notification inbox. |
| POST | `/api/v1/me/notifications/{id}/read` | Authenticated + CSRF | Mark an owned notification read; repeat-safe. |
| GET | `/api/v1/me/notification-preferences` | Authenticated | Read own email/LINE consent settings. |
| PUT | `/api/v1/me/notification-preferences` | Authenticated + CSRF | Update own consent settings. |
| GET | `/api/v1/admin/notification-jobs` | Admin | Inspect queue state, attempts, and redacted failures. |
| POST | `/api/v1/admin/notification-jobs/run` | Admin + CSRF | Process due notification jobs. |
| POST | `/api/v1/admin/notification-jobs/{id}/retry` | Admin + CSRF | Retry eligible failed notification jobs with a reason. |

#### Administration, reports, and health

| Method | Path | Access | Summary |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/users?offset=&limit=` | Admin | List safe user details and roles. |
| PUT | `/api/v1/admin/users/{id}/roles` | Admin + CSRF | Change a user's role assignments and enabled state with a reason. |
| GET | `/api/v1/admin/rooms` | Admin | List rooms. |
| POST | `/api/v1/admin/rooms` | Admin + CSRF | Create a room. |
| PUT | `/api/v1/admin/rooms/{id}` | Admin + CSRF | Update a room subject to capacity/schedule safeguards. |
| GET | `/api/v1/admin/business-policy` | Admin | Read the cancellation window. |
| PUT | `/api/v1/admin/business-policy` | Admin + CSRF | Change the cancellation window for newly created sessions. |
| PUT | `/api/v1/admin/trainers/{id}/commission` | Admin + CSRF | Set the commission snapshot used for future assignments. |
| GET | `/api/v1/admin/audit-events?offset=&limit=` | Admin | Review recent sensitive administration actions. |
| GET | `/api/v1/admin/reports/sales?from=&to=&grouping=&mode=` | Admin | Summarize package sales, refunds, and net amounts. |
| GET | `/api/v1/admin/reports/trainers?from=&to=` | Admin | Summarize completed classes, attendance, utilization, and commission. |
| GET | `/api/v1/trainer/commissions?from=&to=` | Trainer | View own teaching and commission report. |
| GET | `/api/v1/admin/operations` | Admin | View integrity and queue counters for operations. |
| GET | `/api/v1/health` | Public | Return minimal UP/DOWN health status. |

For exact request and response fields, use the OpenAPI schemas in Swagger UI. The source contracts are DTO records under `oasis-api/src/main/java/com/jeyz9/oasisapi/dto`; entities are never returned directly.

Authentication uses same-origin HttpOnly sessions. Before a browser mutation, GET `/auth/csrf` and send the returned token under its returned header name with the session cookie. GET does not mutate business state. Login/register/logout are also CSRF-protected. Sessions refresh current roles/enabled state on subsequent requests. Customer signup cannot assign privileged roles. Earlier implemented endpoint details and request examples are in [Phase 1](PHASE1_HANDOFF.md), [Phase 2](PHASE2_HANDOFF.md) and [Phase 3](PHASE3_HANDOFF.md).

## Role and ownership map

| Boundary | Access |
| --- | --- |
| Public | Auth CSRF/login/register, services, published schedules, payment capabilities and minimal health. |
| `/me/bookings`, `/me/memberships`, orders/receipts, reviews | CUSTOMER; queries bind authenticated owner. No other customer ID input grants ownership. |
| `/me/notifications`, `/me/notification-preferences` | Any authenticated account; inbox/mark-read/prefs bind authenticated owner. |
| `/trainer/**` | TRAINER; assigned sessions/attendees/outcomes, own availability/commission only. |
| `/admin/**` | ADMIN; management, financial and operational data, audited sensitive mutations. |
| `/payments/webhooks/{provider}` | Provider-verified raw request; does not trust browser redirects. Normal provider remains disabled. |
| `/local-payment-simulator/**` | Test/demo classpath only with exact datasource/explicit opt-in; absent from production JAR. |

## Administration added in final increment

All these endpoints require ADMIN and normal mutation CSRF protection.

| Method/path | Body/query and result |
| --- | --- |
| GET `/admin/users` | `offset=0&limit=25` (limit 1–100); safe identity, enabled and roles; no password/hash. |
| PUT `/admin/users/{id}/roles` | `{ "enabled": true, "roles": ["CUSTOMER","TRAINER"], "reason": "Staff assignment" }`; 204. Role list is unique/nonempty. |
| GET `/admin/rooms` | Up to 200 room records: id/name/location/capacity/active. |
| POST `/admin/rooms` | `{ "name":"Garden", "location":"Bangkok", "capacity":8, "active":true }`; 201 room. |
| PUT `/admin/rooms/{id}` | Same body; updated room. Capacity 1–100, bounded text. |
| GET `/admin/business-policy` | `{ "cancellationMinutes":120 }`. |
| PUT `/admin/business-policy` | `{ "cancellationMinutes":180, "reason":"Studio policy" }`; 204; range 0–10080. Applies to new sessions. |
| PUT `/admin/trainers/{id}/commission` | `{ "commissionMinor":25000, "reason":"Fixed teaching fee" }`; 204; future assignments only, 0–1,000,000,000. |
| GET `/admin/audit-events` | `offset=0&limit=25` (1–100); actor/action/resource/reason/time, latest first. |

409 safeguards include LAST_ADMIN, TRAINER_HAS_SESSIONS, CUSTOMER_HAS_BOOKINGS, CUSTOMER_HAS_MEMBERSHIPS, ROOM_NAME_HAS_HISTORY and ROOM_HAS_SESSIONS. ROOM_UNAVAILABLE is 422. Missing resources use 404. Duplicate canonical room names/overlapping published rooms use 409. Access changes take effect on subsequent requests; an operation already in flight cannot be globally revoked, while booking/issuance/assignment eligibility is additionally serialized on user rows.

## Notifications

| Method/path | Contract |
| --- | --- |
| GET `/me/notifications` | `offset=0&limit=25` (1–100); owned id/title/body/createdAt/readAt. |
| POST `/me/notifications/{id}/read` | No body; 204, repeat-safe, 404 for another owner's item. |
| GET `/me/notification-preferences` | emailEnabled/lineEnabled/lineLinked; defaults false. |
| PUT `/me/notification-preferences` | `{ "emailEnabled":false, "lineEnabled":false }`; updated preferences. No destination setter. |
| GET `/admin/notification-jobs` | Latest 200 jobs with kind/channel/state/attempts/redacted last_code/simulation marker. |
| POST `/admin/notification-jobs/run` | ADMIN; processes up to 20 due jobs, returns processed count. |
| POST `/admin/notification-jobs/{id}/retry` | ADMIN; `{ "reason":"Provider repaired" }`; 204, resets retry cycle for BLOCKED/DEAD/RETRY; otherwise 409 JOB_NOT_RETRYABLE. |

INAPP is functional. EMAIL/LINE have no real transport and become BLOCKED; preferences are consent only. Simulator delivery is explicitly marked. Booking confirmation/cancellation enqueues atomically; repeated booking/cancellation requests do not duplicate jobs or charges. Reminder jobs skip cancelled/inactive/started sessions. See operations for retries and lease recovery.

## Reports and health

| GET path | Query/result |
| --- | --- |
| `/admin/reports/sales` | ADMIN; required `from=2030-01-01&to=2030-02-01`, optional grouping DAY/MONTH (default DAY), mode REAL/SIMULATED (default REAL). Returns currency, periods (packages/grossMinor/refundMinor/netMinor), unreconciledMinor. |
| `/admin/reports/trainers` | ADMIN; required from/to. Completed classes, capacity, attended, noShows, utilization (ratio), snapshotted commissionMinor per trainer. |
| `/trainer/commissions` | TRAINER; same date query, authenticated trainer only. |
| `/admin/operations` | ADMIN; creditMismatches, seatMismatches, notificationBlocked/Dead/Pending and paymentReview counters. |
| `/health` | Public; 200 `{ "status":"UP" }` or 503 `{ "status":"DOWN" }`. No datasource/config disclosure. |

Periods include from and exclude to at Bangkok midnight, range 1–366 days. Invalid/missing dates/modes return 400. Empty periods return zero sales buckets; empty completed-session results return []. These reports describe cash movement and fixed teaching fees, not tax revenue or paid-out commissions.

## Replay and transient conflicts

Booking, complimentary issuance, order creation and refund requests use `Idempotency-Key`; retain the key for the same logical command and use a new key for a different command. Reusing a key with different payload returns 409. Database lock conflicts return 409 RETRY_TRANSACTION; bounded retry should retain the logical command key. Preference, room/policy/access updates set complete state; state/read/cancel transitions are checked on the server. External provider event IDs and accepted-payment constraints prevent duplicate fulfillment independently of browser keys.
