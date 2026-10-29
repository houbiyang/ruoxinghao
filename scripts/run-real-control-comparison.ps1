param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$OutputDirectory = "",
    [int]$DurationSeconds = 10,
    [long[]]$Seeds = @(101, 202, 303, 404, 505),
    [string[]]$Strategies = @("CENTRALIZED", "FIXED_WEIGHT", "ADAPTIVE"),
    [string[]]$ScenarioCodes = @(),
    [string]$ReuseCompletedRunsSince = ""
)

$ErrorActionPreference = "Stop"
$requiredStationIds = @(
    "pv-gobi-01", "wind-ridge-02", "thermal-plant-06", "grid-link-00",
    "storage-hub-03", "load-oasis-04", "charge-corridor-05"
)
$terminalStates = @("COMPLETED", "INJECTION_NOT_OBSERVED", "FAILED", "STOPPED")
$scenarios = @(
    @{ Name = "NORMAL"; Code = "T0_STABLE_BASELINE"; Parameters = @{} },
    @{ Name = "LATENCY"; Code = "COM_LATENCY"; Parameters = @{ latency = 800; jitter = 120 } },
    @{ Name = "PACKET_LOSS"; Code = "COM_PACKET_LOSS"; Parameters = @{ packetLossRate = 35; burstLength = 6 } },
    @{ Name = "OFFLINE_RECOVERY"; Code = "COM_OFFLINE"; Parameters = @{} },
    @{ Name = "COMBINED"; Code = "COM_COMBINED"; Parameters = @{ latency = 800; jitter = 120; packetLossRate = 35; burstLength = 6 } }
)
if ($ScenarioCodes.Count -gt 0) {
    $scenarios = @($scenarios | Where-Object { $ScenarioCodes -contains $_.Code })
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot "..\backend\target\real-control-comparison-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$runRows = [System.Collections.Generic.List[object]]::new()
$curveRows = [System.Collections.Generic.List[object]]::new()
$failureRows = [System.Collections.Generic.List[object]]::new()

function Invoke-ApiData {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [string]$Method = "Get",
        [string]$Body = $null
    )
    $parameters = @{ Uri = $Uri; Method = $Method; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body
    }
    try {
        return (Invoke-RestMethod @parameters).data
    } catch {
        $responseDetail = $_.ErrorDetails.Message
        if (-not [string]::IsNullOrWhiteSpace($responseDetail)) {
            $errorPayload = try { $responseDetail | ConvertFrom-Json } catch { $null }
            if ($null -ne $errorPayload) {
                throw "API 请求失败: $($errorPayload.code) - $($errorPayload.message)"
            }
            throw "API 请求失败: $responseDetail"
        }
        throw
    }
}

function Test-Preflight {
    Invoke-ApiData -Uri "$BaseUrl/api/v1/health" | Out-Null
    $solverStatus = Invoke-ApiData -Uri "$BaseUrl/api/v1/market-dispatch/solver/status"
    if (-not $solverStatus.available) {
        throw "集中式求解器不可用: $($solverStatus.failureReason)"
    }
    $stations = @(Invoke-ApiData -Uri "$BaseUrl/api/v1/stations")
    $missingStations = @($requiredStationIds | Where-Object { $_ -notin $stations.stationId })
    if ($missingStations.Count -gt 0) {
        throw "缺少场站遥测: $($missingStations -join ', ')"
    }
    return $solverStatus
}

