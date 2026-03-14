@echo off
setlocal
set ROOT=%~dp0

set EXE1=%ROOT%dist\MinesweepAssist.exe
set EXE2=%ROOT%MinesweepAssist.exe

if exist "%EXE1%" (
    start "" "%EXE1%"
    exit /b 0
)

if exist "%EXE2%" (
    start "" "%EXE2%"
    exit /b 0
)

echo MinesweepAssist.exe not found.
echo Please run build.ps1 or build.bat first.
exit /b 1
