package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.model.Plot;
import io.github.cndrbrbr.buildcontest.util.BlockKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kernlogik des Punktesystems (siehe rules.md#punktesystem und #scoreboard-anzeige).
 *
 * Score = Summe der Punktwerte aktuell stehender gewerteter Bloecke, nicht eine
 * kumulative Zaehlung aller jemals platzierten Bloecke. Abbau zieht den Wert
 * wieder vom urspruenglichen Platzierer (und damit von dessen Gruppe) ab,
 * unabhaengig davon, wer den Block abgebaut hat.
 *
 * Bekannte Einschraenkung: Wird ein verbindender Block entfernt, werden
 * dadurch potenziell "abgehaengte" Bloecke darueber nicht automatisch erneut
 * geprueft/entwertet - die Zusammenhangspruefung findet nur beim Platzieren
 * statt (siehe rules.md#bauregeln).
 */
public final class ScoreManager {

    private record ScoredEntry(int groupId, UUID placer, int points) {
    }

    private static final BlockFace[] NEIGHBOR_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final ScoreConfig scoreConfig;

    private final Map<BlockKey, ScoredEntry> scoredBlocks = new HashMap<>();
    private final Map<Integer, Set<BlockKey>> connectedByPlot = new HashMap<>();
    private final Map<Integer, Integer> groupScores = new HashMap<>();
    private final Map<UUID, Integer> personalScores = new HashMap<>();

    public ScoreManager(ScoreConfig scoreConfig) {
        this.scoreConfig = scoreConfig;
    }

    public int getGroupScore(int groupId) {
        return groupScores.getOrDefault(groupId, 0);
    }

    public int getPersonalScore(UUID playerId) {
        return personalScores.getOrDefault(playerId, 0);
    }

    /**
     * Nicht gewertete Bloecke sind von der Zusammenhangspruefung ausgenommen und
     * duerfen immer frei platziert werden (siehe rules.md#bauregeln). Gewertete
     * Bloecke muessen direkt auf der planierten Oberflaeche stehen oder an einen
     * bereits verbundenen, ebenfalls gewerteten Block angrenzen.
     */
    public boolean canPlace(Block block, Plot plot) {
        int points = scoreConfig.getScore(block.getType());
        if (points <= 0) {
            return true;
        }
        if (block.getY() == plot.getSurfaceY() + 1) {
            return true;
        }
        Set<BlockKey> connected = connectedByPlot.getOrDefault(plot.getGroupId(), Set.of());
        for (BlockFace face : NEIGHBOR_FACES) {
            if (connected.contains(BlockKey.of(block.getRelative(face)))) {
                return true;
            }
        }
        return false;
    }

    public void registerPlacement(Block block, Plot plot, UUID placerId) {
        Material type = block.getType();
        int points = scoreConfig.getScore(type);
        if (points <= 0) {
            return;
        }

        BlockKey key = BlockKey.of(block);
        connectedByPlot.computeIfAbsent(plot.getGroupId(), id -> new HashSet<>()).add(key);
        scoredBlocks.put(key, new ScoredEntry(plot.getGroupId(), placerId, points));
        groupScores.merge(plot.getGroupId(), points, Integer::sum);
        personalScores.merge(placerId, points, Integer::sum);
    }

    public void registerBreak(Block block, Plot plot) {
        BlockKey key = BlockKey.of(block);
        connectedByPlot.getOrDefault(plot.getGroupId(), Set.of()).remove(key);

        ScoredEntry entry = scoredBlocks.remove(key);
        if (entry == null) {
            return;
        }
        groupScores.merge(entry.groupId(), -entry.points(), Integer::sum);
        personalScores.merge(entry.placer(), -entry.points(), Integer::sum);
    }

    /** Ein gewerteter Block fuer den Export/Import ueber einen Neustart hinweg (siehe persistence.DataStore). */
    public record Entry(BlockKey key, int groupId, UUID placer, int points) {
    }

    public List<Entry> exportEntries() {
        List<Entry> entries = new ArrayList<>(scoredBlocks.size());
        scoredBlocks.forEach((key, entry) -> entries.add(new Entry(key, entry.groupId(), entry.placer(), entry.points())));
        return entries;
    }

    /**
     * Fuellt den Zustand nach einem Neustart aus einer gespeicherten Liste
     * wieder auf. Die Punktwerte werden dabei unveraendert aus der Datei
     * uebernommen (nicht neu aus ScoreConfig abgeleitet), damit eine spaetere
     * Config-Aenderung bereits gewertete Bestandsbloecke nicht rueckwirkend
     * umbewertet.
     */
    public void importEntry(Entry entry) {
        connectedByPlot.computeIfAbsent(entry.groupId(), id -> new HashSet<>()).add(entry.key());
        scoredBlocks.put(entry.key(), new ScoredEntry(entry.groupId(), entry.placer(), entry.points()));
        groupScores.merge(entry.groupId(), entry.points(), Integer::sum);
        personalScores.merge(entry.placer(), entry.points(), Integer::sum);
    }
}
