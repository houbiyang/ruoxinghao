$ErrorActionPreference = "Stop"

$quickStartScript = Join-Path $PSScriptRoot "scripts\quick-start.ps1"
if (-not (Test-Path -LiteralPath $quickStartScript -PathType Leaf)) {
    throw "缺少项目启动脚本：$quickStartScript"
}

& $quickStartScript
if (-not $?) {
    exit 1
}
