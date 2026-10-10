# Buildcontest – Regeln und technische Spezifikation

> **Status:** Zielregelwerk. Die beschriebenen Regeln sind Anforderungen an das Plugin; ihre Dokumentation bedeutet nicht, dass alle Funktionen bereits implementiert sind.

## 1. Ziel und Spielprinzip

Mehrere Gruppen treten in **einer gemeinsamen Survival-Welt** gegeneinander an. Jede Gruppe besitzt dort einen eigenen geschützten Bauplatz. Die Gruppen erkunden dieselbe Welt, sammeln Ressourcen, betreiben Farmen, handeln untereinander und bringen ihre Materialien **mit normalen Minecraft-Fortbewegungsmitteln** zu ihren Bauplätzen.

Es gibt **keine separate Farmwelt und keine separate Bauwelt**. Außerhalb der Bauplätze gelten grundsätzlich normale Survival-Regeln. Gewertet werden ausschließlich gültig von Spielern platzierte, noch vorhandene und mit dem Fundament verbundene Blöcke innerhalb des **eigenen** Bauplatzes. Die Gruppe mit den meisten Materialpunkten gewinnt; eine Jury- oder Kreativitätswertung gibt es nicht.

## 2. Welten und Fortbewegung

- Die **Contest-Welt ist zugleich die gemeinsame Survival-Welt** mit sämtlichen Bauplätzen. Das Plugin erstellt beim Beginn eines **neuen** Contests eine frische Survival-Welt mit zufälligem Seed; bei Wiederaufnahme eines bestehenden Contests wird dessen Welt weiterverwendet.
- Eine separate **Eingangs-/Lobbywelt** darf es für die Vorbereitung geben. Sie ist nicht die Farm- oder Bauwelt, wird nicht vom Contest-Neustart gelöscht und wird durch den Admin verwaltet.
- Beim ersten Eintritt in den Contest dürfen Teilnehmer **einmalig** an einen sicheren Startpunkt bei ihrem Bauplatz gebracht werden, bevor die Wettkampfzeit läuft. Danach gibt es **keine Spieler-Teleports durch Buildcontest**: kein `/bc home`, `/bc plot`, `/bc survival` oder `/bc lobby` während RUNNING oder PAUSED. Die normale Fortbewegung zu Fuß, per Boot, Pferd, Minecart, Nether-Reise usw. ist Teil des Spiels.
- Bei Logout, Tod und Wiederanmeldung gelten grundsätzlich normale Minecraft-Mechaniken. Buildcontest darf dabei keine Abkürzung zum Bauplatz schaffen; insbesondere darf ein Wiedereinstieg nicht automatisch dorthin teleportieren.
- Administratoren können unabhängig davon ihre üblichen Moderations-/Rettungswerkzeuge verwenden; dies ist keine Spielerfunktion des Wettbewerbs.
- Auch während RUNNING dürfen Gruppen außerhalb der Grundstücke Wege, Brücken, Minen, Farmen und andere Survival-Bauten errichten. Dafür gibt es keine Contestpunkte.

## 3. Vorbereitung und Bauplätze

Vor Spielstart sind konfigurierbar:

- Gruppenanzahl (Standard: 2) und maximale Gruppengröße (beispielsweise 5).
- Bauplatzgröße (Standard: **60 × 60 Blöcke**).
- Mindest-/Maximalabstand der Grundstücke (Standard: **200–500 Blöcke**); maßgeblich ist der kürzeste horizontale Abstand zwischen Grundstücksgrenzen.
- Optionale Wettbewerbsdauer.
- Blockwerte aus [`scoreboard-config.yml`](./scoreboard-config.yml).
- Optionale Festlegung von Gruppen und Startpositionen.

Für jede Gruppe wird genau ein rechteckiger Bauplatz im natürlich generierten Gelände ausgewählt. Die Plätze liegen an geeigneten **trockenen** Standorten und werden jeweils **lokal** auf eine passende, einheitliche Höhe planiert. **Verschiedene Bauplätze dürfen unterschiedliche Y-Level haben.** Dadurch muss nicht ein großes gemeinsames Gebiet auf derselben Höhe liegen. Wasserflächen und extreme Geländesprünge sind als Grundstücke ungeeignet; Hohlräume direkt unter dem geplanten Fundament müssen sicher verfüllt oder stabilisiert werden. Die jeweiligen Originalhöhen und die neu geschaffene Fundamentfläche werden gespeichert.

