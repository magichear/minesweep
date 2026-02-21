@echo off
REM ============================================================
REM  One-click run script for the Minesweeper application (CMD)
REM ============================================================

setlocal
set ROOT=%~dp0

echo ========== Minesweeper Launcher ==========

REM ---------- Start Spring Boot ----------
echo Starting Spring Boot backend...
start "Minesweep-Backend" /MIN java -jar "%ROOT%minesweepBackend.jar" --spring.datasource.url=jdbc:h2:file:%ROOT%data/minesweep

REM ---------- Wait for backend & open browser ----------
set PORT=8080
echo Waiting for backend on port %PORT%...
set /a ATTEMPTS=0
:waitloop
if %ATTEMPTS% GEQ 30 goto timeout
timeout /t 1 /nobreak >nul
set /a ATTEMPTS+=1
powershell -Command "try { $c = New-Object Net.Sockets.TcpClient('localhost', %PORT%); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 goto waitloop
echo Backend is ready!
start http://localhost:%PORT%
echo Browser opened at http://localhost:%PORT%
goto waitdone
:timeout
echo WARNING: Backend did not start within 30 seconds.
:waitdone

echo Press any key to stop all processes...
pause >nul

REM ---------- Cleanup ----------
echo Stopping processes...
taskkill /FI "WINDOWTITLE eq Minesweep-Backend" /F >nul 2>&1
echo Done.
endlocal
