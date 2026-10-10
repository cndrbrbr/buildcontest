package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

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

    public EntryTeleportManager(GameStateManager gameStateManager, GroupManager groupManager,
                                 PlotManager plotManager) {
        this.gameStateManager = gameStateManager;
        this.groupManager = groupManager;
        this.plotManager = plotManager;
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
        Optional<Location> destinationOpt = plotManager.getPlot(groupOpt.get().getId())
                .flatMap(this::plotCenterLocation);
        if (destinationOpt.isEmpty()) {
            return;
        }

        gameStateManager.markInitialTeleportDone(player.getUniqueId());
        player.teleport(destinationOpt.get());
        player.sendMessage("§aDu wurdest einmalig zu eurem Bauplatz teleportiert.");
    }

    private Optional<Location> plotCenterLocation(Plot plot) {
        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            return Optional.empty();
        }
        double centerX = (plot.getMinX() + plot.getMaxX()) / 2.0 + 0.5;
        double centerZ = (plot.getMinZ() + plot.getMaxZ()) / 2.0 + 0.5;
        return Optional.of(new Location(world, centerX, plot.getSurfaceY() + 1, centerZ));
    }
}
