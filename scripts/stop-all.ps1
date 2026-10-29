$ErrorActionPreference = "Stop"

function Stop-ProcessTree {
    param([Parameter(Mandatory = $true)][int]$ProcessId)

    $children = Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -eq $ProcessId }
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

$runDirectory = Join-Path $PSScriptRoot "..\.run"
if (Test-Path -LiteralPath $runDirectory) {
    foreach ($pidFile in Get-ChildItem -LiteralPath $runDirectory -Filter "*.pid" -File) {
        $processIdText = Get-Content -LiteralPath $pidFile.FullName -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($processIdText -match '^\d+$') {
            Write-Host "Stopping $($pidFile.BaseName), PID=$processIdText"
            Stop-ProcessTree -ProcessId ([int]$processIdText)
        }
        Remove-Item -LiteralPath $pidFile.FullName -Force -ErrorAction SilentlyContinue
    }
}

Get-CimInstance Win32_Process |
    Where-Object {
        $_.CommandLine -like "*spring-boot:run*" -or
        $_.CommandLine -like "*SimulatorApplication*" -or
        $_.CommandLine -like "*vite preview*"
    } |
    ForEach-Object {
        if ($_.ProcessId -ne $PID) {
            Write-Host "Stopping legacy process PID=$($_.ProcessId)"
            Stop-ProcessTree -ProcessId $_.ProcessId
        }
    }

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Set-Location "$PSScriptRoot\.."
    docker compose -p weak-signal --env-file .env stop
    docker compose -p weak-signal ps
}

Write-Host "Stopped application processes and infrastructure containers. Docker volumes were kept."