function Wait-AllStationsS0 {
    param([int]$RetryCount = 60)
    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        try {
            $stations = @(Invoke-ApiData -Uri "$BaseUrl/api/v1/stations")
            $targetStations = @($stations | Where-Object { $_.stationId -in $requiredStationIds })
            $nonS0Stations = @($targetStations | Where-Object { $_.linkState -ne "S0" })
            if (($targetStations.Count -eq $requiredStationIds.Count) -and ($nonS0Stations.Count -eq 0)) {
                return $true
            }
        } catch {
            Write-Verbose "等待 S0 时读取场站失败: $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Get-RunData([string]$RunId) {
    return Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/$RunId"
}

function Find-ReusableRun {
    param(
        [object[]]$Candidates,
        [string]$Strategy,
        [string]$ScenarioCode,
        [long]$Seed
    )
    foreach ($candidate in $Candidates) {
        if ($candidate.status -ne "COMPLETED" -or $candidate.scenarioCode -ne $ScenarioCode -or -not $candidate.resultSummary) {
            continue
        }
        $candidateSummary = try { $candidate.resultSummary | ConvertFrom-Json } catch { $null }
        if ($null -eq $candidateSummary) {
            continue
        }
        $candidateStrategy = [string]$candidateSummary.controlStrategy
        $candidateSeed = [long]$candidateSummary.randomSeed
        if ($candidateStrategy -ne $Strategy -or $candidateSeed -ne $Seed) {
            continue
        }
        try {
            $analysis = Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/$($candidate.runId)/summary"
            $plannedError = $analysis.plannedBalanceErrorKw ?? [double]::PositiveInfinity
            $planBalanced = [Math]::Abs($plannedError) -le 1.0
            $targetReached = $analysis.convergenceStatus -eq "TARGET_REACHED"
            $constraintsSatisfied = ($analysis.constraintViolationCount ?? 0) -eq 0
            $commandsCompleted = $analysis.commandTotalCount -gt 0 `
                -and $analysis.commandExecutedCount -eq $analysis.commandTotalCount `
                -and $analysis.commandPendingCount -eq 0 `
                -and $analysis.commandFailedCount -eq 0
            if ($planBalanced -and $targetReached -and $constraintsSatisfied -and $commandsCompleted) {
                return $candidate
            }
        } catch {
            Write-Verbose "复用候选校验失败: runId=$($candidate.runId), reason=$($_.Exception.Message)"
        }
    }
    return $null
}

function Wait-Run([string]$RunId) {
    for ($attempt = 1; $attempt -le 120; $attempt++) {
        Start-Sleep -Seconds 2
        try {
            $run = Get-RunData -RunId $RunId
            if ($terminalStates -contains $run.status) {
                return $run
            }
        } catch {
            Write-Verbose "等待实验终态时读取失败: $($_.Exception.Message)"
        }
    }
    throw "实验在 240 秒内未进入终态: $RunId"
}

function Stop-RunSafely([string]$RunId) {
    try {
        Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/$RunId/stop" -Method "Post" | Out-Null
    } catch {
        Write-Verbose "停止实验失败或实验已终止: $($_.Exception.Message)"
    }
}

function Get-BalanceMetrics {
    param(
        [string]$RunId,
        [string]$Strategy,
        [string]$Scenario,
        [long]$Seed,
        [Nullable[DateTimeOffset]]$MeasurementStartedAt
    )
    $points = @(Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/$RunId/replay?allStations=true&includeBaseline=false&limit=1000")
    $bySecond = @{}
    foreach ($point in $points) {
        $slot = ([DateTimeOffset]$point.timestamp).ToUnixTimeSeconds()
        if (-not $bySecond.ContainsKey($slot)) {
            $bySecond[$slot] = @{}
        }
        $bySecond[$slot][$point.stationId] = [double]$point.activePower
    }
    $errors = [System.Collections.Generic.List[double]]::new()
    $timestamps = [System.Collections.Generic.List[long]]::new()
    $sampleIndex = 0
    foreach ($slot in ($bySecond.Keys | Sort-Object)) {
        if ($null -ne $MeasurementStartedAt -and $slot -lt $MeasurementStartedAt.ToUnixTimeSeconds()) {
            continue
        }
        $power = $bySecond[$slot]
        if (@($requiredStationIds | Where-Object { -not $power.ContainsKey($_) }).Count -gt 0) {
            continue
        }
        $supply = $power["pv-gobi-01"] + $power["wind-ridge-02"] + $power["thermal-plant-06"] `
            + $power["grid-link-00"] + $power["storage-hub-03"]
        $demand = [Math]::Abs($power["load-oasis-04"]) + [Math]::Abs($power["charge-corridor-05"])
        $error = [Math]::Abs($supply - $demand)
        $errors.Add($error)
        $timestamps.Add([long]$slot)
        $curveRows.Add([pscustomobject]@{
            strategy = $Strategy; scenario = $Scenario; seed = $Seed; sample = $sampleIndex
            timestamp_epoch_seconds = $slot; balance_error_kw = $error
        })
        $sampleIndex += 1
    }
    $constraintViolationCount = @($points | Where-Object {
        $_.online -and ([Math]::Abs([double]$_.activePower) -gt ([double]$_.availableCapacity + 0.001))
    }).Count
    if ($errors.Count -eq 0) {
        return @{ Mean = $null; Rmse = $null; ConvergenceRounds = 0; ConvergenceTimeSeconds = $null; ConstraintViolationCount = $constraintViolationCount }
    }
    $mean = ($errors | Measure-Object -Average).Average
    $rmse = [Math]::Sqrt((($errors | ForEach-Object { $_ * $_ } | Measure-Object -Average).Average))
    $convergenceRound = 0
    $consecutiveSamples = 0
    for ($index = 0; $index -lt $errors.Count; $index++) {
        $consecutiveSamples = if ($errors[$index] -le 1.0) { $consecutiveSamples + 1 } else { 0 }
        if ($consecutiveSamples -ge 3) {
            $convergenceRound = $index + 1
            break
        }
    }
    $convergenceTime = if ($convergenceRound -gt 0) {
        [double]($timestamps[$convergenceRound - 1] - $timestamps[0])
    } else {
        $null
    }
    return @{
        Mean = $mean; Rmse = $rmse; ConvergenceRounds = $convergenceRound
        ConvergenceTimeSeconds = $convergenceTime; ConstraintViolationCount = $constraintViolationCount
    }
}

function Get-Statistics([object[]]$Values) {
    $numbers = @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ })
    if ($numbers.Count -eq 0) {
        return @{ Mean = $null; StdDev = $null; Minimum = $null; Maximum = $null }
    }
    $mean = ($numbers | Measure-Object -Average).Average
    $variance = ($numbers | ForEach-Object { [Math]::Pow($_ - $mean, 2) } | Measure-Object -Average).Average
    return @{
        Mean = $mean
        StdDev = [Math]::Sqrt($variance)
        Minimum = ($numbers | Measure-Object -Minimum).Minimum
        Maximum = ($numbers | Measure-Object -Maximum).Maximum
    }
}

