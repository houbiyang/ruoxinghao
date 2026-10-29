$ErrorActionPreference = "Stop"

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)

    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-CommandExists {
    param(
        [Parameter(Mandatory = $true)][string]$CommandName,
        [Parameter(Mandatory = $true)][string]$InstallHint
    )

    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "$CommandName was not found. $InstallHint"
    }
}

function Test-DockerEngineAvailable {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($null -eq $dockerCommand) {
        return $false
    }

    $dockerCheckProcess = Start-Process -FilePath $dockerCommand.Source `
        -ArgumentList @("version", "--format", "{{.Server.Version}}") `
        -WindowStyle Hidden `
        -PassThru
    if (-not $dockerCheckProcess.WaitForExit(5000)) {
        Stop-Process -Id $dockerCheckProcess.Id -Force -ErrorAction SilentlyContinue
        return $false
    }

    return $dockerCheckProcess.ExitCode -eq 0
}

Set-Location "$PSScriptRoot\.."

try {
    Write-Host "Weak Signal VPP quick start" -ForegroundColor Green
    Write-Host "This script starts Docker services, backend, simulator and frontend."

    Write-Step "Check runtime"
    Test-CommandExists -CommandName "docker" -InstallHint "Install and start Docker Desktop first."
    Test-CommandExists -CommandName "node" -InstallHint "Install Node.js first."
    Test-CommandExists -CommandName "npm" -InstallHint "Install npm from Node.js first."
    if (-not (Test-DockerEngineAvailable)) {
        $dockerDesktopPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        if (-not (Test-Path -LiteralPath $dockerDesktopPath -PathType Leaf)) {
            throw "Docker Desktop is not running and its executable was not found: $dockerDesktopPath"
        }

        Write-Host "Docker Desktop is not running. Starting it automatically..."
        Start-Process -FilePath $dockerDesktopPath -ArgumentList "--accept-license" -WindowStyle Hidden
        $dockerEngineReady = $false
        for ($attempt = 1; $attempt -le 60; $attempt++) {
            Start-Sleep -Seconds 2
            if (Test-DockerEngineAvailable) {
                $dockerEngineReady = $true
                break
            }
        }
        if (-not $dockerEngineReady) {
            throw "Docker Desktop started, but Docker Engine did not become ready within 120 seconds."
        }
    }

    Write-Step "Prepare local config"
    if (-not (Test-Path ".\.env")) {
        Copy-Item -LiteralPath ".\.env.example" -Destination ".\.env"
        Write-Host "Created .env from .env.example."
    } else {
        Write-Host ".env already exists."
    }

    Write-Step "Prepare frontend dependencies"
    if (-not (Test-Path ".\frontend\node_modules")) {
        Push-Location ".\frontend"
        npm install
        Pop-Location
    } else {
        Write-Host "frontend\node_modules exists, skip npm install."
    }

    Write-Step "Start project"
    & "$PSScriptRoot\start-all.ps1"

    Write-Step "Open browser"
    Start-Process "http://127.0.0.1:4173/"

    Write-Host ""
    Write-Host "Startup finished." -ForegroundColor Green
    Write-Host "Frontend: http://127.0.0.1:4173/"
    Write-Host "Backend health: http://127.0.0.1:8080/api/v1/health"
    Write-Host "EMQX Dashboard: http://127.0.0.1:18083/"
    Write-Host ""
    Write-Host "To stop the project, run: .\scripts\stop-all.ps1"
} catch {
    Write-Host ""
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Common reasons:"
    Write-Host "1. Docker Desktop is not running."
    Write-Host "2. Port 5432, 6379, 1883, 18083, 8080 or 4173 is occupied."
    Write-Host "3. JDK 21, Maven, Node.js or npm is missing."
    Write-Host "4. First startup needs to download Docker images and dependencies."
    exit 1
}
