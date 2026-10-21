# Buildcontest – Player Guide

_Deutsche Fassung: [PLAYER_GUIDE.md](./PLAYER_GUIDE.md)_

A short introduction for players. The full rules are in [`rules.md`](./rules.md) (German), the
exact score table is in [`scoreboard-config.yml`](./scoreboard-config.yml).

> **Note:** In-game messages and the vote commands are in German. "ja" means yes, "nein" means
> no, "zeit" means time.

## Goal

You play survival together with your team and score points by placing blocks on your **own
plot**. You gather resources anywhere in the shared world, but only blocks on your own plot count.

## Joining a team

Depending on the server settings there are two modes (your admin will tell you which one is
active):

- **Free choice**: `/bc join <team number>` while the team is not full yet. Teams are kept
  even: you cannot join a team that already has more players than the other. Once you have
  joined, you cannot switch teams yourself.
- **Fixed assignment**: The admin puts you into a team via the config or `/bc assign`. You don't
  have to do anything.

Your team colour and a `[Team]` prefix are shown above your head and in the tab list, and the
sidebar shows "Dein Team" (your team).

**In automatic mode** (Red vs. Blue, presets `2x2`/`2x4`) it works differently: in the lobby
world you get a red and a blue wool "ball" in your inventory. Use the ball of your choice
(right-click) to join that team. This only works while the team is not full and not bigger than
the other one. A floating board shows live who is in which team. As soon as both teams are full,
the contest starts automatically. With preset `2x1` there is no ball choice: the first two
players are randomly assigned to Red and Blue. With preset `2xn` all players currently online
without a team are split randomly into two teams right away, with no waiting. Players who join
later are put into the smaller team.

**Start stones:** If there is a labelled stone with a sign (`2x1`/`2x2`/`2x4`/`2xn`) in the lobby
world, you can start a contest yourself by right-clicking it, even when no admin is around! A
status display next to the stones shows whether a contest is already running (if so, wait until
it is over), with the remaining time and how many players are in each team. `/bc status` shows the
same in chat. Next to it glows the top 30 of players who placed the most blocks in contests (`/bc top`).

## Vote before the start

Before a contest begins you have 60 seconds to vote. Click the buttons in the chat or type the
commands:

- **PvP**: `/bc vote pvp ja` (yes) or `/bc vote pvp nein` (no). If the majority votes yes, you
  can attack players of the **other** team. You can never hurt your own team. A tie means no PvP.
- **Duration**: `/bc vote zeit 15|30|45|60` (minutes). The option with the most votes wins; on a
  tie the shorter one wins, without any votes it is 30 minutes.

The vote ends early once every online player has answered both questions. During the contest,
the remaining time is shown as a bar at the top of the screen, with warnings at 5 minutes,
1 minute and 10 seconds. When the time is up, the contest ends automatically.

## Lobby world, contest and inventory

- In the lobby world you cannot build or break anything (adventure mode); you just wait there.
- When the contest starts you are teleported **once** next to your team's beacon, switched to
  **survival mode** with **full health**. After that there are deliberately **no** teleport
  commands: you get around on foot, by boat, horse, minecart etc., just normal survival gameplay.
- Your **inventory is saved and emptied when you enter the contest**, so everybody starts with
  empty pockets. When you leave the contest again (e.g. after `/bc end`), you get your old
  inventory back. Everything you collected during the contest stays behind.
- If you **die** during the contest, your items drop as usual, but your team and points are not
  affected. Without your own bed you respawn next to your team's beacon. If you sleep in a bed
  (e.g. on your plot), you respawn there.

## Building

- Your plot is a rectangular, flattened area at a height that fits the local terrain. Other
  teams' plots may be at a different height. There is **no** height limit upwards.
- A glowing beacon right next to your plot marks its position, so you can find it from far away.
  Nobody can break the beacon or its iron base.
- Only blocks whose X/Z position is inside your plot's area count.
- Placed blocks must be **connected** to the flattened surface through solid blocks (0-point
  blocks such as glass also count as a connection). A floating block is **not** rejected, but
  scores 0 points until it is connected again. If you connect it later (even indirectly through a
  neighbouring block), you get the points back without placing it again.
- If you break a **connecting** block, all blocks cut off by it also lose their points until the
  connection is restored.
- Pure decoration (torches, flowers, redstone components, containers, …) can be placed freely but
  scores 0 points.
- If you break a scoring block, its points are subtracted again, even if a teammate placed it.

## Points

Each block type is ranked by how hard it is to obtain (roughly doubling per tier):

| Tier | Points | Examples |
|---|---|---|
| T0 | 0 | Functional blocks & pure decoration |
| T1 | 1 | Dirt, sand, stone, wood, wool, terracotta |
| T2 | 3 | Stone bricks, concrete, glass, copper block |
| T3 | 6 | Iron/redstone/lapis block, prismarine |
| T4 | 12 | Gold/diamond/emerald ore |
| T5 | 25 | Diamond/emerald block |
| T6 | 50 | Netherite, beacon, conduit, dragon egg (capped) |

Shape variants (stairs, slabs, fences, doors, …) count like their base material. The complete
list is in [`scoreboard-config.yml`](./scoreboard-config.yml).

## Scoreboard

On the right side you always see:

- **The score of every team**, sorted by rank, the same for all players. The team currently in
  the lead is marked with a star.
- **Your own contribution**: how many of your team's points you personally added.

## Protection

- Nobody from another team can build or break blocks on your plot.
- Explosions (TNT, creepers, beds, respawn anchors) and fire do not spread onto plots.
- Within your own team there is **no** protection: trust your teammates.

## End of the game

The contest ends automatically when the voted time is up, or earlier if the admin uses
`/bc end`. Your plot is then frozen, the final standings are announced in the chat and the
winning team is celebrated with its own announcement and a sound for everyone.
