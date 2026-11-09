$ErrorActionPreference = 'Stop'
Write-Host 'HCIM Atlas - RuneLite live sync installeren' -ForegroundColor Cyan
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dst = Join-Path $env:USERPROFILE 'Documents\HCIMAtlas-GuideOverlay'
if (!(Test-Path $dst)) {
  $zip = Join-Path $env:TEMP 'hcim-guide-overlay-source.zip'
  $tmp = Join-Path $env:TEMP 'hcim-guide-overlay-unzip'
  Write-Host 'Downloaden van de openbare Guide Overlay GitHub-repository...'
  Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/RunelitePlugin/guide-overlay/archive/1e7b4f4503d99bc440b6fa29707b0523aad3aab1.zip' -OutFile $zip
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
  Expand-Archive -Path $zip -DestinationPath $tmp -Force
  $top = Get-ChildItem $tmp -Directory | Select-Object -First 1
  if (!$top -or !(Test-Path (Join-Path $top.FullName 'build.gradle'))) { throw 'Geen geldig Guide Overlay-bronpakket.' }
  Move-Item $top.FullName $dst
  Remove-Item $tmp -Recurse -Force
} else {
  Write-Host 'Bestaande checkout gevonden. Bestanden worden niet opnieuw gedownload.'
}
$source = Join-Path $dst 'src\main\java\com\hcimguide\HcimGuidePlugin.java'
$extra  = Join-Path $dst 'src\main\java\com\hcimguide\StreamSyncExport.java'
if (!(Test-Path $source)) { throw "Pluginbron ontbreekt: $source" }
$java = [System.IO.File]::ReadAllText($source)
if ($java.Contains('StreamSyncExport.write(')) {
  Write-Host 'Stream-export reeds aanwezig, niets gewijzigd.' -ForegroundColor Green
} else {
  if (!$java.Contains('private volatile Guide currentGuide') -or !$java.Contains('profileProgressReady') -or !$java.Contains('void onGameTick(GameTick tick)')) {
    throw 'De upstream Guide Overlay-code is gewijzigd, patch gestopt (geen aanpassingen).'
  }
  $needle = "`t`t++tickCounter;"
  if ([regex]::Matches($java,[regex]::Escape($needle)).Count -ne 1) { throw 'Tick-insertiepunt niet uniek gevonden.' }
  $insert = @'
        // HCIM Atlas local streaming state. No external communication.
        if (tickCounter % 5 == 0 && active && currentGuide != null
            && client.getGameState() == GameState.LOGGED_IN
            && (!config.perCharacterProgress() || profileProgressReady))
        {
            final Guide streamGuide = currentGuide;
            final String streamGuideId = currentGuideId;
            runAsync(() ->
            {
                if (active && currentGuide == streamGuide && streamGuideId.equals(currentGuideId))
                {
                    StreamSyncExport.write(streamGuide, streamGuideId, completedSteps, skippedSteps);
                }
            });
        }
'@
  Copy-Item $source ($source + '.hcim-stream-backup') -ErrorAction Stop
  $updated = $java.Replace($needle, $needle + "`n" + $insert)
  [System.IO.File]::WriteAllText($source,$updated,(New-Object System.Text.UTF8Encoding($false)))
  Copy-Item (Join-Path $scriptDir 'StreamSyncExport.java') $extra -Force
  Write-Host 'Guide Overlay geconfigureerd met lokale live-export.' -ForegroundColor Green
}
$gradle = Join-Path $dst 'build.gradle'
$g = [System.IO.File]::ReadAllText($gradle)
if (!$g.Contains('runAtlas')) {
  $g += @'

// Launch a RuneLite dev client with HCIM Atlas and the existing Guide Overlay.
tasks.register('runAtlas', JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.hcimguide.HcimGuidePluginTest'
    jvmArgs '-ea'
}
'@
  [System.IO.File]::WriteAllText($gradle,$g,(New-Object System.Text.UTF8Encoding($false)))
}
$launcher = Join-Path $dst 'START_HCIM_ATLAS_RUNE_LITE.bat'
if (!(Test-Path $launcher)) {
  [System.IO.File]::WriteAllText($launcher,"@echo off`r`ncd /d `"%~dp0`"`r`ncall gradlew.bat runAtlas`r`npause`r`n")
}
Write-Host "Installatiemap: $dst" -ForegroundColor Cyan
Write-Host '1. Controleer Java JDK (11 of nieuwer) en internet.'
Write-Host '2. Dubbelklik op START_HCIM_ATLAS_RUNE_LITE.bat in die map.'
Write-Host '3. Start daarnaast bridge\start-overlay.bat uit dit pakket.'
Write-Host '4. Gebruik http://127.0.0.1:38877/overlay in OBS of vMix.'
