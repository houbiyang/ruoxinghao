$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker was not found. Install and start Docker Desktop first."
}

if (-not (Test-Path "$PSScriptRoot\..\docker-compose.yml")) {
    throw "docker-compose.yml was not found."
}

if (-not (Test-Path "$PSScriptRoot\..\.env")) {
    throw ".env was not found. Create it from .env.example first."
}

$workspaceDriveName = [System.IO.Path]::GetPathRoot($PSScriptRoot).Substring(0, 1)
$workspaceDrive = Get-PSDrive -Name $workspaceDriveName
$minimumFreeSpaceBytes = 2GB
if ($workspaceDrive.Free -lt $minimumFreeSpaceBytes) {
    throw "Workspace drive free space is below 2 GB. Infrastructure startup was stopped without deleting data."
}

Set-Location "$PSScriptRoot\.."
docker compose -p weak-signal --env-file .env up -d
docker compose -p weak-signal ps

$postgresSharedMemoryBytes = [long](docker inspect --format '{{.HostConfig.ShmSize}}' weak-signal-postgres)
if ($LASTEXITCODE -ne 0 -or $postgresSharedMemoryBytes -lt 256MB) {
    throw "PostgreSQL shared memory is below the required 256 MB."
}

$postgresHealthy = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $postgresHealth = docker inspect --format '{{.State.Health.Status}}' weak-signal-postgres 2>$null
    if ($postgresHealth -eq "healthy") {
        $postgresHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $postgresHealthy) {
    throw "PostgreSQL did not become healthy within 60 seconds. Existing data was left untouched."
}

$envMap = @{}
Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') {
        return
    }

    $parts = $_ -split '=', 2
    $envMap[$parts[0]] = $parts[1]
}

$loginBody = @{
    username = $envMap["EMQX_DASHBOARD_USERNAME"]
    password = $envMap["EMQX_DASHBOARD_PASSWORD"]
} | ConvertTo-Json

$loginResponse = $null
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $loginResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18083/api/v5/login" -ContentType "application/json" -Body $loginBody
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

if ($null -eq $loginResponse) {
    throw "EMQX Dashboard API is not ready. MQTT user cannot be initialized."
}

$authenticatorId = [System.Uri]::EscapeDataString("password_based:built_in_database")
$userBody = @{
    user_id = $envMap["EMQX_USERNAME"]
    password = $envMap["EMQX_PASSWORD"]
    is_superuser = $false
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18083/api/v5/authentication/$authenticatorId/users" -Headers @{ Authorization = "Bearer $($loginResponse.token)" } -ContentType "application/json" -Body $userBody | Out-Null
    Write-Host "Initialized EMQX MQTT user."
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 409) {
        Write-Host "EMQX MQTT user already exists."
    } else {
        throw
    }
}
