package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.Optional;

/**
 * Schutzmechanismen (siehe rules.md#schutzmechanismen): Blöcke auf einem Bauplatz
 * können nicht von Spielern anderer Gruppen abgebaut oder überbaut werden; Explosionen
 * und Feuerausbreitung werden an Bauplatzgrenzen generell unterbunden - auch gegenüber
 * der eigenen Gruppe. Innerhalb der eigenen Gruppe gibt es bewusst keinen Schutz.
 */
public final class ProtectionListener implements Listener {

    private final PlotManager plotManager;
    private final GroupManager groupManager;

    public ProtectionListener(PlotManager plotManager, GroupManager groupManager) {
        this.plotManager = plotManager;
        this.groupManager = groupManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (!isForeignPlot(event.getBlock(), event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cDu kannst keine Blöcke auf fremden Bauplätzen abbauen.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!isForeignPlot(event.getBlock(), event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cDu kannst auf fremden Bauplätzen nicht bauen.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ()).isPresent()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private boolean isForeignPlot(Block block, Player player) {
        Optional<Plot> plotOpt = plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (plotOpt.isEmpty()) {
            return false;
        }
        if (player.hasPermission("buildcontest.admin")) {
            return false;
        }
        Plot plot = plotOpt.get();
        Optional<Group> playerGroup = groupManager.getGroupOf(player.getUniqueId());
        return playerGroup.isEmpty() || playerGroup.get().getId() != plot.getGroupId();
    }
}