$solverStatus = Test-Preflight
if (-not (Wait-AllStationsS0)) {
    throw "正式实验启动前场站未全部恢复到 S0。"
}
$reuseCandidates = @()
$reuseSince = $null
if (-not [string]::IsNullOrWhiteSpace($ReuseCompletedRunsSince)) {
    $reuseSince = [DateTimeOffset]::Parse($ReuseCompletedRunsSince)
    $reuseCandidates = @(Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/latest?limit=200" | Where-Object {
        [DateTimeOffset]$_.startedAt -ge $reuseSince
    })
}

foreach ($strategy in $Strategies) {
    foreach ($scenario in $scenarios) {
        foreach ($seed in $Seeds) {
            $runId = $null
            $runStartedAt = Get-Date
            $runStartedByScript = $false
            $evidenceSource = "NEW_RUN"
            try {
                if (-not (Wait-AllStationsS0)) {
                    throw "本轮启动前场站未全部恢复到 S0"
                }
                Test-Preflight | Out-Null
                $reusableRun = Find-ReusableRun -Candidates $reuseCandidates -Strategy $strategy `
                    -ScenarioCode $scenario.Code -Seed $seed
                if ($null -ne $reusableRun) {
                    $runId = $reusableRun.runId
                    $run = $reusableRun
                    $evidenceSource = "REUSED"
                } else {
                    $scenarioDurationSeconds = if ($scenario.Code -eq "COM_OFFLINE") {
                        [Math]::Max($DurationSeconds, 25)
                    } else {
                        $DurationSeconds
                    }
                    $body = @{
                        durationSeconds = $scenarioDurationSeconds
                        automaticRecovery = $true
                        controlStrategy = $strategy
                        randomSeed = $seed
                        faultParameters = $scenario.Parameters
                    } | ConvertTo-Json -Depth 5 -Compress
                    $started = Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/scenarios/$($scenario.Code)/start" -Method "Post" -Body $body
                    $runId = $started.experimentRunId
                    $runStartedByScript = $true
                    $run = Wait-Run -RunId $runId
                }
                $summary = Invoke-ApiData -Uri "$BaseUrl/api/v1/experiments/runs/$runId/summary"
                $rawSummary = if ($run.resultSummary) { $run.resultSummary | ConvertFrom-Json } else { $null }
                $measurementStartedAt = if ($null -ne $rawSummary -and $rawSummary.orchestrationCompletedAt) {
                    [DateTimeOffset]$rawSummary.orchestrationCompletedAt
                } else {
                    $null
                }
                $metrics = Get-BalanceMetrics -RunId $runId -Strategy $strategy -Scenario $scenario.Name `
                    -Seed $seed -MeasurementStartedAt $measurementStartedAt
                $messageLossRate = if (($summary.messageSentCount ?? 0) -gt 0) {
                    100.0 * ($summary.messageLostCount ?? 0) / $summary.messageSentCount
                } else {
                    0.0
                }
                $recoveryTime = if ($null -ne $rawSummary) {
                    if ($rawSummary.recoveryStartedAt -and $rawSummary.recoveredAt) {
                        ([DateTimeOffset]$rawSummary.recoveredAt - [DateTimeOffset]$rawSummary.recoveryStartedAt).TotalSeconds
                    } else { 0.0 }
                } else { 0.0 }
                $commandSuccessRate = if ($summary.commandTotalCount -gt 0) {
                    100.0 * $summary.commandExecutedCount / $summary.commandTotalCount
                } else { 0.0 }
                $validationFailure = $null
                if ($run.status -ne "COMPLETED") { $validationFailure = "实验终态不是 COMPLETED" }
                elseif ([Math]::Abs(($summary.plannedBalanceErrorKw ?? [double]::PositiveInfinity)) -gt 1.0) { $validationFailure = "计划平衡误差超过 1 kW" }
                elseif ($summary.convergenceStatus -ne "TARGET_REACHED") { $validationFailure = "实际遥测未连续三次达到平衡容差" }
                elseif ($metrics.ConvergenceRounds -le 0) { $validationFailure = "实验曲线缺少连续三次达标证据" }
                elseif ($summary.constraintViolationCount -gt 0 -or $metrics.ConstraintViolationCount -gt 0) { $validationFailure = "存在约束违规" }
                elseif ($summary.commandTotalCount -le 0) { $validationFailure = "未生成真实控制命令" }
                elseif ($summary.commandPendingCount -gt 0) { $validationFailure = "存在未进入终态的控制命令" }
                elseif ($summary.commandFailedCount -gt 0) { $validationFailure = "存在明确失败的控制命令" }
                if ($validationFailure) {
                    $failureRows.Add([pscustomobject]@{
                        strategy = $strategy; scenario = $scenario.Name; seed = $seed; run_id = $runId
                        status = $run.status; evidence_source = $evidenceSource; failure_reason = $validationFailure
                    })
                }
                $runRows.Add([pscustomobject]@{
                    strategy = $strategy; scenario = $scenario.Name; scenario_code = $scenario.Code; seed = $seed
                    run_id = $runId; evidence_source = $evidenceSource; status = $run.status; controller_type = $summary.controllerType
                    convergence_status = $summary.convergenceStatus; mean_balance_error_kw = $metrics.Mean
                    rmse_kw = $metrics.Rmse; convergence_rounds = $summary.convergenceRound ?? $metrics.ConvergenceRounds
                    convergence_time_seconds = $metrics.ConvergenceTimeSeconds
                    message_sent_count = $summary.messageSentCount ?? 0; message_lost_count = $summary.messageLostCount ?? 0
                    message_loss_rate_percent = $messageLossRate; recovery_time_seconds = $recoveryTime
                    constraint_violation_count = [Math]::Max(($summary.constraintViolationCount ?? 0), $metrics.ConstraintViolationCount)
                    command_total_count = $summary.commandTotalCount; command_executed_count = $summary.commandExecutedCount
                    command_failed_count = $summary.commandFailedCount; command_pending_count = $summary.commandPendingCount
                    command_success_rate_percent = $commandSuccessRate; validation_failure = $validationFailure
                })
            } catch {
                $failureReason = $_.Exception.Message
                $failureRows.Add([pscustomobject]@{
                    strategy = $strategy; scenario = $scenario.Name; seed = $seed; run_id = $runId
                    status = "SCRIPT_FAILED"; evidence_source = $evidenceSource; failure_reason = $failureReason
                })
                $runRows.Add([pscustomobject]@{
                    strategy = $strategy; scenario = $scenario.Name; scenario_code = $scenario.Code; seed = $seed
                    run_id = $runId; evidence_source = $evidenceSource; status = "SCRIPT_FAILED"; controller_type = $null; convergence_status = $null
                    mean_balance_error_kw = $null; rmse_kw = $null; convergence_rounds = 0; convergence_time_seconds = $null
                    message_sent_count = 0; message_lost_count = 0; message_loss_rate_percent = 0
                    recovery_time_seconds = 0; constraint_violation_count = 0; command_total_count = 0
                    command_executed_count = 0; command_failed_count = 0; command_pending_count = 0
                    command_success_rate_percent = 0; validation_failure = $failureReason
                })
            } finally {
                if ($runStartedByScript -and $null -ne $runId) {
                    Stop-RunSafely -RunId $runId
                }
                if (-not (Wait-AllStationsS0)) {
                    $failureRows.Add([pscustomobject]@{
                        strategy = $strategy; scenario = $scenario.Name; seed = $seed; run_id = $runId
                        status = "RECOVERY_FAILED"; evidence_source = $evidenceSource; failure_reason = "轮次结束后场站未全部回到 S0"
                    })
                }
                Write-Host ("[{0}] {1}/{2}/{3} 完成，来源 {4}，耗时 {5:N1}s" -f
                    (Get-Date -Format "HH:mm:ss"), $strategy, $scenario.Name, $seed, $evidenceSource,
                    ((Get-Date) - $runStartedAt).TotalSeconds)
            }
        }
    }
}

