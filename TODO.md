# Buildcontest – TODO

> Entwicklungs-Checkliste zum vereinbarten [Regelwerk](./rules.md). Alle Punkte sind zunächst **offen**; die Liste dokumentiert den Soll-Zustand und behauptet nicht, dass die Funktionen bereits fehlen oder funktionieren. Bei Abschluss abhaken (`[x]`) und möglichst auf Commit/Issue verweisen.
>
> **Grundprinzip:** Eine gemeinsame Survival-Welt mit den Bauplätzen darin. Farmen, Handeln und Transport erfolgen in dieser Welt; während des Wettbewerbs gibt es keine Buildcontest-Spielerteleports.

## P0 – Kritische Grundlage

### 1. Welt und Bauplatzgenerierung
- [x] Bestehenden Welt-Lebenszyklus gegen `rules.md` prüfen; eine **gemeinsame** Survival-/Contest-Welt verwenden. (0c95f2b)
- [x] Neue Welt nur für einen **neuen** Wettbewerb mit zufälligem Seed erzeugen; bei Resume dieselbe Welt erhalten. (0c95f2b)
- [x] Für jede Gruppe trockenen, geeigneten Bauplatz ermitteln, lokal planieren und **individuellen Y-Level** zulassen. (0c95f2b)
- [x] Bauplatzabstände als Abstand der Grundstücksgrenzen berücksichtigen (Standard 200–500 Blöcke); Geländegefälle und Höhlen prüfen. (bereits vor 0c95f2b vorhanden, live mit 200/500-Default bestätigt)
- [x] Fundament/Ankerfläche und Grundstücksgrenzen dauerhaft speichern; natürliche Blöcke nie automatisch werten. (bereits vorhanden: persistence.DataStore, ScoreManager wertet nur BlockPlaceEvent)
- [x] Nur beim erstmaligen Start sichere Startposition erlauben; nach dem Start keine Teleport-Abkürzungen bieten. (0c95f2b, EntryTeleportManager)
- [x] Außerhalb der Bauplätze normale Survival-Aktionen erlauben (Farmen, Wege, Brücken, Transport und Handel). (bereits vorhanden: ProtectionListener wirkt nur innerhalb von Bauplätzen)
- [x] Lobby-/Eingangswelt unverändert lassen und niemals durch Contest-Welt-Löschung erfassen. (bereits vorhanden: WorldManager#isPrimaryWorld-Schutz)

**Abnahme:** Mehrere Bauplätze liegen in derselben normalen Survival-Welt, können unterschiedliche Höhen besitzen und sind von Spielern ohne Teleport erreichbar. ✅ Live auf Spigot 26.3 verifiziert (0c95f2b, 0327707).

### 2. Grundstücksschutz
- [x] Bau-/Abbaurechte auf **eigene** Teammitglieder begrenzen; Besucher dürfen fremde Bauplätze betreten, aber nicht verändern. (bereits vorhanden)
- [x] Fremde Container-, Inventar- und Entity-Zugriffe verhindern; Hopper/Hopper-Minecarts testen. (ProtectionListener-Erweiterung, InventoryOpenEvent/InventoryMoveItemEvent)
- [x] Explosionen (inkl. TNT, Creeper, Wither und Betten), Feuer und Feuerausbreitung schützen. (bereits vorhanden)
- [x] Wasser-/Lavafluss und Kolbenbewegungen über Grundstücksgrenzen absichern. (ProtectionListener-Erweiterung, BlockFromToEvent/BlockPistonExtend-/RetractEvent, live verifiziert)
- [x] Enderman-/Mob-Griefing, Blockphysik, Wachstum und sonstige indirekte Blockänderungen absichern. (ProtectionListener-Erweiterung, EntityChangeBlockEvent/BlockSpreadEvent)
- [x] Eigene manuelle Bauaktionen erlauben, aber auch den eigenen Bauplatz vor unkontrollierten Schäden schützen. (alle neuen Handler wirken unabhängig von Gruppenzugehörigkeit)
- [ ] Konflikte mit anderen Plugins und privilegierten Adminaktionen prüfen. (keine automatisierte Prüfung möglich - laufende Beobachtung im Betrieb nötig)

**Abnahme:** Fremde Spieler können besichtigen, aber weder direkt noch indirekt Punkte, Gebäude oder Lagerbestände anderer Teams beschädigen.

### 3. Block-Tracking und Punkte
- [ ] Block-Tracking und Score-Berechnung in getrennte Komponenten aufteilen. (bewusst noch nicht gemacht: ScoreManager vereint beides weiterhin in einer Klasse - funktional korrekt, aber nicht die in rules.md#manipulationssichere-blockerfassung verlangte strukturelle Trennung)
- [x] Nur zulässige **Spielerplatzierungen** registrieren (Koordinaten, Material, Spieler-UUID, Team, Zustand). (ScoreManager-Umbau; "Contest-ID" gibt es noch nicht als eigenes Feld, da aktuell nur ein Contest gleichzeitig existiert)
- [x] Vorhandene Naturblöcke, WorldEdit-/Command-/Plugin-Blöcke, automatisch erzeugte Blöcke und Enderman-Platzierungen von neuen Punkten ausschließen. (registerPlacement laeuft nur ueber echtes BlockPlaceEvent; Enderman-Platzierungen werden durch ProtectionListener#onEntityChangeBlock bereits verhindert)
- [ ] Auf Abbau, Ersetzung, Zerstörung, Blocktransformation und Kolbenbewegung korrekt reagieren; keine doppelten oder Phantom-Punkte zulassen. (Abbau/Zerstoerung behandelt; **bekannte Luecke:** ein Kolben, der einen getrackten Block INNERHALB desselben Bauplatzes verschiebt, aktualisiert die Tracking-Position noch nicht - das Tracking bleibt an der alten Koordinate "haengen". Blocktransformation wie BlockFadeEvent/BlockFormEvent ebenfalls noch nicht abgeglichen)
- [x] **Wertbar** und **verbindend** getrennt behandeln; auch 0-Punkte-Blöcke dürfen verbinden. (ScoreManager-Umbau, live verifiziert)
- [x] Verbindung zum gespeicherten Fundament nur über sechs direkte Blockflächen prüfen, nicht diagonal und nicht über Außenbereiche. (NEIGHBOR_FACES + Gruppen-ID-Grenze)
- [x] Bei Verlust der Verbindung Punkte betroffener Komponenten abziehen und nach Wiederverbindung wiederherstellen. (disconnect-/reconnect-BFS, unit-getestet)
- [x] Punktestand immer aus den aktuell gültigen, noch existierenden und verbundenen Blöcken ableiten (kein kumulatives Farmen durch Abbau/Neusetzen). (inkrementelles Modell, kein Bestandsspeicher ausserhalb der getrackten Bloecke)
- [ ] Punktwerte aus `scoreboard-config.yml` übernehmen; geänderte Beispielwerte, Ausnahmen und Materialvarianten prüfen. (unveraendert seit 831e4c1 - noch kein systematischer Audit der aktuellen Tabelle gegen 26.3-Materialien)
- [x] Verbindungsprüfungen inkrementell/gezielt statt mit Vollscan bei jeder Platzierung umsetzen. (BFS nur ueber bereits getrackte Bloecke der betroffenen Teilstruktur, kein Bauplatz-Vollscan)

**Abnahme:** Haus mit 0-Punkte-Verbindungsblöcken funktioniert; Entfernen einer tragenden Verbindung reduziert Punkte und Wiederverbinden stellt sie genau einmal wieder her. ✅ Unit-getestet und live auf Spigot 26.3 verifiziert - siehe offene Punkte oben (Struktur-Trennung, Kolbenbewegung innerhalb des Bauplatzes, Materialtabellen-Audit) für den Rest von P0-3.

### 4. Persistenz und Absturzsicherheit
- [ ] Persistente Speicherung (z. B. SQLite) für Contest-Status, Welt-ID, Grundstücke, Fundamente, UUIDs, Tracking, Teams, Punkte und Timer festlegen. (bewusste Entscheidung: vorerst bei YAML/data.yml bleiben - deckt alle genannten Felder bereits ab; SQLite-Migration als eigene spaetere Aufgabe vorgemerkt)
- [x] Datenänderungen zuverlässig schreiben; nach Crash/Neustart Inkonsistenzen zwischen Welt und Tracking erkennen und beheben. (DataStore#reconcileTrackedBlocksWithWorld: getrackte Bloecke werden beim Laden gegen die tatsaechliche Welt geprueft, Phantom-Eintraege via ScoreManager#invalidate entfernt; unit-getestet)
- [ ] Serverneustart mit RUNNING und PAUSED testen; kein neuer Seed, keine verlorenen Punkte und keine ungewollt weiterlaufende Zeit. (RUNNING wiederholt live auf Spigot 26.3 bestaetigt - Welt/Scores/Timer ueberleben Neustart korrekt; PAUSED kann erst getestet werden, sobald die Zustandsmaschine aus P1-5 existiert)
- [x] Änderungen an spielentscheidenden Einstellungen während RUNNING/PAUSED sperren. (BuildContestCommand#requireNotRunning fuer setgroups/setgroupsize/setplotsize/setdistance/setscore/automode; /bc assign bleibt bewusst erlaubt, siehe rules.md#gruppen-und-handel; live verifiziert)
- [x] Abschluss-Ergebnisse und Statistiken unabhängig von der später gelöschten Welt speichern. (DataStore#appendResult schreibt den Endstand in eine eigene results.yml, unberuehrt von scoreManager.clear()/Weltloeschung; live verifiziert. Volle /bc results-Ausgabe ist P1-Scope)

**Abnahme:** Nach Neustart stimmen Welt, Plots, Mitglieder, Blöcke, Team-/Einzelpunkte und Restzeit. ✅ Fuer RUNNING live verifiziert; PAUSED folgt mit P1-5.

## P1 – Wichtige Spielfunktionen

### 5. Zustandsmaschine und sichere Administration
- [x] Zustände SETUP, LOBBY, PREPARING, RUNNING, PAUSED und FINISHED implementieren. (vereinfacht: SETUP/LOBBY/PREPARING als ein gemeinsamer "noch nicht gestartet"-Zustand, da das Plugin kein Verhalten unterschiedlich zwischen diesen drei gated - RUNNING/PAUSED/FINISHED vollstaendig abgebildet, siehe GameStateManager-Klassendoc)
- [x] `/bc pause` / `/bc resume`: Timer und wettbewerbsrelevante Spieleraktionen anhalten/fortsetzen. (GameStateManager#pause/resume mit korrekter Restzeit-Uebernahme, live verifiziert)
- [x] Pausenmodus so absichern, dass auch Farmen, Handel und indirekte Änderungen keine Wettbewerbsvorteile bringen. (BlockListener sperrt waehrend PAUSED die GESAMTE Contest-Welt fuer Platzierung/Abbau, nicht nur Bauplaetze - siehe rules.md: "kann die gesamte Contest-Welt eingefroren werden"; Handel/Item-Weitergabe zwischen Spielern bleibt eine bekannte Luecke, da es dafuer kein einzelnes Bukkit-Event gibt)
- [x] `/bc start` darf laufende oder pausierte Wettbewerbe nicht überschreiben. (isRunning() bleibt waehrend PAUSED true, bestehender Guard greift automatisch mit; live verifiziert)
- [x] `/bc end` friert Endstand und Bauplätze ein; Gleichstände als gemeinsame Sieger behandeln. (bereits vorhanden; keine explizite Rang-Nummerierung, dadurch automatisch kein Stichentscheid bei Gleichstand)
- [x] Ergebnishistorie sichern, **kein** Weltbackup anlegen (bewusste Entscheidung 11B). (P0-4, results.yml)
- [x] Löschen/Ersetzen einer abgeschlossenen Contest-Welt nur nach ausdrücklicher Adminbestätigung; keine versehentliche Löschung der Lobbywelt. (P0-1)
- [x] `game.auto-delete-world-on-end` standardmäßig deaktiviert; Besichtigung nach Spielende möglich. (bereits vorhanden, Default false)

**Abnahme:** Pause/Resume und Ende funktionieren nach Neustart; neue Contests löschen nie stillschweigend alte Bauwerke. ✅ Pause/Resume/Start-waehrend-PAUSED live auf Spigot 26.3 verifiziert; Persistenz des Pause-Zustands unit-getestet (Server-Neustart waehrend PAUSED noch nicht live mit echtem Neustart durchgespielt).

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

### 9. Schnellstart-Presets und Start-Steine
- [x] Preset-Familie `2x1`/`2x2`/`2x4`/`2xn` umsetzen (immer 2 Gruppen Rot/Blau); bisheriges `1x1`-Preset durch `2x1` ersetzt. (AutomodeManager-Umbau)
- [x] `2x1`: keine Ballwahl, erste zwei Beitretende ohne Gruppe werden zufällig Rot/Blau zugewiesen. (randomAssign, live verifiziert)
- [x] `2xn`: alle aktuell Anwesenden ohne Gruppe sofort zufällig halbieren, kein Ball, keine Wartephase. (activateInstantSplit)
- [x] `/bc placestartstones`: beschriftete Steine (einer je Preset) in der Eingangswelt an der Admin-Position platzieren. (Stein+Schild+PDC-Tag, live verifiziert)
- [x] Stein-Rechtsklick löst dasselbe Preset wie `/bc automode <preset>` aus, aber immer mit fester 30-Minuten-Spieldauer (unabhängig von `game.duration-minutes`); `/bc automode` durch den Admin bleibt bei der konfigurierten Dauer (Standard unbegrenzt). (GameStateManager#start(int)-Override, live verifiziert)
- [x] Nur ein Contest/eine Teamwahl gleichzeitig: Start-Steine während eines laufenden Contests/einer laufenden Teamwahl inaktiv, mit Hinweis an den Klickenden. (gameRunningOrSelectionInProgress, inkl. Fix: /bc end setzt haengen gebliebenen Wartezustand zurueck)
- [x] Status-Anzeige in der Eingangswelt (aktiv/bereit), die bei Contest-Start/-Ende aktualisiert wird. (festes TextDisplay an den Steinen; GameStateManager#setOnTimerEnd stellt sicher, dass auch ein natuerlicher Zeitablauf die Anzeige zuruecksetzt)
- [x] Eingangswelt-Gamemode auf Adventure (nicht Survival) setzen, bei Join und bei Rückkehr aus dem Contest. (PlayerConnectionListener, BuildContestCommand#teleportAllBackToEntryWorld, live verifiziert)
- [x] Contest-Welt-Gamemode auf Survival und Difficulty auf Normal setzen (Welt bei Erzeugung, Spieler beim Eintritt). (WorldManager#regenerate, EntryTeleportManager, live verifiziert)
- [x] Inventar beim erstmaligen Contest-Eintritt sichern und leeren; beim Verlassen (Rückkehr in die Eingangswelt) das gesicherte Inventar wiederherstellen (ersetzt, nicht zusammengeführt). (InventoryVaultManager, live verifiziert - nicht ueber Serverneustart persistiert, wie der Automode-Wartezustand)

**Abnahme:** Ohne Admin online lässt sich per Start-Stein ein 30-minütiger Contest beginnen; der Admin kann per Befehl weiterhin unbegrenzt spielen. Eingangswelt bleibt unveränderlich (Adventure), Contest-Welt ist Survival/Normal, Spieler starten mit leerem und enden mit ihrem alten Inventar. ✅ Vollständig live auf Spigot 26.3 verifiziert.

## P2 – Qualität und Dokumentation

### 10. Punktetabelle ausbalancieren
- [ ] Sämtliche konfigurierten Blockwerte nach Beschaffungs- und Verarbeitungsaufwand systematisch durchgehen.
- [ ] Glas-/Steinziegel-Werte sowie Eisen-, Gold-, Diamant- und Smaragdblöcke im Spiel testen.
- [ ] Blockform-Vererbung, Container-Ausnahmen (Shulker Box, Beacon, Conduit) und maximale 50 Punkte prüfen.
- [ ] Neue Materialnamen der Ziel-Minecraft-Version ergänzen; unbekannte Blöcke standardmäßig 0 Punkte.
- [ ] Mit Test-Contests prüfen, ob Farmstrategien oder bestimmte Crafting-Rezepte unverhältnismäßig viele Punkte geben.

### 11. Tests und Performance
- [ ] Unit-Tests für Score-Berechnung, Materialregeln, Verbindungsgraph und Teamzuordnung.
- [ ] Integrationstests für Platzieren/Abbauen, WorldEdit, Kolben, Wasser, Explosionen, Wachstum und Mob-Griefing.
- [ ] Multiplayer-Tests mit mehreren Teams, Besuchern, Handel und Gruppenwechseln.
- [ ] Lasttest mit großen Bauwerken, vielen Blockänderungen und Serverneustarts.
- [ ] Crash-/Recovery-Tests mit Datenbank und Weltzustand; Phantom- und Doppelzählungen ausschließen.
- [ ] Prüfen, dass Bauplatzsuche auch bei mehreren Gruppen ohne endlose Generierung endet und brauchbare Fehlermeldungen liefert.

### 12. Dokumentation und Veröffentlichung
- [ ] `README.md`, `ADMIN_GUIDE.md` und `PLAYER_GUIDE.md` an `rules.md` angleichen.
- [ ] Konfigurationsbeispiele, Permission-Nodes, Adminbestätigung und Spielerregeln dokumentieren.
- [ ] Unterschied zwischen **beschlossener Regel**, **implementierter Funktion** und **getesteter Funktion** sichtbar machen.
- [ ] Changelog und Release-/Testcheckliste pflegen.

## Definition of Done

Ein Punkt wird erst abgehakt, wenn die Funktion implementiert, gegen `rules.md` geprüft und sinnvoll getestet ist. Ein Meilenstein gilt erst als abgeschlossen, wenn seine Abnahmekriterien erfüllt sind. Insbesondere müssen die **gemeinsame Survival-Welt ohne Spielerteleport**, korrekt geschützte Grundstücke sowie persistente, manipulationssichere Punkte funktionieren.
