package io.github.cndrbrbr.buildcontest.persistence;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-End-Test fuer die Persistenz ueber einen simulierten Neustart
 * (siehe persistence.DataStore): Zustand speichern, mit komplett frischen
 * Manager-Instanzen wieder laden, und pruefen, dass Gruppen, Bauplaetze und
 * Scores identisch wiederhergestellt werden.
 */
@ExtendWith(MockitoExtension.class)
class DataStoreTest {

    private static final String WORLD = "world";

    @Mock
    private BuildContestPlugin plugin;
    @Mock
    private MainConfig mainConfig;
    @Mock
    private ScoreConfig scoreConfig;

    @TempDir
    private File dataFolder;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("DataStoreTest"));
        lenient().when(mainConfig.getGroupCount()).thenReturn(2);
        lenient().when(mainConfig.getGroupMaxSize()).thenReturn(5);
        lenient().when(mainConfig.getAssignmentMode()).thenReturn(MainConfig.AssignmentMode.FREE);
        lenient().when(mainConfig.getDurationMinutes()).thenReturn(0);
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
    void savedStateSurvivesARestartWithFreshManagerInstances() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        GroupManager groupManager = new GroupManager(mainConfig);
        groupManager.join(playerA, 1);
        groupManager.join(playerB, 2);

        PlotManager plotManager = new PlotManager(plugin, mainConfig);
        Plot plotForGroup1 = new Plot(1, WORLD, 0, 0, 59, 59, 64);
        Plot plotForGroup2 = new Plot(2, WORLD, 260, 0, 319, 59, 64);
        plotManager.importPlots(Map.of(1, plotForGroup1, 2, plotForGroup2), groupManager);

        ScoreManager scoreManager = new ScoreManager(scoreConfig);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block scoredBlock = fakeBlock(WORLD, 5, 65, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(scoredBlock, plotForGroup1, playerA);

        GameStateManager gameStateManager = new GameStateManager(plugin, mainConfig);
        gameStateManager.start();

        DataStore saved = new DataStore(plugin, groupManager, plotManager, scoreManager, gameStateManager);
        saved.save();

        // Komplett frische Instanzen, so als waere der Server neu gestartet.
        GroupManager freshGroupManager = new GroupManager(mainConfig);
        PlotManager freshPlotManager = new PlotManager(plugin, mainConfig);
        ScoreManager freshScoreManager = new ScoreManager(scoreConfig);
        GameStateManager freshGameStateManager = new GameStateManager(plugin, mainConfig);
        DataStore loaded = new DataStore(plugin, freshGroupManager, freshPlotManager, freshScoreManager, freshGameStateManager);

        assertTrue(loaded.load());

        assertEquals(1, freshGroupManager.getGroupOf(playerA).orElseThrow().getId());
        assertEquals(2, freshGroupManager.getGroupOf(playerB).orElseThrow().getId());

        Plot restoredPlot = freshPlotManager.getPlot(1).orElseThrow();
        assertEquals(plotForGroup1.getMinX(), restoredPlot.getMinX());
        assertEquals(plotForGroup1.getMaxX(), restoredPlot.getMaxX());
        assertEquals(plotForGroup1.getMinZ(), restoredPlot.getMinZ());
        assertEquals(plotForGroup1.getMaxZ(), restoredPlot.getMaxZ());

        assertEquals(25, freshScoreManager.getGroupScore(1));
        assertEquals(25, freshScoreManager.getPersonalScore(playerA));

        assertTrue(freshGameStateManager.isRunning());
    }

    @Test
    void loadReturnsFalseWhenNoFileExistsYet() {
        GroupManager groupManager = new GroupManager(mainConfig);
        PlotManager plotManager = new PlotManager(plugin, mainConfig);
        ScoreManager scoreManager = new ScoreManager(scoreConfig);
        GameStateManager gameStateManager = new GameStateManager(plugin, mainConfig);
        DataStore dataStore = new DataStore(plugin, groupManager, plotManager, scoreManager, gameStateManager);

        assertTrue(!dataStore.load());
    }
}
