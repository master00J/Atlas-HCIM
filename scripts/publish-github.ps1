# HCIM Atlas Live: safely push local changes to the existing Atlas-HCIM repository.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $project
$repo = 'https://github.com/master00J/Atlas-HCIM.git'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Installeer Git for Windows.' }
if (-not (Test-Path '.git')) {
  throw 'Clone eerst https://github.com/master00J/Atlas-HCIM (git clone) en werk in die map. Dit script maakt geen nieuwe repo en force-pusht niets.'
}
$remote = (& git remote get-url origin 2>$null)
if ($LASTEXITCODE -ne 0 -or $remote -notmatch '(^https://github\.com/master00J/Atlas-HCIM(\.git)?$|^git@github\.com:master00J/Atlas-HCIM(\.git)?$)') {
  throw "Remote origin is niet exact $repo. Dit script weigert om naar een andere repository te pushen."
}
& git fetch origin main
if ($LASTEXITCODE -ne 0) { throw 'git fetch mislukt.' }
& git merge --ff-only origin/main
if ($LASTEXITCODE -ne 0) { throw 'Je lokale branch verschilt van main. Los dit handmatig op.' }
& git add -A
if ($LASTEXITCODE -ne 0) { throw 'git add mislukt.' }
$staged = (& git diff --cached --name-only)
if (-not $staged) { Write-Host 'Geen wijzigingen om te uploaden.'; exit 0 }
& git commit -m 'Update HCIM Atlas Live'
if ($LASTEXITCODE -ne 0) { throw 'git commit mislukt.' }
& git push origin main
if ($LASTEXITCODE -ne 0) { throw 'git push mislukt. Geen force-push uitgevoerd.' }
Write-Host 'Update geupload naar https://github.com/master00J/Atlas-HCIM' -ForegroundColor Green
