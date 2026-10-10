# Buildcontest – TODO

> Entwicklungs-Checkliste zum vereinbarten [Regelwerk](./rules.md). Alle Punkte sind zunächst **offen**; die Liste dokumentiert den Soll-Zustand und behauptet nicht, dass die Funktionen bereits fehlen oder funktionieren. Bei Abschluss abhaken (`[x]`) und möglichst auf Commit/Issue verweisen.
>
> **Grundprinzip:** Eine gemeinsame Survival-Welt mit den Bauplätzen darin. Farmen, Handeln und Transport erfolgen in dieser Welt; während des Wettbewerbs gibt es keine Buildcontest-Spielerteleports.

## P0 – Kritische Grundlage

### 1. Welt und Bauplatzgenerierung
- [ ] Bestehenden Welt-Lebenszyklus gegen `rules.md` prüfen; eine **gemeinsame** Survival-/Contest-Welt verwenden.
- [ ] Neue Welt nur für einen **neuen** Wettbewerb mit zufälligem Seed erzeugen; bei Resume dieselbe Welt erhalten.
- [ ] Für jede Gruppe trockenen, geeigneten Bauplatz ermitteln, lokal planieren und **individuellen Y-Level** zulassen.
- [ ] Bauplatzabstände als Abstand der Grundstücksgrenzen berücksichtigen (Standard 200–500 Blöcke); Geländegefälle und Höhlen prüfen.
- [ ] Fundament/Ankerfläche und Grundstücksgrenzen dauerhaft speichern; natürliche Blöcke nie automatisch werten.
- [ ] Nur beim erstmaligen Start sichere Startposition erlauben; nach dem Start keine Teleport-Abkürzungen bieten.
- [ ] Außerhalb der Bauplätze normale Survival-Aktionen erlauben (Farmen, Wege, Brücken, Transport und Handel).
- [ ] Lobby-/Eingangswelt unverändert lassen und niemals durch Contest-Welt-Löschung erfassen.

**Abnahme:** Mehrere Bauplätze liegen in derselben normalen Survival-Welt, können unterschiedliche Höhen besitzen und sind von Spielern ohne Teleport erreichbar.

### 2. Grundstücksschutz
- [ ] Bau-/Abbaurechte auf **eigene** Teammitglieder begrenzen; Besucher dürfen fremde Bauplätze betreten, aber nicht verändern.
- [ ] Fremde Container-, Inventar- und Entity-Zugriffe verhindern; Hopper/Hopper-Minecarts testen.
- [ ] Explosionen (inkl. TNT, Creeper, Wither und Betten), Feuer und Feuerausbreitung schützen.
- [ ] Wasser-/Lavafluss und Kolbenbewegungen über Grundstücksgrenzen absichern.
- [ ] Enderman-/Mob-Griefing, Blockphysik, Wachstum und sonstige indirekte Blockänderungen absichern.
- [ ] Eigene manuelle Bauaktionen erlauben, aber auch den eigenen Bauplatz vor unkontrollierten Schäden schützen.
- [ ] Konflikte mit anderen Plugins und privilegierten Adminaktionen prüfen.

**Abnahme:** Fremde Spieler können besichtigen, aber weder direkt noch indirekt Punkte, Gebäude oder Lagerbestände anderer Teams beschädigen.

### 3. Block-Tracking und Punkte
- [ ] Block-Tracking und Score-Berechnung in getrennte Komponenten aufteilen.
- [ ] Nur zulässige **Spielerplatzierungen** registrieren (Contest-ID, Koordinaten, Material, Spieler-UUID, Team, Zustand).
- [ ] Vorhandene Naturblöcke, WorldEdit-/Command-/Plugin-Blöcke, automatisch erzeugte Blöcke und Enderman-Platzierungen von neuen Punkten ausschließen.
- [ ] Auf Abbau, Ersetzung, Zerstörung, Blocktransformation und Kolbenbewegung korrekt reagieren; keine doppelten oder Phantom-Punkte zulassen.
- [ ] **Wertbar** und **verbindend** getrennt behandeln; auch 0-Punkte-Blöcke dürfen verbinden.
- [ ] Verbindung zum gespeicherten Fundament nur über sechs direkte Blockflächen prüfen, nicht diagonal und nicht über Außenbereiche.
- [ ] Bei Verlust der Verbindung Punkte betroffener Komponenten abziehen und nach Wiederverbindung wiederherstellen.
- [ ] Punktestand immer aus den aktuell gültigen, noch existierenden und verbundenen Blöcken ableiten (kein kumulatives Farmen durch Abbau/Neusetzen).
- [ ] Punktwerte aus `scoreboard-config.yml` übernehmen; geänderte Beispielwerte, Ausnahmen und Materialvarianten prüfen.
- [ ] Verbindungsprüfungen inkrementell/gezielt statt mit Vollscan bei jeder Platzierung umsetzen.

