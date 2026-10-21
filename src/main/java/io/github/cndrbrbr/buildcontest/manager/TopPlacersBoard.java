package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leuchtende Top-30-Tafel in der Eingangswelt: wer ueber alle Contests die
 * meisten Bloecke gesetzt hat. Gezaehlt wird jede Platzierung in der
 * Contest-Welt, solange ein Contest laeuft und nicht pausiert ist (auch
 * ausserhalb der Bauplaetze), dauerhaft in placed-blocks.yml. Position in
 * config.yml unter toplist (siehe /bc placetoplist), sonst neben der
 * Statustafel (siehe AutomodeManager).
 */
public final class TopPlacersBoard implements Listener {

    public static final int SIZE = 30;
    private static final long REFRESH_TICKS = 60L * 20L;

    public record Entry(String name, long blocks) {
    }

    private final BuildContestPlugin plugin;
    private final AutomodeManager automodeManager;
    private final File countsFile;
    private final Map<UUID, Long> counts = new HashMap<>();
    private boolean dirty;
    private List<Entry> ranking = List.of();
    private TextDisplay board;
    private Location location;

    public TopPlacersBoard(BuildContestPlugin plugin, AutomodeManager automodeManager) {
        this.plugin = plugin;
        this.automodeManager = automodeManager;
        this.countsFile = new File(plugin.getDataFolder(), "placed-blocks.yml");
        loadLocation();
        loadCounts();
        recomputeRanking();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
                recomputeRanking();
            }
        }, REFRESH_TICKS, REFRESH_TICKS);
        // Nicht persistent - nach Chunk-Entladen zeitnah neu spawnen.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateDisplay, 40L, 20L);
    }

    public List<Entry> getRanking() {
        return ranking;
    }

    /** {@code /bc placetoplist}: Tafel an die Position des Admins setzen. */
    public void placeAt(Location location) {
        this.location = location.clone();
        plugin.getConfig().set("toplist.world", location.getWorld().getName());
        plugin.getConfig().set("toplist.x", location.getX());
        plugin.getConfig().set("toplist.y", location.getY());
        plugin.getConfig().set("toplist.z", location.getZ());
        plugin.saveConfig();
        removeBoard();
        updateDisplay();
    }

    private void loadLocation() {
        String worldName = plugin.getConfig().getString("toplist.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world != null) {
            location = new Location(world, plugin.getConfig().getDouble("toplist.x"),
                    plugin.getConfig().getDouble("toplist.y"), plugin.getConfig().getDouble("toplist.z"));
        }
    }

    /** Ohne eigene Position: rechts neben den Start-Steinen, auf Hoehe der Steine. */
    private Location effectiveLocation() {
        if (location != null) {
            return location;
        }
        Location statusBoard = automodeManager.getBoardLocation();
        return statusBoard != null ? statusBoard.clone().add(6, -1.5, 0) : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        GameStateManager state = plugin.getGameStateManager();
        if (!state.isRunning() || state.isPaused()
                || !event.getBlock().getWorld().getName().equals(plugin.getMainConfig().getWorldName())) {
            return;
        }
        counts.merge(event.getPlayer().getUniqueId(), 1L, Long::sum);
        dirty = true;
    }

    /**
     * Ohne placed-blocks.yml (erster Start dieser Version): mit den noch
     * getrackten Bloecken des letzten Contests vorbelegen - fruehere Contests
     * sind nicht mehr rekonstruierbar.
     */
    private void loadCounts() {
        if (!countsFile.exists()) {
            for (ScoreManager.Entry entry : plugin.getScoreManager().exportEntries()) {
                if (entry.placer() != null) {
                    counts.merge(entry.placer(), 1L, Long::sum);
                }
            }
            save();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(countsFile);
        for (String key : yaml.getKeys(false)) {
            try {
                counts.put(UUID.fromString(key), yaml.getLong(key));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ungültige UUID in placed-blocks.yml: " + key);
            }
        }
    }

    /** Auch von BuildContestPlugin#onDisable aufgerufen. */
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        counts.forEach((id, blocks) -> yaml.set(id.toString(), blocks));
        try {
            yaml.save(countsFile);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().warning("placed-blocks.yml konnte nicht gespeichert werden: " + exception);
        }
    }

    private void recomputeRanking() {
        List<Entry> entries = new ArrayList<>();
        counts.forEach((id, blocks) -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (blocks > 0 && name != null) {
                entries.add(new Entry(name, blocks));
            }
        });
        entries.sort((x, y) -> Long.compare(y.blocks(), x.blocks()));
        ranking = List.copyOf(entries.subList(0, Math.min(SIZE, entries.size())));
        if (board != null && !board.isDead()) {
            board.setText(String.join("\n", lines()));
        }
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + SIZE + " - im Contest gesetzte Blöcke");
        for (int i = 0; i < ranking.size(); i++) {
            Entry entry = ranking.get(i);
            ChatColor color = i == 0 ? ChatColor.GOLD : i == 1 ? ChatColor.WHITE : i == 2 ? ChatColor.RED : ChatColor.AQUA;
            lines.add(color + "" + ChatColor.BOLD + (i + 1) + ". " + ChatColor.RESET + color + entry.name()
                    + ChatColor.GRAY + " - " + ChatColor.YELLOW + entry.blocks());
        }
        if (ranking.isEmpty()) {
            lines.add(ChatColor.GRAY + "Noch keine Blöcke im Contest gesetzt");
        }
        return lines;
    }

    private void updateDisplay() {
        Location target = effectiveLocation();
        if (target == null || target.getWorld() == null) {
            return;
        }
        if (board == null || board.isDead()) {
            if (!target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
                return;
            }
            board = target.getWorld().spawn(target, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                // "Leuchtschrift": unabhaengig von Tageszeit/Schatten voll hell.
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setShadowed(true);
                entity.setLineWidth(400);
                entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
                entity.setText(String.join("\n", lines()));
            });
        }
    }

    private void removeBoard() {
        if (board != null && !board.isDead()) {
            board.remove();
        }
        board = null;
    }
}
