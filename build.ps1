<#
.SYNOPSIS
    Build script for the Minesweeper project.
    Builds the React frontend, copies it into the Spring Boot static resources,
    then packages the Spring Boot backend as a fat JAR.
    Finally assembles everything into a "dist" folder ready for deployment.
#>

param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========== Minesweeper Build ==========" -ForegroundColor Cyan

# ---------- 1. Build frontend ----------
Write-Host "`n[1/4] Building frontend..." -ForegroundColor Yellow
Push-Location "$Root\minesweepFrontend"
npm install --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "npm install failed with exit code $LASTEXITCODE" }
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "npm run build failed with exit code $LASTEXITCODE" }
Pop-Location

# ---------- 2. Copy frontend dist into backend static resources ----------
Write-Host "`n[2/4] Copying frontend build to backend static resources..." -ForegroundColor Yellow
$StaticDir = "$Root\minesweepBackend\src\main\resources\static"
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force $StaticDir
}
Copy-Item -Recurse "$Root\minesweepFrontend\dist" $StaticDir

# ---------- 3. Build backend ----------
Write-Host "`n[3/4] Building backend JAR..." -ForegroundColor Yellow
Push-Location "$Root\minesweepBackend"
# Use cmd /c to prevent PowerShell from treating JVM stderr warnings as errors
if ($SkipTests) {
    cmd /c ".\mvnw.cmd package -DskipTests 2>&1"
} else {
    cmd /c ".\mvnw.cmd package 2>&1"
}
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Maven build failed with exit code $LASTEXITCODE" }
Pop-Location

# ---------- 4. Assemble distribution ----------
Write-Host "`n[4/4] Assembling distribution..." -ForegroundColor Yellow
$Dist = "$Root\dist"
if (Test-Path $Dist) { Remove-Item -Recurse -Force $Dist }
New-Item -ItemType Directory -Path $Dist | Out-Null

# Backend JAR
$Jar = Get-ChildItem "$Root\minesweepBackend\target\*.jar" -Exclude "*-plain*" | Select-Object -First 1
Copy-Item $Jar.FullName "$Dist\minesweepBackend.jar"

# Run script
Copy-Item "$Root\run.ps1" "$Dist\run.ps1"
Copy-Item "$Root\run.bat" "$Dist\run.bat"

Write-Host "`n========== Build complete! ==========" -ForegroundColor Green
Write-Host "Distribution created at: $Dist"
Write-Host "To run: cd dist; .\run.ps1  (or run.bat)"
