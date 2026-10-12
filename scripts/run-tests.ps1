Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Running SynapseX Automated Test Suite   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
if (-not $root) { $root = Get-Location }

Write-Host "`n[1/2] Running Backend Tests..." -ForegroundColor Yellow
Push-Location "$root\backend"
try {
    mvn test
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "`n[2/2] Running Frontend Tests..." -ForegroundColor Yellow
Push-Location "$root\frontend"
try {
    npm test -- --run
    if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "`nAll SynapseX test suites passed successfully!" -ForegroundColor Green