$rawPath = Join-Path $OutputDirectory "real-control-runs.csv"
$curvePath = Join-Path $OutputDirectory "real-control-balance-curves.csv"
$summaryPath = Join-Path $OutputDirectory "real-control-summary.csv"
$failuresPath = Join-Path $OutputDirectory "real-control-failures.csv"
$runRows | Export-Csv -NoTypeInformation -Encoding utf8 -Path $rawPath
$curveRows | Export-Csv -NoTypeInformation -Encoding utf8 -Path $curvePath
$failureRows | Export-Csv -NoTypeInformation -Encoding utf8 -Path $failuresPath

$summaryRows = $runRows | Group-Object strategy, scenario | ForEach-Object {
    $allRows = @($_.Group)
    $completedRows = @($allRows | Where-Object { $_.status -eq "COMPLETED" -and -not $_.validation_failure })
    $balanceStats = Get-Statistics -Values $completedRows.mean_balance_error_kw
    $rmseStats = Get-Statistics -Values $completedRows.rmse_kw
    [pscustomobject]@{
        strategy = $allRows[0].strategy; scenario = $allRows[0].scenario; seed_count = $allRows.Count
        completed_seed_count = $completedRows.Count; failed_seed_count = $allRows.Count - $completedRows.Count
        mean_balance_error_kw = $balanceStats.Mean; std_balance_error_kw = $balanceStats.StdDev
        min_balance_error_kw = $balanceStats.Minimum; max_balance_error_kw = $balanceStats.Maximum
        rmse_kw = $rmseStats.Mean; std_rmse_kw = $rmseStats.StdDev; min_rmse_kw = $rmseStats.Minimum
        max_rmse_kw = $rmseStats.Maximum
        convergence_rounds = ($completedRows.convergence_rounds | Measure-Object -Average).Average
        convergence_time_seconds = ($completedRows.convergence_time_seconds | Measure-Object -Average).Average
        message_loss_rate_percent = ($completedRows.message_loss_rate_percent | Measure-Object -Average).Average
        recovery_time_seconds = ($completedRows.recovery_time_seconds | Measure-Object -Average).Average
        constraint_violation_count = ($allRows.constraint_violation_count | Measure-Object -Sum).Sum
        command_success_rate_percent = ($completedRows.command_success_rate_percent | Measure-Object -Average).Average
    }
}
$summaryRows | Export-Csv -NoTypeInformation -Encoding utf8 -Path $summaryPath

