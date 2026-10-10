# Buildcontest – Admin-Anleitung

Diese Anleitung richtet sich an Server-Admins, die einen Buildcontest einrichten und leiten wollen.
Die vollständige Spielbeschreibung steht in [`rules.md`](./rules.md), die Punktetabelle in
[`scoreboard-config.yml`](./scoreboard-config.yml).

> **Hinweis:** `rules.md` beschreibt das vereinbarte Zielregelwerk; diese Anleitung beschreibt den
> aktuell implementierten Stand. Beide sind noch nicht vollständig deckungsgleich – offene Punkte
> stehen in [`TODO.md`](./TODO.md). Insbesondere kennt das Plugin noch kein `/bc pause`/`/bc resume`
> (Blockplatzierung während einer evtl. zukünftigen Pause lässt sich also noch nicht einfrieren),
> und das Block-Tracking/die Verbindungsprüfung pruefen noch nicht alle in rules.md §5
> beschriebenen Sonderfaelle (Kolben, Hopper, Fremdplugins).

## Voraussetzungen

- Spigot- oder **Paper-Server für Minecraft 26.3** (siehe `pom.xml#spigot.version` / `plugin.yml#api-version`).
- Java 25 auf dem Server.
- Die gebaute `buildcontest-<version>.jar` (siehe [`README.md`](./README.md)) im `plugins`-Ordner.

## Installation

1. JAR in den `plugins`-Ordner kopieren und den Server (neu) starten.
2. Beim ersten Start legt das Plugin `plugins/BuildContest/config.yml` und
   `plugins/BuildContest/scoreboard-config.yml` an.
3. `buildcontest.admin` (Standard: nur OP) an die Personen vergeben, die den Contest leiten sollen.

## Konfiguration (`config.yml`)

| Pfad | Bedeutung |
|---|---|
| `groups.count` | Anzahl der Gruppen |
| `groups.max-size` | Maximale Spieleranzahl pro Gruppe |
| `groups.assignment-mode` | `FREE` (Spieler wählen per `/bc join`) oder `CONFIG` (siehe `groups.assignments`) – siehe rules.md#gruppen |
| `groups.assignments` | Nur bei `CONFIG`: Minecraft-Name → Gruppennummer |
| `plots.size-x` / `plots.size-z` | Grundfläche eines Bauplatzes |
| `plots.min-distance` / `plots.max-distance` | Abstand zwischen Bauplätzen |
| `plots.surface-y` | Nur Fallback, falls für einen Bauplatz keine trockene Stelle gefunden wird (siehe unten) – normalerweise wird der Y-Level **für jeden Bauplatz einzeln** automatisch aus dem tatsächlichen Gelände ermittelt, verschiedene Bauplätze können also unterschiedliche Höhen haben |
| `plots.center-x` / `plots.center-z` | Mittelpunkt des Bauplatz-Rasters |
| `plots.world` | **Siehe Warnung unten** |
| `game.duration-minutes` | Automatisches Ende nach X Minuten, `0` = nur `/bc end` |
| `game.autosave-minutes` | Intervall für automatisches Sichern des Spielstands, `0` = aus |
| `game.auto-delete-world-on-end` | Bauplatz-Welt direkt bei `/bc end` automatisch löschen? Standard `false` (dann per `/bc deleteworld` manuell) |

### ⚠️ `plots.world` darf NICHT die Server-Hauptwelt sein

`/bc start` erzeugt für einen **neuen** Contest die komplette konfigurierte Bauplatz-Welt mit
einem zufälligen Seed neu – nur die Bauplätze selbst werden anschließend planiert, der Rest ist
normales, frisch generiertes Terrain. Existiert bereits eine Bauplatz-Welt (von einem vorherigen
Contest), fragt `/bc start` zur Sicherheit erst nach (siehe unten); läuft der Contest noch, wird
`/bc start` ganz abgelehnt. Bukkit kann die Standard-Hauptwelt des Servers (aus
`server.properties#level-name`, i. d. R. `world`) zur Laufzeit aber nicht entladen. `plots.world`
muss deshalb eine **eigene, dedizierte Welt** sein (Standard: `buildcontest_world`), die nur für
den Contest existiert. Lass Spieler auf der normalen Hauptwelt spawnen/warten, bis der Contest
beginnt.

Ist `plots.world` trotzdem auf die Hauptwelt gesetzt, bricht `/bc start` kontrolliert mit einer
Fehlermeldung ab, statt den Server zu beschädigen.

### Die Eingangswelt (Server-Hauptwelt)

