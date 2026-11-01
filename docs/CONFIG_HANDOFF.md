# Configuration package follow-up

## Subsequent utility/component consolidation (2026-10-27)

Seven production classes now use flat packages: `utils/{Actor,TimeValues}` and `component/{ApiError,RequestTrace,AuthThrottle,PaymentGateway,NotificationTransport}`. Removed the emptied `shared`, `identity`, `payment`, and `notification` production folders. Main/test imports, static imports and fully qualified references were updated with explicit imports preserved. Frontend files and the existing controller/config/dto/models/repository/service layers are unchanged.

Verification: `oasis-api/.\mvnw.cmd -o clean package` (run as `.\mvnw.cmd -o clean package` from `oasis-api`) succeeded: 9 tests passed, 56 opt-in PostgreSQL tests skipped, no failures/errors. From the root, `.\scripts\check-java-imports.ps1` passed. The default context smoke test uses isolated H2 and validates component discovery; this run does not claim PostgreSQL integration coverage. JAR inspection confirmed all seven relocated classes and no classes from the removed packages. Snapshot comparison of all 124 source/resource files confirmed only intended relocation/package/reference edits, with no behavior or resource changes. Evidence: `.local/package-consolidation-build.log` and `.local/package-consolidation-before`. No Git metadata exists in this checkout, so self-review used snapshot comparison instead of a repository diff. No remote database access, commit or push was performed.

## Earlier configuration move

2026-10-27 (Asia/Bangkok). Runtime configuration classes now live in `oasis-api/src/main/java/com/jeyz9/oasisapi/config`, package `com.jeyz9.oasisapi.config`:

- SecurityConfig
- PaymentConfiguration
- NotificationConfiguration (including its conditional scheduled Worker)
- SessionGuard, the package-private helper instantiated by SecurityConfig

SessionGuard moves with SecurityConfig so its class/constructor retain package-private access; no visibility or security change is needed. In the subsequent package consolidation, PaymentGateway and NotificationTransport moved to `component/` alongside ApiError, RequestTrace and AuthThrottle; Actor and TimeValues moved to `utils/`. Bootstrap and profile YAML files retain their conventional locations. The guarded payment/notification simulation configuration stays on the test classpath and is not moved into production.

Source comparison against `.local/config-before` confirms only package declarations and the PaymentGateway/NotificationTransport imports changed. Bean methods, security matchers, CSRF/session rules, disabled adapter defaults, worker conditions/delay and business logic remain identical. All moved-file whitespace checks pass. README/ARCHITECTURE reflect the new folder.

Verification command (PowerShell, oasis-api):

```powershell
$env:OASIS_PG_TEST='true'
$env:OASIS_BROWSER_TEST='false'
.\mvnw.cmd -o '-Dtest=OasisApiApplicationTests,AuthThrottleTests,PhaseOnePostgresTests#authenticationCsrfAndCustomerOnlyRegistration+serverRolesOwnershipAndMinimalTrainerData+disabledAccountLosesExistingSessionAndLoginDenied+loginRotatesSessionAndAbsoluteExpiry+notificationOutboxInboxOwnershipAndCancellationAreDurable+notificationRetriesDeadLettersAndExplicitRetryDoNotUndoBooking' clean package
```

Verification status: PASS — 11 tests, 0 failures, 0 errors, 0 skips; clean executable production JAR built. JAR inspection found all three configuration classes, the nested Worker and SessionGuard under config/, with no old package copies. This scoped suite checks context/bean wiring, disabled external providers, authentication/CSRF, server roles/ownership, disabled/expired sessions, inbox delivery and retry handling. No new implementation-mirroring tests were added for a package move. Transcript `.local/config-check.log`; reviewed diff `.local/config-review.diff`.

No dependency, migration, API contract, business rule or frontend change was made. No commit, push, deployment or production database mutation occurred. The workspace has no Git repository; review uses actual pre-move files.

The initial verification passed compilation and five smoke/unit tests, but integration context startup failed with connection refused on the pinned loopback test datasource. No code/security check was bypassed. The guarded start-test-postgres.ps1 launcher restored that same isolated cluster; the repeated run is recorded in config-check.log and the initial environmental failure in config-first-check.log.

The guarded local demo also starts successfully with the moved configuration and enabled conditional worker; its API health returns UP. Existing demo data is retained. Startup evidence: `.local/config-demo.log`.
