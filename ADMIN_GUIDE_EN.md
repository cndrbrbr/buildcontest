# Buildcontest – Admin Guide

_Deutsche Fassung: [ADMIN_GUIDE.md](./ADMIN_GUIDE.md)_

This guide is for server admins who want to set up and run a build contest. The full game
description is in [`rules.md`](./rules.md) (German), the score table is in
[`scoreboard-config.yml`](./scoreboard-config.yml).

> **Note:** `rules.md` describes the agreed target rule set; this guide describes what is
> implemented in version 1.0. Open items are listed in [`TODO.md`](./TODO.md). In-game messages
> are in German.

## Requirements

- Spigot or **Paper server for Minecraft 26.3** (see `pom.xml#spigot.version` / `plugin.yml#api-version`).
- Java 25 on the server.
- The built `buildcontest-<version>.jar` (see [`README.md`](./README.md), or download it from the
  [releases](https://github.com/cndrbrbr/buildcontest/releases)) in the `plugins` folder.

## Installation

1. Copy the JAR into the `plugins` folder and (re)start the server.
2. On the first start the plugin creates `plugins/BuildContest/config.yml` and
   `plugins/BuildContest/scoreboard-config.yml`.
3. Grant `buildcontest.admin` (default: OPs only) to the people who should run the contest.
   `buildcontest.use` (default: everyone) allows `/bc join`, `/bc vote` and `/bc status`.

## Configuration (`config.yml`)

| Path | Meaning |
|---|---|
| `groups.count` | Number of teams |
| `groups.max-size` | Maximum number of players per team |
| `groups.assignment-mode` | `FREE` (players choose with `/bc join`) or `CONFIG` (see `groups.assignments`) |
| `groups.assignments` | Only for `CONFIG`: Minecraft name → team number |
| `plots.size-x` / `plots.size-z` | Area of a plot |
| `plots.min-distance` / `plots.max-distance` | Distance between plots |
| `plots.surface-y` | Only a fallback if no dry spot is found for a plot (see below). Normally the Y level is determined **for each plot individually** from the actual terrain, so different plots can be at different heights |
| `plots.center-x` / `plots.center-z` | Centre of the plot grid |
| `plots.world` | **See warning below** |
| `game.duration-minutes` | Automatic end after X minutes, `0` = only `/bc end`. Since the pre-start vote was introduced, every start path uses the voted duration (15–60 min) instead |
| `game.autosave-minutes` | Interval for automatically saving the game state, `0` = off |
| `game.auto-delete-world-on-end` | Delete the plot world automatically on `/bc end`? Default `false` (then delete it manually with `/bc deleteworld`) |

### ⚠️ `plots.world` must NOT be the server's main world

For a **new** contest, `/bc start` regenerates the whole configured plot world with a random
seed. Only the plots themselves are flattened afterwards; the rest is normal, freshly generated
terrain. If a plot world from a previous contest still exists, `/bc start` asks for confirmation
first (see below); while a contest is running, `/bc start` is refused. Bukkit cannot unload the
server's default main world (from `server.properties#level-name`, usually `world`) at runtime.
`plots.world` therefore has to be a **separate, dedicated world** (default: `buildcontest_world`)
that only exists for the contest. Let players spawn and wait in the normal main world until the
contest begins.

If `plots.world` is set to the main world anyway, `/bc start` aborts with an error message
instead of damaging the server.

### The lobby world (server main world)

Unlike the plot world, the main world (`world` in `server.properties`) is **never regenerated or
deleted** by the plugin. The admin designs it and it stays permanently. Recommended:
`level-type=minecraft:flat` in `server.properties`, so it stays the same on every server start
(e.g. as a lobby where players meet before the contest). On enable, the plugin automatically
moves the Y coordinate of the world spawn onto solid ground (X/Z stay as set by the admin), so a
"floating" world spawn cannot cause a crash.

## Score table (`scoreboard-config.yml`)

Every block type has a point value according to a seven-tier system (see the
[player guide](./PLAYER_GUIDE_EN.md#points)). To set your own values:

- Permanently: add/change the entry in `scoreboard-config.yml`, then run `/bc reload`.
- Only at runtime (lost on restart): `/bc setscore <block> <points>`.

## Running a contest

1. **Prepare** (can be changed as often as you like before the contest starts):
   ```
   /bc setgroups <n>
   /bc setgroupsize <n>
   /bc setplotsize <x> <z>
   /bc setdistance <min> <max>
   ```
   With `assignment-mode: CONFIG`, also maintain `groups.assignments` in `config.yml` and run
   `/bc reload`.
2. **Assign players** (optional in `FREE` mode, where players use `/bc join <team>` themselves):
   ```
   /bc assign <player> <team>
   ```
   Teams are kept even: free choice refuses the bigger team, and random assignments, the game
   start and late joiners go into the smaller team. Each player's team colour and a `[Team]`
   prefix are shown above their head and in the tab list.
3. **Start**:
   ```
   /bc start
   ```
   First a **60-second vote** runs for all online players: PvP yes/no (only against other teams,
   a tie means no PvP) and the duration (15, 30, 45 or 60 minutes; most votes wins, a tie picks
   the shorter one, no votes means 30). The vote ends early once everyone has answered both
   questions.

   Then the plugin generates the fresh plot world (new random seed), finds a dry height that fits
   the local terrain **for each** plot individually, spreads the plots in a grid and flattens
   them, and resets all scores from a previous contest. Depending on plot size and count this can
   take a few seconds.

   - If a contest is already running, `/bc start` is refused. Run `/bc end` first.
   - If a plot world from a previous (finished) contest still exists, `/bc start` asks once:
     `/bc start confirm` creates the new world and irreversibly deletes the old one.

   Every player with a team is teleported **once** next to their team's beacon: online players
   right at the start, players who join or are assigned later on `/bc join`/`/bc assign` or on
   their first login. Players without their own bed or respawn anchor also respawn at their
   team's beacon. After that the plugin deliberately offers **no** teleport shortcuts (no
   `/bc tp` or similar); normal travel (on foot, boat, horse, minecart, Nether …) is part of the
   game.

   Right outside the south edge of every plot there is an active beacon on a small iron block
   pyramid, at the same height as the plot, with a corridor to the plot cleared up to the sky
   (never inside a mountain). It is a landmark and the team's spawn point, nobody can break it
   (it is also protected against explosions and pistons), and it does not count for scoring (it
   is outside the plot boundaries).
4. **Let it run**: scoreboard, protection and scoring work automatically. The remaining time is
   shown to everyone as a boss bar, with warnings at 5 minutes, 1 minute and 10 seconds.

   If needed (e.g. a break or a technical problem), run `/bc pause`: the timer stops and the
   **entire** contest world is frozen for placing/breaking (not just the plots), so nobody keeps
   building or farming during the break. `/bc resume` continues the same contest with the exact
   score and the remaining time. `/bc start` refuses both a running and a paused contest.
5. **End**:
   ```
   /bc end
   ```
   Freezes the build progress, teleports all players from the plot world back to the lobby world
   (server main world) and announces the final standings (ranking by team points) in the chat.
   This also happens automatically when the voted time is up.
6. **Delete the plot world** (optional): it stays after `/bc end` so the builds can still be
   inspected. Afterwards either run `/bc deleteworld` manually, or set
   `game.auto-delete-world-on-end: true` so this happens right after `/bc end`. The next
   `/bc start` replaces the world completely anyway.

## Automatic mode and start stones

Four presets for quick, self-explanatory events without manual preparation, **always two
teams** (Red and Blue):

| Preset | Team size | Team choice |
|---|---|---|
| `2x1` | 1 | No ball choice: the first two players are randomly assigned to Red/Blue |
| `2x2` | 2 | Red/blue ball, players choose themselves by right-clicking |
| `2x4` | 4 | Red/blue ball, players choose themselves by right-clicking |
| `2xn` | everyone online without a team, split in half | No ball choice: immediate random split, starts right away without a waiting phase; latecomers go into the smaller team |

```
/bc automode <2x1|2x2|2x4|2xn>
```

As with `/bc start`, the players vote on PvP and duration before the contest begins.

### Start stones

On the very first server start, the plugin places the start stones **automatically at the world
spawn** of the lobby world if that has never happened before (neither automatically nor
manually), so **no** manual step is needed for players to start a contest themselves. To place
them again somewhere else (e.g. after rebuilding the lobby):

```
/bc placestartstones
```

This places a labelled stone (stone block + sign) for every preset and a status display at the
admin's current position. Right-clicking a sign triggers the same preset as the corresponding
`/bc automode` command, **so a contest can start without an admin being present**. The duration
is decided by the vote, just like for the other start paths.

Only one contest/team selection runs at a time. The status display shows "Bereit" (ready), the
team selection, the running vote or "Contest läuft" (contest running) with remaining time, PvP and
per team the number of players (online) and points; while a contest runs, the stones do not
trigger another one. The display is reset when the contest ends, both on `/bc end` and when the
time runs out. It reappears automatically after server restarts (position stored in config.yml
under `startstones.board`); `/bc status` shows the same content in chat. Next to it a glowing top 30
board lists total blocks placed per player (vanilla "used" statistic, block items only, refreshed
every minute, position via `/bc placetoplist` → config.yml `toplist`; in chat `/bc top`).

### Lobby world, contest world and inventory

- The lobby world automatically runs in **adventure mode** (no building/breaking). This is set
  on every join and when returning from the contest.
- The contest world uses **survival mode** for all participants and difficulty **Normal** (set
  again on every `/bc start`).
- On **first entry** into a contest the player's inventory is saved and cleared, so everybody
  starts with an empty inventory. On **return** to the lobby world (e.g. after `/bc end`) the
  saved inventory is restored; items collected during the contest are lost (replaced, not
  merged). Like the automatic mode waiting state, this inventory vault is **not** persisted
  across a server restart.

## Persistence

Team membership, team names and colours, plots, tracked blocks, PvP setting and game state are
saved in `plugins/BuildContest/data.yml` in addition to the Minecraft world: automatically every
`game.autosave-minutes` and on a clean server stop. A server restart during a running contest
reloads this state on enable; a running timer correctly continues with the remaining time.

On load, every tracked block is checked against the actual world: if the material at the saved
position no longer matches (e.g. because the block was broken between the last autosave and a
crash), the entry and any dependent connections are removed instead of awarding points for a
build that no longer exists.

The **final standings** of every finished contest are additionally archived permanently in
`plugins/BuildContest/results.yml`, independent of `data.yml` and not affected by a later
`/bc start` (which resets the running scores) or `/bc deleteworld`.

**While a contest is running**, the plugin refuses `/bc setgroups`, `/bc setgroupsize`,
`/bc setplotsize`, `/bc setdistance`, `/bc setscore` and `/bc automode`. These game-defining
settings can only be changed again after `/bc end`. `/bc assign` is deliberately allowed at any
time (team changes by an admin are explicitly permitted).

## Command reference

| Command | Effect |
|---|---|
| `/bc join <team>` | (Player) Join a team in `FREE` mode |
| `/bc status` | (Player) Show whether a contest is running – remaining time, PvP, players and points per team |
| `/bc top` | (Player) Top 30 players by total blocks placed |
| `/bc placetoplist` | Place the glowing top 30 board at your position (default: right of the start stones) |
| `/bc vote pvp <ja\|nein>` | (Player) Vote for PvP yes/no during the pre-start vote |
| `/bc vote zeit <15\|30\|45\|60>` | (Player) Vote for the contest duration in minutes |
| `/bc setgroups <n>` | Set the number of teams (existing memberships are lost) |
| `/bc setgroupsize <n>` | Set the maximum team size |
| `/bc setplotsize <x> <z>` | Set the plot size |
| `/bc setdistance <min> <max>` | Minimum/maximum distance between plots |
| `/bc setscore <block> <points>` | Point value for a block type (runtime only) |
| `/bc assign <player> <team>` | Assign a player to a team |
| `/bc start` | Run the vote, then start a new contest: regenerate the world, create plots, reset scores (refused if a contest is already running) |
| `/bc start confirm` | Like `/bc start`, but with explicit confirmation if an old plot world still exists and should be replaced |
| `/bc pause` | Stop the timer, freeze the whole contest world for placing/breaking |
| `/bc resume` | Continue the same contest with the remaining time |
| `/bc end` | End the contest and announce the final standings |
| `/bc deleteworld` | Delete the plot world (see `game.auto-delete-world-on-end`) |
| `/bc automode <2x1\|2x2\|2x4\|2xn>` | Activate a quick-start preset (see above) |
| `/bc placestartstones` | Place labelled start stones for all presets at your own position |
| `/bc reload` | Reload `config.yml` and `scoreboard-config.yml` |

There is deliberately **no** player teleport command (no `/bc tp` or similar): after the single
start teleport, normal travel is part of the game.

## Troubleshooting

- **"plots.world ist die Server-Hauptwelt"** (plots.world is the main world): see the warning
  above. Enter a different world in `config.yml#plots.world`, then `/bc reload` and `/bc start`
  again.
- **"Unbekanntes Material in scoreboard-config.yml"** (unknown material): the block name does not
  exist (any more) in this Minecraft version or was renamed. This does not affect the plugin;
  the block then simply counts as `default_score` (usually 0).
- **Server low on memory**: keeping `plots.size-x`/`size-z` and `plots.min-distance` small
  noticeably reduces the chunk area that has to be loaded for flattening and world generation.
- **"Kein durchgehend trockener Platz … gefunden (Ozean-Welt?)"** (no continuous dry spot found):
  the automatic land search found no continuously dry area for the whole grid around the
  configured centre and falls back to `plots.center-x`/`-z` and `plots.surface-y` from the
  config. Simply run `/bc start confirm` again (new random seed) or set `plots.center-x`/`-z` to
  a different area.
- **"Bauplatz von Gruppe N hat keinen durchgehend trockenen eigenen Boden"** (plot of team N has
  no continuous dry ground): only this ONE plot is locally in water (e.g. a small lake) even
  though the grid as a whole is dry. It then gets the shared anchor Y level as a fallback and may
  lie in water. Running `/bc start confirm` again usually fixes this (new seed).
