package io.github.cndrbrbr.buildcontest.config;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Laedt die Punktetabelle aus scoreboard-config.yml (siehe rules.md#punktesystem).
 * Alles, was dort nicht gelistet ist, bekommt default_score (i. d. R. 0).
 */
public final class ScoreConfig {

    private static final String[] SHAPE_SUFFIXES = {
            "_STAIRS", "_SLAB", "_WALL", "_FENCE_GATE", "_FENCE", "_DOOR", "_TRAPDOOR"
    };

    private final BuildContestPlugin plugin;
    private final Map<Material, Integer> scores = new EnumMap<>(Material.class);
    private int defaultScore;

    public ScoreConfig(BuildContestPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        scores.clear();

        File file = new File(plugin.getDataFolder(), "scoreboard-config.yml");
        if (!file.exists()) {
            plugin.saveResource("scoreboard-config.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        defaultScore = yaml.getInt("default_score", 0);

        ConfigurationSection section = yaml.getConfigurationSection("scores");
        if (section == null) {
            plugin.getLogger().warning("scoreboard-config.yml enthaelt keinen 'scores'-Abschnitt.");
            return;
        }

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().log(Level.WARNING, "Unbekanntes Material in scoreboard-config.yml: {0}", key);
                continue;
            }
            scores.put(material, section.getInt(key));
        }
    }

    public int getScore(Material material) {
        Integer direct = scores.get(material);
        if (direct != null) {
            return direct;
        }
        return scores.getOrDefault(resolveBaseMaterial(material), defaultScore);
    }

    /**
     * Formvarianten (Treppen, Stufen, Waende, Zaeune, Tore, Tueren, Falltueren) erben
     * den Punktwert ihres Grundmaterials, siehe rules.md#punktesystem.
     *
     * Bekannte Einschraenkung: Minecraft benennt manche Formvarianten uneinheitlich
     * (z. B. STONE_BRICK_STAIRS vs. STONE_BRICKS, OAK_STAIRS vs. OAK_PLANKS). Diese
     * Sonderfaelle greift die einfache Suffix-Heuristik hier nicht - fuer sie bei
     * Bedarf einen expliziten Eintrag in scoreboard-config.yml ergaenzen.
     */
    private Material resolveBaseMaterial(Material material) {
        String name = material.name();

        for (String suffix : SHAPE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                Material base = Material.matchMaterial(name.substring(0, name.length() - suffix.length()));
                if (base != null) {
                    return base;
                }
            }
        }
        if (name.startsWith("STRIPPED_")) {
            Material base = Material.matchMaterial(name.substring("STRIPPED_".length()));
            if (base != null) {
                return base;
            }
        }
        if (name.endsWith("_WOOD")) {
            Material base = Material.matchMaterial(name.substring(0, name.length() - "_WOOD".length()) + "_LOG");
            if (base != null) {
                return base;
            }
        }
        return material;
    }

    public int getDefaultScore() {
        return defaultScore;
    }

    public Map<Material, Integer> getAllScores() {
        return scores;
    }
}
