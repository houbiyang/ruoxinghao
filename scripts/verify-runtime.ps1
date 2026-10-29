$ErrorActionPreference = "Stop"

function Write-Section {
    param([Parameter(Mandatory = $true)][string]$Title)
    Write-Host ""
    Write-Host "== $Title =="
}

function Test-Port {
    param([Parameter(Mandatory = $true)][int]$Port)

    $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($connections) {
        Write-Host "端口 $Port 已被占用。"
    } else {
        Write-Host "端口 $Port 未占用。"
    }
}

Write-Section "Java"
java -version

Write-Section "Maven"
if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) {
    mvn.cmd -version
} else {
    Write-Host "未检测到 mvn.cmd。"
}

Write-Section "Docker"
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker version
    docker compose version
} else {
    Write-Host "未检测到 Docker CLI。"
}

Write-Section "Node"
if (Get-Command node -ErrorAction SilentlyContinue) {
    node --version
} else {
    Write-Host "未检测到 node。"
}
if (Get-Command npm -ErrorAction SilentlyContinue) {
    npm --version
} else {
    Write-Host "未检测到 npm。"
}

Write-Section "端口"
foreach ($port in @(8080, 1883, 18083, 5432, 6379, 4173)) {
    Test-Port -Port $port
}
