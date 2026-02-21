@echo off
REM ============================================================
REM  Build script for the Minesweeper project.
REM  Builds the React frontend, copies it into the Spring Boot
REM  static resources, then packages the Spring Boot backend as
REM  a fat JAR. Finally assembles everything into a "dist" folder
REM  ready for deployment.
REM ============================================================

setlocal enabledelayedexpansion

set ROOT=%~dp0
set SKIP_TESTS=0

REM Parse arguments
for %%a in (%*) do (
    if /I "%%a"=="-SkipTests" set SKIP_TESTS=1
    if /I "%%a"=="/SkipTests" set SKIP_TESTS=1
)

echo ========== Minesweeper Build ==========

REM ---------- 1. Build frontend ----------
echo.
echo [1/4] Building frontend...
pushd "%ROOT%minesweepFrontend"
call npm install --no-audit --no-fund
if %ERRORLEVEL% NEQ 0 (
    popd
    echo ERROR: npm install failed with exit code %ERRORLEVEL%
    exit /b 1
)
call npm run build
if %ERRORLEVEL% NEQ 0 (
    popd
    echo ERROR: npm run build failed with exit code %ERRORLEVEL%
    exit /b 1
)
popd

REM ---------- 2. Copy frontend dist into backend static resources ----------
echo.
echo [2/4] Copying frontend build to backend static resources...
set STATIC_DIR=%ROOT%minesweepBackend\src\main\resources\static
if exist "%STATIC_DIR%" (
    rmdir /s /q "%STATIC_DIR%"
)
xcopy /E /I /Q "%ROOT%minesweepFrontend\dist" "%STATIC_DIR%"

REM ---------- 3. Build backend ----------
echo.
echo [3/4] Building backend JAR...
pushd "%ROOT%minesweepBackend"
if %SKIP_TESTS%==1 (
    call .\mvnw.cmd package -DskipTests 2>&1
) else (
    call .\mvnw.cmd package 2>&1
)
if %ERRORLEVEL% NEQ 0 (
    popd
    echo ERROR: Maven build failed with exit code %ERRORLEVEL%
    exit /b 1
)
popd

REM ---------- 4. Assemble distribution ----------
echo.
echo [4/4] Assembling distribution...
set DIST=%ROOT%dist
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%"

REM Backend JAR — find the first JAR that is not the *-plain* variant
set JAR_FOUND=
for %%f in ("%ROOT%minesweepBackend\target\*.jar") do (
    echo %%~nf | findstr /I "plain original" >nul
    if errorlevel 1 (
        if not defined JAR_FOUND (
            set JAR_FOUND=%%f
        )
    )
)
if not defined JAR_FOUND (
    echo ERROR: No backend JAR found in target folder.
    exit /b 1
)
copy "%JAR_FOUND%" "%DIST%\minesweepBackend.jar" >nul

REM Run scripts
copy "%ROOT%run.ps1" "%DIST%\run.ps1" >nul
copy "%ROOT%run.bat" "%DIST%\run.bat" >nul

echo.
echo ========== Build complete! ==========
echo Distribution created at: %DIST%
echo To run: cd dist ^& run.bat  (or run.ps1)

endlocal
