package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Erzeugt beim Start eines Contests eine komplett frische Welt mit
 * zufaelligem Seed (siehe rules.md#setup-admin): nur die Bauplaetze selbst
 * werden anschliessend von PlotManager planiert, das uebrige Gelaende bleibt
 * normales, prozedural generiertes Terrain statt einer komplett flachen Welt.
 *
 * Wichtige Einschraenkung: Bukkit kann die Standard-Hauptwelt des Servers
 * (aus server.properties level-name) zur Laufzeit nicht entladen. Die in
 * config.yml#plots.world konfigurierte Welt muss deshalb eine davon
 * verschiedene, dedizierte Welt sein - siehe {@link #isPrimaryWorld(String)}.
 */
public final class WorldManager {

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    public WorldManager(BuildContestPlugin plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    /**
     * Loescht eine evtl. vorhandene alte Bauplatz-Welt vollstaendig und
     * erzeugt sie mit einem zufaelligen Seed neu.
     *
     * @return die neue Welt, oder ein leeres Optional, wenn die konfigurierte
     *         Welt die Server-Hauptwelt ist (siehe Klassen-Javadoc) und die
     *         Regenerierung deshalb sicherheitshalber abgelehnt wurde.
     */
    public Optional<World> regenerate() {
        String worldName = mainConfig.getWorldName();
        if (isPrimaryWorld(worldName)) {
            plugin.getLogger().severe("plots.world ('" + worldName + "') ist die Server-Hauptwelt - "
                    + "diese kann nicht neu erzeugt werden. Bitte in config.yml eine andere, "
                    + "dedizierte Welt fuer die Bauplaetze konfigurieren.");
            return Optional.empty();
        }

        World oldWorld = Bukkit.getWorld(worldName);
        List<Player> relocated = new ArrayList<>();
        if (oldWorld != null) {
            World holding = fallbackWorld(oldWorld);
            for (Player player : oldWorld.getPlayers()) {
                player.teleport(holding.getSpawnLocation());
                relocated.add(player);
            }
            Bukkit.unloadWorld(oldWorld, false);
            deleteWorldFolder(worldName);
        }

        long seed = ThreadLocalRandom.current().nextLong();
        World newWorld = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(seed)
                .createWorld();

        Location spawn = newWorld.getSpawnLocation();
        for (Player player : relocated) {
            player.teleport(spawn);
        }

        plugin.getLogger().info("Neue Bauplatz-Welt '" + worldName + "' mit Seed " + seed + " erzeugt.");
        return Optional.of(newWorld);
    }

    private boolean isPrimaryWorld(String worldName) {
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.get(0).getName().equals(worldName);
    }

    private World fallbackWorld(World toReplace) {
        return Bukkit.getWorlds().stream()
                .filter(world -> world != toReplace)
                .findFirst()
                .orElseGet(() -> new WorldCreator("buildcontest_holding").createWorld());
    }

    private void deleteWorldFolder(String worldName) {
        Path path = new File(plugin.getServer().getWorldContainer(), worldName).toPath();
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.delete(entry);
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Konnte nicht loeschen: " + entry, exception);
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Konnte alte Bauplatz-Welt nicht vollstaendig loeschen: " + path,
                    exception);
        }
    }
}
