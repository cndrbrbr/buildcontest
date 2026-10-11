package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.manager.EntryTeleportManager;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Scoreboard-Setup und Configfile-Zuweisung beim Beitreten (siehe rules.md#gruppen). */
public final class PlayerConnectionListener implements Listener {

    private final MainConfig mainConfig;
    private final GroupManager groupManager;
    private final ScoreboardManager scoreboardManager;
    private final EntryTeleportManager entryTeleportManager;
    private final GameStateManager gameStateManager;

    public PlayerConnectionListener(MainConfig mainConfig, GroupManager groupManager,
                                     ScoreboardManager scoreboardManager, EntryTeleportManager entryTeleportManager,
                                     GameStateManager gameStateManager) {
        this.mainConfig = mainConfig;
        this.groupManager = groupManager;
        this.scoreboardManager = scoreboardManager;
        this.entryTeleportManager = entryTeleportManager;
        this.gameStateManager = gameStateManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (mainConfig.getAssignmentMode() == MainConfig.AssignmentMode.CONFIG
                && groupManager.getGroupOf(player.getUniqueId()).isEmpty()) {
            groupManager.resolvePresetGroup(player.getName())
                    .ifPresent(groupId -> groupManager.assign(player.getUniqueId(), groupId));
        }

        // In der Eingangswelt (siehe rules.md#setup-admin) startet jeder Spieler
        // immer am (vom Admin gestalteten, von WorldManager auf festen Boden
        // korrigierten) Weltspawn, statt an seiner zuletzt gespeicherten
        // Position - die kann z. B. nach Admin-Umbauten nicht mehr sicher
        // sein. War der Spieler zuletzt in der Bauplatz-Welt (laufender
        // Contest), bleibt das unangetastet.
        World primaryWorld = Bukkit.getWorlds().get(0);
        if (player.getWorld().equals(primaryWorld)) {
            player.teleport(primaryWorld.getSpawnLocation());
            // Die Eingangswelt laeuft nicht im Ueberlebensmodus (siehe
            // rules.md#welten-und-fortbewegung) - Spieler koennen dort nichts
            // bauen/abbauen, aber mit Schildern/Start-Steinen interagieren.
            player.setGameMode(GameMode.ADVENTURE);
        }

        // Erstmaliger Eintritt in einen laufenden Contest (z. B. per
        // CONFIG-Zuweisung gerade oben aufgeloest) - danach keine
        // Teleport-Abkuerzungen mehr, siehe EntryTeleportManager.
        entryTeleportManager.teleportIfFirstEntry(player);

        scoreboardManager.setup(player);
    }

    /**
     * Ohne eigenes Bett/Anker faellt Bukkit beim Respawn in einer
     * zusaetzlichen (nicht-primaeren) Welt wie der Contest-Welt auf den
     * Weltspawn der SERVER-Hauptwelt zurueck, statt in der Welt zu bleiben,
     * in der der Spieler gestorben ist - das reisst ihn ungewollt aus dem
     * laufenden Contest heraus. Korrigiert das auf den Weltspawn der
     * Contest-Welt (siehe PlotManager#generatePlots: liegt auf dem ersten
     * Bauplatz), OHNE einen individuellen Shortcut zum eigenen Bauplatz zu
     * schaffen - das bleibt laut rules.md#welten-und-fortbewegung bewusst
     * verboten ("ein Wiedereinstieg darf nicht automatisch dorthin
     * teleportieren"). Ein eigenes Bett auf dem Bauplatz wird weiterhin ganz
     * normal respektiert.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn() || !gameStateManager.isRunning()) {
            return;
        }
        World contestWorld = Bukkit.getWorld(mainConfig.getWorldName());
        if (contestWorld == null || contestWorld.equals(event.getRespawnLocation().getWorld())) {
            return;
        }
        if (groupManager.getGroupOf(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        event.setRespawnLocation(contestWorld.getSpawnLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboardManager.clear(event.getPlayer());
    }
}
