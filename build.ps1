# NeoMod build script (JDK only: javac + jar). No Gradle / Loom needed.
#   build.bat                       -> neomod.jar (next to build.bat; intermediate files go to bin\)
#   set JAVA_RELEASE=25 / set MC_DIR=C:\path\to\.minecraft  (optional)
# NOTE: this file is ASCII-only on purpose (cmd/PowerShell 5.1 code page safe).
$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot

$Name    = 'neomod'
$Version = '26.3'
$Release = if ($env:JAVA_RELEASE) { [int]$env:JAVA_RELEASE } else { 25 }
$Out     = 'bin'
$Jar     = "$Name.jar"   # next to build.bat (not inside $Out); no version in the file name (the version still goes into fabric.mod.json / mods.toml)
$Utf8    = New-Object System.Text.UTF8Encoding($false)

function Die($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }
function Run($exe, [string[]]$a) {
    & $exe @a
    if ($LASTEXITCODE -ne 0) { Die "$exe failed (exit code $LASTEXITCODE)" }
}
function Q($p) { '"' + ($p -replace '\\', '/') + '"' }   # javac @argfile: quote + forward slashes

# --- locate JDK ---
$javac = Get-Command javac -ErrorAction SilentlyContinue
$jarc  = Get-Command jar   -ErrorAction SilentlyContinue
if ((-not $javac -or -not $jarc) -and $env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    $jarc  = Get-Command jar   -ErrorAction SilentlyContinue
}
if (-not $javac) { Die "javac not found. Install JDK $Release or newer (or set JAVA_HOME)." }
if (-not $jarc)  { Die "jar not found (a full JDK is required)." }
$vtxt = (& javac -version 2>&1 | Out-String)
if ($vtxt -match 'javac\s+(\d+)') {
    if ([int]$Matches[1] -lt $Release) { Die "JDK $Release or newer is required (found: $($vtxt.Trim()))." }
} else { Die "could not parse javac version: $vtxt" }

# --- classpath: libs\**\*.jar + Fabric Loader libraries of an installed .minecraft ---
$mc = $env:MC_DIR
if (-not $mc -and $env:APPDATA -and (Test-Path "$env:APPDATA\.minecraft\libraries")) { $mc = "$env:APPDATA\.minecraft" }
function VerKey($dirName) {
    if ($dirName -match '^(\d+(\.\d+){0,3})') {
        $v = $Matches[1]; if ($v -notmatch '\.') { $v += '.0' }
        return [version]$v
    }
    return [version]'0.0'
}
# Keep only the NEWEST version of each library (libraries\<group>\<artifact>\<version>\*.jar).
# Plain string sorting would put 2.8.9 before 2.11.0, so versions are compared numerically.
function Newest-Jars($root) {
    Get-ChildItem -LiteralPath $root -Recurse -Filter '*.jar' -File |
        Where-Object { $_.FullName -notmatch '[\\/]META-INF[\\/]' } |
        Group-Object { $_.Directory.Parent.FullName } |
        ForEach-Object {
            $best = $_.Group | Group-Object { $_.Directory.FullName } |
                Sort-Object { VerKey (Split-Path $_.Name -Leaf) } -Descending | Select-Object -First 1
            $best.Group
        }
}
$jars = @()
$jars += Get-ChildItem -LiteralPath 'libs' -Recurse -Filter '*.jar' -File
if ($mc -and (Test-Path "$mc\libraries")) {
    Write-Host "using libraries: $mc\libraries"
    $jars += Newest-Jars "$mc\libraries"
} else {
    Write-Host "WARNING: .minecraft\libraries not found. Fabric Loader / Mixin / MixinExtras / Gson / Guava jars are needed." -ForegroundColor Yellow
    Write-Host "         Run Minecraft 26.3 once with a Fabric Loader 0.19.5 profile, or set MC_DIR=C:\path\to\.minecraft" -ForegroundColor Yellow
}
if ($jars.Count -eq 0) { Die "no dependency jars found. See libs\README.txt." }
$cpList = @($jars | ForEach-Object { $_.FullName -replace '\\', '/' })

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force "$Out/classes", "$Out/res" | Out-Null

# Fabric Loader may carry MixinExtras as a nested jar. Extract nested jars with LOWEST priority (end of classpath).
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force "$Out/nested" | Out-Null
foreach ($j in ($jars | Where-Object { $_.Name -like 'fabric-loader*' })) {
    $zip = [IO.Compression.ZipFile]::OpenRead($j.FullName)
    try {
        foreach ($e in $zip.Entries) {
            if ($e.FullName -like '*.jar') {
                $dest = Join-Path (Resolve-Path "$Out/nested").Path $e.Name
                if (-not (Test-Path $dest)) { [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest) }
                $cpList += ($dest -replace '\\', '/')
            }
        }
    } finally { $zip.Dispose() }
}
# Old MixinExtras (0.3.x) lacks @Local(type=...). Drop it from the classpath; only 0.5.x is kept.
$cpList = @($cpList | Where-Object { $_ -notmatch 'mixinextras-[a-z]+-0\.[0-4]\.' })
$cp = $cpList -join ';'
Write-Host "selected jars (key libraries):"
$cpList | Where-Object { $_ -match 'gson|mixinextras|sponge-mixin|fabric-loader|guava' } | ForEach-Object { Write-Host "  $_" }

