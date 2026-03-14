<#
.SYNOPSIS
    Launcher script for the single-file Minesweeper desktop EXE.
#>

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

$Candidates = @(
    (Join-Path $Root "dist\MinesweepAssist.exe"),
    (Join-Path $Root "MinesweepAssist.exe")
)

$ExePath = $Candidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $ExePath) {
    Write-Host "未找到 MinesweepAssist.exe。请先执行 .\build.ps1 或 .\build.bat" -ForegroundColor Red
    exit 1
}

Write-Host "启动: $ExePath" -ForegroundColor Cyan
Start-Process -FilePath $ExePath
