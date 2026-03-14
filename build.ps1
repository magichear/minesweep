<#
.SYNOPSIS
    Build script for the Minesweeper desktop package.
    Produces a single portable EXE that bundles frontend, backend JAR and a trimmed Java runtime.
#>

param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $Root "minesweepFrontend"
$BackendDir = Join-Path $Root "minesweepBackend"
$DistDir = Join-Path $Root "dist"
$BundleDir = Join-Path $DistDir "_bundle"
$BundleBackendDir = Join-Path $BundleDir "backend"
$BundleRuntimeDir = Join-Path $BundleDir "runtime"

function Set-DefaultMirrorEnv([string]$name, [string]$value) {
    $current = [Environment]::GetEnvironmentVariable($name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($current)) {
        return
    }
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

function Initialize-DownloadMirrors() {
    # Prefer China mirrors by default, but keep user-provided values untouched.
    Set-DefaultMirrorEnv "ELECTRON_MIRROR" "https://npmmirror.com/mirrors/electron/"
    Set-DefaultMirrorEnv "ELECTRON_BUILDER_BINARIES_MIRROR" "https://npmmirror.com/mirrors/electron-builder-binaries/"
    # Avoid npm treating this as unknown config key and printing warnings.
    [Environment]::SetEnvironmentVariable("NPM_CONFIG_ELECTRON_MIRROR", $null, "Process")
}

function Initialize-PackagingEnv() {
    # Disable auto code-signing for local unsigned builds to avoid winCodeSign symlink extraction issues.
    Set-DefaultMirrorEnv "CSC_IDENTITY_AUTO_DISCOVERY" "false"
}

function Remove-PathIfExists([string]$path) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
    }
}

function Clear-ElectronCaches() {
    $cacheDirs = @(
        (Join-Path $env:LOCALAPPDATA "electron\Cache"),
        (Join-Path $env:LOCALAPPDATA "electron-builder\Cache")
    )
    foreach ($cache in $cacheDirs) {
        Remove-PathIfExists $cache
    }
}

function Ensure-ElectronRuntimePresent([string]$frontendDir) {
    $electronExe = Join-Path $frontendDir "node_modules\electron\dist\electron.exe"
    if (Test-Path $electronExe) {
        return
    }

    Write-Warning "electron runtime is missing in node_modules. Reinstalling frontend dependencies..."
    Push-Location $frontendDir
    npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        throw "Failed to reinstall frontend dependencies (exit code $LASTEXITCODE)."
    }
    Pop-Location

    if (-not (Test-Path $electronExe)) {
        throw "Electron runtime is still missing after npm install: $electronExe"
    }
}

function Invoke-ElectronPackWithRetry([string]$frontendDir, [string]$distDir) {
    $maxAttempts = 3
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        if ($attempt -gt 1) {
            Write-Warning "electron-builder retry $attempt/$maxAttempts (cleaning cache and output first)..."
            Clear-ElectronCaches
        }

        Remove-PathIfExists (Join-Path $distDir "win-unpacked")

        Push-Location $frontendDir
        npm run pack:win
        $exitCode = $LASTEXITCODE
        Pop-Location

        if ($exitCode -eq 0) {
            return
        }

        $electronExe = Join-Path $distDir "win-unpacked\electron.exe"
        $productExe = Join-Path $distDir "win-unpacked\MinesweepAssist.exe"
        if ((Test-Path $productExe) -or (Test-Path $electronExe)) {
            throw "electron-builder failed with exit code $exitCode, but executable files exist. Please inspect logs and output manually."
        }

        if ($attempt -eq $maxAttempts) {
            throw "electron-builder packaging failed with exit code $exitCode after $maxAttempts attempts."
        }
    }
}

function Require-CommandPath([string]$toolName, [string]$preferredPath) {
    if ($preferredPath -and (Test-Path $preferredPath)) {
        return $preferredPath
    }
    $cmd = Get-Command $toolName -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    throw "Required tool '$toolName' not found. Please install JDK 25+ and set JAVA_HOME."
}

function Find-LocalJdkHome() {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\jdeps.exe"))) {
        return $env:JAVA_HOME
    }

    $jdepsCmd = Get-Command jdeps -ErrorAction SilentlyContinue
    if ($jdepsCmd) {
        $binDir = Split-Path -Parent $jdepsCmd.Source
        $jdkHome = Split-Path -Parent $binDir
        if (Test-Path (Join-Path $jdkHome "bin\jlink.exe")) {
            return $jdkHome
        }
    }

    $searchRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Amazon Corretto"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -match '^jdk' -or $_.Name -match '^temurin' -or $_.Name -match '^corretto'
            } |
            Sort-Object Name -Descending |
            Where-Object { (Test-Path (Join-Path $_.FullName "bin\jdeps.exe")) -and (Test-Path (Join-Path $_.FullName "bin\jlink.exe")) } |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    return $null
}

Write-Host "========== Minesweeper Single-EXE Build ==========" -ForegroundColor Cyan

Initialize-DownloadMirrors
Initialize-PackagingEnv
Write-Host "Using ELECTRON_MIRROR=$($env:ELECTRON_MIRROR)" -ForegroundColor DarkCyan
Write-Host "Using ELECTRON_BUILDER_BINARIES_MIRROR=$($env:ELECTRON_BUILDER_BINARIES_MIRROR)" -ForegroundColor DarkCyan
Write-Host "Using CSC_IDENTITY_AUTO_DISCOVERY=$($env:CSC_IDENTITY_AUTO_DISCOVERY)" -ForegroundColor DarkCyan

if (Test-Path $DistDir) {
    Remove-Item -Recurse -Force $DistDir
}