# Remove directories that contain no files (recursively, deepest first).
# Deleting the stub packages leaves empty parents such as "eu/" and "org/", and `jar` would
# otherwise pack them as empty folders.
function Remove-EmptyDirs($root) {
    Get-ChildItem -LiteralPath $root -Recurse -Directory |
        Sort-Object { $_.FullName.Length } -Descending |
        Where-Object { -not (Get-ChildItem -LiteralPath $_.FullName -Force) } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
}

function Compile($label, $srcRoots, $dest, $classpath, $rel = $Release) {
    $lines = @('-encoding UTF-8', "--release $rel", '-proc:none', '-Xlint:-options')
    if ($classpath) { $lines += ('-cp ' + (Q $classpath)) }
    $lines += ('-d ' + (Q $dest))
    $files = foreach ($root in $srcRoots) {
        Get-ChildItem -LiteralPath $root -Recurse -Filter '*.java' -File | ForEach-Object { Q $_.FullName }
    }
    $argfile = "$Out/$label.args"
    [IO.File]::WriteAllLines($argfile, [string[]]($lines + $files), $Utf8)
    Run 'javac' @("@$argfile")
}

Write-Host '[1/3] sources'
# src/neo/java = Fabric/Forge 非依存コード, src/main/java = Fabric 依存コード (Mixin / 入口 / Mod Menu 本体)。両方をまとめてコンパイルする。
# Compile-only stubs live inside src/neo/java but must NOT be packaged into the mod jar.
$SrcRoots = @('src/neo/java', 'src/main/java')
$StubDirs = @('org/jetbrains', 'org/jspecify', 'eu/pb4')
Compile 'sources' $SrcRoots "$Out/classes" $cp

# --- compat (src/compat, package neo.compat): the entry gates, compiled for Java 8 ---
# The mod itself is Java 25 bytecode and Minecraft 26.3 only. These few classes are what the loaders call first; on an old
# Java / Minecraft they write mods/NeoMods.log and do nothing (the game is not stopped). They must not import Minecraft / loader types.
# The Forge @Mod annotation is a compile-only stub (real Forge jars are newer than Java 8 can read) and is NOT packaged.
New-Item -ItemType Directory -Force "$Out/compat-stub" | Out-Null
Compile 'compat-stub' @('src/compat/stubs') "$Out/compat-stub" '' 8
Compile 'compat' @('src/compat/java') "$Out/classes" "$Out/compat-stub" 8

# --- Forge (optional): src/forge is compiled only when Forge jars are available ---
# Put Forge's jars in libs/forge/ (see libs/README.txt), or set BUILD_FORGE=1 when a Forge install's
# libraries are already picked up from .minecraft/libraries. Without Forge jars the result is Fabric-only.
$ForgeJars = @()
if (Test-Path 'libs/forge') { $ForgeJars = @(Get-ChildItem -LiteralPath 'libs/forge' -Recurse -Filter '*.jar' -File) }
$BuildForge = ($ForgeJars.Count -gt 0) -or ($env:BUILD_FORGE -eq '1')
if ($BuildForge) {
    Write-Host '      + Forge entry point (src/forge) is compiled too'
    $forgeCp = (@("$Out/classes") + $cpList + @($ForgeJars | ForEach-Object { $_.FullName -replace '\\', '/' })) -join ';'
    Compile 'forge' @('src/forge/java') "$Out/classes" $forgeCp
} else {
    Write-Host 'NOTE: no Forge jars (libs/forge is empty) -> Fabric-only jar. No mods.toml / Forge classes are included.' -ForegroundColor Yellow
}
foreach ($d in $StubDirs) { Remove-Item -Recurse -Force "$Out/classes/$d" -ErrorAction SilentlyContinue }
Remove-EmptyDirs "$Out/classes"

Write-Host '[2/3] resources'
Copy-Item -Path 'src/main/resources/*' -Destination "$Out/res" -Recurse -Force
Copy-Item -Path 'LICENSE_*' -Destination "$Out/res" -Force
$json = [IO.File]::ReadAllText((Resolve-Path 'src/main/resources/fabric.mod.json').Path, $Utf8)
$json = $json.Replace('$version', $Version)
[IO.File]::WriteAllText((Join-Path (Resolve-Path "$Out/res").Path 'fabric.mod.json'), $json, $Utf8)
if ($BuildForge) {
    Copy-Item -Path 'src/forge/resources/*' -Destination "$Out/res" -Recurse -Force
    $toml = [IO.File]::ReadAllText((Resolve-Path 'src/forge/resources/META-INF/mods.toml').Path, $Utf8)
    [IO.File]::WriteAllText((Join-Path (Resolve-Path "$Out/res").Path 'META-INF/mods.toml'), $toml.Replace('${version}', $Version), $Utf8)
}
Remove-EmptyDirs "$Out/res"

Write-Host '[3/3] jar'
Run 'jar' @('--create', '--file', $Jar, '-C', "$Out/classes", '.', '-C', "$Out/res", '.')

if ($BuildForge) { Write-Host "DONE: $Jar (Fabric + Forge)" } else { Write-Host "DONE: $Jar (Fabric only)" }
