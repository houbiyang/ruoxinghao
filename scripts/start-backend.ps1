$ErrorActionPreference = "Stop"

. "$PSScriptRoot\use-jdk21.ps1"
Set-Jdk21ForProcess

if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue) -and -not (Test-Path "$PSScriptRoot\..\backend\mvnw.cmd")) {
    throw "Maven was not found. Use mvn.cmd or provide backend\mvnw.cmd."
}

if (-not (Test-Path "$PSScriptRoot\..\.env")) {
    throw ".env was not found. Create it from .env.example first."
}

Set-Location "$PSScriptRoot\..\backend"
Get-Content "$PSScriptRoot\..\.env" | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') {
        return
    }

    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}

$settingsPath = "$PSScriptRoot\..\maven-settings.xml"

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd -s $settingsPath spring-boot:run
} else {
    mvn.cmd -s $settingsPath spring-boot:run
}
