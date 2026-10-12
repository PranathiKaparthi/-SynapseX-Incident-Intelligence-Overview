param(
  [string]$BackendUrl = "http://localhost:8080"
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " SynapseX Automated End-to-End Demo      " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

Write-Host "1. Authenticating as seeded engineer..." -ForegroundColor Yellow
$loginBody = @{
  email = "admin@synapsex.io"
  password = "Password123!"
} | ConvertTo-Json

try {
  $loginResp = Invoke-RestMethod -Uri "$BackendUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
  $token = $loginResp.accessToken
  Write-Host "Authenticated successfully! User: $($loginResp.user.fullName)" -ForegroundColor Green
} catch {
  Write-Host "Failed to login. Ensure backend is running. Error: $_" -ForegroundColor Red
  exit 1
}

Write-Host "`n2. Triggering Database Connection Pool Exhaustion Failure Scenario..." -ForegroundColor Yellow
$simBody = @{
  scenario = "DATABASE_CONNECTION_POOL_EXHAUSTION"
  durationSeconds = 60
  seed = 42
} | ConvertTo-Json

$headers = @{
  Authorization = "Bearer $token"
}

$simResp = Invoke-RestMethod -Uri "$BackendUrl/api/simulations/start" -Method Post -Body $simBody -Headers $headers -ContentType "application/json"
Write-Host "Simulation started! Status: $($simResp.status)" -ForegroundColor Green

Write-Host "`n3. Waiting 3s for telemetry processing and anomaly correlation..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

$incidents = Invoke-RestMethod -Uri "$BackendUrl/api/incidents" -Method Get -Headers $headers
Write-Host "Retrieved incidents count: $($incidents.totalElements)" -ForegroundColor Green
if ($incidents.content.Count -gt 0) {
  $first = $incidents.content[0]
  Write-Host "Top Incident: [$($first.severity)] $($first.title) (Status: $($first.status))" -ForegroundColor Cyan
}

Write-Host "`nDemo completed! Navigate to http://localhost:3000 to interact with War Rooms & Time Machine." -ForegroundColor Green