**Abnahme:** Haus mit 0-Punkte-Verbindungsblöcken funktioniert; Entfernen einer tragenden Verbindung reduziert Punkte und Wiederverbinden stellt sie genau einmal wieder her.

### 4. Persistenz und Absturzsicherheit
- [ ] Persistente Speicherung (z. B. SQLite) für Contest-Status, Welt-ID, Grundstücke, Fundamente, UUIDs, Tracking, Teams, Punkte und Timer festlegen.
- [ ] Datenänderungen zuverlässig schreiben; nach Crash/Neustart Inkonsistenzen zwischen Welt und Tracking erkennen und beheben.
- [ ] Serverneustart mit RUNNING und PAUSED testen; kein neuer Seed, keine verlorenen Punkte und keine ungewollt weiterlaufende Zeit.
- [ ] Änderungen an spielentscheidenden Einstellungen während RUNNING/PAUSED sperren.
- [ ] Abschluss-Ergebnisse und Statistiken unabhängig von der später gelöschten Welt speichern.

**Abnahme:** Nach Neustart stimmen Welt, Plots, Mitglieder, Blöcke, Team-/Einzelpunkte und Restzeit.

## P1 – Wichtige Spielfunktionen

### 5. Zustandsmaschine und sichere Administration
- [ ] Zustände SETUP, LOBBY, PREPARING, RUNNING, PAUSED und FINISHED implementieren.
- [ ] `/bc pause` / `/bc resume`: Timer und wettbewerbsrelevante Spieleraktionen anhalten/fortsetzen.
- [ ] Pausenmodus so absichern, dass auch Farmen, Handel und indirekte Änderungen keine Wettbewerbsvorteile bringen.
- [ ] `/bc start` darf laufende oder pausierte Wettbewerbe nicht überschreiben.
- [ ] `/bc end` friert Endstand und Bauplätze ein; Gleichstände als gemeinsame Sieger behandeln.
- [ ] Ergebnishistorie sichern, **kein** Weltbackup anlegen (bewusste Entscheidung 11B).
- [ ] Löschen/Ersetzen einer abgeschlossenen Contest-Welt nur nach ausdrücklicher Adminbestätigung; keine versehentliche Löschung der Lobbywelt.
- [ ] `game.auto-delete-world-on-end` standardmäßig deaktivieren; Besichtigung nach Spielende ermöglichen.

**Abnahme:** Pause/Resume und Ende funktionieren nach Neustart; neue Contests löschen nie stillschweigend alte Bauwerke.

### 6. Teams, UUIDs und Handel
- [ ] Gruppenmitgliedschaften über UUID statt über Namen führen.
- [ ] Freie Teamwahl **oder** Admin-Festzuweisung pro Contest; beide Modi nicht mischen.
- [ ] Gruppengrößen durchsetzen; Wechsel nach Start nur per Adminbefehl.
- [ ] Bei Admin-Teamwechsel vorhandene Baubeiträge dem ursprünglichen Team zugeordnet lassen.
- [ ] Freiwilligen Handel/Tausch zwischen Gruppen zulassen, ohne fremde Plot-Rechte zu vergeben.
- [ ] Logout, Tod und Wiedereinstieg ohne automatische Rückteleportation testen.

