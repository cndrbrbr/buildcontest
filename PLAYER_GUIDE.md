# Buildcontest – Spieler-Anleitung

_English version: [PLAYER_GUIDE_EN.md](./PLAYER_GUIDE_EN.md)_

Kurze Einführung für Spieler. Die vollständigen Regeln stehen in [`rules.md`](./rules.md), die
genaue Punktetabelle in [`scoreboard-config.yml`](./scoreboard-config.yml).

## Ziel

Du spielst mit deiner Gruppe Survival und sammelt Punkte, indem ihr Blöcke auf eurem **eigenen
Bauplatz** verbaut. Ressourcen farmt ihr in der gemeinsamen Welt, gebaut wird aber ausschließlich
auf dem eigenen Bauplatz.

## Gruppe beitreten

Je nach Server-Einstellung gibt es zwei Modi (euer Admin sagt euch, welcher aktiv ist):

- **Freie Wahl**: `/bc join <gruppennummer>`, solange die Gruppe noch nicht voll ist. Die Teams
  werden gleich groß gehalten: einer Gruppe, die schon mehr Spieler hat als die andere, könnt ihr
  nicht beitreten. Ihr könnt danach nicht mehr selbst wechseln.
- **Feste Zuweisung**: Der Admin trägt euch per Konfiguration oder `/bc assign` einer Gruppe zu –
  hier müsst ihr nichts selbst tun.

Eure Teamfarbe und ein `[Team]`-Präfix stehen über eurem Kopf und in der Tab-Liste, die Sidebar
zeigt „Dein Team“.

Sobald ihr einer Gruppe zugeordnet seid und der Contest läuft, bringt euch das Plugin **einmalig**
automatisch neben den Beacon eures Teams direkt an eurem Bauplatz. Danach gibt es bewusst **keine**
weiteren Teleport-Befehle mehr – zu eurem Bauplatz (oder woanders hin) kommt ihr zu Fuß, per Boot,
Pferd, Minecart usw., ganz normales Survival-Gameplay.

**Im Automatikmodus** (Rot gegen Blau, Presets `2x2`/`2x4`) läuft das anders: Ihr bekommt in der
Eingangswelt einen roten und einen blauen Wolle-"Ball" ins Inventar. Benutzt den Ball eurer Wahl
(Rechtsklick), um dem jeweiligen Team beizutreten (nur möglich, solange das Team noch nicht voll
und nicht größer als das andere ist). Eine schwebende Tafel zeigt live, wer schon in welchem Team ist. Sobald beide Teams voll
sind, startet der Contest automatisch. Beim Preset `2x1` gibt es keine Ballwahl – die ersten zwei
Beitretenden werden einfach zufällig Rot/Blau zugeteilt. Beim Preset `2xn` werden alle gerade
anwesenden Spieler ohne Gruppe sofort zufällig in zwei Teams aufgeteilt, ohne Wartezeit. Wer
später dazukommt, landet im kleineren Team.

**Start-Steine:** Steht in der Eingangswelt ein beschrifteter Stein mit Schild (`2x1`/`2x2`/`2x4`/
`2xn`), könnt ihr per Rechtsklick selbst einen Contest starten – auch wenn kein Admin da ist!
Wie lange der Contest dauert, entscheidet ihr in der Abstimmung (siehe unten). Eine Statusanzeige neben
den Steinen zeigt, ob gerade schon ein Contest läuft (dann warten, bis er vorbei ist).

## Eingangswelt, Contest und Inventar

- In der Eingangswelt könnt ihr nichts bauen oder abbauen (Adventure-Modus) – dort wartet ihr nur.
- Sobald ihr in den Contest kommt, wechselt ihr automatisch in den **Überlebensmodus** mit
  **voller Gesundheit**.
- Euer **mitgebrachtes Inventar wird beim Eintritt gesichert und geleert** – ihr startet also
  immer mit leeren Taschen, fair für alle. Wenn ihr den Contest wieder verlasst (z. B. nach
  `/bc end`), bekommt ihr dieses alte Inventar zurück – was ihr im Contest gesammelt/gebaut
  getragen habt, bleibt dort zurück.
- **Stirbt** ihr während des Contests, respawnt ihr normal (Minecraft-Standardverhalten) – euer
  Inventar fällt zu Boden wie üblich, eure Gruppe/Punkte sind davon nicht betroffen. Ohne eigenes
  Bett respawnt ihr neben dem Beacon eures Teams; schlaft ihr in einem Bett (z. B. auf eurem
  Bauplatz), respawnt ihr dort.

## Abstimmung vor dem Start

Bevor ein Contest beginnt, habt ihr 60 Sekunden Zeit abzustimmen – einfach im Chat anklicken
oder `/bc vote pvp ja|nein` und `/bc vote zeit 15|30|45|60` eingeben:

- **PvP**: Gibt es eine Mehrheit für „Ja“, könnt ihr Spieler des **anderen** Teams angreifen.
  Euer eigenes Team könnt ihr nie verletzen. Bei Gleichstand gibt es kein PvP.
- **Spieldauer**: 15, 30, 45 oder 60 Minuten. Es gewinnt die Option mit den meisten Stimmen, bei
  Gleichstand die kürzere, ohne Stimmen sind es 30 Minuten.

Die Abstimmung endet früher, sobald alle Spieler beide Fragen beantwortet haben. Die Restzeit seht
ihr oben als Leiste, mit Warnungen bei 5 Minuten, 1 Minute und 10 Sekunden; wenn sie abgelaufen
ist, endet der Contest automatisch.

## Bauen

- Euer Bauplatz ist eine rechteckige, flach planierte Fläche auf eurer eigenen, lokal passenden
  Höhe – andere Gruppen können auf einer anderen Höhe liegen. Nach oben (Y) gibt es **kein
  Limit**.
- Direkt neben eurem Bauplatz markiert ein leuchtender Beacon die Position – so findet ihr euren
  Bauplatz auch von weitem wieder. Den Beacon und sein Eisenfundament kann niemand abbauen.
- Gewertet werden nur Blöcke, deren X/Z-Position innerhalb eurer Bauplatz-Grundfläche liegt.
- Verbaute Blöcke müssen **zusammenhängend** sein und über feste Blöcke (auch 0-Punkte-Blöcke wie
  Glas zählen als Verbindung) mit der planierten Oberfläche verbunden sein. Eine schwebende
  Platzierung wird **nicht** abgelehnt, zählt aber 0 Punkte, bis sie wieder verbunden ist –
  verbindet ihr sie später (auch indirekt über einen Nachbarblock), bekommt ihr die Punkte ohne
  erneutes Platzieren zurück.
- Baut ihr einen **verbindenden** Block ab, verlieren dadurch abgeschnittene Blöcke ebenfalls ihre
  Punkte, bis die Verbindung wiederhergestellt ist.
- Reine Deko (Fackeln, Blumen, Redstone-Komponenten, Container, …) dürft ihr frei platzieren, bringt
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

Der Contest endet automatisch, wenn die abgestimmte Spielzeit abgelaufen ist, oder vorher, wenn
der Admin `/bc end` ausführt. Danach
ist euer Bauplatz eingefroren, der Endstand wird im Chat bekanntgegeben und die Gewinnergruppe
zusätzlich mit einer eigenen Ansage und einem Sound für alle gefeiert.
