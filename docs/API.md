# Implemented API reference

Persistence update: controllers now consume service interfaces/DTOs and persistence uses JPA repositories. Paths, validation, JSON shapes, roles, ownership and HTTP status semantics are unchanged. See [JPA refactor handoff](JPA_REFACTOR_HANDOFF.md).


Base path: `/api/v1`. JSON requests reject unknown properties. UUIDs identify resources; instants use ISO-8601 UTC, date periods use Asia/Bangkok. Money is integer THB minor units. Errors use `application/problem+json` with HTTP status, `code` and server-generated `requestId`; no SQL, credentials or provider payload is returned.

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
