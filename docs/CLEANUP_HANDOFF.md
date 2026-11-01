# Unused-folder and explicit-import cleanup

2026-10-27 (Asia/Bangkok). COMPLETE: unused source folders removed and explicit imports enforced; all verification below passed.

## Changes

- Expanded 333 wildcard import declarations across 104 Java files, including test fixtures and static JUnit assertions. All class, annotation and static-method imports are now explicit.
- Resolution used installed Java compiler symbols against the existing application/test classpath, not a guessed list of annotation or method names. No dependency was added.
- Compared all changed Java files against the actual before snapshot: only imports and blank-line formatting changed. Method bodies, signatures, annotation values, business logic and all six migrations are unchanged.
- Added the no-wildcard rule to AGENTS.md and the guard script `scripts/check-java-imports.ps1`, covering main/test sources. Future Java import changes must run the guard.
- Removed eight empty legacy Java/resource directories and one unreferenced frontend asset directory. `hero.png`, `react.svg` and `vite.svg` had no references in frontend source, HTML, Vite config or project documentation. The asset backup was hash-verified before deletion.

Removed directories:

- `oasis-api/src/main/java/com/jeyz9/oasisapi/admin`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/booking`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/catalog`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/membership`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/reporting`
- `oasis-api/src/main/java/com/jeyz9/oasisapi/training`
- `oasis-api/src/main/resources/static`
- `oasis-api/src/main/resources/templates`
- `oasis-app/src/assets`

The remaining identity, payment and notification packages contain active AuthThrottle, PaymentGateway and NotificationTransport code. Build/runtime support and local synthetic database state remain necessary. No important application or production data was deleted. Recovery snapshot: `.local/import-cleanup-before`; removed-folder manifest: `.local/removed-unused-folders.json`.

## Verification

Commands actually run:

```powershell
# Project root
.\scripts\check-java-imports.ps1
# oasis-api
$env:OASIS_PG_TEST='true'
$env:OASIS_BROWSER_TEST='true'
.\mvnw.cmd -o clean package
# oasis-app
npm.cmd run build
npm.cmd run lint
```

- Explicit-import guard: PASS, zero wildcard imports in main/test sources.
- Frontend build/lint: PASS after removing the unreferenced assets; existing production JS/CSS bundle hashes remain unchanged.
- Java before/after comparison and whitespace checks: PASS; no non-import code changes. Review evidence `.local/import-review.json`.
- Full backend/PostgreSQL/Chrome results: PASS — 61 tests, 0 failures, 0 errors, 0 skips; clean executable JAR built. Transcript `.local/import-cleanup-check.log`.

README and working rules are updated. No commit, push, deployment, gateway connection or production mutation occurred. This is a self-review by one agent; there is no Git metadata, so comparisons use the saved pre-edit sources.
