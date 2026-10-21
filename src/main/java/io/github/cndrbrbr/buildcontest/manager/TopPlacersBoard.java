package io.github.cndrbrbr.buildcontest.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leuchtende Top-30-Tafel in der Eingangswelt: wer insgesamt die meisten
 * Bloecke gesetzt hat. Quelle ist die Vanilla-Statistik "minecraft:used",
 * beschraenkt auf Block-Items (Werkzeuge, Essen usw. zaehlen nicht) - offline
 * Spieler aus den Statistikdateien, online Spieler live. Position in
 * config.yml unter toplist (siehe /bc placetoplist), sonst neben der
 * Statustafel (siehe AutomodeManager).
 */
public final class TopPlacersBoard {

    public static final int SIZE = 30;
    private static final long REFRESH_TICKS = 60L * 20L;

    public record Entry(String name, long blocks) {
    }

    private final BuildContestPlugin plugin;
    private final AutomodeManager automodeManager;
    private volatile Map<UUID, Long> fileTotals = Map.of();
    private List<Entry> ranking = List.of();
    private TextDisplay board;
    private Location location;

    public TopPlacersBoard(BuildContestPlugin plugin, AutomodeManager automodeManager) {
        this.plugin = plugin;
        this.automodeManager = automodeManager;
        loadLocation();
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::readStatFiles, 0L, REFRESH_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::recomputeRanking, 40L, REFRESH_TICKS);
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

    /** Laeuft asynchron - liest nur Dateien, keine Bukkit-Weltzugriffe. */
    private void readStatFiles() {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File dir = new File(worldFolder, "players/stats");
        if (!dir.isDirectory()) {
            dir = new File(worldFolder, "stats");
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        Map<UUID, Long> totals = new HashMap<>();
        for (File file : files) {
            try (Reader reader = Files.newBufferedReader(file.toPath())) {
                UUID id = UUID.fromString(file.getName().substring(0, file.getName().length() - 5));
                JsonObject stats = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("stats");
                JsonObject used = stats != null ? stats.getAsJsonObject("minecraft:used") : null;
                if (used == null) {
                    continue;
                }
                long sum = 0;
                for (Map.Entry<String, JsonElement> entry : used.entrySet()) {
                    Material material = Material.matchMaterial(entry.getKey());
                    if (material != null && material.isBlock() && !material.isAir()) {
                        sum += entry.getValue().getAsLong();
                    }
                }
                totals.put(id, sum);
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().fine("Statistikdatei " + file.getName() + " nicht lesbar: " + exception);
            }
        }
        fileTotals = totals;
    }

    private void recomputeRanking() {
        Map<UUID, Long> totals = new HashMap<>(fileTotals);
        for (Player player : Bukkit.getOnlinePlayers()) {
            long sum = 0;
            for (Material material : Material.values()) {
                if (material.isBlock() && material.isItem() && !material.isAir() && !material.isLegacy()) {
                    sum += player.getStatistic(Statistic.USE_ITEM, material);
                }
            }
            totals.put(player.getUniqueId(), sum);
        }
        List<Entry> entries = new ArrayList<>();
        totals.forEach((id, blocks) -> {
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (blocks > 0 && name != null) {
                entries.add(new Entry(name, blocks));
            }
        });
        entries.sort((a, b) -> Long.compare(b.blocks(), a.blocks()));
        ranking = List.copyOf(entries.subList(0, Math.min(SIZE, entries.size())));
        if (board != null && !board.isDead()) {
            board.setText(String.join("\n", lines()));
        }
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + SIZE + " - gesetzte Blöcke");
        for (int i = 0; i < ranking.size(); i++) {
            Entry entry = ranking.get(i);
            ChatColor color = i == 0 ? ChatColor.GOLD : i == 1 ? ChatColor.WHITE : i == 2 ? ChatColor.RED : ChatColor.AQUA;
            lines.add(color + "" + ChatColor.BOLD + (i + 1) + ". " + ChatColor.RESET + color + entry.name()
                    + ChatColor.GRAY + " - " + ChatColor.YELLOW + entry.blocks());
        }
        if (ranking.isEmpty()) {
            lines.add(ChatColor.GRAY + "Noch keine Blöcke gesetzt");
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
