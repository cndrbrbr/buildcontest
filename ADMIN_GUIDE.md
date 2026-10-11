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

   Direkt außerhalb jeder Bauplatz-Südkante steht automatisch ein aktiver Beacon auf einer kleinen
   Eisenblock-Pyramide – rein visuelle Orientierungshilfe, zählt nicht zur Wertung (liegt außerhalb
   der Bauplatzgrenzen).
4. **Laufenlassen**: Scoreboard, Schutzmechanismen und Punktewertung laufen automatisch (siehe
   rules.md#bauregeln, #schutzmechanismen, #scoreboard-anzeige).

   Bei Bedarf (z. B. Pause, technisches Problem) `/bc pause` ausführen: Timer hält an, die
   **gesamte** Contest-Welt wird für Platzierung/Abbau eingefroren (nicht nur die Bauplätze) –
   damit niemand während der Pause heimlich weiterbaut oder farmt. `/bc resume` setzt denselben
   Contest mit dem exakten Punktestand und der verbleibenden Restzeit fort. `/bc start` lehnt
   sowohl einen laufenden als auch einen pausierten Contest ab.
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

## Automatikmodus und Start-Steine (siehe rules.md#schnellstart-presets-und-start-steine)

Vier Presets für schnelle, selbsterklärende Events ohne manuelle Vorbereitung – **immer zwei
Gruppen** (Rot und Blau):

| Preset | Gruppengröße | Teamwahl |
|---|---|---|
| `2x1` | 1 | Keine Ballwahl – die ersten zwei Beitretenden werden zufällig Rot/Blau zugewiesen |
| `2x2` | 2 | Rot/Blau-Ball, Spieler wählen per Rechtsklick selbst |
| `2x4` | 4 | Rot/Blau-Ball, Spieler wählen per Rechtsklick selbst |
| `2xn` | alle Anwesenden ohne Gruppe, halbiert | Keine Ballwahl – sofortige zufällige Zweiteilung, startet ohne Wartephase direkt |

```
/bc automode <2x1|2x2|2x4|2xn>
```

Per Admin-Befehl aktiviert, läuft der Contest mit der **konfigurierten** Spieldauer (Standard:
unbegrenzt).

### Start-Steine

Beim allerersten Serverstart stellt das Plugin die Start-Steine **automatisch am Weltspawn** der
Eingangswelt auf, falls das noch nie passiert ist (weder automatisch noch manuell) – es ist also
**kein** manueller Schritt nötig, damit Spieler sofort selbst starten können. Zum erneuten
Platzieren an einer anderen Stelle (z. B. nach einem Umbau der Lobby):

```
/bc placestartstones
```

Stellt an der aktuellen Position des Admins für jedes Preset einen beschrifteten Stein (Stein-
Block + Schild) sowie eine Statusanzeige auf. Ein Rechtsklick auf ein Schild löst dasselbe Preset
aus wie der entsprechende `/bc automode`-Befehl – **damit kann auch ohne anwesenden Admin ein
Contest beginnen**. Einziger Unterschied: über einen Stein gestartete Contests laufen immer genau
**30 Minuten**, unabhängig von `game.duration-minutes`.

Es läuft immer nur ein Contest/eine Teamwahl gleichzeitig – die Statusanzeige zeigt "Bereit" oder
"Contest läuft"; währenddessen lösen die Steine keinen weiteren Contest aus. `/bc end` setzt die
Anzeige zurück, auch bei natürlichem Zeitablauf (nicht nur bei explizitem `/bc end`).

### Eingangswelt, Contest-Welt und Inventar

- Die Eingangswelt läuft automatisch im **Adventure-Modus** (kein Bauen/Abbauen) – wird bei jedem
  Join und bei Rückkehr aus dem Contest gesetzt.
- Die Contest-Welt hat **Survival-Modus** für alle Teilnehmer und Schwierigkeitsgrad **Normal**
  (wird bei jedem `/bc start` neu gesetzt).
- Beim **erstmaligen Eintritt** in einen Contest wird das mitgebrachte Inventar gesichert und
  geleert – jeder beginnt mit leerem Inventar. Bei **Rückkehr** in die Eingangswelt (z. B. nach
  `/bc end`) wird das gesicherte Inventar wiederhergestellt; im Contest gesammelte Gegenstände
  gehen dabei verloren (ersetzt, nicht zusammengeführt). Wie der Automode-Wartezustand wird dieser
  Inventar-Tresor **nicht** über einen Serverneustart hinweg persistiert.

## Persistenz

Gruppenmitgliedschaft, Bauplätze, getrackte Blöcke und der Spielstatus werden zusätzlich zur
Minecraft-Welt in `plugins/BuildContest/data.yml` gesichert – automatisch alle
`game.autosave-minutes` sowie beim sauberen Server-Stopp. Ein Server-Neustart während eines
laufenden Contests lädt diesen Stand beim Aktivieren automatisch wieder; ein noch laufender
Timer wird dabei korrekt mit der verbleibenden Restzeit fortgesetzt.

Beim Laden wird jeder getrackte Block gegen die tatsächliche Welt geprüft: stimmt das Material an
der gespeicherten Position nicht mehr (z. B. weil der Block zwischen letztem Autosave und einem
Absturz schon abgebaut wurde), wird der Eintrag samt eventuell abhängiger Verbindungen entfernt,
statt für ein nicht mehr existierendes Bauwerk weiter Punkte zu vergeben.

Der **Endstand** jedes beendeten Contests wird zusätzlich dauerhaft in
`plugins/BuildContest/results.yml` archiviert – unabhängig von `data.yml` und unberührt von einem
späteren `/bc start` (setzt die laufenden Scores zurück) oder `/bc deleteworld`.

**Während ein Contest läuft**, lehnt das Plugin `/bc setgroups`, `/bc setgroupsize`,
`/bc setplotsize`, `/bc setdistance`, `/bc setscore` und `/bc automode` ab – diese
spielentscheidenden Einstellungen lassen sich erst nach `/bc end` wieder ändern.
`/bc assign` bleibt bewusst jederzeit erlaubt (Admin-Teamwechsel sind explizit gestattet).

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
| `/bc pause` | Timer anhalten, gesamte Contest-Welt für Platzierung/Abbau einfrieren |
| `/bc resume` | Denselben Contest mit verbleibender Restzeit fortsetzen |
| `/bc end` | Contest beenden, Endstand bekanntgeben |
| `/bc deleteworld` | Bauplatz-Welt löschen (siehe `game.auto-delete-world-on-end`) |
| `/bc automode <2x1\|2x2\|2x4\|2xn>` | Schnellstart-Preset aktivieren (siehe oben, konfigurierte Spieldauer) |
| `/bc placestartstones` | Beschriftete Start-Steine für alle Presets an der eigenen Position aufstellen |
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
