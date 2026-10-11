package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Unveraenderlicher Schluessel fuer eine Blockposition, geeignet als Map-Key. */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Nachbarposition rein rechnerisch, ohne Bukkit-Weltzugriff (siehe ScoreManager#reachesFoundation). */
    public BlockKey relative(BlockFace face) {
        return new BlockKey(world, x + face.getModX(), y + face.getModY(), z + face.getModZ());
    }
}
