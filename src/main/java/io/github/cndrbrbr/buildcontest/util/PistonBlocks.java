package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bukkit liefert in BlockPistonExtend-/RetractEvent nur die VERSCHOBENEN
 * Bloecke. Bloecke mit {@link PistonMoveReaction#BREAK} (z. B. Kuerbis,
 * Melone), die vor der Bewegungsfront stehen, zerstoert der Kolben dagegen
 * ohne eigenes Event - sie werden hier nachgebildet.
 */
public final class PistonBlocks {

    private PistonBlocks() {
    }

    public static List<Block> moved(BlockPistonEvent event) {
        return switch (event) {
            case BlockPistonExtendEvent extend -> extend.getBlocks();
            case BlockPistonRetractEvent retract -> retract.getBlocks();
            default -> List.of();
        };
    }

    /**
     * @return zerstoerter Block -&gt; der Block, der in ihn hineingeschoben wird
     *         (ein verschobener Block oder beim Ausfahren der Kolben selbst, dessen Kopf ihn trifft)
     */
    public static Map<Block, Block> destroyed(BlockPistonEvent event) {
        List<Block> moved = moved(event);
        List<Block> sources = new ArrayList<>(moved);
        if (event instanceof BlockPistonExtendEvent) {
            sources.add(event.getBlock());
        }
        Set<Block> movedSet = new HashSet<>(moved);
        BlockFace direction = event.getDirection();
        Map<Block, Block> destroyed = new LinkedHashMap<>();
        for (Block source : sources) {
            Block front = source.getRelative(direction);
            if (!movedSet.contains(front) && front.getPistonMoveReaction() == PistonMoveReaction.BREAK) {
                destroyed.putIfAbsent(front, source);
            }
        }
        return destroyed;
    }
}
