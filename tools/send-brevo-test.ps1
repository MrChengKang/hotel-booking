param([Parameter(Mandatory=$true)][string]$To)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path '.tools' -Force | Out-Null
    $wrapper=if($env:OS -eq 'Windows_NT'){'.\mvnw.cmd'}else{'./mvnw'}
    & $wrapper -B -ntp -DskipTests package dependency:build-classpath '-Dmdep.outputFile=.tools/brevo-classpath.txt'
    if($LASTEXITCODE -ne 0){throw 'Backend build failed; no test email sent.'}
    $dependencies=(Get-Content '.tools/brevo-classpath.txt' -Raw).Trim()
    $classpath=(Join-Path $projectRoot 'target/classes')+[IO.Path]::PathSeparator+$dependencies
    & java --class-path $classpath (Join-Path $PSScriptRoot 'SendBrevoTestEmail.java') $To
    if($LASTEXITCODE -ne 0){throw 'Brevo test was not confirmed. Check mail configuration and provider logs before retrying.'}
} finally {Pop-Location}
