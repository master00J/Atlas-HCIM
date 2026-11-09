# HCIM Atlas Live

RuneLite Hardcore Ironman guide-progressie en live stream-overlay voor OBS/vMix.

> **Status:** ontwikkelproject. De automatische guide-sync is een lokale uitbreiding op de externe Guide Overlay-plugin, niet een officiële RuneLite Plugin Hub-versie. Live RuneLite/OBS-end-to-endtests zijn nog nodig.

## Projectonderdelen

- RuneLite Guide Overlay-koppeling voor afgevinkte stappen, huidige bank en de eerstvolgende stap
- Lokale Node.js-bridge op `127.0.0.1:38877`
- `/overlay` (1920×1080), `/widget`, `/control`
- Automatisch *Current Goal* gebaseerd op de actieve guidefase; *Current Step* blijft de concrete volgende instructie
- HCIM-status, deaths en persoonlijke milestones voorlopig handmatig instelbaar
- Webcam-frame voorzien rechts, boven de OSRS-inventory

## Starten (zodra de bronbestanden beschikbaar zijn)

1. Installeer Node.js 18+, Git en een Java JDK.
2. Start de streambridge met `npm start`.
3. Open `http://127.0.0.1:38877/control` voor instellingen.
4. Voeg `http://127.0.0.1:38877/overlay` toe als Browser Source in OBS/vMix.
5. Installeer/patch de upstream Guide Overlay ontwikkelclient en importeer de Atlas-guide.

## Bronnen, rechten en veiligheid

De originele RuneLite [Guide Overlay](https://github.com/RunelitePlugin/guide-overlay) is een afzonderlijk project. De HCIM Atlas-route is gebaseerd op onder meer B0aty/Tpapa; controleer de rechten en licentie voor openbare herpublicatie van de integrale gidsteksten. De complete gedetailleerde B0aty-compilatie wordt om die reden niet zonder rechtencontrole in deze **publieke** repository geplaatst.

**Publiceer nooit:** authenticatietokens, RuneLite-characterprogressie, `stream-state.json`, `stream-settings.json` of privégegevens.

Dit repositoryproject wordt stap voor stap vanuit de eerder gemaakte lokale HCIM Atlas-pakketten gevuld.
