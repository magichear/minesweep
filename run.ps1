<#
.SYNOPSIS
    One-click run script for the Minesweeper application.
    Starts the Spring Boot backend.
    Press Ctrl+C to stop everything.
#>

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========== Minesweeper Launcher ==========" -ForegroundColor Cyan

# ---------- Start Spring Boot backend ----------
Write-Host "`nStarting Spring Boot backend..." -ForegroundColor Yellow
$backendProcess = Start-Process -FilePath "java" `
    -ArgumentList "-jar `"$Root\minesweepBackend.jar`" --spring.datasource.url=`"jdbc:h2:file:$Root/data/minesweep`"" `
    -PassThru -NoNewWindow

# ---------- Wait for backend port & open browser ----------
$port = 8080
Write-Host "Waiting for backend on port $port..." -ForegroundColor Yellow
$maxAttempts = 30
for ($i = 0; $i -lt $maxAttempts; $i++) {
    Start-Sleep -Seconds 1
    if ($backendProcess.HasExited) {
        Write-Host "ERROR: Backend process exited unexpectedly!" -ForegroundColor Red
        break
    }
    $conn = Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($conn.TcpTestSucceeded) {
        Write-Host "Backend is ready!" -ForegroundColor Green
        Start-Process "http://localhost:$port"
        Write-Host "Browser opened at http://localhost:$port" -ForegroundColor Cyan
        break
    }
}

# ---------- Wait for backend to finish ----------
Write-Host "`nPress Ctrl+C to stop." -ForegroundColor Cyan
try {
    $backendProcess.WaitForExit()
} finally {
    # Cleanup: stop all child processes
    Write-Host "`nStopping processes..." -ForegroundColor Yellow
    if (-not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    Write-Host "All processes stopped." -ForegroundColor Green
}
