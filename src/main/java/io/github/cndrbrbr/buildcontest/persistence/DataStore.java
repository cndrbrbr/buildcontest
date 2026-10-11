package io.github.cndrbrbr.buildcontest.persistence;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.model.Plot;
import io.github.cndrbrbr.buildcontest.util.BlockKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Speichert/laedt den kompletten Laufzeitzustand (Gruppenmitgliedschaft,
 * Bauplaetze, getrackte Bloecke inkl. Verbindungsstatus, Spielstatus) in einer Datei im
 * Plugin-Datenordner, damit ein Server-Neustart waehrend eines laufenden
 * Contests nicht den gesamten Fortschritt verwirft.
 *
 * Die platzierten Bloecke selbst werden bereits durch Minecrafts
 * Welt-Speicherung persistiert - hier wird nur die zusaetzliche Buchfuehrung
 * des Plugins gesichert (wer ist in welcher Gruppe, wo liegen die Bauplaetze,
 * welche Bloecke zaehlen fuer wen).
 */
public final class DataStore {

    private static final String FILE_NAME = "data.yml";
    private static final String RESULTS_FILE_NAME = "results.yml";

    /** Ein Gruppen-Endstand fuer das Ergebnis-Archiv, siehe {@link #appendResult}. */
    public record ResultEntry(int groupId, String groupName, int score) {
    }

    private final BuildContestPlugin plugin;
    private final GroupManager groupManager;
    private final PlotManager plotManager;
    private final ScoreManager scoreManager;
    private final GameStateManager gameStateManager;

