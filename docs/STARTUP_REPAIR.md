# Startup repair: duplicate service implementation beans

2026-10-27 (Asia/Bangkok). **COMPLETE for the reported ConflictingBeanDefinitionException.**

## Diagnosis

The current source has exactly one set of eleven implementation classes under `com.jeyz9.oasisapi.service.impl`. The user-reported old package `com.jeyz9.oasisapi.serviceImpl` has no source definitions. Before repair, `target/classes` contained both packages, each with eleven ServiceImpl classes. Spring scanned the orphaned old class files and the current classes and derived the same `accountServiceImpl` bean name.

The existing source package choice was preserved. No bean naming/overriding, component scan exclusions, authorization rules, database schema, dependencies or service behavior was changed to hide the conflict.

## Repair

- Verified the exact generated Maven output path before running Maven clean. Clean removed orphaned bytecode; the rebuilt classes/JAR now contain eleven current implementations and zero old-package implementations.
- Stopped only the synthetic LocalDemo process previously started by this task, avoiding a subsequent port-8080 collision with the user's IDE run. No IDE/compiler process was stopped.
- The local demo launcher now runs `clean spring-boot:test-run` so future package moves do not carry old compiled beans into demo startup.
- Restored five wildcard imports found in SecurityConfig to explicit class imports, as required by AGENTS.md. Its configuration/security method bodies are unchanged.
- README/ARCHITECTURE now show the actual `service/impl` layout and explain rebuilding after Java package moves.

## Commands and actual verification

| Command/check | Result |
| --- | --- |
| `oasis-api`: `.\mvnw.cmd -o clean package` with OASIS_PG_TEST/OASIS_BROWSER_TEST removed from that shell | PASS. Clean compilation and Spring context tests succeeded. |
| `oasis-api`: `.\mvnw.cmd -o package` after final explicit-import edit | PASS. **5 smoke/unit tests passed**, 56 opted-in PostgreSQL tests skipped; no failures/errors. `.local/startup-final-check.log`. |
| Compiled output and executable JAR inspection | PASS. Eleven implementations in `service/impl`, none in `serviceImpl`. |
| `scripts/check-java-imports.ps1` | PASS. |
| SecurityConfig body and changed-file diff/whitespace comparison | PASS. Imports only in SecurityConfig; launcher/docs changes reviewed against `.local/startup-fix-before`. |
| Packaged JAR startup using explicit local demo datasource, prod profile, schema validation, baseline disabled and notification worker disabled | PASS. Real embedded Tomcat started on OS-assigned port 62472; `/api/v1/health` returned **UP**, payment provider **UNCONFIGURED**, enabled **false**. Temporary verification process stopped afterward. |

The packaged startup check used `jdbc:postgresql://127.0.0.1:55439/oasis_phase1_demo`, user oasis_test and the synthetic cluster's existing local trust configuration. Credentials/.env for ordinary runtime were not read or changed. Flyway validated the existing six migrations; no schema update or real provider connection occurred. Startup log `.local/startup-jar-smoke.log`, result `.local/startup-jar-smoke-result.json`. Port 8080 is available for the user's IDE run after verification.

The launcher change was parsed/reviewed; the live startup check used the executable JAR on a temporary port to keep 8080 free. No claim is made that the user's IDE-specific datasource/environment settings were run or changed.

## Run again in IntelliJ

Stop the previous run, choose **Build → Rebuild Project**, then run OasisApiApplication again. Alternatively, from oasis-api run `.\mvnw.cmd -o clean test` after a package move, then start using the existing configured environment. A retained `target/classes/.../serviceImpl` folder is the stale-output symptom. DevTools reporting exit code 0 in the supplied log did not indicate successful ApplicationContext startup.

No commit, push, deployment, production data deletion or Supabase change occurred. This was a single-agent self-review.
