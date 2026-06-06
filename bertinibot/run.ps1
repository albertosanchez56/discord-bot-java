# Stops any previous BertiniBot V2 instance (to avoid two bots responding with
# the same token after Cursor / the launching shell was closed) and then
# launches the freshly built JAR in the foreground. Press Ctrl+C to stop.

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

& (Join-Path $PSScriptRoot 'stop.ps1')

$jar = Join-Path $PSScriptRoot 'target\bertinibot-2.0.0.jar'
if (-not (Test-Path $jar)) {
    Write-Host "Jar not found: $jar" -ForegroundColor Red
    Write-Host "Build it first with: .\mvnw.cmd -B -ntp -DskipTests package" -ForegroundColor Yellow
    exit 1
}

Write-Host "Starting BertiniBot V2 ..." -ForegroundColor Cyan
& java -jar $jar