### 7. Scoreboard und persönliche Statistiken
- [ ] Live-Sidebar mit Restzeit, Rangfolge aller Teams, eigenem Team und persönlichem Bau-Beitrag ergänzen.
- [ ] Für persönliche Baupunkte den tatsächlichen Platzierer pro gültigem Block verwenden; Abbau zieht dessen Beitrag ab.
- [ ] Invariante sicherstellen: Summe persönlicher Baupunkte pro Team = Team-Score.
- [ ] Abgebaute Ressourcen, gültige Platzierungen und aktive Spielzeit als **separate** Statistiken erfassen, ohne Zusatzpunkte.
- [ ] Aktivitätsstatistiken gegen triviales Hochzählen durch Wiederholen schützen.
- [ ] `/bc stats`, `/bc top`, `/bc time`, `/bc status`, `/bc teams` umsetzen/prüfen.
- [ ] Updates bei vielen Spielern effizient halten; optional Führungswechsel-Meldungen drosseln.

### 8. Befehle und Permissions
- [ ] Spielerbefehle mit `rules.md` abgleichen; keine `/bc home`-/`/bc plot`-Teleportfunktion im laufenden Contest.
- [ ] Adminbefehle für Start, Pause, Resume, Ende, Teamzuweisung, Setup, Ergebnisse, Reload und bestätigtes Löschen abgleichen.
- [ ] Admin- und Spieler-Permissions dokumentieren und testen.
- [ ] Tab-Completion, Fehlermeldungen und `/bc help` aktualisieren.

## P2 – Qualität und Dokumentation

### 9. Punktetabelle ausbalancieren
- [ ] Sämtliche konfigurierten Blockwerte nach Beschaffungs- und Verarbeitungsaufwand systematisch durchgehen.
- [ ] Glas-/Steinziegel-Werte sowie Eisen-, Gold-, Diamant- und Smaragdblöcke im Spiel testen.
- [ ] Blockform-Vererbung, Container-Ausnahmen (Shulker Box, Beacon, Conduit) und maximale 50 Punkte prüfen.
- [ ] Neue Materialnamen der Ziel-Minecraft-Version ergänzen; unbekannte Blöcke standardmäßig 0 Punkte.
- [ ] Mit Test-Contests prüfen, ob Farmstrategien oder bestimmte Crafting-Rezepte unverhältnismäßig viele Punkte geben.

### 10. Tests und Performance
- [ ] Unit-Tests für Score-Berechnung, Materialregeln, Verbindungsgraph und Teamzuordnung.
- [ ] Integrationstests für Platzieren/Abbauen, WorldEdit, Kolben, Wasser, Explosionen, Wachstum und Mob-Griefing.
- [ ] Multiplayer-Tests mit mehreren Teams, Besuchern, Handel und Gruppenwechseln.
- [ ] Lasttest mit großen Bauwerken, vielen Blockänderungen und Serverneustarts.
- [ ] Crash-/Recovery-Tests mit Datenbank und Weltzustand; Phantom- und Doppelzählungen ausschließen.
- [ ] Prüfen, dass Bauplatzsuche auch bei mehreren Gruppen ohne endlose Generierung endet und brauchbare Fehlermeldungen liefert.

### 11. Dokumentation und Veröffentlichung
- [ ] `README.md`, `ADMIN_GUIDE.md` und `PLAYER_GUIDE.md` an `rules.md` angleichen.
- [ ] Konfigurationsbeispiele, Permission-Nodes, Adminbestätigung und Spielerregeln dokumentieren.
- [ ] Unterschied zwischen **beschlossener Regel**, **implementierter Funktion** und **getesteter Funktion** sichtbar machen.
- [ ] Changelog und Release-/Testcheckliste pflegen.

## Definition of Done

Ein Punkt wird erst abgehakt, wenn die Funktion implementiert, gegen `rules.md` geprüft und sinnvoll getestet ist. Ein Meilenstein gilt erst als abgeschlossen, wenn seine Abnahmekriterien erfüllt sind. Insbesondere müssen die **gemeinsame Survival-Welt ohne Spielerteleport**, korrekt geschützte Grundstücke sowie persistente, manipulationssichere Punkte funktionieren.
