# NeoMod: generate the Eclipse .classpath (+ UTF-8 encoding setting) from your .minecraft\libraries.
#   setup-eclipse.bat                          (set MC_DIR=C:\path\to\.minecraft  if not in %APPDATA%)
# Nothing is copied into src\. Library jars are referenced in place. The only extracted files are the
# jars nested inside Fabric Loader (MixinExtras), which go to .nested\ .
# NOTE: this file is ASCII-only on purpose (cmd/PowerShell 5.1 code page safe).
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$Utf8 = New-Object System.Text.UTF8Encoding($false)
function Die($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# --- project name (Eclipse "mod"): read from .project, fall back to folder name ---
$proj = Split-Path -Leaf $PSScriptRoot
if (Test-Path '.project') {
    $m = [regex]::Match([IO.File]::ReadAllText((Resolve-Path '.project').Path), '<name>([^<]+)</name>')
    if ($m.Success) { $proj = $m.Groups[1].Value.Trim() }
}

# --- locate .minecraft ---
$mc = $env:MC_DIR
if (-not $mc -and $env:APPDATA -and (Test-Path "$env:APPDATA\.minecraft\libraries")) { $mc = "$env:APPDATA\.minecraft" }
if (-not $mc -or -not (Test-Path "$mc\libraries")) { Die ".minecraft\libraries not found. Set MC_DIR=C:\path\to\.minecraft" }
Write-Host "using libraries: $mc\libraries"

function VerKey($dirName) {
    if ($dirName -match '^(\d+(\.\d+){0,3})') {
        $v = $Matches[1]; if ($v -notmatch '\.') { $v += '.0' }
        return [version]$v
    }
    return [version]'0.0'
}
# Newest version of each library only; jars inside extracted META-INF folders are ignored.
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
$jars = @(Newest-Jars "$mc\libraries")
if ($jars.Count -eq 0) { Die "no jars found under $mc\libraries" }
$paths = New-Object System.Collections.Generic.List[string]
foreach ($j in $jars) { $paths.Add(($j.FullName -replace '\\', '/')) }

# --- jars nested inside Fabric Loader (MixinExtras 0.5.x) -> .nested\ ---
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force '.nested' | Out-Null
$nestedDir = (Resolve-Path '.nested').Path
foreach ($j in ($jars | Where-Object { $_.Name -like 'fabric-loader*' })) {
    $zip = [IO.Compression.ZipFile]::OpenRead($j.FullName)
    try {
        foreach ($e in $zip.Entries) {
            if ($e.FullName -like '*.jar') {
                $dest = Join-Path $nestedDir $e.Name
                [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
                $paths.Add(($dest -replace '\\', '/'))
            }
        }
    } finally { $zip.Dispose() }
}

# --- Minecraft jar bundled in libs\ (project-relative) ---
$rel = New-Object System.Collections.Generic.List[string]
if (Test-Path 'libs') {
    foreach ($f in (Get-ChildItem -LiteralPath 'libs' -Recurse -Filter '*.jar' -File)) {
        $rel.Add(($f.FullName.Substring($PSScriptRoot.Length + 1) -replace '\\', '/'))
    }
}

# Old MixinExtras (0.0-0.4) has no @Local(type=...): never put it on the classpath.
$paths = @($paths | Where-Object { $_ -notmatch 'mixinextras-[a-z]+-0\.[0-4]\.' } | Sort-Object -Unique)

# --- keep the existing JRE container / output folder if there is an old .classpath ---
$con = '<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>'
$out = '<classpathentry kind="output" path="bin"/>'
if (Test-Path '.classpath') {
    $old = [IO.File]::ReadAllText((Resolve-Path '.classpath').Path)
    foreach ($c in [regex]::Matches($old, '(?s)<classpathentry\s[^>]*kind="con"[^>]*?(/>|>.*?</classpathentry>)')) {
        if ($c.Value -match 'JRE_CONTAINER') { $con = $c.Value; break }
    }
    $mc2 = [regex]::Match($old, '<classpathentry\s[^>]*kind="output"[^>]*/>')
    if ($mc2.Success) { $out = $mc2.Value }
}
function Esc($s) { $s.Replace('&', '&amp;').Replace('"', '&quot;').Replace('<', '&lt;') }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$sb.AppendLine('<classpath>')
[void]$sb.AppendLine('	<classpathentry kind="src" path="src/neo/java"/>')
[void]$sb.AppendLine('	<classpathentry kind="src" path="src/main/java"/>')
# neo.compat (entry gates) + the compile-only Forge @Mod stub. The real build (build.ps1) compiles them with --release 8;
# Eclipse compiles the whole project at one level, which is fine for checking. The stub is never packaged.
[void]$sb.AppendLine('	<classpathentry kind="src" path="src/compat/java"/>')
[void]$sb.AppendLine('	<classpathentry kind="src" path="src/compat/stubs"/>')
[void]$sb.AppendLine('	<classpathentry kind="src" path="src/main/resources"/>')
[void]$sb.AppendLine("	$con")
foreach ($p in $rel)   { [void]$sb.AppendLine('	<classpathentry kind="lib" path="' + (Esc $p) + '"/>') }
foreach ($p in $paths) { [void]$sb.AppendLine('	<classpathentry kind="lib" path="' + (Esc $p) + '"/>') }
[void]$sb.AppendLine("	$out")
[void]$sb.AppendLine('</classpath>')
[IO.File]::WriteAllText((Join-Path $PSScriptRoot '.classpath'), $sb.ToString(), $Utf8)

# --- UTF-8 as the project text encoding (removes "No explicit project encoding") ---
New-Item -ItemType Directory -Force '.settings' | Out-Null
$prefs = Join-Path (Resolve-Path '.settings').Path 'org.eclipse.core.resources.prefs'
$lines = @()
if (Test-Path $prefs) {
    $lines = @([IO.File]::ReadAllLines($prefs) | Where-Object { $_ -notmatch '^encoding/' -and $_ -notmatch '^eclipse\.preferences\.version=' })
}
$lines = @('eclipse.preferences.version=1', "encoding/$proj=UTF-8") + $lines
[IO.File]::WriteAllText($prefs, (($lines -join "`n") + "`n"), $Utf8)

# --- Eclipse compiler: silence false positives (Closeable screens / deprecated Fabric Loader API) ---
$jdt = Join-Path (Resolve-Path '.settings').Path 'org.eclipse.jdt.core.prefs'
$set = [ordered]@{
    'org.eclipse.jdt.core.compiler.problem.unclosedCloseable'            = 'ignore'
    'org.eclipse.jdt.core.compiler.problem.potentiallyUnclosedCloseable' = 'ignore'
    'org.eclipse.jdt.core.compiler.problem.deprecation'                  = 'ignore'
}
$jl = @()
if (Test-Path $jdt) {
    $jl = @([IO.File]::ReadAllLines($jdt) | Where-Object {
        $line = $_
        -not ($line -match '^eclipse\.preferences\.version=') -and -not ($set.Keys | Where-Object { $line.StartsWith($_ + '=') })
    })
}
$jl = @('eclipse.preferences.version=1') + @($set.Keys | ForEach-Object { "$_=$($set[$_])" }) + $jl
[IO.File]::WriteAllText($jdt, (($jl -join "`n") + "`n"), $Utf8)

Write-Host ""
Write-Host "DONE: .classpath ($($rel.Count + $paths.Count) jars), project '$proj' encoding = UTF-8" -ForegroundColor Green
Write-Host "Eclipse: right-click the project -> Refresh (F5), then Project -> Clean."