$weakScenarios = @("LATENCY", "PACKET_LOSS", "OFFLINE_RECOVERY", "COMBINED")
$fixedRows = @($runRows | Where-Object { $_.strategy -eq "FIXED_WEIGHT" -and $_.scenario -in $weakScenarios -and $_.status -eq "COMPLETED" -and -not $_.validation_failure })
$adaptiveRows = @($runRows | Where-Object { $_.strategy -eq "ADAPTIVE" -and $_.scenario -in $weakScenarios -and $_.status -eq "COMPLETED" -and -not $_.validation_failure })
$fixedAggregateRmse = if ($fixedRows.Count -gt 0) { [Math]::Sqrt((($fixedRows.rmse_kw | ForEach-Object { $_ * $_ } | Measure-Object -Average).Average)) } else { $null }
$adaptiveAggregateRmse = if ($adaptiveRows.Count -gt 0) { [Math]::Sqrt((($adaptiveRows.rmse_kw | ForEach-Object { $_ * $_ } | Measure-Object -Average).Average)) } else { $null }
$improvementRate = if ($null -ne $fixedAggregateRmse -and $fixedAggregateRmse -gt 0 -and $null -ne $adaptiveAggregateRmse) {
    100.0 * ($fixedAggregateRmse - $adaptiveAggregateRmse) / $fixedAggregateRmse
} else { $null }
$adaptiveAssessment = [pscustomobject]@{
    fixed_weight_weak_scenario_rmse_kw = $fixedAggregateRmse
    adaptive_weak_scenario_rmse_kw = $adaptiveAggregateRmse
    adaptive_improvement_percent = $improvementRate
    may_claim_performance_improvement = ($null -ne $improvementRate -and $improvementRate -ge 10.0)
    claim_threshold_percent = 10.0
}
$adaptiveAssessment | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory "adaptive-performance-assessment.json") -Encoding utf8