# ---------- 1. Build frontend ----------
Write-Host "`n[1/6] Building frontend..." -ForegroundColor Yellow
Push-Location $FrontendDir
npm install --no-audit --no-fund
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "npm install failed with exit code $LASTEXITCODE" }
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "npm run build failed with exit code $LASTEXITCODE" }
Pop-Location

# ---------- 2. Copy frontend dist into backend static resources ----------
Write-Host "`n[2/6] Syncing frontend build to backend static resources..." -ForegroundColor Yellow
$StaticDir = Join-Path $BackendDir "src\main\resources\static"
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force $StaticDir
}
Copy-Item -Recurse (Join-Path $FrontendDir "dist") $StaticDir

# ---------- 3. Build backend ----------
Write-Host "`n[3/6] Building backend JAR..." -ForegroundColor Yellow
Push-Location $BackendDir
if ($SkipTests) {
    cmd /c ".\mvnw.cmd package -DskipTests 2>&1"
} else {
    cmd /c ".\mvnw.cmd package 2>&1"
}
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Maven build failed with exit code $LASTEXITCODE" }
Pop-Location

$Jar = Get-ChildItem (Join-Path $BackendDir "target\*.jar") -Exclude "*-plain*", "*original*" | Select-Object -First 1
if (-not $Jar) {
    throw "No backend fat JAR found under $BackendDir\target"
}

# ---------- 4. Build trimmed Java runtime (jdeps + jlink) ----------
Write-Host "`n[4/6] Creating trimmed Java runtime with jdeps + jlink..." -ForegroundColor Yellow

$JavaHome = Find-LocalJdkHome

$JdepsPath = if ($JavaHome) { Join-Path $JavaHome "bin\jdeps.exe" } else { "" }
$JlinkPath = if ($JavaHome) { Join-Path $JavaHome "bin\jlink.exe" } else { "" }
$JarToolPath = if ($JavaHome) { Join-Path $JavaHome "bin\jar.exe" } else { "" }

$JdepsTool = Require-CommandPath "jdeps" $JdepsPath
$JlinkTool = Require-CommandPath "jlink" $JlinkPath
$JarTool = Require-CommandPath "jar" $JarToolPath

if (Test-Path $BundleDir) {
    Remove-Item -Recurse -Force $BundleDir
}
New-Item -ItemType Directory -Path $BundleBackendDir -Force | Out-Null

$ExtractDir = Join-Path $BundleDir "_jar_extract"
New-Item -ItemType Directory -Path $ExtractDir -Force | Out-Null
Push-Location $ExtractDir
& $JarTool xf $Jar.FullName
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Failed to extract backend jar via jar tool." }
Pop-Location

$libDir = Join-Path $ExtractDir "BOOT-INF\lib"
$classPath = Join-Path $libDir "*"

$jdepsArgs = @(
    '--ignore-missing-deps',
    '--multi-release', '25',
    '--recursive',
    '--print-module-deps'
)
if (Test-Path $libDir) {
    $jdepsArgs += @('--class-path', $classPath)
}
$jdepsArgs += (Join-Path $ExtractDir "BOOT-INF\classes")

$detectedModules = ''
$jdepsOutput = & $JdepsTool @jdepsArgs 2>&1
if ($LASTEXITCODE -eq 0) {
    $detectedModules = (($jdepsOutput | Out-String).Trim() -split "`r?`n" | Select-Object -Last 1).Trim()
} else {
    Write-Warning "jdeps auto-detection failed, fallback to conservative module set."
}
$baseModules = @(
    'java.base',
    'java.desktop',
    'java.logging',
    'java.management',
    'java.naming',
    'java.security.jgss',
    'java.sql',
    'jdk.crypto.ec',
    'jdk.unsupported'
)

$moduleSet = New-Object 'System.Collections.Generic.HashSet[string]'
if ($detectedModules) {
    foreach ($m in ($detectedModules -split ',')) {
        $mod = $m.Trim()
        if ($mod) { [void]$moduleSet.Add($mod) }
    }
}
foreach ($m in $baseModules) { [void]$moduleSet.Add($m) }
$allModules = ($moduleSet | Sort-Object) -join ','

if (Test-Path $BundleRuntimeDir) {
    Remove-Item -Recurse -Force $BundleRuntimeDir
}

$jlinkArgs = @(
    '--add-modules', $allModules,
    '--strip-debug',
    '--no-header-files',
    '--no-man-pages',
    '--compress=2',
    '--output', $BundleRuntimeDir
)
& $JlinkTool @jlinkArgs
if ($LASTEXITCODE -ne 0) {
    throw "jlink failed while creating trimmed runtime image."
}

Copy-Item $Jar.FullName (Join-Path $BundleBackendDir "minesweepBackend.jar")
Remove-Item -Recurse -Force $ExtractDir

# ---------- 5. Package single portable EXE ----------
Write-Host "`n[5/6] Packaging single portable EXE..." -ForegroundColor Yellow
Ensure-ElectronRuntimePresent $FrontendDir
Invoke-ElectronPackWithRetry $FrontendDir $DistDir

# ---------- 6. Copy launch scripts ----------
Write-Host "`n[6/6] Preparing launcher scripts..." -ForegroundColor Yellow
Copy-Item (Join-Path $Root "run.ps1") (Join-Path $DistDir "run.ps1") -Force
Copy-Item (Join-Path $Root "run.bat") (Join-Path $DistDir "run.bat") -Force

Write-Host "`n========== Build complete! ==========" -ForegroundColor Green
Write-Host "Single EXE: $(Join-Path $DistDir 'MinesweepAssist.exe')"
Write-Host "Run directly by double-clicking the EXE, or use dist\\run.bat / dist\\run.ps1"
