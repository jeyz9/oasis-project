param([string]$PostgresBin='')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'start-test-postgres.ps1') -PostgresBin $PostgresBin
if(!$PostgresBin){$PostgresBin=Split-Path -Parent (Get-Command pg_ctl.exe).Source}
$workspacePath=Split-Path -Parent $PSScriptRoot
$outputDirectory=Join-Path $workspacePath '.local/recovery-check'
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$dumpPath=Join-Path $outputDirectory 'synthetic-test.dump'
$restoreName='oasis_restore_check_'+[Guid]::NewGuid().ToString('N')
if($restoreName -notmatch '^oasis_restore_check_[a-f0-9]{32}$'){throw 'Unsafe recovery database name.'}
$created=$false
try {
    # start-test-postgres verified that port 55439 belongs to this workspace's isolated cluster.
    & (Join-Path $PostgresBin 'pg_dump.exe') -h 127.0.0.1 -p 55439 -U oasis_test -d oasis_phase1_test -Fc --no-owner --no-acl -f $dumpPath
    if($LASTEXITCODE -ne 0){throw 'Synthetic test backup failed.'}
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p 55439 -U oasis_test $restoreName
    if($LASTEXITCODE -ne 0){throw 'Cannot create disposable restore database.'}
    $created=$true
    & (Join-Path $PostgresBin 'pg_restore.exe') -h 127.0.0.1 -p 55439 -U oasis_test -d $restoreName --no-owner --no-acl --exit-on-error $dumpPath
    if($LASTEXITCODE -ne 0){throw 'Synthetic backup restore failed.'}
    $checks="SELECT (SELECT count(*) FROM flyway_schema_history WHERE success)||','||(SELECT count(*) FROM membership_grants g WHERE entitlement_mode='CREDITS' AND remaining_credits<>(SELECT coalesce(sum(delta),0) FROM credit_ledger l WHERE l.grant_id=g.id))||','||(SELECT count(*) FROM class_sessions s WHERE reserved_count<>(SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status<>'CANCELLED'))"
    $source=& (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p 55439 -U oasis_test -d oasis_phase1_test -v ON_ERROR_STOP=1 -At -c $checks
    if($LASTEXITCODE -ne 0){throw 'Cannot verify source test database.'}
    $restored=& (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p 55439 -U oasis_test -d $restoreName -v ON_ERROR_STOP=1 -At -c $checks
    if($LASTEXITCODE -ne 0 -or $source -ne $restored -or $restored -ne '6,0,0'){throw "Recovery invariant mismatch: $source / $restored"}
    @{status='PASS';source='oasis_phase1_test';restored=$restoreName;checks=$restored;verifiedAt=[DateTimeOffset]::Now.ToString('o')}|ConvertTo-Json|Set-Content (Join-Path $outputDirectory 'result.json')
    Get-Content (Join-Path $outputDirectory 'result.json')
} finally {
    if($created) {
        # Only the UUID-named DB created by this invocation is removed; no source/demo DB is deleted.
        & (Join-Path $PostgresBin 'dropdb.exe') -h 127.0.0.1 -p 55439 -U oasis_test $restoreName
        if($LASTEXITCODE -ne 0){Write-Warning "Disposable restore database still exists: $restoreName"}
    }
}
