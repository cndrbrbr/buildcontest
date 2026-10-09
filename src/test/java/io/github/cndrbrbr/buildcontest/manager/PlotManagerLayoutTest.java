package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.model.Plot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die reine Rastergeometrie aus PlotManager#computeGridLayout (siehe
 * rules.md#setup-admin, "Bauplätze werden automatisch im Weltraster
 * verteilt"): keine Ueberlappungen, Kante-zu-Kante-Abstand == gap, und ein
 * moeglichst quadratisches Raster statt einer einzelnen Reihe.
 */
class PlotManagerLayoutTest {

    private static final int SIZE = 60;
    private static final int GAP = 200;
    private static final int SURFACE_Y = 64;

    @Test
    void singleGroupGetsOnePlotCenteredOnOrigin() {
        List<Plot> plots = PlotManager.computeGridLayout(1, SIZE, SIZE, GAP, 0, 0, SURFACE_Y, "world");

        assertEquals(1, plots.size());
        Plot plot = plots.get(0);
        assertEquals(SIZE, plot.getMaxX() - plot.getMinX() + 1);
        assertEquals(SIZE, plot.getMaxZ() - plot.getMinZ() + 1);
    }

    @Test
    void fourGroupsFormATwoByTwoGrid() {
        List<Plot> plots = PlotManager.computeGridLayout(4, SIZE, SIZE, GAP, 0, 0, SURFACE_Y, "world");

        assertEquals(4, plots.size());
        assertNoOverlaps(plots);

        // Reihe 0: Plots 0 und 1 muessen entlang X um genau GAP getrennt sein.
        Plot left = plots.get(0);
        Plot right = plots.get(1);
        assertEquals(left.getMinZ(), right.getMinZ(), "gleiche Reihe -> gleiches minZ");
        assertEquals(GAP, right.getMinX() - left.getMaxX() - 1);
    }

    @Test
    void sevenGroupsUseACeilingSquareGridNotASingleRow() {
        // sqrt(7) ~ 2.65 -> 3 Spalten, 3 Reihen (letzte Zelle bleibt frei).
        List<Plot> plots = PlotManager.computeGridLayout(7, SIZE, SIZE, GAP, 0, 0, SURFACE_Y, "world");

        assertEquals(7, plots.size());
        assertNoOverlaps(plots);

        long distinctMinZ = plots.stream().map(Plot::getMinZ).distinct().count();
        assertTrue(distinctMinZ > 1, "sollte mehrere Reihen nutzen, keine einzelne Reihe entlang X");
    }

    @Test
    void zeroGroupsProduceEmptyLayout() {
        assertTrue(PlotManager.computeGridLayout(0, SIZE, SIZE, GAP, 0, 0, SURFACE_Y, "world").isEmpty());
    }

    private static void assertNoOverlaps(List<Plot> plots) {
        for (int i = 0; i < plots.size(); i++) {
            for (int j = i + 1; j < plots.size(); j++) {
                Plot a = plots.get(i);
                Plot b = plots.get(j);
                boolean overlap = a.getMinX() <= b.getMaxX() && b.getMinX() <= a.getMaxX()
                        && a.getMinZ() <= b.getMaxZ() && b.getMinZ() <= a.getMaxZ();
                assertTrue(!overlap, "Plots " + i + " und " + j + " ueberlappen sich");
            }
        }
    }
}
