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

- Jeder Blocktyp hat einen Punktwert, der sich am Beschaffungsaufwand orientiert (seltener/aufwendiger zu farmen = mehr Punkte). Die Tabelle ist admin-konfigurierbar; Beispiel:

  | Block | Punkte |
  |---|---|
  | Stein / Erdblöcke | 1 |
  | Holz / Sandstein | 2 |
  | Eisenblock | 10 |
  | Goldblock | 15 |
  | Diamantblock | 50 |
  | Netheritblock | 100 |

- Der Punktestand einer Gruppe entspricht der Summe der Punktwerte **aktuell stehender** gewerteter Blöcke auf ihrem Bauplatz – nicht einer kumulativen Zählung aller jemals platzierten Blöcke.
- Wird ein gewerteter Block abgebaut, wird sein Punktwert wieder vom Gruppen-Score abgezogen. Das verhindert, dass durch wiederholtes Platzieren/Abbauen desselben Blocks unbegrenzt Punkte erzeugt werden.
- Der Punktestand aller Gruppen wird live in einem Scoreboard angezeigt (sortiert nach Rang).

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
