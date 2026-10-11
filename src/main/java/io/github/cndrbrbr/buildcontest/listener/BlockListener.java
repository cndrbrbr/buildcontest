package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
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
    private final MainConfig mainConfig;

    public BlockListener(ScoreManager scoreManager, PlotManager plotManager,
                          GameStateManager gameStateManager, ScoreboardManager scoreboardManager,
                          MainConfig mainConfig) {
        this.scoreManager = scoreManager;
        this.plotManager = plotManager;
        this.gameStateManager = gameStateManager;
        this.scoreboardManager = scoreboardManager;
        this.mainConfig = mainConfig;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        if (isBlockedByPause(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDer Contest ist pausiert - keine Platzierungen möglich.");
            return;
        }

        Optional<Plot> plotOpt = plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (plotOpt.isEmpty()) {
            return;
        }
        Plot plot = plotOpt.get();

        if (gameStateManager.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        // Unverbundene Platzierungen werden NICHT abgelehnt - sie zaehlen
        // lediglich 0 Punkte, bis eine Verbindung zum Fundament besteht
        // (siehe ScoreManager, rules.md#bauregeln-und-verbindungsprüfung).
        scoreManager.registerPlacement(block, plot, event.getPlayer().getUniqueId());
        scoreboardManager.refreshAll();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (isBlockedByPause(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDer Contest ist pausiert - kein Abbau möglich.");
            return;
        }

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

    /**
     * Waehrend PAUSED wird die GESAMTE Contest-Welt gegenueber Block-
     * Platzierung/-Abbau eingefroren, nicht nur die Bauplaetze - damit auch
     * wettbewerbsrelevantes Farmen/Sammeln ausserhalb der Grundstuecke keinen
     * Vorteil waehrend der Pause bringt (siehe
     * rules.md#ablauf-unterbrechung-und-spielende: "Technisch kann dafuer
     * die gesamte Contest-Welt gegenueber Spieleraktionen eingefroren
     * werden").
     */
    private boolean isBlockedByPause(Block block) {
        return gameStateManager.isPaused() && block.getWorld().getName().equals(mainConfig.getWorldName());
    }
}
