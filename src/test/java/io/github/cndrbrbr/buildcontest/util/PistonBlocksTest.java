package io.github.cndrbrbr.buildcontest.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft die Aufteilung der Kolben-Event-Bloecke in verschobene und
 * zerstoerte - sowohl mit der fehlerhaften Reaktions-Abbildung von Spigot
 * 26.3 (zerstoert = BLOCK, push-only = BREAK) als auch mit einer korrekten.
 */
class PistonBlocksTest {

    private static Block block(PistonMoveReaction reaction) {
        Block block = mock(Block.class);
        when(block.getPistonMoveReaction()).thenReturn(reaction);
        return block;
    }

    @Test
    void spigot263MappingTreatsPushOnlyAsMovedAndPoppedAsDestroyed() {
        Block piston = mock(Block.class);
        Block stone = block(PistonMoveReaction.MOVE);
        Block glazedTerracotta = block(PistonMoveReaction.BREAK);
        Block pumpkin = block(PistonMoveReaction.BLOCK);

        PistonBlocks.Split split = PistonBlocks.split(
                new BlockPistonExtendEvent(piston, List.of(stone, glazedTerracotta, pumpkin), BlockFace.EAST),
                PistonMoveReaction.BLOCK);

        assertEquals(List.of(stone, glazedTerracotta), split.moved());
        assertEquals(List.of(pumpkin), split.destroyed());
    }

    @Test
    void correctMappingTreatsBreakAsDestroyed() {
        Block piston = mock(Block.class);
        Block stone = block(PistonMoveReaction.MOVE);
        Block glazedTerracotta = block(PistonMoveReaction.PUSH_ONLY);
        Block melon = block(PistonMoveReaction.BREAK);

        PistonBlocks.Split split = PistonBlocks.split(
                new BlockPistonExtendEvent(piston, List.of(stone, glazedTerracotta, melon), BlockFace.UP),
                PistonMoveReaction.BREAK);

        assertEquals(List.of(stone, glazedTerracotta), split.moved());
        assertEquals(List.of(melon), split.destroyed());
    }

    @Test
    void retractWithoutBlocksIsEmpty() {
        PistonBlocks.Split split = PistonBlocks.split(
                new BlockPistonRetractEvent(mock(Block.class), List.of(), BlockFace.WEST), PistonMoveReaction.BLOCK);

        assertEquals(List.of(), split.moved());
        assertEquals(List.of(), split.destroyed());
    }
}