Die Wertungsgrenze ist die rechteckige X/Z-Grundfläche des eigenen Bauplatzes. Vertikal gelten die normalen Unter- und Obergrenzen der Welt; außerhalb der X/Z-Grenze gibt es niemals Punkte.

**Vorhandene Weltblöcke** (einschließlich planiertem Fundament und natürlich vorkommenden Erzen) erhalten nie automatisch Punkte. Die Geländevorbereitung erfolgt vor der Wertung.

## 4. Gruppen und Handel

Pro Contest wird genau **ein** Beitrittsmodus festgelegt:

1. **Freie Wahl:** Spieler verwenden `/bc join <gruppe>`, solange Plätze frei sind.
2. **Festzuweisung:** Ein Admin weist Spieler per Konfiguration oder `/bc assign` zu; normale Spieler können die Zuweisung nicht ändern.

Die dauerhafte Spieleridentität ist die **Minecraft-UUID**, der angezeigte Spielername dient nur zur Darstellung und Auflösung von Admin-Eingaben. Nach Contestbeginn sind Gruppenwechsel grundsätzlich gesperrt; ausschließlich ein Admin kann sie ausdrücklich durchführen. Dabei bleiben bereits registrierte Punkte und Blöcke der bisherigen Gruppe zugeordnet, damit kein Team Punkte durch einen Wechsel verliert oder erhält.

**Handel und Materialtausch zwischen Gruppen sind erlaubt.** Spieler können Ressourcen freiwillig austauschen. Bauplätze fremder Gruppen sind dennoch geschützt; ein Handel gewährt keine fremden Baurechte oder Containerrechte.

## 5. Bauregeln und Verbindungsprüfung

- Punkte erhalten nur **gültige Spielerplatzierungen** auf dem eigenen Bauplatz, bei denen der resultierende Block weiterhin vorhanden ist und den zugehörigen aktuellen Materialtyp aufweist.
- Ein Block darf nur dann Punkte liefern, wenn er über eine Kette aus benachbarten **verbindenden Blöcken** mit der gespeicherten Bauplatz-Fundamentfläche verbunden ist.
- Als Nachbarn zählen die **sechs gemeinsamen Blockflächen** (±X, ±Y, ±Z), nicht diagonale Kontakte.
- Auch Materialtypen mit **0 Punkten** dürfen Teil einer Verbindung sein, beispielsweise Glas oder andere Dekoration. **Wertbar** und **verbindend** sind also unabhängige Eigenschaften.
- Standardmäßig gelten platzierte feste Blöcke als verbindend; Flüssigkeiten, Luft, Feuer und ausdrücklich ausgeschlossene, nicht tragfähige Zustände verbinden nicht. Die verbindenden Materialien sollen konfigurierbar sein. Das gespeicherte Grundstücksfundament ist ein Anker, selbst wenn dessen Blöcke 0 Punkte bringen.
- Ein wertbarer, aber momentan unverbundener Block zählt **0**, bis wieder eine Verbindung zum Fundament besteht. Unverbundene Platzierungen müssen deshalb nicht abgelehnt werden.
- Nach Platzierung, Abbau, Zerstörung oder erlaubter Blockzustandsänderung werden betroffene Verbindungen aktualisiert. Beim Entfernen eines tragenden Blocks verlieren alle dadurch getrennten Blöcke ihre Punkte; bei Wiederverbindung werden die Punkte zurückgewonnen, **ohne** dass ein erneutes Platzieren erforderlich ist.
- Für die Verbindung dürfen auch 0-Punkte-Blöcke auf dem eigenen Bauplatz berücksichtigt werden; außerhalb des Bauplatzes laufende Strukturen zählen **nicht** als Verbindung.

Die Implementierung soll nicht bei jeder Platzierung den ganzen Bauplatz per Flood-Fill neu durchsuchen. Besonders beim Entfernen von Verbindungen ist eine gezielte Neuprüfung betroffener Komponenten nötig.

## 6. Punktesystem

Punkte sollen den ungefähren **Beschaffungs- und Verarbeitungsaufwand** besser widerspiegeln. Die vollständige, verbindliche Blocktabelle befindet sich in [`scoreboard-config.yml`](./scoreboard-config.yml), nicht in dieser Beispielübersicht. Die bisherige grobe Tier-Einteilung dient weiter der Orientierung; **einzelne Materialien dürfen abgestufte Zwischenwerte** erhalten.

