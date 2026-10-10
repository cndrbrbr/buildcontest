package io.github.cndrbrbr.buildcontest.config;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/** Typisierter Zugriff auf config.yml (Gruppen, Bauplaetze, Spieldauer). */
public final class MainConfig {

    public enum AssignmentMode { FREE, CONFIG }

    private final BuildContestPlugin plugin;

    private int groupCount;
    private int groupMaxSize;
    private AssignmentMode assignmentMode;
    private final Map<String, Integer> presetAssignments = new HashMap<>();

    private int plotSizeX;
    private int plotSizeZ;
    private int minDistance;
    private int maxDistance;
    private int surfaceY;
    private int centerX;
    private int centerZ;
    private String worldName;

    private int durationMinutes;
    private int autosaveMinutes;
    private boolean autoDeleteWorldOnEnd;

    public MainConfig(BuildContestPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        groupCount = config.getInt("groups.count", 2);
        groupMaxSize = config.getInt("groups.max-size", 5);
        assignmentMode = AssignmentMode.valueOf(
                config.getString("groups.assignment-mode", "FREE").toUpperCase());

        presetAssignments.clear();
        ConfigurationSection assignments = config.getConfigurationSection("groups.assignments");
        if (assignments != null) {
            for (String playerName : assignments.getKeys(false)) {
                presetAssignments.put(playerName.toLowerCase(), assignments.getInt(playerName));
            }
        }

        plotSizeX = config.getInt("plots.size-x", 60);
        plotSizeZ = config.getInt("plots.size-z", 60);
        minDistance = config.getInt("plots.min-distance", 200);
        maxDistance = config.getInt("plots.max-distance", 500);
        surfaceY = config.getInt("plots.surface-y", 64);
        centerX = config.getInt("plots.center-x", 0);
        centerZ = config.getInt("plots.center-z", 0);
        worldName = config.getString("plots.world", "buildcontest_world");

        durationMinutes = config.getInt("game.duration-minutes", 0);
        autosaveMinutes = config.getInt("game.autosave-minutes", 5);
        autoDeleteWorldOnEnd = config.getBoolean("game.auto-delete-world-on-end", false);
    }

    public int getGroupCount() {
        return groupCount;
    }

    public int getGroupMaxSize() {
        return groupMaxSize;
    }

    public AssignmentMode getAssignmentMode() {
        return assignmentMode;
    }

    public Map<String, Integer> getPresetAssignments() {
        return presetAssignments;
    }

    public int getPlotSizeX() {
        return plotSizeX;
    }

    public int getPlotSizeZ() {
        return plotSizeZ;
    }

    public int getMinDistance() {
        return minDistance;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public int getSurfaceY() {
        return surfaceY;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    /** Intervall fuer das automatische Sichern des Spielstands, siehe persistence.DataStore. 0 = deaktiviert. */
    public int getAutosaveMinutes() {
        return autosaveMinutes;
    }

    /** Ob /bc end die Bauplatz-Welt automatisch loeschen soll, siehe WorldManager#delete. */
    public boolean isAutoDeleteWorldOnEnd() {
        return autoDeleteWorldOnEnd;
    }
}
