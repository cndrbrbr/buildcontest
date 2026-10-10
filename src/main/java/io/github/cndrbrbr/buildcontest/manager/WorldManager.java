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
 * Nach Spielende kann die Welt (manuell per Befehl oder automatisch, siehe
 * config.yml#game.auto-delete-world-on-end) wieder vollstaendig geloescht
 * werden, siehe rules.md#spielablauf--ende.
 *
 * Wichtige Einschraenkungen:
 * <ul>
 *   <li>Bukkit kann die Standard-Hauptwelt des Servers (aus
 *   server.properties level-name) zur Laufzeit nicht entladen. Die in
 *   config.yml#plots.world konfigurierte Welt muss deshalb eine davon
 *   verschiedene, dedizierte Welt sein - siehe {@link #isPrimaryWorld(String)}.</li>
 *   <li>Eine zusaetzliche Welt mit {@code Environment.NORMAL} und einem
 *   Namen, der nicht die Hauptwelt ist, legt Paper/Bukkit NICHT als
 *   eigenstaendigen Ordner an, sondern als "Custom Dimension" verschachtelt
 *   unter {@code <hauptwelt>/dimensions/minecraft/<name>} - das betrifft
 *   sowohl das Laden beim Serverstart ({@link #ensureLoaded()}) als auch das
 *   Loeschen des Weltordners.</li>
 * </ul>
 */
public final class WorldManager {

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    public WorldManager(BuildContestPlugin plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    /**
     * Laedt eine bereits auf der Platte vorhandene Bauplatz-Welt erneut, falls
     * sie aktuell nicht geladen ist. Bukkit laedt beim Serverstart nur die
     * Standard-Hauptwelt automatisch - eine zur Laufzeit per WorldCreator
     * erzeugte Zusatzwelt wie die Bauplatz-Welt muss nach einem Neustart
     * explizit erneut geladen werden, sonst bleiben persistierte Bauplaetze
     * (siehe persistence.DataStore) auf eine nicht existierende Welt
     * verwiesen. Erzeugt bewusst NICHTS neu, falls der Ordner noch nicht
     * existiert (das macht ausschliesslich {@link #regenerate()} bei /bc
     * start) - sonst wuerde jeder Serverstart versehentlich schon eine Welt
     * anlegen, noch bevor ein Contest ueberhaupt gestartet wurde.
     */
    public void ensureLoaded() {
        String worldName = mainConfig.getWorldName();
        if (Bukkit.getWorld(worldName) != null) {
            return;
        }
        if (!resolveWorldFolder(worldName).isDirectory()) {
            return;
        }
        new WorldCreator(worldName).createWorld();
        plugin.getLogger().info("Bestehende Bauplatz-Welt '" + worldName + "' beim Serverstart wieder geladen.");
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
            logRejectedPrimaryWorld(worldName);
            return Optional.empty();
        }

        unloadAndDelete(worldName);

        long seed = ThreadLocalRandom.current().nextLong();
        World newWorld = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(seed)
                .createWorld();

        plugin.getLogger().info("Neue Bauplatz-Welt '" + worldName + "' mit Seed " + seed + " erzeugt.");
        return Optional.of(newWorld);
    }

    /**
     * Loescht die Bauplatz-Welt vollstaendig, OHNE sie neu zu erzeugen (siehe
     * rules.md#spielablauf--ende) - z. B. nach Ansage des Endstands, wenn der
     * Admin die fertigen Bauten noch inspizieren wollte und sie jetzt
     * endgueltig entfernt.
     *
     * @return {@code true}, wenn geloescht wurde (oder nichts zu loeschen
     *         war), {@code false} bei der Hauptwelt-Schutzablehnung.
     */
    public boolean delete() {
        String worldName = mainConfig.getWorldName();
        if (isPrimaryWorld(worldName)) {
            logRejectedPrimaryWorld(worldName);
            return false;
        }
        unloadAndDelete(worldName);
        plugin.getLogger().info("Bauplatz-Welt '" + worldName + "' geloescht.");
        return true;
    }

    private void unloadAndDelete(String worldName) {
        World oldWorld = Bukkit.getWorld(worldName);
        if (oldWorld == null) {
            return;
        }
        File oldWorldFolder = oldWorld.getWorldFolder();

        World holding = fallbackWorld(oldWorld);
        List<Player> relocated = new ArrayList<>(oldWorld.getPlayers());
        for (Player player : relocated) {
            player.teleport(holding.getSpawnLocation());
        }

        Bukkit.unloadWorld(oldWorld, false);
        deleteWorldFolder(oldWorldFolder);
    }

    private void logRejectedPrimaryWorld(String worldName) {
        plugin.getLogger().severe("plots.world ('" + worldName + "') ist die Server-Hauptwelt - diese kann nicht "
                + "geloescht/neu erzeugt werden. Bitte in config.yml eine andere, dedizierte Welt fuer die "
                + "Bauplaetze konfigurieren.");
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

    /**
     * Ermittelt den Weltordner anhand des bekannten Ablagemusters (siehe
     * Klassen-Javadoc), SOLANGE die Welt noch nicht geladen ist und deshalb
     * kein {@code World}-Objekt zur Verfuegung steht, das den Pfad direkt
     * liefern koennte (siehe {@link World#getWorldFolder()}, genutzt in
     * {@link #unloadAndDelete(String)}).
     *
     * Wichtig: Auch die Hauptwelt selbst liegt in Paper 26.3 verschachtelt
     * unter {@code <hauptwelt>/dimensions/minecraft/overworld} - der Pfad
     * fuer eine Zusatzwelt wird deshalb relativ zum WorldContainer und dem
     * NAMEN der Hauptwelt aufgebaut, nicht relativ zu deren eigenem
     * {@code getWorldFolder()} (das wuerde zu einem doppelt verschachtelten,
     * falschen Pfad fuehren).
     */
    private File resolveWorldFolder(String worldName) {
        File flat = new File(plugin.getServer().getWorldContainer(), worldName);
        if (flat.isDirectory()) {
            return flat;
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            String primaryName = Bukkit.getWorlds().get(0).getName();
            File nested = new File(plugin.getServer().getWorldContainer(), primaryName
                    + File.separator + "dimensions" + File.separator + "minecraft" + File.separator + worldName);
            if (nested.isDirectory()) {
                return nested;
            }
        }
        return flat;
    }

    private void deleteWorldFolder(File worldFolder) {
        Path path = worldFolder.toPath();
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