| Material / Beispiel | Punkte |
|---|---:|
| Erde, Stein, normales Holz | 1 |
| Steinziegel | 2 |
| Glas | 2 |
| Beton | 3 |
| Eisenblock | 10 |
| Goldblock | 20 |
| Diamantblock | 40 |
| Smaragdblock | 40 |
| Netheritblock | 50 (Obergrenze) |

Weitere Grundsätze:

- **Maximal 50 Punkte pro Block.** Der Maximalwert gilt auch für ausdrücklich ausgezeichnete seltene Endgame-Blöcke.
- Roh-Erze und kompakte Blöcke werden getrennt bewertet; die höhere Materialmenge eines kompakten Blocks soll stärker berücksichtigt werden.
- Materialvarianten (Treppen, Stufen, Wände, Zäune, Türen usw.) übernehmen ihren Wert **nur gemäß den expliziten Vererbungs- und Ausnahmeregeln** der Konfiguration. Funktionale Varianten wie Knöpfe, Schilder und Druckplatten bleiben 0.
- Funktionale Container zählen normalerweise 0. **Shulker-Boxen**, **Beacon** und **Conduit** sind ausdrücklich begründete Ausnahmen.
- Nicht konfigurierte Materialien haben `default_score: 0`. Für Minecraft-Versionswechsel müssen neue Blocktypen geprüft werden.
- Die Wertung ist **nicht kumulativ**: Sie entspricht der Summe aktuell stehender, gültig platzierter und verbundener Blöcke. Wird ein Block entfernt, ersetzt oder ungültig, entfällt sein Wert. Reines Wiederholen von Platzierung und Abbau erzeugt keinen dauerhaften Gewinn.
- Änderungen an Punktwerten während RUNNING/PAUSED sind gesperrt; eine Änderung zwischen Wettbewerben darf nicht rückwirkend einen abgeschlossenen Endstand verändern.

Die derzeitigen Werte sind eine **überarbeitete Startkonfiguration** und sollten anhand von Test-Contests weiter ausbalanciert werden. Die Aktualisierung der Tabelle allein implementiert noch keine neue Wertungslogik.

## 7. Manipulationssichere Blockerfassung

Punkte werden **nicht** aus sämtlichen vorhandenen Weltblöcken rekonstruiert. Für jeden gültig gesetzten Block werden mindestens Contest-ID, Welt, Koordinaten, Material, Eigentümer-UUID, Gruppen-ID und Wertungsstatus gespeichert.

- Nur normale, zugelassene **Spielerplatzierungen** können neue Wertungsdatensätze erstellen.
- Natürlich generierte Blöcke, WorldEdit- und sonstige Plugin-/Command-Änderungen, Enderman-Platzierungen, automatisch generierter Cobblestone usw. erzeugen **keine neuen** Punktedatensätze.
- Kolbenbewegungen, Explosionen, Flüssigkeiten, Wachstum und Blocktransformationen dürfen weder Datensätze duplizieren noch bestehende Punkte trotz geänderter Weltlage fälschlich erhalten. Änderungen werden unterbunden oder korrekt mit dem Tracking abgeglichen.
- Ein einmal registrierter Block darf nach einem Austausch nicht unter falschem Material oder falschem Platzierer weiterzählen.
- Die **tatsächliche Welt** und die gespeicherten Metadaten müssen konsistent gehalten werden; nach Absturz/Neustart ist eine Wiederherstellung beziehungsweise Validierung vorzusehen.

Technische Trennung: **Block-Tracking** registriert Herkunft, Bestand und Verbindungsstatus; **Scoring** berechnet daraus die Wertung anhand der konfigurierten Tabelle.

## 8. Persönliche Beiträge und Statistiken

- Für jeden Spieler zeigt das System dessen **persönlichen Bau-Beitrag** an: Summe der aktuell gültigen und verbundenen, von diesem Spieler gesetzten Punkteblöcke auf dem zugeordneten Teamgrundstück.
- Entfernt ein beliebiges Teammitglied einen Block, verliert **der ursprüngliche Platzierer** den Beitrag. Ersetzt jemand einen Block, gehört der neue Platzier-Beitrag dem neuen Platzierer.
- Die Summe der aktuellen persönlichen Bau-Beiträge einer Gruppe ist gleich deren aktuellem Gruppenscore, auch nach einem administrativen Teamwechsel; die frühere Teamzuordnung der gesetzten Blöcke bleibt maßgeblich.
- Separat werden **Aktivitätsstatistiken** erfasst, beispielsweise abgebaute Ressourcen, gültige Platzierungen und aktive Spielzeit. Diese Werte sind **informativ und geben keine zusätzlichen Gruppenpunkte**. Beim Ressourcenabbau muss doppeltes Zählen durch wiederholtes Setzen und Abbauen vermieden werden; eine solche Statistik ist nicht automatisch ein verlässliches Maß der Teamleistung.

