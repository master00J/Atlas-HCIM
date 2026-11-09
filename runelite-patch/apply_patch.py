#!/usr/bin/env python3
"""Patch the matching Guide Overlay 2.0 source tree (no remote execution)."""
from pathlib import Path
import argparse
import shutil
import sys

p=argparse.ArgumentParser(description="Voeg HCIM Atlas stream-synchronisatie toe aan Guide Overlay")
p.add_argument("repository", help="Pad naar lokale guide-overlay GitHub checkout")
p.add_argument("--dry-run", action="store_true")
a=p.parse_args()
root=Path(a.repository).expanduser().resolve()
pkg=root/"src"/"main"/"java"/"com"/"hcimguide"
source=pkg/"HcimGuidePlugin.java"
extra=pkg/"StreamSyncExport.java"
if not source.is_file():
    sys.exit(f"Geen Guide Overlay checkout gevonden op: {source}")
text=source.read_text(encoding="utf-8")
if "StreamSyncExport.write(" in text:
    print("Plugin bevat de streamkoppeling al; geen wijzigingen nodig.")
    sys.exit(0)
if "private volatile Guide currentGuide" not in text or "profileProgressReady" not in text or "void onGameTick(GameTick tick)" not in text:
    sys.exit("De upstream-code is gewijzigd: geen automatische patch toegepast. Controleer de versie.")
needle="\t\t++tickCounter;"
if text.count(needle)!=1:
    sys.exit("Kon geen unieke GameTick-ingang vinden: patch gestopt.")
addition='''
        // HCIM Atlas: export only while logged in and after the correct character
        // profile was loaded. This writes a small local file, NOT a network call.
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
'''
updated=text.replace(needle,needle+addition,1)
if a.dry_run:
    print("Controle geslaagd: de patch past op deze codebase; niets gewijzigd.")
    sys.exit(0)
backup=pkg/"HcimGuidePlugin.java.hcim-stream-backup"
if backup.exists():
    sys.exit(f"Backup bestaat al, om overschrijven te voorkomen: {backup}")
shutil.copy2(source,backup)
source.write_text(updated,encoding="utf-8")
shutil.copy2(Path(__file__).parent/"StreamSyncExport.java",extra)
gradle = root/"build.gradle"
if gradle.is_file() and "runAtlas" not in gradle.read_text(encoding="utf-8"):
    gradle.write_text(gradle.read_text(encoding="utf-8") + """

// Local RuneLite development client with HCIM Atlas live stream sync.
tasks.register('runAtlas', JavaExec) {
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.hcimguide.HcimGuidePluginTest'
    jvmArgs '-ea'
}
""", encoding="utf-8")
launcher=root/"START_HCIM_ATLAS_RUNE_LITE.bat"
if not launcher.exists():
    launcher.write_text('@echo off\ncd /d "%~dp0"\ncall gradlew.bat runAtlas\npause\n',encoding='utf-8')
print("✓ Patch toegepast:")
print("  - src/main/java/com/hcimguide/HcimGuidePlugin.java")
print("  - src/main/java/com/hcimguide/StreamSyncExport.java")
print(f"✓ Backup: {backup.name}")
print("Volgende stap: bouw de plugin in IntelliJ en start HcimGuidePluginTest.main")
