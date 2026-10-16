package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.model.Group;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Setzt rules.md#welten-und-fortbewegung um: "Beim ersten Eintritt in den
 * Contest duerfen Teilnehmer einmalig an einen sicheren Startpunkt bei ihrem
 * Bauplatz gebracht werden, bevor die Wettkampfzeit laeuft. Danach gibt es
 * keine Spieler-Teleports durch Buildcontest [...] insbesondere darf ein
 * Wiedereinstieg nicht automatisch dorthin teleportieren."
 *
 * Wird von mehreren Stellen aufgerufen, die alle zu einem "ersten Eintritt"
 * fuehren koennen: {@code /bc start} (Spieler, die schon vorher einer Gruppe
 * zugeordnet waren), spaeteres {@code /bc join}/{@code /bc assign} waehrend
 * der Contest laeuft, und das erstmalige Einloggen eines per
 * CONFIG-Zuweisung bereits zugeordneten Spielers (siehe
 * PlayerConnectionListener). {@link GameStateManager} merkt sich dauerhaft
 * (persistiert), wer seinen Teleport bereits bekommen hat, damit genau das
 * nicht doppelt passiert.
 */
public final class EntryTeleportManager {

    private final GameStateManager gameStateManager;
    private final GroupManager groupManager;
    private final PlotManager plotManager;
    private final InventoryVaultManager inventoryVaultManager;

    public EntryTeleportManager(GameStateManager gameStateManager, GroupManager groupManager,
                                 PlotManager plotManager, InventoryVaultManager inventoryVaultManager) {
        this.gameStateManager = gameStateManager;
        this.groupManager = groupManager;
        this.plotManager = plotManager;
        this.inventoryVaultManager = inventoryVaultManager;
    }

    /** Fuer alle online Spieler, z. B. direkt nach /bc start. */
    public void teleportAllNewEntrants() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportIfFirstEntry(player);
        }
    }

    /** Fuer einen einzelnen Spieler, z. B. nach /bc join, /bc assign oder beim Einloggen. */
    public void teleportIfFirstEntry(Player player) {
        if (!gameStateManager.isRunning()) {
            return;
        }
        if (gameStateManager.hasReceivedInitialTeleport(player.getUniqueId())) {
            return;
        }
        Optional<Group> groupOpt = groupManager.getGroupOf(player.getUniqueId());
        if (groupOpt.isEmpty()) {
            return;
        }
        Optional<Location> destinationOpt = teamSpawnLocation(player.getUniqueId());
        if (destinationOpt.isEmpty()) {
            return;
        }

        gameStateManager.markInitialTeleportDone(player.getUniqueId());
        // Jeder beginnt mit leerem Inventar, das mitgebrachte wird gesichert
        // und beim Verlassen des Contests wiederhergestellt (siehe
        // InventoryVaultManager, rules.md#welten-und-fortbewegung).
        inventoryVaultManager.saveAndClear(player);
        player.teleport(destinationOpt.get());
        player.setGameMode(GameMode.SURVIVAL);
        // Fairer Start fuer alle - volle Gesundheit unabhaengig vom Zustand davor.
        player.setHealth(player.getMaxHealth());
        player.sendMessage("§aDu wurdest zum Beacon eures Bauplatzes teleportiert.");
    }

    /**
     * Spawnpunkt am Beacon des eigenen Teams (siehe
     * PlotManager#beaconSpawnLocation) - fuer den Start-Teleport und fuer den
     * Respawn ohne Bett (PlayerConnectionListener#onRespawn).
     */
    public Optional<Location> teamSpawnLocation(UUID playerId) {
        return groupManager.getGroupOf(playerId)
                .flatMap(group -> plotManager.getPlot(group.getId()))
                .flatMap(plotManager::beaconSpawnLocation);
    }
}