## 9. Anzeige

Jeder Teilnehmer sieht ein Live-Sidebar-Scoreboard mit:

- Verbleibender Wettkampfzeit (bei unbegrenzter Dauer entsprechend „ohne Zeitlimit“).
- Punktestand **aller Gruppen** in Rangfolge, bei Gleichstand mit gleichem Rang.
- Eigener Gruppe und eigenem aktuellem Bau-Beitrag.

Weiterführende Aktivitätsstatistiken sind über `/bc stats` abrufbar. Die Anzeige soll performant aktualisiert werden und Scoreboard-Einträge bei großen Gruppenanzahlen sinnvoll begrenzen. Meldungen zu Führungswechseln dürfen optional und gedrosselt angezeigt werden.

## 10. Schutzmechanismen

**Fremde Gruppen dürfen Bauplätze betreten und besichtigen**, dort jedoch keine Blöcke setzen/abbauen/überbauen, keine fremden Container öffnen und keine geschützten Objekte verändern. Teams besitzen innerhalb ihres eigenen Grundstücks gemeinsame Baurechte; zwischen Mitgliedern desselben Teams besteht kein privater Blockschutz.

Der Schutz muss auch **indirekte Schäden** zuverlässig abwehren, insbesondere:

- Explosionen (TNT, Creeper, Wither, Betten usw.), Feuer und Brandausbreitung.
- Wasser- und Lavafluss über Grundstücksgrenzen, unzulässige Flüssigkeitsänderungen.
- Kolben und bewegte Blöcke über Grenzen.
- Enderman-/Mob-Griefing, Entity-Interaktionen und vergleichbare Blockänderungen.
- Hopper, Hopper-Minecarts und andere Wege zum Zugriff auf fremde Container/Items.
- Wachstum, Blockphysik und Änderungen durch andere Plugins, soweit sie das geschützte Grundstück treffen.

Der Schutz gilt auch gegen Schäden **innerhalb** des eigenen Grundstücks durch Explosionen, Feuer oder unkontrollierte Mechaniken. Legitime manuelle Änderungen durch eigene Teammitglieder bleiben möglich. Außerhalb der Grundstücke bleiben die normalen Survival-Regeln maßgeblich.

## 11. Ablauf, Unterbrechung und Spielende

Das System hat folgende persistente Zustände:

| Zustand | Bedeutung |
|---|---|
| SETUP | Admin stellt Regeln und Teams ein |
| LOBBY | Teilnehmer melden sich an / wählen ihre Gruppe |
| PREPARING | Gemeinsame Survival-Welt und Bauplätze werden vorbereitet |
| RUNNING | Spiel und Wertung laufen |
| PAUSED | Admin hat den Contest angehalten; keine neuen Punkte oder Aktivitätsfortschritte, Timer steht |
| FINISHED | Endstand eingefroren, Bauten dürfen besichtigt werden |

- `/bc start` startet **einen neuen** Contest aus SETUP/LOBBY nach erfolgreicher Vorbereitung. Ein bereits laufender oder pausierter Contest darf damit **nicht** überschrieben werden.
- Die konfigurierte Spieldauer läuft nur während RUNNING. `/bc pause` stoppt den Timer und die wettbewerbsrelevanten Aktionen; `/bc resume` setzt denselben Contest mit allen Punkten und derselben Welt fort.
- Während PAUSED sind Blockplatzierung/-abbau auf Bauplätzen und wettbewerbsrelevantes Farmen/Handeln zu unterbinden, damit keine Gruppe unfaire Vorteile erhält. Technisch kann dafür die gesamte Contest-Welt gegenüber Spieleraktionen eingefroren werden; Admin-Wartungsmaßnahmen bleiben möglich.
- Nach Serverneustart muss der vollständige Zustand inklusive **Welt, Teamzuordnung, Block-Tracking, Punkten, Timer und Pause-Status** wiederhergestellt werden. Der Timer darf während des Offline-Zustands nicht ungewollt weiterlaufen.
- Bei Ablauf der Zeit oder `/bc end` wird FINISHED erreicht: Endstand und alle punktrelevanten Daten werden unveränderlich gespeichert. Die Bauplätze bleiben zum Anschauen erhalten, dürfen von Spielern aber nicht mehr verändert werden.
- Bei gleicher Punktzahl gibt es **gleichplatzierte Gewinner**, keinen willkürlichen Stichentscheid.
- **Nur Ergebnisse und Statistiken werden archiviert, kein Weltbackup.** Die abgeschlossene Contest-Welt kann bis zum nächsten Wettbewerb zur Besichtigung verbleiben, wird aber bei einem bewusst gestarteten neuen Contest ersetzt.
- Vor dem Ersetzen der bisherigen Welt muss der Admin ausdrücklich bestätigen, dass deren Bauwerke **nicht** erhalten bleiben. `/bc deleteworld` erfordert ebenfalls eine ausdrückliche Bestätigung. Die Lobby-/Eingangswelt darf nie gelöscht werden.
- Die alte Option `game.auto-delete-world-on-end` ist **standardmäßig deaktiviert**; eine automatische Löschung unmittelbar nach `/bc end` widerspräche der gewünschten Besichtigungsphase und soll nicht ohne explizite Admin-Entscheidung passieren.

