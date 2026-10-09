package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.block.Block;

/** Unveraenderlicher Schluessel fuer eine Blockposition, geeignet als Map-Key. */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