$svgPath = Join-Path $OutputDirectory "real-control-balance-curves.svg"
$colors = @{ CENTRALIZED = "#2563eb"; FIXED_WEIGHT = "#dc2626"; ADAPTIVE = "#16a34a" }
$svg = [System.Text.StringBuilder]::new('<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="480"><rect width="100%" height="100%" fill="white"/><text x="30" y="28">真实 MQTT 实验：功率平衡误差曲线</text>')
foreach ($strategy in $Strategies) {
    $series = @($curveRows | Where-Object { $_.strategy -eq $strategy } | Group-Object sample | ForEach-Object {
        [pscustomobject]@{ sample = [int]$_.Name; value = ($_.Group.balance_error_kw | Measure-Object -Average).Average }
    } | Sort-Object sample)
    $points = ($series | ForEach-Object { "{0},{1}" -f (40 + $_.sample * 12), (440 - [Math]::Min(380, $_.value * 2)) }) -join ' '
    [void]$svg.Append("<polyline fill='none' stroke='$($colors[$strategy])' stroke-width='2' points='$points'/><text x='850' y='$((50 + 22 * [array]::IndexOf($Strategies, $strategy)))' fill='$($colors[$strategy])'>$strategy</text>")
}
[void]$svg.Append('</svg>')
[System.IO.File]::WriteAllText($svgPath, $svg.ToString(), [System.Text.Encoding]::UTF8)

$gitCommit = try { (git rev-parse HEAD 2>$null).Trim() } catch { $null }
$environment = [pscustomobject]@{
    generated_at = (Get-Date).ToString("o"); base_url = $BaseUrl; duration_seconds = $DurationSeconds
    seeds = $Seeds; strategies = $Strategies; scenarios = $scenarios.Code; expected_run_count = $Strategies.Count * $scenarios.Count * $Seeds.Count
    actual_run_count = $runRows.Count; reused_run_count = @($runRows | Where-Object { $_.evidence_source -eq "REUSED" }).Count
    new_run_count = @($runRows | Where-Object { $_.evidence_source -eq "NEW_RUN" }).Count
    reuse_completed_runs_since = $reuseSince; failure_count = $failureRows.Count; solver = $solverStatus; git_commit = $gitCommit
}
$environment | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory "experiment-environment.json") -Encoding utf8

$manifestPath = [System.IO.Path]::GetFullPath((Join-Path $OutputDirectory "sha256-manifest.csv"))
Get-ChildItem -LiteralPath $OutputDirectory -File | Where-Object {
    -not [string]::Equals($_.FullName, $manifestPath, [System.StringComparison]::OrdinalIgnoreCase)
} | Sort-Object Name | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    [pscustomobject]@{ file = $_.Name; sha256 = $hash.Hash.ToLowerInvariant(); size_bytes = $_.Length }
} | Export-Csv -NoTypeInformation -Encoding utf8 -Path $manifestPath

Write-Host "真实实验导出完成: $OutputDirectory"
Write-Host "运行数: $($runRows.Count)，失败记录: $($failureRows.Count)"
Write-Host "自适应性能提升可声明: $($adaptiveAssessment.may_claim_performance_improvement)"
