param([string]$PostgresBin = '')
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$dataPath = Join-Path $workspacePath '.local/phase1-pg'
$logPath = Join-Path $workspacePath '.local/phase1-postgres.log'
if (!$PostgresBin) {
    $postgresCommand = Get-Command pg_ctl.exe -ErrorAction SilentlyContinue
    if (!$postgresCommand) { throw 'PostgreSQL server binaries are required. Pass -PostgresBin with their bin directory.' }
    $PostgresBin = Split-Path -Parent $postgresCommand.Source
}
$psqlPath = Join-Path $PostgresBin 'psql.exe'
$readyPath = Join-Path $PostgresBin 'pg_isready.exe'
New-Item -ItemType Directory -Force (Join-Path $workspacePath '.local') | Out-Null
if (!(Test-Path (Join-Path $dataPath 'PG_VERSION'))) {
    & (Join-Path $PostgresBin 'initdb.exe') -D $dataPath -U oasis_test --auth-local=trust --auth-host=trust --encoding=UTF8
    if ($LASTEXITCODE -ne 0) { throw 'Isolated PostgreSQL initialization failed.' }
}
& $readyPath -h 127.0.0.1 -p 55439
if ($LASTEXITCODE -ne 0) {
    $arguments = "-D `"$dataPath`" -h 127.0.0.1 -p 55439 -c max_connections=30"
    Start-Process -FilePath (Join-Path $PostgresBin 'postgres.exe') -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardError $logPath | Out-Null
    for ($attempt=0; $attempt -lt 50; $attempt++) {
        Start-Sleep -Milliseconds 200
        & $readyPath -h 127.0.0.1 -p 55439 | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    }
    if ($LASTEXITCODE -ne 0) { throw "Isolated PostgreSQL did not start. Inspect $logPath" }
}
# Do not create a database on an unrelated server that happens to use our port.
$actualDirectory = & $psqlPath -h 127.0.0.1 -p 55439 -U oasis_test -d postgres -At -c 'SHOW data_directory'
if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($actualDirectory.Trim()).TrimEnd('\','/') -ne [IO.Path]::GetFullPath($dataPath).TrimEnd('\','/')) {
    throw 'Port 55439 is occupied by a different or inaccessible PostgreSQL cluster. Nothing was seeded.'
}
foreach ($databaseName in @('oasis_phase1_test','oasis_phase1_demo')) {
    $exists = & $psqlPath -h 127.0.0.1 -p 55439 -U oasis_test -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='$databaseName'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect local fixture databases.' }
    if ($exists -ne '1') {
        & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p 55439 -U oasis_test $databaseName
        if ($LASTEXITCODE -ne 0) { throw "Cannot create $databaseName" }
    }
}
Write-Output 'Isolated PostgreSQL ready: 127.0.0.1:55439 (test and demo databases are separate).'
