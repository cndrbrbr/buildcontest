package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Teilt die Bloecke eines Kolben-Events in VERSCHOBENE und ZERSTOERTE auf.
 *
 * CraftBukkit liefert in {@code getBlocks()} beide Sorten in einer Liste
 * (verschobene, danach vom Kolben zerstoerte wie Kuerbis/Melone), ohne sie
 * zu kennzeichnen. Unterscheiden lassen sie sich nur ueber die
 * Kolbenreaktion - und die bildet Spigot 26.3 falsch ab: Mojang hat das
 * NMS-Enum umbenannt/umsortiert (PUSH_PULL, PUSH, POPPED, IMMOVEABLE,
 * IGNORE_ENTITY), CraftBlock#getPistonMoveReaction mappt aber weiter per
 * Ordinal auf MOVE, BREAK, BLOCK, IGNORE, PUSH_ONLY. Zerstoerte Bloecke
 * melden dadurch BLOCK, push-only-Bloecke (glasierte Keramik) BREAK.
 *
 * Statt eines festen Enum-Werts wird deshalb einmalig kalibriert: Kuerbisse
 * werden in Vanilla seit jeher vom Kolben zerstoert, ihre gemeldete Reaktion
 * ist also auf diesem Server der Wert fuer "wird zerstoert" - egal ob die
 * Abbildung fehlerhaft ist oder in einer spaeteren Spigot-Version repariert
 * wird.
 */
public final class PistonBlocks {

    public record Split(List<Block> moved, List<Block> destroyed) {
    }

    private static PistonMoveReaction destroyedReaction;

    private PistonBlocks() {
    }

    public static Split split(BlockPistonEvent event) {
        if (destroyedReaction == null) {
            destroyedReaction = Bukkit.createBlockData(Material.PUMPKIN).getPistonMoveReaction();
        }
        return split(event, destroyedReaction);
    }

    /** Fuer Tests ohne laufenden Server: {@code destroyedReaction} ist die Reaktion, die zerstoerte Bloecke melden. */
    static Split split(BlockPistonEvent event, PistonMoveReaction destroyedReaction) {
        List<Block> moved = new ArrayList<>();
        List<Block> destroyed = new ArrayList<>();
        for (Block block : blocks(event)) {
            (block.getPistonMoveReaction() == destroyedReaction ? destroyed : moved).add(block);
        }
        return new Split(moved, destroyed);
    }

    private static List<Block> blocks(BlockPistonEvent event) {
        return switch (event) {
            case BlockPistonExtendEvent extend -> extend.getBlocks();
            case BlockPistonRetractEvent retract -> retract.getBlocks();
            default -> List.of();
        };
    }
}
