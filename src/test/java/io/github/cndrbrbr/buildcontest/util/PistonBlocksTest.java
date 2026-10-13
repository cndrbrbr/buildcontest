package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Prueft die Nachbildung der vom Kolben zerstoerten BREAK-Bloecke (z. B. Kuerbis). */
class PistonBlocksTest {

    private static Block block(PistonMoveReaction reaction) {
        Block block = mock(Block.class);
        lenient().when(block.getPistonMoveReaction()).thenReturn(reaction);
        return block;
    }

    private static void inFrontOf(Block behind, Block front, BlockFace direction) {
        lenient().when(behind.getRelative(direction)).thenReturn(front);
    }

    @Test
    void pistonHeadDestroysBreakBlockDirectlyInFront() {
        Block piston = block(PistonMoveReaction.BLOCK);
        Block pumpkin = block(PistonMoveReaction.BREAK);
        inFrontOf(piston, pumpkin, BlockFace.EAST);

        Map<Block, Block> destroyed = PistonBlocks.destroyed(new BlockPistonExtendEvent(piston, List.of(), BlockFace.EAST));

        assertEquals(Map.of(pumpkin, piston), destroyed);
    }

    @Test
    void pushedBlockDestroysBreakBlockAtTheFrontButNotMovableOnes() {
        Block piston = block(PistonMoveReaction.BLOCK);
        Block stone = block(PistonMoveReaction.MOVE);
        Block melon = block(PistonMoveReaction.BREAK);
        inFrontOf(piston, stone, BlockFace.UP);
        inFrontOf(stone, melon, BlockFace.UP);

        Map<Block, Block> destroyed = PistonBlocks.destroyed(new BlockPistonExtendEvent(piston, List.of(stone), BlockFace.UP));

        // Der Stein wird verschoben, nicht zerstoert - nur die Melone davor bricht.
        assertEquals(Map.of(melon, stone), destroyed);
    }

    @Test
    void retractingPistonHeadDestroysNothing() {
        Block piston = block(PistonMoveReaction.BLOCK);
        Block behindPiston = block(PistonMoveReaction.BREAK);
        inFrontOf(piston, behindPiston, BlockFace.WEST);

        Map<Block, Block> destroyed = PistonBlocks.destroyed(new BlockPistonRetractEvent(piston, List.of(), BlockFace.WEST));

        assertTrue(destroyed.isEmpty());
    }
}
