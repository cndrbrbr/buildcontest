package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Platzierung und Planierung der Bauplaetze (siehe rules.md#bauplaetze).
 *
 * Bauplaetze werden in einem moeglichst quadratischen 2D-Raster um einen
 * Mittelpunkt verteilt (Reihen x Spalten ~ sqrt(Anzahl)), jeweils mit dem
 * konfigurierten min-distance als Abstand zwischen den Rasterzellen in beide
 * Richtungen. Damit ist der tatsaechliche Abstand zwischen Baupaltz-Kanten
 * konstant gleich min-distance <= max-distance, die konfigurierte
 * Obergrenze wird also automatisch eingehalten; eine variable Ausnutzung
 * der Spanne bis max-distance (z. B. zufaellig) ist eine moegliche
 * spaetere Erweiterung, aber fuer die Kernanforderung
 * ("Mindest-/Maximalabstand") nicht notwendig.
 *
 * Der konfigurierte Mittelpunkt (plots.center-x/-z) ist dabei nur ein
 * STARTPUNKT fuer die Suche: da die Bauplatz-Welt bei jedem Contest neu mit
 * zufaelligem Seed erzeugt wird (siehe WorldManager), kann an dieser Stelle
 * Wasser (Ozean/See) liegen. {@link #findLandAnchor} sucht deshalb
 * automatisch einen nahegelegenen, durchgehend trockenen Platz fuer das
 * GESAMTE Bauplatz-Raster und leitet den gemeinsamen Y-Level daraus ab,
 * statt den festen config.yml-Wert fuer alle Gruppen gleich zu verwenden -
 * ein Bauplatz im Wasser waere sonst zufaellig nur fuer einzelne Gruppen ein
 * Nachteil (siehe rules.md#setup-admin).
 */
public final class PlotManager {

    private static final int LAND_SEARCH_RING_STEP = 64;
    private static final int LAND_SEARCH_MAX_RINGS = 15;
    private static final int[][] LAND_SEARCH_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;
    private final Map<Integer, Plot> plotsByGroup = new LinkedHashMap<>();

    public PlotManager(BuildContestPlugin plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    public Optional<Plot> getPlot(int groupId) {
        return Optional.ofNullable(plotsByGroup.get(groupId));
    }

    public Optional<Plot> getPlotAt(String world, int x, int z) {
        return plotsByGroup.values().stream()
                .filter(plot -> plot.containsColumn(world, x, z))
                .findFirst();
    }

    /** Berechnet und planiert die Bauplaetze fuer alle uebergebenen Gruppen neu. */
    public void generatePlots(Iterable<Group> groups) {
        List<Group> groupList = new ArrayList<>();
        groups.forEach(groupList::add);
        if (groupList.isEmpty()) {
            plotsByGroup.clear();
            return;
        }

        String worldName = mainConfig.getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Welt '" + worldName + "' nicht gefunden, Bauplaetze wurden nicht generiert.");
            return;
        }

        int sizeX = mainConfig.getPlotSizeX();
        int sizeZ = mainConfig.getPlotSizeZ();
        int gap = mainConfig.getMinDistance();
        int[] halfExtent = gridHalfExtent(groupList.size(), sizeX, sizeZ, gap);

        LandAnchor anchor = findLandAnchor(world, mainConfig.getCenterX(), mainConfig.getCenterZ(),
                halfExtent[0], halfExtent[1]).orElseGet(() -> {
                    plugin.getLogger().warning("Kein durchgehend trockener Platz fuer die Bauplaetze in der Naehe "
                            + "des konfigurierten Mittelpunkts gefunden (Ozean-Welt?) - verwende Mittelpunkt und "
                            + "surface-y aus config.yml trotzdem, Bauplaetze liegen moeglicherweise im Wasser.");
                    return new LandAnchor(mainConfig.getCenterX(), mainConfig.getCenterZ(), mainConfig.getSurfaceY());
                });

        List<Plot> layout = computeGridLayout(groupList.size(), sizeX, sizeZ, gap,
                anchor.centerX(), anchor.centerZ(), anchor.surfaceY(), worldName);

        plotsByGroup.clear();
        for (int i = 0; i < groupList.size(); i++) {
            Group group = groupList.get(i);
            // computeGridLayout kennt keine Gruppen-IDs, also hier zuordnen.
            Plot template = layout.get(i);
            Plot plot = new Plot(group.getId(), template.getWorldName(),
                    template.getMinX(), template.getMinZ(), template.getMaxX(), template.getMaxZ(),
                    template.getSurfaceY());
            plotsByGroup.put(group.getId(), plot);
            group.setPlot(plot);
            flatten(plot, world);
        }
    }

    /**
     * Reine Layout-Berechnung ohne Bukkit-Weltzugriff (daher gut unit-testbar):
     * ordnet {@code count} gleich grosse Bauplaetze in einem moeglichst
     * quadratischen Raster um den Mittelpunkt an, Kante-zu-Kante-Abstand
     * {@code gap} in X- und Z-Richtung.
     */
    public static List<Plot> computeGridLayout(int count, int sizeX, int sizeZ, int gap,
                                                int centerX, int centerZ, int surfaceY, String worldName) {
        List<Plot> result = new ArrayList<>(count);
        if (count <= 0) {
            return result;
        }

        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);

        int totalWidth = cols * sizeX + (cols - 1) * gap;
        int totalDepth = rows * sizeZ + (rows - 1) * gap;
        int startX = centerX - totalWidth / 2;
        int startZ = centerZ - totalDepth / 2;

        int index = 0;
        for (int row = 0; row < rows && index < count; row++) {
            for (int col = 0; col < cols && index < count; col++) {
                int minX = startX + col * (sizeX + gap);
                int minZ = startZ + row * (sizeZ + gap);
                result.add(new Plot(0, worldName, minX, minZ, minX + sizeX - 1, minZ + sizeZ - 1, surfaceY));
                index++;
            }
        }
        return result;
    }

    /** Halbe Breite/Tiefe des Gesamtrasters, siehe {@link #computeGridLayout}, fuer die Landsuche. */
    private static int[] gridHalfExtent(int count, int sizeX, int sizeZ, int gap) {
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int totalWidth = cols * sizeX + (cols - 1) * gap;
        int totalDepth = rows * sizeZ + (rows - 1) * gap;
        return new int[]{totalWidth / 2, totalDepth / 2};
    }

    private record LandAnchor(int centerX, int centerZ, int surfaceY) {
    }

    /**
     * Sucht ausgehend vom Startpunkt in konzentrischen Ringen nach einem
     * Mittelpunkt, an dem die vier Eckpunkte und die Mitte des gesamten
     * Bauplatz-Rasters durchgehend auf trockenem Land liegen (siehe
     * Klassen-Javadoc). Liefert den Mittelpunkt sowie den dafuer passenden,
     * gemeinsamen Y-Level - bewusst der TIEFSTE Bodenpunkt der Stichproben,
     * nicht der hoechste: so wird beim Planieren immer nur ausgeschachtet
     * (natuerliches Terrain oberhalb des Levels abgetragen), nie mit
     * Fuellmaterial aufgeschuettet. Ein aufgeschuetteter Bauplatz koennte an
     * tieferen Stellen auf einem Hohlraum "schweben", von dem Spieler nicht
     * mehr auf natuerlichem Weg hochkommen.
     */
    private Optional<LandAnchor> findLandAnchor(World world, int startX, int startZ, int halfWidth, int halfDepth) {
        OptionalInt startGround = checkAllLand(world, startX, startZ, halfWidth, halfDepth);
        if (startGround.isPresent()) {
            return Optional.of(new LandAnchor(startX, startZ, startGround.getAsInt()));
        }

        for (int ring = 1; ring <= LAND_SEARCH_MAX_RINGS; ring++) {
            int offset = ring * LAND_SEARCH_RING_STEP;
            for (int[] direction : LAND_SEARCH_DIRECTIONS) {
                int candidateX = startX + direction[0] * offset;
                int candidateZ = startZ + direction[1] * offset;
                OptionalInt ground = checkAllLand(world, candidateX, candidateZ, halfWidth, halfDepth);
                if (ground.isPresent()) {
                    return Optional.of(new LandAnchor(candidateX, candidateZ, ground.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Prueft Mittelpunkt und vier Eckpunkte des Bauplatz-Rasters an dieser
     * Kandidaten-Position: liegt an JEDEM Punkt Wasser (Hoehe inkl.
     * Fluessigkeiten > tatsaechlicher Boden), gilt die Position als
     * ungeeignet. Sonst wird der TIEFSTE Bodenpunkt der Stichproben
     * zurueckgegeben (siehe {@link #findLandAnchor}, Bauplatz-Oberflaeche
     * liegt direkt darueber - nie darunter aufgefuellt).
     */
    private OptionalInt checkAllLand(World world, int centerX, int centerZ, int halfWidth, int halfDepth) {
        int[][] samplePoints = {
                {centerX, centerZ},
                {centerX - halfWidth, centerZ - halfDepth},
                {centerX + halfWidth, centerZ - halfDepth},
                {centerX - halfWidth, centerZ + halfDepth},
                {centerX + halfWidth, centerZ + halfDepth},
        };

        int minGround = Integer.MAX_VALUE;
        for (int[] point : samplePoints) {
            int ground = world.getHighestBlockYAt(point[0], point[1], HeightMap.OCEAN_FLOOR);
            int surfaceWithLiquids = world.getHighestBlockYAt(point[0], point[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (surfaceWithLiquids > ground) {
                return OptionalInt.empty();
            }
            minGround = Math.min(minGround, ground);
        }
        return OptionalInt.of(minGround);
    }

    /**
     * Planiert einen Bauplatz auf den gemeinsamen surfaceY (siehe
     * {@link #findLandAnchor}: der tiefste Bodenpunkt der Stichproben, damit
     * hier immer nur ausgeschachtet statt aufgefuellt wird). Alles oberhalb
     * wird abgetragen, darunter liegt garantiert bereits natuerlicher,
     * solider Boden.
     */
    private void flatten(Plot plot, World world) {
        int surfaceY = plot.getSurfaceY();
        int clearUpTo = Math.min(world.getMaxHeight() - 1, surfaceY + 150);
        int fillDownTo = Math.max(world.getMinHeight(), surfaceY - 4);

        for (int x = plot.getMinX(); x <= plot.getMaxX(); x++) {
            for (int z = plot.getMinZ(); z <= plot.getMaxZ(); z++) {
                for (int y = clearUpTo; y > surfaceY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
                for (int y = surfaceY; y > fillDownTo; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(y == surfaceY ? Material.GRASS_BLOCK : Material.DIRT, false);
                }
            }
        }
    }

    /** Fuer die Persistenz (siehe persistence.DataStore): aktueller Stand ohne erneutes Planieren. */
    public Map<Integer, Plot> exportPlots() {
        return plotsByGroup;
    }

    /**
     * Stellt Bauplaetze nach einem Neustart wieder her, OHNE sie erneut zu
     * planieren - die Bloecke stehen ja bereits in der Welt (siehe
     * persistence.DataStore). Verknuepft die Plots zusaetzlich mit den
     * passenden Gruppen, damit z. B. Schutzmechanismen sofort wieder greifen.
     */
    public void importPlots(Map<Integer, Plot> plots, GroupManager groupManager) {
        plotsByGroup.clear();
        plotsByGroup.putAll(plots);
        plots.forEach((groupId, plot) -> groupManager.getGroup(groupId).ifPresent(group -> group.setPlot(plot)));
    }

    /**
     * Verwirft alle Bauplatz-Zuordnungen, z. B. nachdem die Bauplatz-Welt
     * geloescht wurde (siehe WorldManager#delete, rules.md#spielablauf--ende)
     * und die bisherigen Plot-Grenzen damit gegenstandslos sind.
     */
    public void clear(GroupManager groupManager) {
        plotsByGroup.keySet().forEach(groupId -> groupManager.getGroup(groupId).ifPresent(group -> group.setPlot(null)));
        plotsByGroup.clear();
    }
}
