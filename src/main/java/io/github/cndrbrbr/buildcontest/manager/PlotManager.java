package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Platzierung und Planierung der Bauplaetze (siehe rules.md#bauplaetze).
 *
 * Erste einfache Umsetzung: Bauplaetze werden in einer Reihe entlang der
 * X-Achse um den konfigurierten Mittelpunkt verteilt, jeweils mit dem
 * minimalen konfigurierten Abstand (min-distance) zueinander. max-distance
 * wird dadurch automatisch eingehalten, aber nicht aktiv genutzt - ein
 * rasterfoermiges oder zufaelliges Layout fuer sehr viele Gruppen ist eine
 * moegliche spaetere Erweiterung.
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
        plotsByGroup.clear();

        int sizeX = mainConfig.getPlotSizeX();
        int sizeZ = mainConfig.getPlotSizeZ();
        int gap = mainConfig.getMinDistance();
        String worldName = mainConfig.getWorldName();

        int count = 0;
        for (Group ignored : groups) {
            count++;
        }
        if (count == 0) {
            return;
        }

        int totalWidth = count * sizeX + (count - 1) * gap;
        int startX = mainConfig.getCenterX() - totalWidth / 2;
        int minZ = mainConfig.getCenterZ() - sizeZ / 2;

        int index = 0;
        for (Group group : groups) {
            int minX = startX + index * (sizeX + gap);
            int maxX = minX + sizeX - 1;
            int maxZ = minZ + sizeZ - 1;

            Plot plot = new Plot(group.getId(), worldName, minX, minZ, maxX, maxZ, mainConfig.getSurfaceY());
            plotsByGroup.put(group.getId(), plot);
            group.setPlot(plot);

            flatten(plot);
            index++;
        }
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
}
