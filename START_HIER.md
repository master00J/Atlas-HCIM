# HCIM Atlas — LIVE Stream Overlay voor RuneLite (2026)

Met dit pakket kun je de voortgang van je **Guide Overlay / HCIM Atlas-route** live tonen in **OBS** of **vMix**. De huidige stap, actieve bank/sectie, voltooide taken en het percentage komen uit RuneLite. Current Goal wordt automatisch op basis van de actieve guidefase bepaald; een eigen doel kun je tijdelijk instellen. HCIM-status en unlocks stel je zelf in.

**Dit is een werkend lokaal stream-bridgepakket plus een broncodepatch voor Guide Overlay 2.0.** Het is **niet** zomaar een Plugin Hub-update: de bestaande Guide Overlay uit Plugin Hub stuurt nog geen streamdata uit. Voor echte automatische updates moet je de aangepaste Guide Overlay **via de meegeleverde ontwikkelstarter** draaien. Er is hier geen live OSRS/RuneLite-login getest.

## Vereisten (Windows)

- RuneLite + een OSRS-account, liefst met Guide Overlay al gekend.
- **Node.js LTS** voor de lokale webserver (https://nodejs.org/).
- **Java JDK 11 of nieuwer** om de aangepaste RuneLite-plugin lokaal te starten (JDK 21 is geschikt als de upstream Gradle-wrapper het ondersteunt).
- Internet tijdens de eerste RuneLite-broncode/download en de Gradle-installatie.
- OBS of vMix **op dezelfde pc** als de lokale overlayserver. De browserbron gebruikt 127.0.0.1 en is niet bereikbaar vanaf een tweede streaming-pc.

## Installeren — stap voor stap

1. Pak dit ZIP-bestand volledig uit (bijv. in `Documenten\HCIM Atlas Live`).
2. Dubbelklik op **`runelite-patch\setup-runelite-windows.bat`**. Dit haalt [HCIMAtlas-GuideOverlay](https://github.com/master00J/HCIMAtlas-GuideOverlay) op in `Documenten\HCIMAtlas-GuideOverlay` (stream-export + auto-complete). Een bestaande map wordt niet overschreven.
3. Open `Documenten\HCIMAtlas-GuideOverlay` en dubbelklik op **`START_HCIM_ATLAS_RUNE_LITE.bat`**. De eerste keer moet Gradle Java/RuneLite-afhankelijkheden ophalen; dit kan een tijdje duren. Bij buildfouten: controleer JDK/Java-versie en de foutmelding. Als alternatief kan de repository in IntelliJ worden geopend en `HcimGuidePluginTest.main` uitgevoerd.
4. Gebruik **de RuneLite-client die door deze starter wordt geopend**. Als de normale Plugin Hub-versie van Guide Overlay óók actief is, schakel die dan uit om dubbele overlays of pluginconflicten te vermijden.
5. In de Guide Overlay-zijbalk: kies jouw bestaande **HCIM Atlas 2026**-route, of importeer `guide\HCIM_ATLAS_VOLLEDIGE_ROUTE.json` via **Add guide from file...** met exact de naam `HCIM Atlas 2026`. Voor de extra routepins kun je `guide\HCIM_ATLAS_PINNEN_VOLLEDIGE_ROUTE.json` via **Location tools → Import custom pins from clipboard...** gebruiken.
6. Dubbelklik op **`bridge\start-overlay.bat`** (Node.js moet geïnstalleerd zijn). Dit start de server lokaal op poort `38877`.
7. Open **http://127.0.0.1:38877/control** in Chrome. Bovenaan zou **LIVE** moeten staan als je ingelogd bent met de gepatchte Guide Overlay. Vink één stap in-game af: het aantal voltooide stappen moet stijgen.
8. Stel in het dashboard je **Hardcore-status**, eventueel je persoonlijke **Next Goal**, optionele **Questline %** en je zes **Major Unlocks** in. Klik op **Instellingen opslaan**. Deze instellingen veranderen live mee op je stream, maar zijn **niet** afgeleid uit OSRS.
9. Voeg in OBS of vMix een **Browser Source / Web Browser** toe met de onderstaande URL en resolutie.

## Browserbron voor OBS / vMix

| Bron | URL | Resolutie |
|---|---|---|
| Volledige HCIM streamoverlay | `http://127.0.0.1:38877/overlay` | **1920×1080** |
| Compacte verticale progress-widget | `http://127.0.0.1:38877/widget` | **560×700** of **1122×1402** |
| Dashboard om streaminstellingen te beheren | `http://127.0.0.1:38877/control` | gewone browser |

Browser Source in OBS: *Sources → + → Browser* → URL invullen → afmeting instellen. In vMix: *Add Input → Web Browser* (of een ondersteunde browser-input) en de URL gebruiken. Zorg dat de browserbron op de streaming-pc zelf laadt. De pagina heeft een transparante achtergrond en bevat geen RuneLite-gameplaycapture; voeg je gamecapture er als aparte laag onder toe.

## Welke informatie is echt live?

**Automatisch uit RuneLite (via de patch):**

- De naam van de gekozen Guide Overlay-guide.
- De huidige chapter, bank/sectie en volgende onafgewerkte instructie.
- Het aantal voltooide stappen, totaal aantal niet-overgeslagen stappen en voortgangspercentage.
- Verversing ongeveer elke **3 seconden** tijdens actief in-game spelen; het scherm markeert de verbinding als **offline** zodra de laatste export langer dan 15 seconden oud is.

**Handmatig in het streamdashboard:**

- Je eigen Current Goal-override (leeg laten = automatisch guidefase-doel).
- `Red Helm Alive`, `Hardcore verloren` of `Status onbekend`.
- Death-counter, questpercentage en de zes belangrijke unlocks.

**Niet ondersteund:** exacte skill-XP, drops, quests, raid-kills, werkelijke HCIM-deathdetectie of een garantie dat alle guide-instructies automatisch voltooid worden. De RuneLite-guide bepaalt zelf welke stappen automatisch afgehandeld worden. De stream koppelt enkel hun geregistreerde voortgang.

## Streambridge in detail

- Geen externe website, login, API-key of cloudopslag.
- Patch schrijft een klein JSON-bestand naar `%USERPROFILE%\.runelite\hcim-guide\stream-state.json`.
- Node-server kijkt naar dat bestand en levert de OBS-browserbron plus live updates via lokale Server-Sent Events.
- Instellingen worden bewaard in het bestand `stream-settings.json` naast `START_HIER.md`.
- De server luistert alleen op **127.0.0.1**; niet op 0.0.0.0 of internet.
- De export bevat **geen accountnaam, wachtwoord, bankinhoud of game-coördinaten**, enkel voortgang en huidige guide-instructie. Zet de lokale port niet publiek open.

## Demonstratie zonder RuneLite

Alleen bedoeld om de overlay te testen. Open twee terminalvensters in de map van het pakket:

```bat
node tests\mock_publisher.js
node bridge\server.js --demo --port 38878
```

Open dan `http://127.0.0.1:38878/overlay`. Dat is **demo-informatie**, geen RuneLite-voortgang. Gebruik voor echte streams poort 38877 en de normale starter.

## Problemen oplossen

- **Dashboard blijft offline:** controleer dat je de **gepatchte** RuneLite-ontwikkelclient gebruikt en ingelogd bent, en dat Guide Overlay met de route ingeschakeld is. Controleer het bestand `stream-state.json` onder `.runelite\hcim-guide` (pas 3 seconden na het inloggen).
- **Normale Guide Overlay werkt wel, maar geen streamsync:** de Plugin Hub-variant heeft de wijziging niet. Gebruik `START_HCIM_ATLAS_RUNE_LITE.bat`.
- **Java/Gradle build faalt:** verifieer je `java -version`, internet en eventueel de versie van de upstream repository. Het Java-exportformaat is lokaal met Java 11-stubs getest; een volledige actuele RuneLite-build vereist de upstream dependencies.
- **Geen pijlen in-game:** dat is Guide Overlay zelf; stel de overlayfuncties en routepins in. Deze streamkoppeling verandert de navigatielogica niet.
- **OBS op tweede pc:** deze versie werkt alleen op dezelfde machine. Voor dual-PC setup moet een beveiligde lokale netwerkbridge worden toegevoegd; zet de server niet zomaar publiek open.
- **Mijn banktitel is niet 'Bank 27/214':** de plugin exporteert de echte huidige sectietitel. De afbeelding van de voortgangswidget bevat een voorbeeldtitel die door dynamische tekst bedekt wordt.
- **Ik wil terug naar de oorspronkelijke plugin:** schakel de ontwikkelclient uit en open je normale RuneLite. De patch heeft alleen de aparte gedownloade broncode aangepast, niet RuneLite zelf.

## Testing / bewijsniveau

- 9 Node.js tests geslaagd: serverroutering, PNG-assets, initial offline, nieuwe stappen, instellingen en lokale paden.
- Java-exporter met Java 11-stubs gecompileerd en getest op JSON-structuur, skiplogica, huidige stap en atomische opslag.
- Patchinstaller getest op code-insertie en herhaald uitvoeren.
- **Nog niet getest in een ingelogde echte RuneLite/OSRS-client of in OBS/vMix zelf.** Beschouw deze uitgave als een functionele, lokaal geteste integratiebuild die nog een echte end-to-end-test nodig heeft.

## Bronvermelding

- Guide Overlay: https://github.com/RunelitePlugin/guide-overlay (BSD-2-Clause voor upstream plugin).
- HCIM Atlas guideinhoud: gebaseerd op B0aty/Tpapa's HCIM Guide V3 en aanvullende endgame-stappen. Respecteer de bronlicenties bij eventuele publieke verspreiding.
- Overlayillustraties: speciaal voor dit pakket gegenereerde artwork.

## Automatisch Current Goal (nieuw)

Het actuele doel volgt de chapter/sectie of bankrange uit de RuneLite-guide. Regels staan in `guide/goal-rules.json`. Dit is **geen verificatie dat een unlock al bereikt is**. Je kunt een tijdelijk eigen doel invullen via `/control`; leeg = automatisch. De huidige stap blijft exact uit de RuneLite-export komen.

## Webcam boven inventory

Het nieuwe 16:9-artwork heeft de webcam rechts boven de inventoryzone. Voeg je webcambron in OBS/vMix als aparte bron onder de browserframebron toe; de camerabron zelf wordt niet automatisch door de webserver geopend.
