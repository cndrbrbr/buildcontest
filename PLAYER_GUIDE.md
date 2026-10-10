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

Sobald ihr einer Gruppe zugeordnet seid und der Contest läuft, bringt euch das Plugin **einmalig**
automatisch zu einem sicheren Startpunkt bei eurem Bauplatz. Danach gibt es bewusst **keine**
weiteren Teleport-Befehle mehr – zu eurem Bauplatz (oder woanders hin) kommt ihr zu Fuß, per Boot,
Pferd, Minecart usw., ganz normales Survival-Gameplay.

**Im Automatikmodus** (Rot gegen Blau) läuft das anders: Ihr bekommt in der Eingangswelt einen
roten und einen blauen Wolle-"Ball" ins Inventar. Benutzt den Ball eurer Wahl (Rechtsklick), um dem
jeweiligen Team beizutreten (nur möglich, solange das Team noch nicht voll ist). Eine schwebende
Tafel in der Eingangswelt zeigt live, wer schon in welchem Team ist. Sobald beide Teams voll sind,
startet der Contest automatisch.

**Im Free-for-all-Automatikmodus** (`/bc automode 1x1`) bekommt jeder online Spieler sofort seine
eigene Gruppe mit eigenem Bauplatz – ihr müsst nichts wählen, der Contest startet direkt.

## Bauen

- Euer Bauplatz ist eine rechteckige, flach planierte Fläche auf eurer eigenen, lokal passenden
  Höhe – andere Gruppen können auf einer anderen Höhe liegen. Nach oben (Y) gibt es **kein
  Limit**.
- Direkt neben eurem Bauplatz markiert ein leuchtender Beacon die Position – so findet ihr euren
  Bauplatz auch von weitem wieder.
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

- **Punktestand aller Gruppen**, sortiert nach Rang – für alle Spieler gleich. Die aktuell
  führende Gruppe ist zusätzlich mit einem Stern markiert.
- **Euer eigener Beitrag**: wie viele der Gruppenpunkte ihr persönlich beigesteuert habt.

## Schutz

- Niemand aus einer anderen Gruppe kann auf eurem Bauplatz bauen oder abbauen.
- Explosionen (TNT, Creeper, Betten, Respawn-Anker) und Feuer greifen nicht auf Bauplätze über.
- Innerhalb der eigenen Gruppe gibt es **keinen** Schutz untereinander – vertraut euren
  Mitspielern.

## Spielende

Der Admin beendet den Contest per `/bc end` (oder automatisch nach Ablauf der Spielzeit). Danach
ist euer Bauplatz eingefroren, der Endstand wird im Chat bekanntgegeben und die Gewinnergruppe
zusätzlich mit einer eigenen Ansage und einem Sound für alle gefeiert.
