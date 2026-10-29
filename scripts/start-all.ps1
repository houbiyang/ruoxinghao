$ErrorActionPreference = "Stop"

function Wait-HttpOk {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$RetryCount = 60,
        [int]$DelaySeconds = 2
    )

    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        try {
            Invoke-RestMethod -Uri $Uri -TimeoutSec 3 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds $DelaySeconds
        }
    }
    throw "HTTP service timeout: $Uri"
}

function Wait-ComposeHealthy {
    param([int]$RetryCount = 60)

    $requiredServices = @("postgres", "redis", "emqx")
    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        $allServicesHealthy = $true
        foreach ($serviceName in $requiredServices) {
            $containerId = (& docker compose -p weak-signal ps -q $serviceName 2>$null | Select-Object -First 1)
            if ([string]::IsNullOrWhiteSpace($containerId)) {
                $allServicesHealthy = $false
                break
            }

            $healthStatus = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId 2>$null | Select-Object -First 1)
            if ($healthStatus -ne "healthy" -and $healthStatus -ne "running") {
                $allServicesHealthy = $false
                break
            }
        }

        if ($allServicesHealthy) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Infrastructure health check timeout."
}

function Start-ManagedProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    $runDirectory = Join-Path $PSScriptRoot "..\.run"
    $logDirectory = Join-Path $PSScriptRoot "..\logs"
    New-Item -ItemType Directory -Force -Path $runDirectory, $logDirectory | Out-Null

    $logPath = Join-Path $logDirectory "$Name.log"
    $errorLogPath = Join-Path $logDirectory "$Name.err.log"
    $powerShellCommand = Get-Command pwsh.exe -ErrorAction SilentlyContinue
    $powerShellExecutable = if ($null -ne $powerShellCommand) { $powerShellCommand.Source } else { "" }
    if ([string]::IsNullOrWhiteSpace($powerShellExecutable)) {
        $powerShellExecutable = (Get-Command powershell.exe -ErrorAction Stop).Source
    }

    $process = Start-Process -FilePath $powerShellExecutable `
        -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $Command `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $logPath `
        -RedirectStandardError $errorLogPath `
        -PassThru
    Set-Content -LiteralPath (Join-Path $runDirectory "$Name.pid") -Value $process.Id
    Write-Host "Started $Name, PID=$($process.Id), log=$logPath"
}

Set-Location "$PSScriptRoot\.."

$existingBackendListener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $existingBackendListener) {
    throw "端口 8080 已被进程 PID=$($existingBackendListener.OwningProcess) 占用。请先执行 .\scripts\stop-all.ps1，再重新启动项目。"
}

. "$PSScriptRoot\use-jdk21.ps1"
Set-Jdk21ForProcess

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found."
}
docker version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop is not running or Docker Engine is unavailable."
}

if (-not (Test-Path ".\.env")) {
    throw ".env was not found. Create it from .env.example first."
}

Write-Host "Starting infrastructure..."
& "$PSScriptRoot\start-infrastructure.ps1"
Wait-ComposeHealthy

Write-Host "Starting backend..."
Start-ManagedProcess -Name "backend" -WorkingDirectory (Join-Path $PSScriptRoot "..") -Command "& '$PSScriptRoot\start-backend.ps1'"
Wait-HttpOk -Uri "http://127.0.0.1:8080/api/v1/health"

Write-Host "Starting simulator..."
Start-ManagedProcess -Name "simulator" -WorkingDirectory (Join-Path $PSScriptRoot "..") -Command "& '$PSScriptRoot\start-simulator.ps1'"

Write-Host "Building and starting frontend API preview..."
$frontendPath = Join-Path $PSScriptRoot "..\frontend"
Start-ManagedProcess -Name "frontend-preview" -WorkingDirectory $frontendPath -Command "`$env:VITE_DATA_SOURCE='api'; `$env:VITE_API_BASE_URL='http://127.0.0.1:8080'; npm run build; npm run preview -- --host 127.0.0.1 --port 4173"
Wait-HttpOk -Uri "http://127.0.0.1:4173/"

Write-Host "Startup complete."
Write-Host "Frontend: http://127.0.0.1:4173/"
Write-Host "Backend health: http://127.0.0.1:8080/api/v1/health"
Write-Host "EMQX Dashboard: http://127.0.0.1:18083/"
