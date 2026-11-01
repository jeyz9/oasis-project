$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
$javaSourcePaths = @(
    (Join-Path $workspacePath 'oasis-api/src/main/java'),
    (Join-Path $workspacePath 'oasis-api/src/test/java')
)
$wildcardImports = @(
    Get-ChildItem -LiteralPath $javaSourcePaths -Filter '*.java' -File -Recurse |
        Select-String -Pattern '^\s*import\s+(?:static\s+)?[\w.]+\.\*\s*;'
)
if ($wildcardImports.Count -gt 0) {
    $wildcardImports | ForEach-Object { Write-Output "$($_.Path):$($_.LineNumber): $($_.Line.Trim())" }
    throw 'Wildcard Java imports are not allowed. Use explicit class, annotation, field, or method imports.'
}
Write-Output 'Java explicit-import check: PASS (main and test sources).'
