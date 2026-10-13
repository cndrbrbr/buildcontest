package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.model.Plot;
import io.github.cndrbrbr.buildcontest.util.PistonBlocks;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePistonMove(event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePistonMove(event);
    }

    /**
     * Haelt das Block-Tracking bei Kolbenbewegungen INNERHALB eines Bauplatzes
     * aktuell (Bewegungen ueber Bauplatzgrenzen verhindert ProtectionListener
     * schon mit LOWEST-Prioritaet). Ohne diesen Abgleich bliebe ein
     * verschobener Block an seiner alten Koordinate getrackt - Phantom-Punkte
     * dort, keine Punkte an der neuen Position. Vom Kolben zerstoerte Bloecke
     * (siehe {@link PistonBlocks#destroyed}) werden wie ein Abbau behandelt.
     */
    private void handlePistonMove(BlockPistonEvent event) {
        List<Block> moved = PistonBlocks.moved(event);
        Set<Block> destroyed = PistonBlocks.destroyed(event).keySet();

        Optional<Plot> plotOpt = Stream.concat(moved.stream(), destroyed.stream())
                .map(block -> plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ()))
                .flatMap(Optional::stream)
                .findFirst();
        if (plotOpt.isEmpty()) {
            return;
        }
        Plot plot = plotOpt.get();

        // Wie bei Spieleraktionen: pausierte oder beendete Bauplaetze aendern sich auch per Redstone nicht.
        if (gameStateManager.isPaused() || gameStateManager.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        destroyed.forEach(block -> scoreManager.registerBreak(block, plot));
        scoreManager.registerMove(moved, event.getDirection(), plot);
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
