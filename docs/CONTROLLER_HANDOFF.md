# Controller package follow-up

2026-10-27 (Asia/Bangkok). All nine production REST controllers were moved to `oasis-api/src/main/java/com/jeyz9/oasisapi/controller`, package `com.jeyz9.oasisapi.controller`:

- AdminController
- AuthController
- BookingController
- CatalogController
- MembershipController
- NotificationController
- PaymentController
- ReportController
- TrainingController

Only package declarations and the AuthThrottle/PaymentGateway imports changed. Routes, HTTP methods, DTOs, service dependencies and controller method bodies are identical to the before snapshot. A source comparison verified all nine files and retained all 83 class/method mapping annotations. The test-only simulator remains in its guarded fixture package and is excluded from production.

README, ARCHITECTURE and the JPA handoff now reflect the controller layer. Security configuration, services, repositories, models, migrations and frontend source were not changed in this follow-up. Existing empty domain directories may remain on disk; no additional domain implementation was moved.

Verification command (PowerShell in oasis-api):

```powershell
$env:OASIS_PG_TEST='true'
$env:OASIS_BROWSER_TEST='true'
.\mvnw.cmd -o clean package
```

Verification status: PASS — 61 tests, 0 failures, 0 errors, 0 skips; clean executable production JAR built. All nine controllers are packaged under controller/ and no legacy domain-controller classes remain. All moved-file whitespace checks and documentation links pass. Transcript `.local/controller-check.log`; reviewed diff `.local/controller-review.diff`; before snapshot `.local/controller-before`. No Git repository/HEAD exists, so review uses the actual pre-move source snapshot.

No commit, push, deployment or production database mutation was requested or performed.

Later cleanup removed the empty legacy directories mentioned above; see [cleanup handoff](CLEANUP_HANDOFF.md).
