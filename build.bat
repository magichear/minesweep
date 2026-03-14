@echo off
setlocal
set ROOT=%~dp0

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%build.ps1" %*
set EXIT_CODE=%ERRORLEVEL%

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Build failed with exit code %EXIT_CODE%.
    exit /b %EXIT_CODE%
)

echo.
echo Build succeeded.
endlocal
