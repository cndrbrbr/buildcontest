package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft die Kernlogik aus rules.md#punktesystem, #bauregeln und
 * #scoreboard-anzeige: Zusammenhangspruefung beim Platzieren, Gruppen- und
 * persoenlicher Score, sowie dass Abbau immer den urspruenglichen Platzierer
 * trifft - unabhaengig davon, wer tatsaechlich abgebaut hat.
 */
@ExtendWith(MockitoExtension.class)
class ScoreManagerTest {

    private static final String WORLD = "world";
    private static final int SURFACE_Y = 64;

    @Mock
    private ScoreConfig scoreConfig;

    private ScoreManager scoreManager;
    private Plot plotA;
    private Plot plotB;

    @BeforeEach
    void setUp() {
        scoreManager = new ScoreManager(scoreConfig);
        plotA = new Plot(1, WORLD, 0, 0, 9, 9, SURFACE_Y);
        plotB = new Plot(2, WORLD, 100, 100, 109, 109, SURFACE_Y);
    }

    private static Block fakeBlock(String world, int x, int y, int z, Material type) {
        Block block = mock(Block.class);
        World bukkitWorld = mock(World.class);
        lenient().when(bukkitWorld.getName()).thenReturn(world);
        lenient().when(block.getWorld()).thenReturn(bukkitWorld);
        lenient().when(block.getX()).thenReturn(x);
        lenient().when(block.getY()).thenReturn(y);
        lenient().when(block.getZ()).thenReturn(z);
        lenient().when(block.getType()).thenReturn(type);
        lenient().when(block.getRelative(any(BlockFace.class))).thenAnswer(invocation -> {
            BlockFace face = invocation.getArgument(0);
            return fakeBlock(world, x + face.getModX(), y + face.getModY(), z + face.getModZ(), Material.AIR);
        });
        return block;
    }

    @Test
    void undecoratedBlocksCanAlwaysBePlaced() {
        when(scoreConfig.getScore(Material.GLASS_PANE)).thenReturn(0);
        Block floating = fakeBlock(WORLD, 5, SURFACE_Y + 50, 5, Material.GLASS_PANE);

        assertTrue(scoreManager.canPlace(floating, plotA));
    }

    @Test
    void scoredBlockDirectlyOnFlattenedSurfaceCanBePlaced() {
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block onSurface = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);

        assertTrue(scoreManager.canPlace(onSurface, plotA));
    }

    @Test
    void scoredBlockFloatingWithoutConnectionCannotBePlaced() {
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block floating = fakeBlock(WORLD, 5, SURFACE_Y + 5, 5, Material.STONE);

        assertFalse(scoreManager.canPlace(floating, plotA));
    }

    @Test
    void scoredBlockAdjacentToRegisteredBlockCanBePlaced() {
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block base = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(base, plotA, UUID.randomUUID());

        Block onTop = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.STONE);
        assertTrue(scoreManager.canPlace(onTop, plotA));
    }

    @Test
    void registerPlacementUpdatesGroupAndPersonalScore() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block block = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.DIAMOND_BLOCK);

        scoreManager.registerPlacement(block, plotA, placer);

        assertEquals(25, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(25, scoreManager.getPersonalScore(placer));
        assertEquals(0, scoreManager.getGroupScore(plotB.getGroupId()));
    }

    @Test
    void breakingABlockDeductsFromOriginalPlacerEvenIfSomeoneElseBreaksIt() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block block = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(block, plotA, placer);

        // Ein Teammitglied (anderer Spieler) baut denselben Block wieder ab.
        scoreManager.registerBreak(block, plotA);

        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(0, scoreManager.getPersonalScore(placer));
    }

    @Test
    void exportAndImportEntriesRoundTripsScores() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block block = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(block, plotA, placer);

        List<ScoreManager.Entry> exported = scoreManager.exportEntries();
        assertEquals(1, exported.size());

        ScoreManager restored = new ScoreManager(scoreConfig);
        exported.forEach(restored::importEntry);

        assertEquals(scoreManager.getGroupScore(plotA.getGroupId()), restored.getGroupScore(plotA.getGroupId()));
        assertEquals(scoreManager.getPersonalScore(placer), restored.getPersonalScore(placer));

        // Nach dem Wiederherstellen muss die Zusammenhangspruefung ebenfalls
        // funktionieren, sonst koennten nach einem Neustart keine weiteren
        // Bloecke mehr an den Bestand angebaut werden.
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block onTop = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.STONE);
        assertTrue(restored.canPlace(onTop, plotA));
    }
}