    public DataStore(BuildContestPlugin plugin, GroupManager groupManager, PlotManager plotManager,
                      ScoreManager scoreManager, GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.groupManager = groupManager;
        this.plotManager = plotManager;
        this.scoreManager = scoreManager;
        this.gameStateManager = gameStateManager;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        GameStateManager.State state = gameStateManager.exportState();
        yaml.set("state.running", state.running());
        yaml.set("state.paused", state.paused());
        yaml.set("state.frozen", state.frozen());
        yaml.set("state.end-timestamp", state.endTimestampMillis());
        yaml.set("state.remaining-at-pause", state.remainingMillisAtPause());
        yaml.set("state.teleported-players",
                state.teleportedPlayers().stream().map(UUID::toString).toList());

        groupManager.exportMembership().forEach((groupId, members) ->
                yaml.set("groups." + groupId, members.stream().map(UUID::toString).toList()));

        plotManager.exportPlots().forEach((groupId, plot) -> {
            String path = "plots." + groupId + ".";
            yaml.set(path + "world", plot.getWorldName());
            yaml.set(path + "min-x", plot.getMinX());
            yaml.set(path + "min-z", plot.getMinZ());
            yaml.set(path + "max-x", plot.getMaxX());
            yaml.set(path + "max-z", plot.getMaxZ());
            yaml.set(path + "surface-y", plot.getSurfaceY());
        });

        List<String> trackedBlockLines = scoreManager.exportEntries().stream()
                .map(entry -> entry.key().world() + "|" + entry.key().x() + "|" + entry.key().y() + "|"
                        + entry.key().z() + "|" + entry.groupId() + "|" + entry.placer() + "|"
                        + entry.material().name() + "|" + entry.points() + "|" + entry.connected())
                .toList();
        yaml.set("tracked-blocks", trackedBlockLines);

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Spielstand nicht speichern: " + file, exception);
        }
    }

    /**
     * Haengt den Endstand eines abgeschlossenen Contests an eine eigene,
     * dauerhafte Archivdatei ({@value #RESULTS_FILE_NAME}) an - unabhaengig
     * von {@value #FILE_NAME}, damit ein spaeterer /bc start
     * (scoreManager.clear()) oder das Loeschen der Bauplatz-Welt den
     * historischen Endstand NICHT mit beseitigt (siehe
     * rules.md#persistenz-und-absturzsicherheit: "Nur Ergebnisse und
     * Statistiken werden archiviert, kein Weltbackup").
     */
    public void appendResult(List<ResultEntry> ranking) {
        File file = new File(plugin.getDataFolder(), RESULTS_FILE_NAME);
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        List<Map<?, ?>> contests = new ArrayList<>(yaml.getMapList("contests"));
        Map<String, Object> contestEntry = new LinkedHashMap<>();
        contestEntry.put("ended-at", System.currentTimeMillis());
        List<Map<String, Object>> groups = new ArrayList<>();
        for (ResultEntry entry : ranking) {
            Map<String, Object> groupEntry = new LinkedHashMap<>();
            groupEntry.put("group-id", entry.groupId());
            groupEntry.put("name", entry.groupName());
            groupEntry.put("score", entry.score());
            groups.add(groupEntry);
        }
        contestEntry.put("groups", groups);
        contests.add(contestEntry);
        yaml.set("contests", contests);

        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Ergebnis-Archiv nicht speichern: " + file, exception);
        }
    }

    /** @return true, wenn eine gespeicherte Datei gefunden und geladen wurde. */
    public boolean load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Set<UUID> teleportedPlayers = yaml.getStringList("state.teleported-players").stream()
                .map(this::parseUuidOrNull)
                .filter(uuid -> uuid != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        gameStateManager.importState(new GameStateManager.State(
                yaml.getBoolean("state.running", false),
                yaml.getBoolean("state.paused", false),
                yaml.getBoolean("state.frozen", false),
                yaml.getLong("state.end-timestamp", -1),
                yaml.getLong("state.remaining-at-pause", -1),
                teleportedPlayers));

        groupManager.importMembership(readMembership(yaml));
        plotManager.importPlots(readPlots(yaml), groupManager);

        for (String line : yaml.getStringList("tracked-blocks")) {
            parseTrackedBlockLine(line).ifPresent(scoreManager::importEntry);
        }

        int corrected = reconcileTrackedBlocksWithWorld();
        if (corrected > 0) {
            plugin.getLogger().warning(corrected + " getrackte(r) Block(bloecke) stimmten nach dem Neustart "
                    + "nicht mehr mit der tatsaechlichen Welt ueberein (z. B. durch einen Absturz vor dem letzten "
                    + "Speichern bereits zerstoert/veraendert) und wurden aus der Wertung entfernt.");
        }

        return true;
    }

    /**
     * Konsistenzpruefung nach einem Neustart (siehe
     * rules.md#persistenz-und-absturzsicherheit): die WELT selbst wird
     * bereits von Minecraft gespeichert, kann zwischen dem letzten Autosave
     * und einem Absturz aber weitergelaufen sein (Bloecke abgebaut/ersetzt),
     * waehrend data.yml noch den aelteren Stand zeigt. Jeder getrackte Block,
     * dessen tatsaechliches Material in der echten Welt nicht mehr passt,
     * wird entfernt (inkl. gezielter Neuprüfung dadurch abhaengiger
     * Nachbarn), statt fuer ein laengst veraendertes/nicht mehr existierendes
     * Bauwerk weiter Punkte zu vergeben.
     *
     * @return Anzahl der dabei entfernten/korrigierten Bloecke.
     */
    private int reconcileTrackedBlocksWithWorld() {
        int corrected = 0;
        for (ScoreManager.Entry entry : new ArrayList<>(scoreManager.exportEntries())) {
            World world = plugin.getServer().getWorld(entry.key().world());
            Material actual = world == null
                    ? null
                    : world.getBlockAt(entry.key().x(), entry.key().y(), entry.key().z()).getType();
            if (actual == entry.material()) {
                continue;
            }
            int surfaceY = plotManager.getPlot(entry.groupId()).map(Plot::getSurfaceY).orElse(Integer.MIN_VALUE);
            if (scoreManager.invalidate(entry.key(), surfaceY)) {
                corrected++;
            }
        }
        return corrected;
    }

    private Map<Integer, Set<UUID>> readMembership(YamlConfiguration yaml) {
        Map<Integer, Set<UUID>> membership = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("groups");
        if (section == null) {
            return membership;
        }
        for (String key : section.getKeys(false)) {
            Integer groupId = parseIntKey(key);
            if (groupId == null) {
                continue;
            }
            Set<UUID> members = new LinkedHashSet<>();
            for (String uuid : section.getStringList(key)) {
                try {
                    members.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ungueltige Spieler-UUID in data.yml ignoriert: " + uuid);
                }
            }
            membership.put(groupId, members);
        }
        return membership;
    }

    private Map<Integer, Plot> readPlots(YamlConfiguration yaml) {
        Map<Integer, Plot> plots = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("plots");
        if (section == null) {
            return plots;
        }
        for (String key : section.getKeys(false)) {
            Integer groupId = parseIntKey(key);
            ConfigurationSection plotSection = section.getConfigurationSection(key);
            if (groupId == null || plotSection == null) {
                continue;
            }
            plots.put(groupId, new Plot(groupId, plotSection.getString("world", "world"),
                    plotSection.getInt("min-x"), plotSection.getInt("min-z"),
                    plotSection.getInt("max-x"), plotSection.getInt("max-z"),
                    plotSection.getInt("surface-y")));
        }
        return plots;
    }

    private Optional<ScoreManager.Entry> parseTrackedBlockLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 9) {
            plugin.getLogger().warning("Ungueltige Zeile in data.yml (tracked-blocks) ignoriert: " + line);
            return Optional.empty();
        }
        try {
            BlockKey key = new BlockKey(parts[0],
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            int groupId = Integer.parseInt(parts[4]);
            UUID placer = UUID.fromString(parts[5]);
            Material material = Material.valueOf(parts[6]);
            int points = Integer.parseInt(parts[7]);
            boolean connected = Boolean.parseBoolean(parts[8]);
            return Optional.of(new ScoreManager.Entry(key, groupId, placer, material, points, connected));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ungueltige Zeile in data.yml (tracked-blocks) ignoriert: " + line);
            return Optional.empty();
        }
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Ungueltige Spieler-UUID in data.yml (state.teleported-players) ignoriert: " + value);
            return null;
        }
    }

    private Integer parseIntKey(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            plugin.getLogger().warning("Ungueltiger Gruppen-Schluessel in data.yml ignoriert: " + key);
            return null;
        }
    }
}
