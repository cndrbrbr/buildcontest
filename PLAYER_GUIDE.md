# Buildcontest – Spieler-Anleitung

Kurze Einführung für Spieler. Die vollständigen Regeln stehen in [`rules.md`](./rules.md), die
genaue Punktetabelle in [`scoreboard-config.yml`](./scoreboard-config.yml).

## Ziel

Du spielst mit deiner Gruppe Survival und sammelt Punkte, indem ihr Blöcke auf eurem **eigenen
Bauplatz** verbaut. Ressourcen farmt ihr in der gemeinsamen Welt, gebaut wird aber ausschließlich
auf dem eigenen Bauplatz.

## Gruppe beitreten

Je nach Server-Einstellung gibt es zwei Modi (euer Admin sagt euch, welcher aktiv ist):

- **Freie Wahl**: `/bc join <gruppennummer>`, solange die Gruppe noch nicht voll ist. Ihr könnt
  danach nicht mehr selbst wechseln.
- **Feste Zuweisung**: Der Admin trägt euch per Konfiguration oder `/bc assign` einer Gruppe zu –
  hier müsst ihr nichts selbst tun.

## Bauen

- Euer Bauplatz ist eine rechteckige, flach planierte Fläche. Nach oben (Y) gibt es **kein
  Limit**.
- Gewertet werden nur Blöcke, deren X/Z-Position innerhalb eurer Bauplatz-Grundfläche liegt.
- Verbaute Blöcke müssen **zusammenhängend** sein und über andere gewertete Blöcke mit der
  planierten Oberfläche verbunden sein. Schwebende/unverbundene Platzierungen werden abgelehnt.
- Reine Deko (Glas, Blumen, Redstone-Komponenten, Container, …) dürft ihr frei platzieren, bringt
  aber 0 Punkte.
- Baut ihr einen gewerteten Block ab, verliert ihr seinen Punktwert wieder – auch wenn ein
  anderes Gruppenmitglied ihn gebaut hat.

## Punkte

Jeder Blocktyp ist nach Beschaffungsaufwand gestaffelt (grob verdoppelnd):

| Tier | Punkte | Beispiele |
|---|---|---|
| T0 | 0 | Funktionale Blöcke & reine Deko |
| T1 | 1 | Erde, Sand, Stein, Holz, Wolle, Terracotta |
| T2 | 3 | Steinziegel, Beton, Glas, Kupferblock |
| T3 | 6 | Eisen-/Redstone-/Lapisblock, Prismarine |
| T4 | 12 | Gold-/Diamant-/Smaragderz |
| T5 | 25 | Diamant-/Smaragdblock |
| T6 | 50 | Netherit, Beacon, Conduit, Dragon Egg (gedeckelt) |

Formvarianten (Treppen, Stufen, Zäune, Türen, …) zählen wie ihr Grundmaterial. Die vollständige
Liste steht in [`scoreboard-config.yml`](./scoreboard-config.yml).

## Scoreboard

Rechts seht ihr dauerhaft:

- **Punktestand aller Gruppen**, sortiert nach Rang – für alle Spieler gleich.
- **Euer eigener Beitrag**: wie viele der Gruppenpunkte ihr persönlich beigesteuert habt.

## Schutz

- Niemand aus einer anderen Gruppe kann auf eurem Bauplatz bauen oder abbauen.
- Explosionen (TNT, Creeper, Betten, Respawn-Anker) und Feuer greifen nicht auf Bauplätze über.
- Innerhalb der eigenen Gruppe gibt es **keinen** Schutz untereinander – vertraut euren
  Mitspielern.

## Spielende

Der Admin beendet den Contest per `/bc end` (oder automatisch nach Ablauf der Spielzeit). Danach
ist euer Bauplatz eingefroren, der Endstand und die Gewinnergruppe werden im Chat bekanntgegeben.
