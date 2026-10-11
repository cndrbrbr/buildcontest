package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.model.Plot;
import io.github.cndrbrbr.buildcontest.util.BlockKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Block-Tracking und Punkteberechnung (siehe rules.md#bauregeln-und-verbindungsprüfung,
 * #punktesystem, #manipulationssichere-blockerfassung).
 *
 * Getrackt wird jeder von einem Spieler gesetzte FESTE Block (siehe
 * {@link #isTrackable}) auf einem Bauplatz - unabhaengig von seinem Punktwert,
 * denn auch 0-Punkte-Bloecke (Glas, Beton, ...) duerfen Teil einer Verbindung
 * zum Fundament sein. "Wertbar" (points &gt; 0) und "verbindend" (fester
 * Block) sind damit unabhaengige Eigenschaften.
 *
 * Score = Summe der Punktwerte aktuell VERBUNDENER, aktuell existierender
 * Bloecke - nicht kumulativ. Ein unverbundener, aber noch vorhandener Block
 * zaehlt 0, bis er wieder mit dem Fundament verbunden ist; die Platzierung
 * selbst wird dafuer NIE abgelehnt (siehe rules.md: "Unverbundene
 * Platzierungen muessen deshalb nicht abgelehnt werden"). Beim Abbau eines
 * verbindenden Blocks wird gezielt (nicht per Vollscan des Bauplatzes) neu
 * geprueft, welche Nachbar-Bloecke dadurch die Verbindung zum Fundament
 * verlieren; beim Platzieren eines neuen verbindenden Blocks wird umgekehrt
 * geprueft, ob er eine zuvor isolierte Struktur ans Fundament zurueck
 * anschliesst. Beide Pruefungen sind auf die tatsaechlich betroffene
 * Teilstruktur begrenzt (BFS ueber bereits getrackte Bloecke), kein
 * Flood-Fill ueber den gesamten Bauplatz.
 */
public final class ScoreManager {

    /** material/points sind bei der Platzierung eingefroren (rules.md#punktesystem: Config-Aenderungen wirken nicht rueckwirkend auf Bestandsbloecke). */
    private record TrackedBlock(int groupId, UUID placer, Material material, int points, boolean connected) {
        TrackedBlock withConnected(boolean newConnected) {
            return new TrackedBlock(groupId, placer, material, points, newConnected);
        }
    }

    private static final BlockFace[] NEIGHBOR_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final ScoreConfig scoreConfig;
    private final Predicate<Material> connectingMaterial;

    private final Map<BlockKey, TrackedBlock> trackedBlocks = new HashMap<>();
    private final Map<Integer, Integer> groupScores = new HashMap<>();
    private final Map<UUID, Integer> personalScores = new HashMap<>();

    public ScoreManager(ScoreConfig scoreConfig) {
        // Material#isSolid() fragt live die Server-Block-Registry ab (siehe
        // {@link #isTrackable}) - fuer Unit-Tests ohne laufenden Server den
        // anderen Konstruktor mit einem eigenen Praedikat verwenden.
        this(scoreConfig, Material::isSolid);
    }

    public ScoreManager(ScoreConfig scoreConfig, Predicate<Material> connectingMaterial) {
        this.scoreConfig = scoreConfig;
        this.connectingMaterial = connectingMaterial;
    }

    public int getGroupScore(int groupId) {
        return groupScores.getOrDefault(groupId, 0);
    }

    public int getPersonalScore(UUID playerId) {
        return personalScores.getOrDefault(playerId, 0);
    }

    /**
     * Nur FESTE Bloecke nehmen ueberhaupt an der Verbindungspruefung teil -
     * Fluessigkeiten, Luft, Feuer und freie Deko (Fackeln, Blumen, Schilder,
     * Knoepfe, Druckplatten, ...) verbinden nicht und werden nicht getrackt
     * (siehe rules.md: "Standardmaessig gelten platzierte feste Bloecke als
     * verbindend; Fluessigkeiten, Luft, Feuer ... verbinden nicht"). Solche
     * Deko darf deshalb weiterhin ueberall frei platziert werden.
     */
    private boolean isTrackable(Material material) {
        return connectingMaterial.test(material);
    }

    private boolean touchesFoundation(int y, Plot plot) {
        return y == plot.getSurfaceY() + 1;
    }

    private boolean isConnected(BlockKey key, int groupId) {
        TrackedBlock block = trackedBlocks.get(key);
        return block != null && block.groupId() == groupId && block.connected();
    }

    /**
     * Trackt eine gueltige Spielerplatzierung (siehe
     * rules.md#manipulationssichere-blockerfassung). Wird NIE abgelehnt -
     * siehe Klassen-Javadoc.
     */
    public void registerPlacement(Block block, Plot plot, UUID placerId) {
        Material type = block.getType();
        if (!isTrackable(type)) {
            return;
        }

        BlockKey key = BlockKey.of(block);
        int points = scoreConfig.getScore(type);
        boolean connected = touchesFoundation(block.getY(), plot) || hasConnectedNeighbor(block, plot.getGroupId());

        trackedBlocks.put(key, new TrackedBlock(plot.getGroupId(), placerId, type, points, connected));
        if (connected && points > 0) {
            groupScores.merge(plot.getGroupId(), points, Integer::sum);
            personalScores.merge(placerId, points, Integer::sum);
        }
        if (connected) {
            reconnectDisconnectedNeighbors(key, plot.getGroupId());
        }
    }

    private boolean hasConnectedNeighbor(Block block, int groupId) {
        for (BlockFace face : NEIGHBOR_FACES) {
            if (isConnected(BlockKey.of(block.getRelative(face)), groupId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Baut ein Spieler einen getrackten Block ab, verliert dieser sofort
     * seinen Punktwert (falls verbunden). Zusaetzlich wird gezielt neu
     * geprueft, ob dadurch Nachbarn ihre Verbindung zum Fundament verloren
     * haben (siehe Klassen-Javadoc).
     */
    public void registerBreak(Block block, Plot plot) {
        BlockKey key = BlockKey.of(block);
        TrackedBlock removed = trackedBlocks.remove(key);
        if (removed == null) {
            return;
        }
        if (removed.connected() && removed.points() > 0) {
            groupScores.merge(removed.groupId(), -removed.points(), Integer::sum);
            personalScores.merge(removed.placer(), -removed.points(), Integer::sum);
        }
        disconnectUnreachableNeighbors(key, removed.groupId(), plot.getSurfaceY());
    }

    /**
     * BFS ausgehend von jedem noch verbundenen Nachbarn des entfernten
     * Blocks: kann er (ueber andere verbundene Bloecke derselben Gruppe)
     * noch das Fundament erreichen? Wenn nicht, verlieren alle auf diesem Weg
     * erreichten Bloecke ihre Verbindung (und ggf. ihre Punkte). Begrenzt auf
     * die tatsaechlich betroffene Teilstruktur, kein Vollscan des Bauplatzes.
     */
    private void disconnectUnreachableNeighbors(BlockKey removed, int groupId, int surfaceY) {
        Set<BlockKey> verified = new HashSet<>();
        for (BlockFace face : NEIGHBOR_FACES) {
            BlockKey neighborKey = removed.relative(face);
            if (verified.contains(neighborKey) || !isConnected(neighborKey, groupId)) {
                continue;
            }
            Set<BlockKey> component = new HashSet<>();
            if (reachesFoundation(neighborKey, groupId, surfaceY, component)) {
                verified.addAll(component);
            } else {
                component.forEach(this::disconnect);
            }
        }
    }

    /** @return ob von {@code start} aus ueber verbundene Bloecke derselben Gruppe das Fundament erreichbar ist; {@code visited} sammelt die besuchten Bloecke. */
    private boolean reachesFoundation(BlockKey start, int groupId, int surfaceY, Set<BlockKey> visited) {
        Deque<BlockKey> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockKey current = queue.poll();
            if (current.y() == surfaceY + 1) {
                return true;
            }
            for (BlockFace face : NEIGHBOR_FACES) {
                BlockKey next = current.relative(face);
                if (visited.contains(next) || !isConnected(next, groupId)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        return false;
    }

    private void disconnect(BlockKey key) {
        TrackedBlock block = trackedBlocks.get(key);
        if (block == null || !block.connected()) {
            return;
        }
        trackedBlocks.put(key, block.withConnected(false));
        if (block.points() > 0) {
            groupScores.merge(block.groupId(), -block.points(), Integer::sum);
            personalScores.merge(block.placer(), -block.points(), Integer::sum);
        }
    }

    /**
     * BFS ausgehend vom neu platzierten/neu verbundenen Block: haengt direkt
     * oder indirekt eine zuvor isolierte (unverbundene) Struktur daran, wird
     * sie komplett mit-verbunden (siehe Klassen-Javadoc) - ohne dass ein
     * erneutes Platzieren der betroffenen Bloecke noetig waere.
     */
    private void reconnectDisconnectedNeighbors(BlockKey from, int groupId) {
        Deque<BlockKey> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockKey current = queue.poll();
            for (BlockFace face : NEIGHBOR_FACES) {
                BlockKey next = current.relative(face);
                TrackedBlock neighbor = trackedBlocks.get(next);
                if (neighbor == null || neighbor.groupId() != groupId || neighbor.connected()) {
                    continue;
                }
                trackedBlocks.put(next, neighbor.withConnected(true));
                if (neighbor.points() > 0) {
                    groupScores.merge(neighbor.groupId(), neighbor.points(), Integer::sum);
                    personalScores.merge(neighbor.placer(), neighbor.points(), Integer::sum);
                }
                queue.add(next);
            }
        }
    }

    /**
     * Setzt alle Scores und getrackten Bloecke zurueck - fuer einen komplett
     * frischen Contest-Start auf der neu erzeugten Welt (siehe
     * WorldManager#regenerate). Ohne diesen Reset wuerden Punkte aus einem
     * vorherigen Contest in den naechsten uebernommen, obwohl die
     * zugehoerigen Bloecke mit der alten Welt bereits geloescht wurden.
     */
    public void clear() {
        trackedBlocks.clear();
        groupScores.clear();
        personalScores.clear();
    }

    /** Ein getrackter Block fuer den Export/Import ueber einen Neustart hinweg (siehe persistence.DataStore). */
    public record Entry(BlockKey key, int groupId, UUID placer, Material material, int points, boolean connected) {
    }

    public List<Entry> exportEntries() {
        List<Entry> entries = new ArrayList<>(trackedBlocks.size());
        trackedBlocks.forEach((key, block) -> entries.add(
                new Entry(key, block.groupId(), block.placer(), block.material(), block.points(), block.connected())));
        return entries;
    }

    /**
     * Fuellt den Zustand nach einem Neustart aus einer gespeicherten Liste
     * wieder auf. Punktwert und Verbindungsstatus werden unveraendert
     * uebernommen (nicht neu berechnet), damit eine spaetere Config-Aenderung
     * bereits bestehende Bloecke nicht rueckwirkend umbewertet.
     */
    public void importEntry(Entry entry) {
        trackedBlocks.put(entry.key(), new TrackedBlock(
                entry.groupId(), entry.placer(), entry.material(), entry.points(), entry.connected()));
        if (entry.connected() && entry.points() > 0) {
            groupScores.merge(entry.groupId(), entry.points(), Integer::sum);
            personalScores.merge(entry.placer(), entry.points(), Integer::sum);
        }
    }
}
