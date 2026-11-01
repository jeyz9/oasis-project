param([string]$PostgresBin = '')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'start-test-postgres.ps1') -PostgresBin $PostgresBin
$oldOptIn = $env:OASIS_LOCAL_FIXTURE
try {
    $env:OASIS_LOCAL_FIXTURE = 'true'
    Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'oasis-api')
    # Package moves leave orphaned .class files in incremental builds; clean before scanning beans.
    & .\mvnw.cmd clean spring-boot:test-run '-Dspring-boot.run.main-class=com.jeyz9.oasisapi.LocalDemo' '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false'
    if ($LASTEXITCODE -ne 0) { throw 'Local demonstration backend failed.' }
} finally {
    Pop-Location
    $env:OASIS_LOCAL_FIXTURE = $oldOptIn
}
