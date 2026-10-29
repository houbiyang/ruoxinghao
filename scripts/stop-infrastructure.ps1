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

Set-Location "$PSScriptRoot\.."
docker compose -p weak-signal --env-file .env stop
docker compose -p weak-signal ps
