# HCIM Macroquest Route – RuneLite-plugin

Volg de **HCIM Macroquest Guide 2026** volledig in RuneLite: alle fases, banks en
stappen in een zijpaneel, je voortgang per account, en een overlay met je
volgende stap.

## Wat de plugin doet

- **Zijpaneel** met de hele guide: kies een fase (Fase 0 tot 6, Sailing,
  HCIM-veiligheid, bijlagen), klap banks open en dicht, vink stappen af.
  Per bank zie je wat je meeneemt en hoeveel stappen je al gedaan hebt.
- **Volgende stap** bovenaan het paneel met de knoppen *Klaar* en *Toon*.
- **Overlay** linksboven in het spel met je huidige bank, de volgende stap(pen)
  en een voortgangsbalk bij skill-doelen ("tot 62 Farming", "tot 31.512 Thieving XP").
- **Automatisch afvinken**:
  - stappen met "Voltooi X" zodra de quest in je questlijst klaar is (standaard aan);
  - stappen met een skill-doel zodra je dat level of die XP haalt (standaard uit).
- **Voortgang per account**: opgeslagen in je RuneLite-profiel, dus elk account
  (ook een tweede HCIM) heeft zijn eigen voortgang.
- **Zoeken** in de hele route ("limpwurt", "Bank 104", "Shellbane").
- **Rechtsklik** op een stap: afvinken, *Alles tot hier afvinken* (voor een account
  dat al onderweg is), of de wiki-pagina van de quest of diary openen.
- **Hotkey** om je huidige stap af te vinken (in te stellen in de plugin-config).

Fase 2 (Sailing) loopt naast de hoofdroute: die stappen vink je zelf af, en in de
config kan je de volgende Sailing-stap ook in de overlay tonen.

## Lokaal draaien

Je hebt een JDK nodig (versie 11 of hoger, bv. Eclipse Temurin 17).

```bash
# Windows
gradlew.bat run

# macOS / Linux
./gradlew run
```

Dat start een RuneLite-client in developer-mode met deze plugin al geladen.
Inloggen met een Jagex-account in zo'n dev-client gaat volgens
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Kort: start RuneLite één keer via de Jagex Launcher met de client-optie
`--insecure-write-credentials`; daarna logt de dev-client in met die opgeslagen sessie.

Open de map gewoon in Cursor of IntelliJ; het is een standaard Gradle-project.

### Testen

```bash
gradlew.bat test
```

`RouteGuideTest` controleert de route tegen de echte RuneLite-API: elke quest
die automatisch afgevinkt wordt, moet in `net.runelite.api.Quest` bestaan, elke
skill in `Skill`, en elke stap-id moet uniek zijn.

## De route bijwerken

De route zit in `src/main/resources/com/hcimroute/route.json` en wordt gemaakt
uit de Markdown-export van de guide:

1. Exporteer de guide als Markdown en zet het bestand op `tools/guide.md`.
2. Draai:
   ```bash
   python tools/guide_to_json.py tools/guide.md src/main/resources/com/hcimroute/route.json
   ```
3. Het script meldt quests die het niet herkent; dat zijn meestal diaries of
   gewone woorden en mag je negeren.
4. `gradlew.bat test` en daarna `gradlew.bat run`.

Het script herkent de opbouw van de guide: `## Fase …` wordt een hoofdstuk,
`**Bank X – …**` een bank, genummerde regels worden stappen, "Voltooi X" wordt
automatisch afvinken bij quest X, en "tot N Skill" of "tot N Skill XP" wordt
een skill-doel. Komt er een nieuwe quest uit, werk dan ook
`tools/runelite_quests.txt` bij met de namen uit `net.runelite.api.Quest`.

Afgevinkte stappen blijven bewaard zolang de tekst van een stap niet verandert:
een stap-id is de bank plus een hash van de stap-tekst. Pas je een stap aan, dan
moet je alleen die ene stap opnieuw afvinken.

## Publiceren op de Plugin Hub

1. Zet het project in een publieke GitHub-repository.
2. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) en voeg
   `plugins/hcim-route-guide` toe met:
   ```
   repository=https://github.com/<jij>/hcim-route-guide.git
   commit=<commit-hash>
   ```
3. Open een pull request.

De route is gebaseerd op de B0aty HCIM Guide v3 (oorspronkelijk van Tpapa).
Vraag hun toestemming voor je de route publiek verspreidt.

## Structuur

| Bestand | Wat |
| --- | --- |
| `HcimRoutePlugin.java` | Laden, voortgang opslaan, automatisch afvinken, hotkey |
| `RoutePanel.java` | Het zijpaneel |
| `RouteOverlay.java` | De overlay in het spel |
| `RouteGuide.java` | De route in volgorde, volgende stap, "tot hier" |
| `ProgressTracker.java` | Afgevinkte stappen, (de)serialisatie |
| `RouteText.java` | Tekst naar HTML of platte tekst, wiki-links |
| `Route.java` | Datamodel van `route.json` |
| `tools/guide_to_json.py` | Guide (Markdown) naar `route.json` |
