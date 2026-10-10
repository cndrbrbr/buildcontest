package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Schutzmechanismen (siehe rules.md#schutzmechanismen): Blöcke auf einem Bauplatz
 * können nicht von Spielern anderer Gruppen abgebaut, überbaut oder deren Container
 * geöffnet werden. Alle indirekten/nicht-spielerbezogenen Veränderungen - Explosionen
 * (Entities wie TNT/Creeper UND entity-lose wie Betten/Respawn-Anker), Feuer,
 * Fluessigkeitsfluss, Kolbenbewegung, Hopper-Transfer, Entity-/Mob-Griefing
 * (z. B. Enderman) und Blockausbreitung (Feuer/Ranken/Pilze) - werden an
 * Bauplatzgrenzen generell unterbunden, UNABHAENGIG davon wessen Gruppe betroffen
 * ist (auch gegenueber der eigenen Gruppe: Bauplaetze aendern sich ausschliesslich
 * durch direkte Spieler-Platzierung/-Abbau). Innerhalb der eigenen Gruppe gibt es
 * bei DIREKTEN Bauaktionen (Platzieren/Abbauen) bewusst keinen gegenseitigen Schutz.
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
        removePlotBlocks(event.blockList());
    }

    /**
     * Betten und Respawn-Anker explodieren ohne zugehoerige Entity (z. B. im
     * falschen Dimensionstyp) und feuern deshalb BlockExplodeEvent statt
     * EntityExplodeEvent - ohne diesen Handler waeren Bauplaetze dagegen
     * ungeschuetzt (siehe rules.md#schutzmechanismen, "Betten").
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        removePlotBlocks(event.blockList());
    }

    private void removePlotBlocks(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plotOf(block).isPresent()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (plotOf(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBurn(BlockBurnEvent event) {
        if (plotOf(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Verhindert, dass fremde Spieler Container (Chest, Hopper, Furnace,
     * Shulker Box, ...) auf einem Bauplatz oeffnen (siehe
     * rules.md#schutzmechanismen: "Fremde Container-, Inventar- und
     * Entity-Zugriffe verhindern"). Nutzt {@code InventoryOpenEvent} statt
     * {@code PlayerInteractEvent}, damit JEDE Art des Oeffnens erfasst wird,
     * nicht nur Rechtsklick.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block block = inventoryBlock(event.getInventory());
        if (block == null || !isForeignPlot(block, player)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cDu kannst fremde Container nicht öffnen.");
    }

    /**
     * Hopper/Hopper-Minecarts duerfen keine Gegenstaende ueber eine
     * Bauplatzgrenze hinweg transferieren - weder aus einem fremden Bauplatz
     * heraus noch (versehentlich durch eigene Kontraptionen) in einen
     * anderen Bauplatz hinein. Transfer INNERHALB desselben Bauplatzes oder
     * komplett ausserhalb jeglicher Bauplaetze bleibt erlaubt (siehe
     * rules.md#schutzmechanismen: "Hopper/Hopper-Minecarts testen").
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Optional<Plot> sourcePlot = plotOfInventory(event.getSource());
        Optional<Plot> destinationPlot = plotOfInventory(event.getDestination());
        if (!sourcePlot.equals(destinationPlot)) {
            event.setCancelled(true);
        }
    }

    /**
     * Wasser-/Lavafluss (und sonstige {@code BlockFromToEvent}-Ausbreitung)
     * darf keine Bauplatzgrenze ueberschreiten - weder von aussen in einen
     * Bauplatz hinein noch zwischen zwei verschiedenen Bauplaetzen. Fluss
     * INNERHALB desselben Bauplatzes bleibt erlaubt (siehe
     * rules.md#schutzmechanismen).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!plotOf(event.getBlock()).equals(plotOf(event.getToBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesAcrossPlotBoundary(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesAcrossPlotBoundary(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /**
     * Ein Kolben darf keinen Block so verschieben, dass sich dessen
     * Bauplatz-Zugehoerigkeit aendert (rein/raus aus einem Bauplatz oder von
     * einem Bauplatz in einen anderen) - siehe rules.md#schutzmechanismen:
     * "Kolbenbewegungen ... ueber Grundstuecksgrenzen absichern". Bewegungen
     * komplett innerhalb eines Bauplatzes oder komplett ausserhalb bleiben
     * erlaubt, unabhaengig davon wo der Kolben selbst steht.
     */
    private boolean movesAcrossPlotBoundary(List<Block> movedBlocks, BlockFace direction) {
        for (Block block : movedBlocks) {
            Optional<Plot> before = plotOf(block);
            Optional<Plot> after = plotOf(block.getRelative(direction));
            if (!before.equals(after)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blockt jede entity-verursachte Blockaenderung auf einem Bauplatz -
     * Enderman-Griefing, Mob-Trampeln, fallende Bloecke, Silverfish usw.
     * (siehe rules.md#schutzmechanismen: "Enderman-/Mob-Griefing ...
     * absichern"). Bauplaetze aendern sich bewusst ausschliesslich durch
     * direkte Spieler-Platzierung/-Abbau.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plotOf(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Verhindert, dass sich z. B. Feuer, Ranken oder Pilze auf einen Bauplatz ausbreiten. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (plotOf(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    private boolean isForeignPlot(Block block, Player player) {
        Optional<Plot> plotOpt = plotOf(block);
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

    private Optional<Plot> plotOf(Block block) {
        return plotManager.getPlotAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    private Optional<Plot> plotOfInventory(Inventory inventory) {
        Block block = inventoryBlock(inventory);
        return block == null ? Optional.empty() : plotOf(block);
    }

    /** @return den Block hinter einem Container-Inventar, oder {@code null} bei nicht-block-gebundenen Inventaren (z. B. Spieler, Pferd). */
    private Block inventoryBlock(Inventory inventory) {
        Location location = inventory.getLocation();
        return location == null ? null : location.getBlock();
    }
}
