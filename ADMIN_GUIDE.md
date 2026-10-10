# Buildcontest – Admin-Anleitung

Diese Anleitung richtet sich an Server-Admins, die einen Buildcontest einrichten und leiten wollen.
Die vollständige Spielbeschreibung steht in [`rules.md`](./rules.md), die Punktetabelle in
[`scoreboard-config.yml`](./scoreboard-config.yml).

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
| `plots.surface-y` | Nur Fallback, falls keine trockene Stelle gefunden wird (siehe unten) – normalerweise wird der Y-Level automatisch aus dem tatsächlichen Gelände ermittelt |
| `plots.center-x` / `plots.center-z` | Mittelpunkt des Bauplatz-Rasters |
| `plots.world` | **Siehe Warnung unten** |
| `game.duration-minutes` | Automatisches Ende nach X Minuten, `0` = nur `/bc end` |
| `game.autosave-minutes` | Intervall für automatisches Sichern des Spielstands, `0` = aus |
| `game.auto-delete-world-on-end` | Bauplatz-Welt direkt bei `/bc end` automatisch löschen? Standard `false` (dann per `/bc deleteworld` manuell) |

### ⚠️ `plots.world` darf NICHT die Server-Hauptwelt sein

`/bc start` **löscht die komplette konfigurierte Bauplatz-Welt und erzeugt sie mit einem
zufälligen Seed neu** – nur die Bauplätze selbst werden anschließend planiert, der Rest ist
normales, frisch generiertes Terrain. Bukkit kann die Standard-Hauptwelt des Servers (aus
`server.properties#level-name`, i. d. R. `world`) zur Laufzeit aber nicht entladen. `plots.world`
muss deshalb eine **eigene, dedizierte Welt** sein (Standard: `buildcontest_world`), die nur für
den Contest existiert. Lass Spieler auf der normalen Hauptwelt spawnen/warten, bis der Contest
beginnt.

Ist `plots.world` trotzdem auf die Hauptwelt gesetzt, bricht `/bc start` kontrolliert mit einer
Fehlermeldung ab, statt den Server zu beschädigen.

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
   Erzeugt die frische Bauplatz-Welt (neuer Zufalls-Seed, automatisch auf trockenem Land - siehe
   rules.md#setup-admin), verteilt die Bauplätze im Raster und planiert sie, und setzt alle Scores
   aus einem eventuell vorherigen Contest zurück. Das kann je nach Bauplatzgröße/-anzahl einige
   Sekunden dauern. Alle online Spieler werden automatisch in die Bauplatz-Welt teleportiert -
   Mitglieder einer Gruppe direkt zu ihrem Bauplatz, alle anderen an den Welt-Spawn. Später
   beitretende/zugewiesene Spieler nutzen `/bc tp`, um selbst nachzukommen.
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
| `/bc start` | Welt neu erzeugen, Bauplätze generieren, Scores zurücksetzen, Contest starten |
| `/bc end` | Contest beenden, Endstand bekanntgeben |
| `/bc deleteworld` | Bauplatz-Welt löschen (siehe `game.auto-delete-world-on-end`) |
| `/bc reload` | `config.yml` und `scoreboard-config.yml` neu laden |

Zusätzlich für Spieler nützlich: `/bc tp` teleportiert die eigene (online) Gruppe gemeinsam zu
ihrem Bauplatz.

## Troubleshooting

- **„plots.world ist die Server-Hauptwelt“**: siehe Warnung oben – eine andere Welt in
  `config.yml#plots.world` eintragen, dann `/bc reload` und erneut `/bc start`.
- **„Unbekanntes Material in scoreboard-config.yml“**: Der Blockname existiert in dieser
  Minecraft-Version nicht (mehr) oder wurde umbenannt. Betrifft nicht die Funktion des Plugins,
  dieser Block zählt dann einfach `default_score` (meist 0).
- **Server knapp bei Arbeitsspeicher**: `plots.size-x`/`size-z` und `plots.min-distance` klein
  halten reduziert die beim Planieren/Welterzeugen zu ladende Chunk-Fläche spürbar.
- **„Kein durchgehend trockener Platz … gefunden (Ozean-Welt?)“**: Die automatische Landsuche
  (siehe rules.md#setup-admin) hat in der Umgebung des konfigurierten Mittelpunkts keine
  durchgehend trockene Fläche gefunden und weicht auf `plots.center-x`/`-z` und `plots.surface-y`
  aus der Config aus - die Bauplätze können dann im Wasser liegen. Einfach `/bc start` erneut
  ausführen (neuer Zufalls-Seed) oder `plots.center-x`/`-z` auf eine andere Gegend setzen.
