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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft die Kernlogik aus rules.md#bauregeln-und-verbindungsprüfung,
 * #punktesystem und #manipulationssichere-blockerfassung: Platzierungen
 * werden nie abgelehnt, 0-Punkte-Bloecke duerfen verbinden, Abbau eines
 * tragenden Blocks zieht abhaengige Bloecke gezielt mit ab, und
 * Wiederverbindung stellt Punkte ohne erneutes Platzieren wieder her.
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

    /**
     * Material#isSolid() fragt live die Server-Block-Registry ab und braucht
     * dafuer einen laufenden Bukkit-Server (siehe ScoreManager) - in Tests
     * stattdessen ein fest verdrahtetes Praedikat fuer die hier verwendeten
     * Materialien nutzen, das exakt die reale isSolid()-Antwort nachbildet.
     */
    private static boolean isSolidForTest(Material material) {
        return material != Material.AIR && material != Material.TORCH;
    }

    @BeforeEach
    void setUp() {
        scoreManager = new ScoreManager(scoreConfig, ScoreManagerTest::isSolidForTest);
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
    void disconnectedScoredBlockCountsZeroButIsNotRejected() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        // Nicht auf dem Fundament (surfaceY+1) UND kein verbundener Nachbar -> schwebend.
        Block floating = fakeBlock(WORLD, 5, SURFACE_Y + 5, 5, Material.STONE);

        scoreManager.registerPlacement(floating, plotA, placer);

        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(0, scoreManager.getPersonalScore(placer));
    }

    @Test
    void nonSolidDecorationIsAlwaysAllowedAndNeverScored() {
        // isTrackable() filtert TORCH schon vor dem Score-Lookup heraus - kein Stub noetig.
        Block torch = fakeBlock(WORLD, 5, SURFACE_Y + 50, 5, Material.TORCH);

        scoreManager.registerPlacement(torch, plotA, UUID.randomUUID());

        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
    }

    @Test
    void zeroPointBlockCanBridgeConnectionToAScoredBlock() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.GLASS)).thenReturn(0);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);

        // Glas (0 Punkte) steht direkt auf dem Fundament - verbindet, zaehlt aber 0.
        Block glass = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.GLASS);
        scoreManager.registerPlacement(glass, plotA, placer);

        // Diamantblock haengt NUR am Glas, nicht direkt am Fundament.
        Block diamond = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(diamond, plotA, placer);

        assertEquals(25, scoreManager.getGroupScore(plotA.getGroupId()));
    }

    @Test
    void breakingASupportingBlockDisconnectsDependentBlocks() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);

        Block base = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(base, plotA, placer);
        Block diamond = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(diamond, plotA, placer);
        assertEquals(26, scoreManager.getGroupScore(plotA.getGroupId()));

        // Die einzige tragende Verbindung zum Fundament wird entfernt.
        scoreManager.registerBreak(base, plotA);

        // Diamantblock steht zwar noch, ist aber jetzt unverbunden -> 0 Punkte.
        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(0, scoreManager.getPersonalScore(placer));
    }

    @Test
    void reconnectingRestoresPointsWithoutReplacingTheBlock() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);

        Block base = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(base, plotA, placer);
        Block diamond = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(diamond, plotA, placer);
        scoreManager.registerBreak(base, plotA);
        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));

        // Neuer Block verbindet den Diamantblock wieder ans Fundament - der
        // Diamantblock selbst wird dabei nicht neu platziert.
        Block newBase = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(newBase, plotA, placer);

        assertEquals(26, scoreManager.getGroupScore(plotA.getGroupId()));
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
    void pistonMoveKeepsPointsAndTracksTheNewPosition() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block diamond = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(diamond, plotA, placer);

        // Seitlich auf dem Fundament verschoben -> bleibt verbunden, kein Doppel-/Phantompunkt.
        scoreManager.registerMove(List.of(diamond), BlockFace.EAST, plotA);
        assertEquals(25, scoreManager.getGroupScore(plotA.getGroupId()));

        // Abbau an der ALTEN Position darf nichts mehr abziehen ...
        scoreManager.registerBreak(diamond, plotA);
        assertEquals(25, scoreManager.getGroupScore(plotA.getGroupId()));

        // ... Abbau an der NEUEN Position schon.
        scoreManager.registerBreak(fakeBlock(WORLD, 6, SURFACE_Y + 1, 5, Material.DIAMOND_BLOCK), plotA);
        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(0, scoreManager.getPersonalScore(placer));
    }

    @Test
    void pistonMovingASupportingBlockAwayDisconnectsDependents() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block base = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(base, plotA, placer);
        Block diamond = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(diamond, plotA, placer);
        assertEquals(26, scoreManager.getGroupScore(plotA.getGroupId()));

        // Nur der Sockel wird weggeschoben, der Diamantblock schwebt danach.
        scoreManager.registerMove(List.of(base), BlockFace.EAST, plotA);

        assertEquals(1, scoreManager.getGroupScore(plotA.getGroupId()));
    }

    @Test
    void pistonPushingAStackUpLiftsItOffTheFoundation() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block lower = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(lower, plotA, placer);
        Block upper = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.STONE);
        scoreManager.registerPlacement(upper, plotA, placer);

        // Beide Bloecke gemeinsam eine Ebene hoch - kein Kontakt mehr zum Fundament.
        scoreManager.registerMove(List.of(lower, upper), BlockFace.UP, plotA);
        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));

        // Und wieder zurueck nach unten - beide Punkte genau einmal zurueck.
        Block movedLower = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.STONE);
        Block movedUpper = fakeBlock(WORLD, 5, SURFACE_Y + 3, 5, Material.STONE);
        scoreManager.registerMove(List.of(movedUpper, movedLower), BlockFace.DOWN, plotA);
        assertEquals(2, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(2, scoreManager.getPersonalScore(placer));
    }

    @Test
    void pistonMoveCanReconnectAFloatingStructure() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block floating = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        scoreManager.registerPlacement(floating, plotA, placer);
        Block side = fakeBlock(WORLD, 4, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(side, plotA, placer);
        assertEquals(1, scoreManager.getGroupScore(plotA.getGroupId()));

        // Steinblock unter den schwebenden Diamantblock geschoben.
        scoreManager.registerMove(List.of(side), BlockFace.EAST, plotA);

        assertEquals(26, scoreManager.getGroupScore(plotA.getGroupId()));
    }

    @Test
    void pistonMovingUntrackedBlocksChangesNothing() {
        scoreManager.registerMove(List.of(fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE)), BlockFace.EAST, plotA);

        assertEquals(0, scoreManager.getGroupScore(plotA.getGroupId()));
        assertEquals(0, scoreManager.exportEntries().size());
    }

    @Test
    void exportAndImportEntriesRoundTripsScoresAndConnectivity() {
        UUID placer = UUID.randomUUID();
        when(scoreConfig.getScore(Material.STONE)).thenReturn(1);
        Block block = fakeBlock(WORLD, 5, SURFACE_Y + 1, 5, Material.STONE);
        scoreManager.registerPlacement(block, plotA, placer);

        List<ScoreManager.Entry> exported = scoreManager.exportEntries();
        assertEquals(1, exported.size());

        ScoreManager restored = new ScoreManager(scoreConfig, ScoreManagerTest::isSolidForTest);
        exported.forEach(restored::importEntry);

        assertEquals(scoreManager.getGroupScore(plotA.getGroupId()), restored.getGroupScore(plotA.getGroupId()));
        assertEquals(scoreManager.getPersonalScore(placer), restored.getPersonalScore(placer));

        // Nach dem Wiederherstellen muss die Zusammenhangspruefung ebenfalls
        // funktionieren, sonst koennten nach einem Neustart keine weiteren
        // Bloecke mehr an den Bestand angebaut werden.
        when(scoreConfig.getScore(Material.DIAMOND_BLOCK)).thenReturn(25);
        Block onTop = fakeBlock(WORLD, 5, SURFACE_Y + 2, 5, Material.DIAMOND_BLOCK);
        restored.registerPlacement(onTop, plotA, placer);
        assertEquals(26, restored.getGroupScore(plotA.getGroupId()));
    }
}
