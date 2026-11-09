# HCIM Atlas Live — RuneLite × OBS/vMix

**Nederlandstalig project voor een OSRS Hardcore Ironman-stream.** Deze repository bundelt de uitgebreide guide, RuneLite Guide Overlay-uitbreiding, transparante streamvisuals en een lokale live voortgangsserver.

## Status

- ✅ Live browser-overlays: `/overlay` (1920×1080) en `/widget` (560×700).
- ✅ Lokale export van de volgende onafgewerkte guidestap en voortgang (via patch voor Guide Overlay 2.0).
- ✅ **Current Goal automatisch** via de actieve guidefase; Current Step blijft de exacte taak.
- ✅ Optionele handmatige override van Current Goal in het dashboard.
- ✅ Webcam-frame rechtsboven de OSRS-inventory-zone; cameraplaatje wordt als aparte OBS/vMix-bron ingesteld.
- ✅ Node testdekking voor API en doelregels; Python guide tests.
- ⚠️ Niet getest met een live OSRS-login. Het script patcht een afzonderlijke upstream ontwikkelclient; geen Plugin Hub-distributie.
- ⚠️ HCIM-status, deaths, skilllevels en questpercentages worden **niet** automatisch geverifieerd.

## GitHub-repository

Dit project staat op [master00J/Atlas-HCIM](https://github.com/master00J/Atlas-HCIM). De repository is openbaar. Projectbroncode en eigen gegenereerde streamvisuals staan in de repository; gidsmateriaal afkomstig van de Old School RuneScape Wiki behoudt de afzonderlijke CC BY-NC-SA 3.0-voorwaarden. Zie [NOTICE.md](NOTICE.md) en [LICENSE-CONTENT.md](LICENSE-CONTENT.md). Publiceer geen persoonlijke RuneLite-gegevens, streaminstellingen of accountgegevens.

## Snelstart

1. Installeer **Node.js 18+**, **Git**, Java JDK en [OBS](https://obsproject.com/) of vMix.
2. Download deze repository als ZIP, of clone hem.
3. Voer `runelite-patch/setup-runelite-windows.bat` uit. Hiermee wordt upstream [Guide Overlay](https://github.com/RunelitePlugin/guide-overlay) opgehaald in een aparte map en krijgt die de lokale `StreamSyncExport`-patch.
4. Start de gegenereerde `START_HCIM_ATLAS_RUNE_LITE.bat` in die aparte map; importeer `guide/HCIM_ATLAS_VOLLEDIGE_ROUTE.json` en eventueel `guide/HCIM_ATLAS_PINNEN_VOLLEDIGE_ROUTE.json` via Guide Overlay.
5. Start `bridge/start-overlay.bat`, open `http://127.0.0.1:38877/control` voor instellingen.
6. Voeg `http://127.0.0.1:38877/overlay` als **Browser Source** van 1920×1080 toe. Voeg je webcam als aparte bron *onder* de framebrowserbron toe op de positie van het rechter webcamkader.
7. Wil je alleen het voortgangspaneel, gebruik `/widget`.

## Current Goal versus Current Step

`Current Step` komt uit de eerste niet-voltooide stap uit Guide Overlay. `Current Goal` wordt **op de lokale server** gekoppeld aan de actieve guidefase, op basis van `chapter`, `section` en eventuele banknummers. De regels staan in [`guide/goal-rules.json`](guide/goal-rules.json).

Bijvoorbeeld:
- Bank 1 van Episode 1: **Vroege quests & accountbasis opbouwen**.
- Bank 55 van Episode 2: **Questvereisten & vroege skillprogressie**.
- Episode 12: **Zulrah veilig voorbereiden**.
- Raids chapter: **Raids: CoX, ToA, ToB en Fractured Archive**.

Het is een **automatisch doel op basis van voortgang in de guide**; het bevestigt niet of een quest daadwerkelijk is voltooid. Voor een tijdelijk eigen doel kun je op `/control` de handmatige override instellen; leegmaken activeert automatisch opnieuw.

## Repo-structuur

- `bridge/`: Node.js HTTP/SSE-server, alleen bereikbaar via `127.0.0.1`.
- `assets/`: geanimeerd-ready overlay met live tekst, voortgangsbalken, PNG-frames en dashboard.
- `runelite-patch/`: Java-exporter + patch/installatiescripts voor Guide Overlay.
- `guide/`: complete guide en waypoint-import; fase-doelregels.
- `guide-tools/`: audit, bronbestanden en tests van de route.
- `docs/earlier-guides/`: originele uitgebreide HTML/MD/PDF-uitgaven.
- `tests/`: offline Node-tests en Java-teststubs.

## Grote PDF herstellen

De 178-pagina's tellende historische bank-per-bank PDF is wegens een limiet op de gebruikte GitHub-uploadverbinding als vier binaire delen opgeslagen in `docs/earlier-guides/pdf-parts/`. De bytes zijn niet gewijzigd. Herstel de originele PDF met:

```bash
python docs/earlier-guides/reassemble_pdf.py
```

Het script vergelijkt de SHA-256 met de oorspronkelijke PDF. De overige HTML-, Markdown- en kleinere PDF-bestanden staan rechtstreeks in de repository.

## Testen

```bash
node tests/test-live.js
node tests/test-goals.js
python guide-tools/test_atlas.py
```

Live server:

```bash
node bridge/server.js
```

Gegevens blijven lokaal; publiceer geen `stream-state.json` of `stream-settings.json`.

## Upstream en bronvermelding

Guide Overlay is een afzonderlijk project onder **BSD-2-Clause**; zie hun repository. De guide bevat materiaal uit B0aty/Tpapa's HCIM Guide V3, die door hen is geschreven; dit is een persoonlijke bewerking/compilatie voor de gebruiker. **De guideinhoud afkomstig van de OSRS Wiki wordt gedeeld onder CC BY-NC-SA 3.0 met bronvermelding; gebruik overeenkomstig die licentie.** Eigen aanvullingen hebben geen automatische externe goedkeuring of status als officiële gameguide. De streamillustraties zijn voor dit project gegenereerd.

Voor meer details: [`START_HIER.md`](START_HIER.md).
