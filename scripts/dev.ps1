$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Write-Host 'Building RIMeet backend...' -ForegroundColor Cyan
Push-Location "$Root\backend"
try {
  mvn -q -DskipTests package
  if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
  $BackendJar = Get-Item -Path target\rimeet-backend.jar -ErrorAction SilentlyContinue
  if (-not $BackendJar) { throw 'Backend JAR was not created.' }
  Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$Root\backend'; java -jar '$($BackendJar.FullName)'"
} finally {
  Pop-Location
}
Set-Location "$Root\desktop"
npm run dev