## 12. Datenhaltung und Zuverlässigkeit

Contest-ID, Team-/Spieler-UUIDs, Plot-Koordinaten, Fundamentanker, einzelne wertbare Blockeinträge, Aktivitätsstatistiken, Endstände und Zustands-/Timerdaten sind persistent abzulegen (z. B. SQLite).

- Punktänderungen werden effizient ereignisbasiert verarbeitet; teure Welt-Scans werden vermieden.
- Speicherung und Weltänderung sind möglichst ausfallsicher zu koordinieren. Nach Crash/Reboot darf das Plugin keine Phantom-Punkte vergeben.
- Admin-`reload` darf im aktiven Wettbewerb keine spielentscheidenden Regeln unbemerkt verändern.
- Konfiguration, Plugin-Implementierung und Minecraft-Materialnamen müssen zur eingesetzten Serverversion passen.
- Die gespeicherten Resultate bleiben auch nach dem Löschen der Contest-Welt lesbar.

## 13. Befehle und Berechtigungen

**Spielerbefehle:**

| Befehl | Bedeutung |
|---|---|
| `/bc help` | Hilfe und erlaubte Befehle |
| `/bc status` | Contestphase und Restzeit |
| `/bc teams` | Gruppenübersicht |
| `/bc join <gruppe>` | Gruppe in Modus „freie Wahl“ auswählen, vor Start |
| `/bc leave` | Gruppe nur vor Start verlassen, sofern frei wählbar |
| `/bc top` | Rangliste |
| `/bc stats [spieler]` | Bau-Beitrag und separate Aktivitätsstatistiken |
| `/bc time` | Restzeit |

**Adminbefehle:**

| Befehl | Bedeutung |
|---|---|
| `/bc setgroups <n>` | Gruppenanzahl |
| `/bc setgroupsize <n>` | Gruppengröße |
| `/bc setplotsize <x> <z>` | Plot-Größe |
| `/bc setdistance <min> <max>` | Plotabstände |
| `/bc setscore <block> <punkte>` | Materialwertung (nur vor Start) |
| `/bc assign <spieler> <gruppe>` | Festzuweisung/Adminwechsel |
| `/bc start` | Neuen Contest beginnen; bestehenden nicht überschreiben |
| `/bc pause` / `/bc resume` | Aktiven Contest pausieren / fortsetzen |
| `/bc end` | Contest beenden |
| `/bc results` | Gespeicherte Ergebnisse ansehen |
| `/bc deleteworld` | Abgeschlossene Contest-Welt nach Bestätigung löschen |
| `/bc reload` | Zulässige Konfiguration neu laden |

Alle Adminbefehle benötigen gesonderte Permissions. **Spieler-Teleportbefehle gehören bewusst nicht zum Contest.**

## 14. Umsetzungshinweis

Dieses Dokument beschreibt die **vereinbarten Spielregeln und die gewünschte Zielimplementierung**. Noch nicht implementierte Funktionen müssen in der Entwicklung ergänzt und getestet werden. Besonders kritisch sind: Welt-Lebenszyklus ohne Datenverlust, indirekter Plot-Schutz, persistente Zustandsmaschine, korrektes Block-Tracking samt Verbindungsprüfung und die Leistungsfähigkeit der Punkteberechnung.
