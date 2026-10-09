# Buildcontest – Regeln

## Ziel

Mehrere Gruppen spielen Survival und wetteifern darum, auf ihrem eigenen Bauplatz möglichst viele Punkte durch das Verbauen von Blöcken zu sammeln. Ressourcen werden in der gemeinsamen Survival-Welt gefarmt, verbaut wird ausschließlich auf dem eigenen Bauplatz.

## Setup (Admin)

Vor Spielstart konfiguriert ein Admin:

- Anzahl der Gruppen (Standard: 2)
- Maximale Gruppengröße (Standard: konfigurierbar, z. B. 5 Spieler pro Gruppe)
- Bauplatzgröße (Standard: 60×60 Blöcke – deutlich kleiner als ursprünglich angedacht, da 300×400 für ein zeitlich begrenztes Event zu groß ist; für Langzeit-Server-Projekte kann der Wert erhöht werden)
- Mindest-/Maximalabstand zwischen Bauplätzen (200–500 Blöcke)
- Spieldauer (optional, siehe „Spielablauf & Ende")
- Die Block-Punktetabelle (siehe „Punktesystem")

Bauplätze werden automatisch im Weltraster verteilt, flach planiert (Vegetation entfernt, Oberfläche auf ein einheitliches Y-Level gebracht) und als rechteckige Grundfläche festgelegt.

## Gruppen

- Jede Gruppe hat eine admin-konfigurierbare **maximale Gruppengröße**.
- Die Zuweisung von Spielern zu Gruppen erfolgt auf **genau eine** der folgenden Arten (nicht gemischt, Admin legt sich pro Event fest):
  1. **Freie Wahl:** Spieler wählen per Befehl `/bc join <gruppe>` selbst, solange die Gruppe noch nicht voll ist.
  2. **Configfile:** Ein Admin trägt Minecraft-Namen fest einer Gruppe zu; Spieler können ihre Gruppe in diesem Modus nicht selbst wählen oder wechseln.

## Bauplätze

- Pro Gruppe gibt es genau einen rechteckigen, flach planierten Bauplatz.
- Bauplätze liegen 200–500 Blöcke voneinander entfernt.
- Die Grundfläche (X/Z) ist die alleinige Grenze für die Wertung – siehe „Bauregeln".

## Bauregeln

- Gewertet werden nur Blöcke, deren X/Z-Position innerhalb der eigenen Bauplatz-Grundfläche liegt. Nach oben (Y) gibt es kein Limit außer der Welthöhengrenze.
- Verbaute (gewertete) Blöcke müssen zusammenhängend sein und über andere gewertete Blöcke mit der planierten Bauplatz-Oberfläche verbunden sein (geprüft per Flood-Fill beim Platzieren, ausgehend von der beim Planieren gespeicherten Höhenkarte). Nicht verbundene Platzierungen werden abgelehnt oder zählen mit 0 Punkten.
- Nicht gewertete Blöcke (reine Deko, z. B. Glas, Blumen o. ä., sofern nicht in der Punktetabelle enthalten) dürfen frei platziert werden, geben aber 0 Punkte.

## Punktesystem

Jeder Blocktyp hat einen Punktwert, der sich am Beschaffungs-/Verarbeitungsaufwand orientiert (seltener/aufwendiger zu farmen = mehr Punkte). Die vollständige, admin-konfigurierbare Tabelle liegt in [`scoreboard-config.yml`](./scoreboard-config.yml). Sie folgt einem siebenstufigen, grob verdoppelnden Tier-System:

| Tier | Punkte | Beispiele |
|---|---|---|
| T0 | 0 | Funktionale Blöcke (Redstone-Komponenten, Workstations, Container, Rails) und reine Dekoration (Teppich, Banner, Schild, Laub). Zählen grundsätzlich nicht, unabhängig vom Materialwert – Ausnahme: Beacon/Conduit (siehe T6) |
| T1 | 1 | Erde, Sand, Stein, Holz, Wolle, Terracotta, Coal/Copper-Erz |
| T2 | 3 | Steinziegel, Beton, Glas, Quarzblock, Kupfer-Block, Coal-Block, Obsidian |
| T3 | 6 | Eisenerz/-block, Redstone-/Lapis-Erz/-block, Prismarine, Amethystblock |
| T4 | 12 | Golderz/-block, Diamant-/Smaragderz (unverarbeitet), Froglight |
| T5 | 25 | Diamantblock, Smaragdblock, Ancient Debris |
| T6 (Cap) | 50 | Netheritblock, Beacon, Conduit, Dragon Egg, Shulker Box – bewusst gedeckelt, keine weitere Eskalation untereinander |

Zusätzliche Regeln:

- Natürliche Erz-Rohblöcke (z. B. Diamanterz direkt aus dem Abbau) zählen niedriger als der daraus hergestellte kompakte Block (z. B. Diamantblock) – weniger Aufwand als 9 Einheiten zu sammeln und zu verarbeiten.
- Formvarianten (Stufen, Treppen, Wände, Zäune, Tore, Türen, Fallen) erben automatisch den Punktwert ihres Grundmaterials. Rein funktionale/informative Varianten (Knöpfe, Druckplatten, Schilder) bleiben immer bei 0 Punkten.
- Alles, was nicht in `scoreboard-config.yml` gelistet ist, zählt automatisch 0 Punkte (`default_score`).
- Der Punktestand einer Gruppe entspricht der Summe der Punktwerte **aktuell stehender** gewerteter Blöcke auf ihrem Bauplatz – nicht einer kumulativen Zählung aller jemals platzierten Blöcke.
- Wird ein gewerteter Block abgebaut, wird sein Punktwert wieder vom Gruppen-Score abgezogen. Das verhindert, dass durch wiederholtes Platzieren/Abbauen desselben Blocks unbegrenzt Punkte erzeugt werden.

## Scoreboard-Anzeige

Jeder Spieler sieht permanent ein Sidebar-Scoreboard mit zwei Informationen:

- **Punktestand aller Gruppen**, sortiert nach Rang – für jeden Spieler identisch, unabhängig von der eigenen Gruppe.
- **Eigener Beitrag**: wie viele der Punkte seiner Gruppe der Spieler selbst erzielt hat.

Dazu wird pro platziertem gewerteten Block gespeichert, welcher Spieler ihn platziert hat. Der persönliche Beitrag entspricht – analog zum Gruppen-Score – der Summe der Punktwerte der **aktuell stehenden** Blöcke, die dieser Spieler selbst platziert hat:

- Baut irgendjemand einen gewerteten Block ab, verliert **der ursprüngliche Platzierer** diesen Punktwert aus seinem persönlichen Beitrag (nicht der Abbauende).
- Daraus folgt die Invariante: Summe der persönlichen Beiträge aller Mitglieder einer Gruppe = Gruppen-Score.
- Platziert ein Teammitglied an derselben Stelle einen neuen Block, geht der Beitrag auf diesen neuen Platzierer über. Da Teammitglieder sich gegenseitig nicht vor Abbau geschützt sind (siehe „Schutzmechanismen"), kann so theoretisch Beitrag zwischen Mitgliedern „umverteilt" werden – das ist eine bewusst akzeptierte Einschränkung des bestehenden Vertrauensmodells innerhalb der Gruppe, kein Exploit gegenüber anderen Gruppen.

## Schutzmechanismen

- Blöcke auf einem Bauplatz können nicht von Spielern anderer Gruppen abgebaut oder überbaut werden.
- Explosionen (TNT, Creeper, Betten etc.) und Feuerausbreitung werden an den Bauplatzgrenzen blockiert, damit Umgebungsschäden nicht auf fremde (oder eigene, bereits gewertete) Bauten übergreifen.
- Innerhalb der eigenen Gruppe haben alle Mitglieder vollen Zugriff auf den eigenen Bauplatz (kein Schutz zwischen Teammitgliedern).

## Spielablauf & Ende

- `/bc start` beginnt den Contest.
- Der Admin kann optional eine feste Spieldauer konfigurieren, nach deren Ablauf der Contest automatisch endet.
- Unabhängig davon kann der Admin den Contest jederzeit vorzeitig per `/bc end` beenden.
- Nach dem Ende wird der Bauplatz eingefroren (kein weiteres Platzieren/Abbauen gewerteter Blöcke mehr möglich) und der Endstand sowie die Gewinnergruppe werden bekanntgegeben.

## Admin-Befehle (Übersicht)

- `/bc setgroups <n>` – Anzahl der Gruppen festlegen
- `/bc setgroupsize <n>` – maximale Gruppengröße festlegen
- `/bc setplotsize <x> <z>` – Bauplatzgröße festlegen
- `/bc setdistance <min> <max>` – Mindest-/Maximalabstand zwischen Bauplätzen
- `/bc setscore <block> <punkte>` – Punktwert für einen Blocktyp setzen
- `/bc assign <spieler> <gruppe>` – Spieler per Configfile/Befehl einer Gruppe zuweisen
- `/bc start` / `/bc end` – Contest starten/beenden
- `/bc reload` – Konfiguration neu laden