Anders als die Bauplatz-Welt wird die Hauptwelt (`world` in `server.properties`) vom Plugin
**nie neu erzeugt oder gelöscht** – der Admin gestaltet sie selbst und sie bleibt dauerhaft
erhalten. Empfohlen: `level-type=minecraft:flat` in `server.properties`, damit sie bei jedem
Serverstart deterministisch gleich bleibt (z. B. als Lobby/Wartebereich, in dem sich Spieler vor
Contest-Start treffen). Beim Aktivieren korrigiert das Plugin automatisch die Y-Koordinate des
Weltspawns auf festen Boden (X/Z bleiben wie vom Admin gesetzt) – ein Absturz durch einen
"schwebenden" Weltspawn ist damit ausgeschlossen.

## Punktetabelle (`scoreboard-config.yml`)

Jeder Blocktyp hat einen Punktwert nach einem siebenstufigen Tier-System (siehe
rules.md#punktesystem). Eigene Werte setzen:

- Dauerhaft: Eintrag in `scoreboard-config.yml` ergänzen/ändern, danach `/bc reload`.
- Nur zur Laufzeit (geht beim Neustart verloren): `/bc setscore <block> <punkte>`.

## Ablauf eines Contests

1. **Vorbereiten** (beliebig oft änderbar, bevor der Contest beginnt):
   ```
   /bc setgroups <n>
   /bc setgroupsize <n>
   /bc setplotsize <x> <z>
   /bc setdistance <min> <max>
   ```
   Bei `assignment-mode: CONFIG` zusätzlich `groups.assignments` in `config.yml` pflegen und
   `/bc reload` ausführen.
2. **Spieler zuweisen** (nur bei `FREE`-Modus nötig – Spieler nutzen `/bc join <gruppe>` selbst):
   ```
   /bc assign <spieler> <gruppe>
   ```
3. **Start**:
   ```
   /bc start
   ```
   Erzeugt die frische Bauplatz-Welt (neuer Zufalls-Seed), ermittelt für **jeden** Bauplatz
   einzeln eine trockene, lokal passende Höhe (verschiedene Bauplätze können unterschiedliche
   Y-Level haben), verteilt sie im Raster und planiert sie, und setzt alle Scores aus einem
   eventuell vorherigen Contest zurück. Das kann je nach Bauplatzgröße/-anzahl einige Sekunden
   dauern.

   - Läuft bereits ein Contest, wird `/bc start` abgelehnt – erst `/bc end` ausführen.
   - Existiert noch eine Bauplatz-Welt eines vorherigen (bereits beendeten) Contests, fragt
     `/bc start` einmal nach: `/bc start confirm` erzeugt die neue Welt und löscht die alte damit
     unwiderruflich (siehe rules.md#spielablauf--ende).

   Jeder Spieler mit Gruppenzuordnung bekommt dabei **einmalig** eine sichere Startposition auf
   seinem Bauplatz – online Spieler sofort bei `/bc start`, später beitretende/zugewiesene Spieler
   bei `/bc join`/`/bc assign` bzw. beim ersten Einloggen. Danach bietet das Plugin bewusst
   **keine** weiteren Teleport-Abkürzungen mehr an (kein `/bc tp` o. ä.) – normale Fortbewegung
   (zu Fuß, Boot, Pferd, Minecart, Nether …) ist Teil des Spiels.
4. **Laufenlassen**: Scoreboard, Schutzmechanismen und Punktewertung laufen automatisch (siehe
   rules.md#bauregeln, #schutzmechanismen, #scoreboard-anzeige).
5. **Ende**:
   ```
   /bc end
   ```
   Friert den Baufortschritt ein, teleportiert alle Spieler aus der Bauplatz-Welt zurück in die
   Eingangswelt (Server-Hauptwelt) und gibt den Endstand (Rangliste nach Gruppenpunkten) im Chat
   bekannt. Läuft auch automatisch nach `game.duration-minutes`, falls gesetzt.
6. **Bauplatz-Welt löschen** (optional, siehe rules.md#spielablauf--ende): Bleibt nach `/bc end`
   zunächst stehen, damit die Bauten noch begutachtet werden können. Danach entweder
   `/bc deleteworld` manuell ausführen, oder `game.auto-delete-world-on-end: true` setzen, damit das
   automatisch direkt nach `/bc end` passiert. Spätestens der nächste `/bc start` ersetzt die Welt
   ohnehin komplett.

## Automatikmodus (Rot gegen Blau)

Zusatzfunktion des Plugins, (noch) nicht Teil des verhandelten `rules.md`: Ein schnelles,
selbsterklärendes Event ohne manuelle Vorbereitung.

```
/bc automode 2x2
```
oder
```
/bc automode 2x4
```

Setzt Gruppenanzahl (2), Gruppengröße (2 bzw. 4) und Bauplatzgröße (10×10 bzw. 20×20) automatisch,
benennt die Gruppen **Rot** und **Blau** und aktiviert die Ball-basierte Team-Wahl: Spieler
bekommen in der Eingangswelt einen roten und einen blauen Wolle-"Ball" ins Inventar und wählen per
Rechtsklick ihr Team. Eine schwebende Tafel in der Eingangswelt zeigt live, wer in welcher Gruppe
ist. Sobald beide Gruppen voll sind, startet der Contest automatisch – kein `/bc start` nötig.

## Persistenz

Gruppenmitgliedschaft, Bauplätze, gewertete Blöcke und der Spielstatus werden zusätzlich zur
Minecraft-Welt in `plugins/BuildContest/data.yml` gesichert – automatisch alle
`game.autosave-minutes` sowie beim sauberen Server-Stopp. Ein Server-Neustart während eines
laufenden Contests lädt diesen Stand beim Aktivieren automatisch wieder; ein noch laufender
Timer wird dabei korrekt mit der verbleibenden Restzeit fortgesetzt.

## Admin-Befehlsreferenz

| Befehl | Wirkung |
|---|---|
| `/bc setgroups <n>` | Anzahl der Gruppen festlegen (bestehende Mitgliedschaften gehen verloren) |
| `/bc setgroupsize <n>` | Maximale Gruppengröße festlegen |
| `/bc setplotsize <x> <z>` | Bauplatzgröße festlegen |
| `/bc setdistance <min> <max>` | Mindest-/Maximalabstand zwischen Bauplätzen |
| `/bc setscore <block> <punkte>` | Punktwert für einen Blocktyp (nur Laufzeit) |
| `/bc assign <spieler> <gruppe>` | Spieler einer Gruppe zuweisen |
| `/bc start` | Neuen Contest starten: Welt neu erzeugen, Bauplätze generieren, Scores zurücksetzen (abgelehnt, falls bereits ein Contest läuft) |
| `/bc start confirm` | Wie `/bc start`, aber mit ausdrücklicher Bestätigung, falls noch eine alte Bauplatz-Welt existiert und ersetzt werden soll |
| `/bc end` | Contest beenden, Endstand bekanntgeben |
| `/bc deleteworld` | Bauplatz-Welt löschen (siehe `game.auto-delete-world-on-end`) |
| `/bc automode 2x2` / `/bc automode 2x4` | Schnellstart-Automatikmodus aktivieren (siehe oben) |
| `/bc reload` | `config.yml` und `scoreboard-config.yml` neu laden |

Es gibt bewusst **keinen** Spieler-Teleportbefehl (kein `/bc tp` o. ä.) – siehe
rules.md#welten-und-fortbewegung: Nach dem einmaligen Startpunkt-Teleport ist normale
Fortbewegung Teil des Spiels.

## Troubleshooting

- **„plots.world ist die Server-Hauptwelt“**: siehe Warnung oben – eine andere Welt in
  `config.yml#plots.world` eintragen, dann `/bc reload` und erneut `/bc start`.
- **„Unbekanntes Material in scoreboard-config.yml“**: Der Blockname existiert in dieser
  Minecraft-Version nicht (mehr) oder wurde umbenannt. Betrifft nicht die Funktion des Plugins,
  dieser Block zählt dann einfach `default_score` (meist 0).
- **Server knapp bei Arbeitsspeicher**: `plots.size-x`/`size-z` und `plots.min-distance` klein
  halten reduziert die beim Planieren/Welterzeugen zu ladende Chunk-Fläche spürbar.
- **„Kein durchgehend trockener Platz … gefunden (Ozean-Welt?)“**: Die automatische Landsuche hat
  in der Umgebung des konfigurierten Mittelpunkts keine durchgehend trockene Fläche für das
  Gesamt-Raster gefunden und weicht auf `plots.center-x`/`-z` und `plots.surface-y` aus der Config
  aus. Einfach `/bc start confirm` erneut ausführen (neuer Zufalls-Seed) oder `plots.center-x`/`-z`
  auf eine andere Gegend setzen.
- **„Bauplatz von Gruppe N hat keinen durchgehend trockenen eigenen Boden“**: Nur dieser EINE
  Bauplatz liegt trotz des insgesamt trockenen Gesamt-Rasters lokal im Wasser (z. B. ein kleiner
  See); er bekommt dann den gemeinsamen Anker-Y-Level als Rückfalloption und kann im Wasser liegen.
  `/bc start confirm` erneut ausführen behebt das meist (neuer Seed).
