package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Platzierung und Planierung der Bauplaetze (siehe rules.md#bauplaetze).
 *
 * Bauplaetze werden in einem moeglichst quadratischen 2D-Raster um den
 * konfigurierten Mittelpunkt verteilt (Reihen x Spalten ~ sqrt(Anzahl)),
 * jeweils mit dem konfigurierten min-distance als Abstand zwischen den
 * Rasterzellen in beide Richtungen. Damit ist der tatsaechliche Abstand
 * zwischen Baupaltz-Kanten konstant gleich min-distance <= max-distance,
 * die konfigurierte Obergrenze wird also automatisch eingehalten; eine
 * variable Ausnutzung der Spanne bis max-distance (z. B. zufaellig) ist
 * eine moegliche spaetere Erweiterung, aber fuer die Kernanforderung
 * ("Mindest-/Maximalabstand") nicht notwendig.
 */
public final class PlotManager {

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

        List<Plot> layout = computeGridLayout(
                groupList.size(),
                mainConfig.getPlotSizeX(), mainConfig.getPlotSizeZ(),
                mainConfig.getMinDistance(),
                mainConfig.getCenterX(), mainConfig.getCenterZ(),
                mainConfig.getSurfaceY(), mainConfig.getWorldName());

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
            flatten(plot);
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

    private void flatten(Plot plot) {
        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Welt '" + plot.getWorldName() + "' nicht gefunden, Bauplatz "
                    + plot.getGroupId() + " wurde nicht planiert.");
            return;
        }

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
}
