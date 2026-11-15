param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path '.tools' -Force | Out-Null
    $wrapper = if ($env:OS -eq 'Windows_NT') { '.\mvnw.cmd' } else { './mvnw' }
    & $wrapper -B -ntp -DskipTests package dependency:build-classpath '-Dmdep.outputFile=.tools/cloudinary-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; no migration was started.' }
    $dependencies = (Get-Content '.tools/cloudinary-classpath.txt' -Raw).Trim()
    $runtimeClasspath = (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + $dependencies
    $mode = if ($Apply) { '--apply' } else { '--dry-run' }
    & java --class-path $runtimeClasspath (Join-Path $PSScriptRoot 'MigrateCloudinaryMedia.java') $mode
    if ($LASTEXITCODE -ne 0) { throw 'Migration did not finish completely. Original local files are retained; check configuration and retry.' }
} finally {
    Pop-Location
}
