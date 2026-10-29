Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$auditPath = Join-Path $projectRoot 'docs\open-source\dependency-audit.csv'
$mavenSettingsPath = Join-Path $projectRoot 'maven-settings.xml'
$auditRecords = @(Import-Csv -LiteralPath $auditPath)

function New-PackageSet {
    return ,([System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal))
}

function Assert-InventoryMatches {
    param(
        [string]$Ecosystem,
        [System.Collections.Generic.HashSet[string]]$ExpectedPackages,
        [System.Collections.Generic.HashSet[string]]$ResolvedPackages
    )

    $missingRecords = @($ResolvedPackages | Where-Object { -not $ExpectedPackages.Contains($_) } | Sort-Object)
    $staleRecords = @($ExpectedPackages | Where-Object { -not $ResolvedPackages.Contains($_) } | Sort-Object)
    if ($missingRecords.Count -gt 0 -or $staleRecords.Count -gt 0) {
        throw "$Ecosystem 许可清单与当前依赖不一致。缺少记录：$($missingRecords -join ', ')；过期记录：$($staleRecords -join ', ')"
    }

    Write-Host "$Ecosystem 已核对 $($ResolvedPackages.Count) 项依赖，清单无缺漏或过期项。"
}

$expectedMavenPackages = New-PackageSet
$expectedNpmPackages = New-PackageSet
foreach ($record in $auditRecords) {
    if ([string]::IsNullOrWhiteSpace($record.declared_license) -or
        -not $record.license_metadata_url.StartsWith('https://', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "许可名称或来源链接缺失：$($record.ecosystem) $($record.package) $($record.version)"
    }

    $packageKey = "$($record.package)@$($record.version)"
    switch ($record.ecosystem) {
        'Maven' { $packageSet = $expectedMavenPackages }
        'npm' { $packageSet = $expectedNpmPackages }
        default { throw "未知依赖类别：$($record.ecosystem)" }
    }
    if (-not $packageSet.Add($packageKey)) {
        throw "重复的许可记录：$($record.ecosystem) $packageKey"
    }
}

$lockfilePath = Join-Path $projectRoot 'frontend\package-lock.json'
$lockfile = Get-Content -LiteralPath $lockfilePath -Raw | ConvertFrom-Json -AsHashtable
$resolvedNpmPackages = New-PackageSet
foreach ($packagePath in $lockfile.packages.Keys) {
    if ($packagePath -notmatch '(^|/)node_modules/(?<packageName>(@[^/]+/)?[^/]+)$') {
        continue
    }
    $packageVersion = $lockfile.packages[$packagePath].version
    if ([string]::IsNullOrWhiteSpace($packageVersion)) {
        throw "锁文件中的依赖版本缺失：$packagePath"
    }
    [void]$resolvedNpmPackages.Add("$($Matches.packageName)@$packageVersion")
}
Assert-InventoryMatches -Ecosystem 'npm' -ExpectedPackages $expectedNpmPackages -ResolvedPackages $resolvedNpmPackages

$resolvedMavenPackages = New-PackageSet
foreach ($moduleName in @('backend', 'simulator')) {
    $modulePath = Join-Path $projectRoot $moduleName
    $dependencyTreePath = New-TemporaryFile
    try {
        Push-Location $modulePath
        try {
            & mvn.cmd -gs $mavenSettingsPath -s $mavenSettingsPath dependency:tree "-DoutputFile=$dependencyTreePath" '-DoutputType=text' | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "$moduleName Maven 依赖树生成失败，退出码：$LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }

        foreach ($treeLine in Get-Content -LiteralPath $dependencyTreePath) {
            if ($treeLine -match '(?<group>[^:\s]+):(?<artifact>[^:\s]+):(?<type>[^:\s]+):(?:(?<classifier>[^:\s]+):)?(?<version>[^:\s]+):(?<scope>compile|runtime|test|provided|system)$') {
                [void]$resolvedMavenPackages.Add("$($Matches.group):$($Matches.artifact)@$($Matches.version)")
            }
        }
    }
    finally {
        Remove-Item -LiteralPath $dependencyTreePath -Force
    }
}
Assert-InventoryMatches -Ecosystem 'Maven' -ExpectedPackages $expectedMavenPackages -ResolvedPackages $resolvedMavenPackages
