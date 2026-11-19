$ErrorActionPreference = 'Stop'
Write-Host 'Checking MeetMind prerequisites...'
node --version
java --version
mvn --version
Write-Host 'Prerequisites look available.' -ForegroundColor Green
