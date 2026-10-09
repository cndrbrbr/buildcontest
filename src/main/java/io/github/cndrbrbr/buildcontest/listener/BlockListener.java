package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

/**
 * Wertung und Zusammenhangspruefung gewerteter Bloecke (siehe rules.md#bauregeln,
 * #punktesystem und #scoreboard-anzeige). Laeuft mit NORMAL-Prioritaet und
 * ignoriert bereits abgebrochene Events, damit ProtectionListener (LOWEST)
 * zuerst greifen kann.
 */
public final class BlockListener implements Listener {

    private final ScoreManager scoreManager;
    private final PlotManager plotManager;
    private final GameStateManager gameStateManager;
    private final ScoreboardManager scoreboardManager;

    public BlockListener(ScoreManager scoreManager, PlotManager plotManager,
                          GameStateManager gameStateManager, ScoreboardManager scoreboardManager) {
        this.scoreManager = scoreManager;
        this.plotManager = plotManager;
        this.gameStateManager = gameStateManager;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Optional<Plot> plotOpt = plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (plotOpt.isEmpty()) {
            return;
        }
        Plot plot = plotOpt.get();

        if (gameStateManager.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        if (!scoreManager.canPlace(block, plot)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    "§cDieser Block ist nicht mit deinem Bauplatz verbunden und wurde nicht platziert.");
            return;
        }

        scoreManager.registerPlacement(block, plot, event.getPlayer().getUniqueId());
        scoreboardManager.refreshAll();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<Plot> plotOpt = plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (plotOpt.isEmpty()) {
            return;
        }
        Plot plot = plotOpt.get();

        if (gameStateManager.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        scoreManager.registerBreak(block, plot);
        scoreboardManager.refreshAll();
    }
}
