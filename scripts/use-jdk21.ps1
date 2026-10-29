$ErrorActionPreference = "Stop"

function Test-Jdk21Home {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaHome
    )

    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExe)) {
        return $false
    }

    $versionOutput = & cmd.exe /c "`"$javaExe`" -version 2>&1" | Out-String
    return $versionOutput -match 'version "21(\.|")'
}

function Resolve-Jdk21Home {
    $candidateHomes = @()

    foreach ($envName in @("JAVA21_HOME", "JDK21_HOME", "JAVA_HOME")) {
        $envValue = [System.Environment]::GetEnvironmentVariable($envName, "Process")
        if ([string]::IsNullOrWhiteSpace($envValue)) {
            $envValue = [System.Environment]::GetEnvironmentVariable($envName, "User")
        }
        if ([string]::IsNullOrWhiteSpace($envValue)) {
            $envValue = [System.Environment]::GetEnvironmentVariable($envName, "Machine")
        }
        if (-not [string]::IsNullOrWhiteSpace($envValue)) {
            $candidateHomes += $envValue
        }
    }

    $candidateRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\BellSoft",
        "C:\Program Files\Zulu"
    )

    foreach ($candidateRoot in $candidateRoots) {
        if (Test-Path -LiteralPath $candidateRoot) {
            $candidateHomes += Get-ChildItem -LiteralPath $candidateRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match "21" } |
                ForEach-Object { $_.FullName }
        }
    }

    foreach ($candidateHome in ($candidateHomes | Select-Object -Unique)) {
        if (Test-Jdk21Home -JavaHome $candidateHome) {
            return $candidateHome
        }
    }

    throw "JDK 21 was not found. Install JDK 21 and set JAVA21_HOME or JDK21_HOME to the JDK 21 root directory."
}

function Set-Jdk21ForProcess {
    $jdk21Home = Resolve-Jdk21Home
    $jdk21Bin = Join-Path $jdk21Home "bin"

    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk21Home, "Process")
    [System.Environment]::SetEnvironmentVariable("PATH", "$jdk21Bin;$env:PATH", "Process")

    Write-Host "Using JDK 21: $jdk21Home"
}
