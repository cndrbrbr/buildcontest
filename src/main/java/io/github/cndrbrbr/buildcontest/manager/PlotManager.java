package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Platzierung und Planierung der Bauplaetze (siehe rules.md#bauplaetze).
 *
 * Bauplaetze werden in einem moeglichst quadratischen 2D-Raster um einen
 * Mittelpunkt verteilt (Reihen x Spalten ~ sqrt(Anzahl)), jeweils mit dem
 * konfigurierten min-distance als Abstand zwischen den Rasterzellen in beide
 * Richtungen. Damit ist der tatsaechliche Abstand zwischen Baupaltz-Kanten
 * konstant gleich min-distance <= max-distance, die konfigurierte
 * Obergrenze wird also automatisch eingehalten; eine variable Ausnutzung
 * der Spanne bis max-distance (z. B. zufaellig) ist eine moegliche
 * spaetere Erweiterung, aber fuer die Kernanforderung
 * ("Mindest-/Maximalabstand") nicht notwendig.
 *
 * Der konfigurierte Mittelpunkt (plots.center-x/-z) ist dabei nur ein
 * STARTPUNKT fuer die Suche: da die Bauplatz-Welt bei jedem NEUEN Contest neu
 * mit zufaelligem Seed erzeugt wird (siehe WorldManager), kann an dieser
 * Stelle Wasser (Ozean/See) liegen. {@link #findLandAnchor} sucht deshalb
 * automatisch einen nahegelegenen, durchgehend trockenen Platz fuer das
 * GESAMTE Bauplatz-Raster (ein Bauplatz im Wasser waere sonst zufaellig nur
 * fuer einzelne Gruppen ein Nachteil).
 *
 * Der tatsaechliche Y-Level wird aber NICHT fuer alle Gruppen gemeinsam aus
 * diesem groben Anker uebernommen: jeder Bauplatz wird anhand seiner EIGENEN
 * vier Eckpunkte individuell planiert (siehe {@link #determinePlotSurfaceY}),
 * da verschiedene Bauplaetze unterschiedliche Y-Level haben duerfen/sollen
 * (siehe rules.md#bauplaetze: "Verschiedene Bauplätze dürfen unterschiedliche
 * Y-Level haben"). Der grobe Anker dient nur noch als Fallback, falls ein
 * einzelner Bauplatz keinen durchgehend trockenen eigenen Boden hat, und als
 * Ausgangspunkt fuer den Weltspawn.
 */
public final class PlotManager {

    private static final int LAND_SEARCH_RING_STEP = 96;
    private static final int LAND_SEARCH_MAX_RINGS = 10;
    /** Hartes Zeitlimit fuer die Landsuche, siehe {@link #findLandAnchor}. */
    private static final long LAND_SEARCH_TIME_BUDGET_NANOS = 8_000_000_000L;
    /** Maximale Abweichung (tiefer ODER hoeher) vom natuerlichen Boden am Referenzpunkt, siehe {@link #checkAllLand}. */
    private static final int MAX_EXCAVATION_DEPTH = 5;
    /** Sicherheitsgrenze, falls {@link #flatten} wegen einer Hoehle immer tiefer aufgefuellt werden muss. */
    private static final int MAX_FILL_SAFETY_DEPTH = 40;
    /** Abstand der Beacon-Markierung von der Bauplatzkante, siehe {@link #placeBeaconMarker}. */
    private static final int BEACON_MARKER_OFFSET = 3;
    /** Wie viele Bloecke oberhalb der Markierung freigeraeumt werden, damit der Beacon-Strahl sichtbar ist. */
    private static final int BEACON_CLEAR_HEIGHT = 6;
    private static final Material BEACON_BASE_MATERIAL = Material.IRON_BLOCK;
    /** Deckschicht eines Bauplatzes, siehe {@link #flatten} - gut sichtbar/abgegrenzt vom natuerlichen Gelaende. */
    private static final Material PLOT_SURFACE_MATERIAL = Material.ORANGE_WOOL;
    private static final int[][] LAND_SEARCH_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

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
        List<Group> groupList = new ArrayList<>();
        groups.forEach(groupList::add);
        if (groupList.isEmpty()) {
            plotsByGroup.clear();
            return;
        }

        String worldName = mainConfig.getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Welt '" + worldName + "' nicht gefunden, Bauplaetze wurden nicht generiert.");
            return;
        }

        int sizeX = mainConfig.getPlotSizeX();
        int sizeZ = mainConfig.getPlotSizeZ();
        int gap = mainConfig.getMinDistance();
        int[] halfExtent = gridHalfExtent(groupList.size(), sizeX, sizeZ, gap);

        LandAnchor anchor = findLandAnchor(world, mainConfig.getCenterX(), mainConfig.getCenterZ(),
                halfExtent[0], halfExtent[1], sizeX, sizeZ).orElseGet(() -> {
                    plugin.getLogger().warning("Kein durchgehend trockener Platz fuer die Bauplaetze in der Naehe "
                            + "des konfigurierten Mittelpunkts gefunden (Ozean-Welt?) - verwende Mittelpunkt und "
                            + "surface-y aus config.yml trotzdem, Bauplaetze liegen moeglicherweise im Wasser.");
                    return new LandAnchor(mainConfig.getCenterX(), mainConfig.getCenterZ(), mainConfig.getSurfaceY());
                });

        // computeGridLayout liefert hier nur die X/Z-Aufteilung; der mitgegebene
        // surfaceY ist nur ein Platzhalter und wird unten je Bauplatz einzeln
        // ueberschrieben (siehe Klassen-Javadoc).
        List<Plot> layout = computeGridLayout(groupList.size(), sizeX, sizeZ, gap,
                anchor.centerX(), anchor.centerZ(), anchor.surfaceY(), worldName);

        plotsByGroup.clear();
        Plot firstPlot = null;
        for (int i = 0; i < groupList.size(); i++) {
            Group group = groupList.get(i);
            // computeGridLayout kennt keine Gruppen-IDs, also hier zuordnen.
            Plot template = layout.get(i);
            int surfaceY = determinePlotSurfaceY(world, template).orElseGet(() -> {
                plugin.getLogger().warning("Bauplatz von Gruppe " + group.getId() + " hat keinen durchgehend "
                        + "trockenen eigenen Boden - verwende den gemeinsamen Anker-Y-Level als Rueckfalloption, "
                        + "der Bauplatz liegt moeglicherweise im Wasser.");
                return anchor.surfaceY();
            });
            Plot plot = new Plot(group.getId(), template.getWorldName(),
                    template.getMinX(), template.getMinZ(), template.getMaxX(), template.getMaxZ(), surfaceY);
            plotsByGroup.put(group.getId(), plot);
            group.setPlot(plot);
            flatten(plot, world);
            placeBeaconMarker(world, plot);
            if (firstPlot == null) {
                firstPlot = plot;
            }
        }

        // Der von Minecraft bei der Welterzeugung automatisch gewaehlte
        // Weltspawn liegt VOR dem Ausschachten der Bauplaetze und kann in der
        // Luft/auf einem Huegel landen - Spieler ohne eigenes Bett wuerden
        // dort wieder aufwachen. Stattdessen explizit auf die (garantiert
        // flach ausgehobene) Mitte des ersten Bauplatzes setzen.
        int spawnX = (firstPlot.getMinX() + firstPlot.getMaxX()) / 2;
        int spawnZ = (firstPlot.getMinZ() + firstPlot.getMaxZ()) / 2;
        world.setSpawnLocation(spawnX, firstPlot.getSurfaceY() + 1, spawnZ);
    }

    /**
     * Reine Layout-Berechnung ohne Bukkit-Weltzugriff (daher gut unit-testbar):
     * ordnet {@code count} gleich grosse Bauplaetze in einem moeglichst
     * quadratischen Raster um den Mittelpunkt an, Kante-zu-Kante-Abstand
     * {@code gap} in X- und Z-Richtung.
     */
    public static List<Plot> computeGridLayout(int count, int sizeX, int sizeZ, int gap,
                                                int centerX, int centerZ, int surfaceY, String worldName) {
        List<Plot> result = new ArrayList<>(count);
        if (count <= 0) {
            return result;
        }

        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);

        int totalWidth = cols * sizeX + (cols - 1) * gap;
        int totalDepth = rows * sizeZ + (rows - 1) * gap;
        int startX = centerX - totalWidth / 2;
        int startZ = centerZ - totalDepth / 2;

        int index = 0;
        for (int row = 0; row < rows && index < count; row++) {
            for (int col = 0; col < cols && index < count; col++) {
                int minX = startX + col * (sizeX + gap);
                int minZ = startZ + row * (sizeZ + gap);
                result.add(new Plot(0, worldName, minX, minZ, minX + sizeX - 1, minZ + sizeZ - 1, surfaceY));
                index++;
            }
        }
        return result;
    }

    /** Halbe Breite/Tiefe des Gesamtrasters, siehe {@link #computeGridLayout}, fuer die Landsuche. */
    private static int[] gridHalfExtent(int count, int sizeX, int sizeZ, int gap) {
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        int totalWidth = cols * sizeX + (cols - 1) * gap;
        int totalDepth = rows * sizeZ + (rows - 1) * gap;
        return new int[]{totalWidth / 2, totalDepth / 2};
    }

    private record LandAnchor(int centerX, int centerZ, int surfaceY) {
    }

    /**
     * Sucht ausgehend vom Startpunkt in konzentrischen Ringen nach einem
     * Mittelpunkt, an dem die vier Eckpunkte des gesamten Bauplatz-Rasters
     * UND die Mitte des ersten Bauplatzes durchgehend auf trockenem Land
     * liegen (siehe Klassen-Javadoc). Liefert den Mittelpunkt sowie den
     * dafuer passenden, gemeinsamen Y-Level (siehe {@link #checkAllLand}).
     *
     * Zeitbudget statt nur Ring-Limit: Auf einem Ozean-Seed kann JEDER
     * Kandidat einen neuen, noch ungenerierten Chunk beruehren (siehe
     * {@link #checkAllLand}) - bei einem besonders grossen Ozean reichte das
     * reine Ring-Limit nicht, um /bc start innerhalb einer fuer Spieler
     * zumutbaren Zeit abzuschliessen (beobachteter Server-Hang >45s inkl.
     * Verbindungsabbruch). Die Suche bricht deshalb zusaetzlich nach
     * {@link #LAND_SEARCH_TIME_BUDGET_NANOS} ab und faellt dann auf den
     * konfigurierten Mittelpunkt zurueck, auch wenn noch nicht alle Ringe
     * durchsucht wurden.
     */
    private Optional<LandAnchor> findLandAnchor(World world, int startX, int startZ, int halfWidth, int halfDepth,
                                                 int sizeX, int sizeZ) {
        long deadline = System.nanoTime() + LAND_SEARCH_TIME_BUDGET_NANOS;

        OptionalInt startGround = checkAllLand(world, startX, startZ, halfWidth, halfDepth, sizeX, sizeZ);
        if (startGround.isPresent()) {
            return Optional.of(new LandAnchor(startX, startZ, startGround.getAsInt()));
        }

        for (int ring = 1; ring <= LAND_SEARCH_MAX_RINGS; ring++) {
            int offset = ring * LAND_SEARCH_RING_STEP;
            for (int[] direction : LAND_SEARCH_DIRECTIONS) {
                if (System.nanoTime() > deadline) {
                    plugin.getLogger().warning("Landsuche nach " + (LAND_SEARCH_TIME_BUDGET_NANOS / 1_000_000_000)
                            + "s abgebrochen (vermutlich sehr grosser Ozean) - verwende Mittelpunkt und surface-y "
                            + "aus config.yml trotzdem.");
                    return Optional.empty();
                }
                int candidateX = startX + direction[0] * offset;
                int candidateZ = startZ + direction[1] * offset;
                OptionalInt ground = checkAllLand(world, candidateX, candidateZ, halfWidth, halfDepth, sizeX, sizeZ);
                if (ground.isPresent()) {
                    return Optional.of(new LandAnchor(candidateX, candidateZ, ground.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Grobe Verfuegbarkeitspruefung fuer das GESAMTE Bauplatz-Raster (siehe
     * {@link #findLandAnchor}): prueft die vier Eckpunkte des Rasters UND die
     * Mitte des ERSTEN Bauplatzes (nicht die geometrische Mitte des
     * Gesamtrasters - die faellt z. B. bei zwei Gruppen in der Luecke
     * zwischen den Bauplaetzen und waere damit kein sinnvoller
     * Referenzpunkt). Der zurueckgegebene Y-Level ist nur ein grober Anker/
     * Fallback - die tatsaechliche Planierung je Bauplatz erfolgt individuell
     * in {@link #determinePlotSurfaceY}.
     */
    private OptionalInt checkAllLand(World world, int centerX, int centerZ, int halfWidth, int halfDepth,
                                      int sizeX, int sizeZ) {
        int plot1X = centerX - halfWidth + sizeX / 2;
        int plot1Z = centerZ - halfDepth + sizeZ / 2;
        int[][] corners = {
                {centerX - halfWidth, centerZ - halfDepth},
                {centerX + halfWidth, centerZ - halfDepth},
                {centerX - halfWidth, centerZ + halfDepth},
                {centerX + halfWidth, centerZ + halfDepth},
        };
        return evaluateGround(world, plot1X, plot1Z, corners);
    }

    /**
     * Bestimmt den INDIVIDUELLEN Y-Level eines einzelnen Bauplatzes anhand
     * seiner eigenen vier Eckpunkte und seiner eigenen Mitte als Referenz
     * (siehe rules.md#bauplaetze: "Verschiedene Bauplätze dürfen
     * unterschiedliche Y-Level haben") - anders als {@link #checkAllLand}
     * (das nur grob die Lage des GESAMTEN Rasters z. B. gegen einen Ozean
     * prueft) wird hier jeder Bauplatz unabhaengig von den anderen planiert,
     * jeder darf also seine eigene, lokal passende Hoehe bekommen.
     */
    private OptionalInt determinePlotSurfaceY(World world, Plot template) {
        int centerX = (template.getMinX() + template.getMaxX()) / 2;
        int centerZ = (template.getMinZ() + template.getMaxZ()) / 2;
        int[][] corners = {
                {template.getMinX(), template.getMinZ()},
                {template.getMaxX(), template.getMinZ()},
                {template.getMinX(), template.getMaxZ()},
                {template.getMaxX(), template.getMaxZ()},
        };
        return evaluateGround(world, centerX, centerZ, corners);
    }

    /**
     * Liegt an der Referenzstelle ODER einem der uebergebenen Punkte Wasser
     * (Hoehe inkl. Fluessigkeiten &gt; tatsaechlicher Boden), gilt die
     * Flaeche als ungeeignet. Der zurueckgegebene surfaceY bleibt ausgehend
     * vom Referenzpunkt innerhalb von +/- {@link #MAX_EXCAVATION_DEPTH}
     * Bloecken: liegt ein Punkt tiefer, wird surfaceY um bis zu diesen Betrag
     * abgesenkt (ausschachten); liegt ein Punkt hoeher (z. B. ein Huegel),
     * wird surfaceY um bis zu diesen Betrag angehoben (dort muss dann
     * weniger abgetragen werden) - siehe rules.md#bauplaetze: Bauplaetze
     * liegen immer nahe am natuerlichen Boden, nicht tief in der Erde und
     * nicht erkennbar aufgeschuettet.
     *
     * Performance: {@code getHighestBlockYAt} erzwingt synchrones Laden/
     * Generieren des jeweiligen Chunks auf dem Hauptthread - bei vielen
     * Fehlversuchen (z. B. grosser Ozean) kann das den Server fuer Sekunden
     * einfrieren. Deshalb wird zuerst NUR die Referenzstelle geprueft; liegt
     * dort schon Wasser (der haeufigste Fall in einem Ozean-Seed), werden die
     * teureren zusaetzlichen Punkt-Abfragen gar nicht erst ausgefuehrt.
     */
    private OptionalInt evaluateGround(World world, int referenceX, int referenceZ, int[][] points) {
        int referenceGround = world.getHighestBlockYAt(referenceX, referenceZ, HeightMap.OCEAN_FLOOR);
        int referenceSurface = world.getHighestBlockYAt(referenceX, referenceZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (referenceSurface > referenceGround) {
            return OptionalInt.empty();
        }

        int minGround = referenceGround;
        int maxGround = referenceGround;
        for (int[] point : points) {
            int ground = world.getHighestBlockYAt(point[0], point[1], HeightMap.OCEAN_FLOOR);
            int surfaceWithLiquids = world.getHighestBlockYAt(point[0], point[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (surfaceWithLiquids > ground) {
                return OptionalInt.empty();
            }
            minGround = Math.min(minGround, ground);
            maxGround = Math.max(maxGround, ground);
        }

        int surfaceY = referenceGround;
        if (minGround < referenceGround - MAX_EXCAVATION_DEPTH) {
            surfaceY = referenceGround - MAX_EXCAVATION_DEPTH;
        } else if (maxGround > referenceGround + MAX_EXCAVATION_DEPTH) {
            surfaceY = referenceGround + MAX_EXCAVATION_DEPTH;
        }
        return OptionalInt.of(surfaceY);
    }

    /**
     * Planiert einen Bauplatz auf den gemeinsamen surfaceY (siehe
     * {@link #checkAllLand}). Alles oberhalb wird abgetragen (ausschachten);
     * liegt der natuerliche Boden einer Spalte tiefer als die auf
     * {@link #MAX_EXCAVATION_DEPTH} Bloecke begrenzte Standardtiefe natuerlich
     * schon Hohlraum liegt (z. B. eine Hoehle direkt unter der Oberflaeche -
     * von der Hoehenkarte allein nicht erkennbar), wird so lange tiefer
     * aufgefuellt, bis tatsaechlich ein fester Block erreicht ist, statt eine
     * schwebende Plattform mit Hohlraum darunter zu hinterlassen.
     */
    private void flatten(Plot plot, World world) {
        int surfaceY = plot.getSurfaceY();
        int clearUpTo = Math.min(world.getMaxHeight() - 1, surfaceY + 150);
        int minFillDownTo = Math.max(world.getMinHeight(), surfaceY - 4);
        int safetyBottom = Math.max(world.getMinHeight(), surfaceY - MAX_FILL_SAFETY_DEPTH);

        for (int x = plot.getMinX(); x <= plot.getMaxX(); x++) {
            for (int z = plot.getMinZ(); z <= plot.getMaxZ(); z++) {
                for (int y = clearUpTo; y > surfaceY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }

                int fillDownTo = minFillDownTo;
                while (fillDownTo > safetyBottom && !world.getBlockAt(x, fillDownTo, z).getType().isSolid()) {
                    fillDownTo--;
                }
                for (int y = surfaceY; y > fillDownTo; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(y == surfaceY ? PLOT_SURFACE_MATERIAL : Material.DIRT, false);
                }
            }
        }
    }

    /**
     * Stellt direkt ausserhalb der Suedkante des Bauplatzes einen aktiven
     * Beacon auf eine kleine 3x3-Eisenblock-Pyramide, damit der Bauplatz
     * schon von weitem am Lichtstrahl erkennbar ist. Die Markierung steht
     * bewusst AUSSERHALB der Bauplatzgrenzen (siehe Plot#containsColumn) und
     * zaehlt deshalb nicht zur Wertung. Nutzt die tatsaechliche lokale
     * Gelaendehoehe an der Markierungsposition (nicht den surfaceY des
     * Bauplatzes selbst), da das Gelaende direkt dahinter von der Bauplatz-
     * Planierung unberuehrt bleibt und abweichend hoch/tief liegen kann.
     */
    private void placeBeaconMarker(World world, Plot plot) {
        int centerX = (plot.getMinX() + plot.getMaxX()) / 2;
        int baseZ = plot.getMaxZ() + BEACON_MARKER_OFFSET;
        int groundY = world.getHighestBlockYAt(centerX, baseZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = centerX + dx;
                int z = baseZ + dz;
                world.getBlockAt(x, groundY, z).setType(BEACON_BASE_MATERIAL, false);
                for (int y = groundY + 1; y <= groundY + BEACON_CLEAR_HEIGHT; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        world.getBlockAt(centerX, groundY + 1, baseZ).setType(Material.BEACON, false);
    }

    /** Fuer die Persistenz (siehe persistence.DataStore): aktueller Stand ohne erneutes Planieren. */
    public Map<Integer, Plot> exportPlots() {
        return plotsByGroup;
    }

    /**
     * Stellt Bauplaetze nach einem Neustart wieder her, OHNE sie erneut zu
     * planieren - die Bloecke stehen ja bereits in der Welt (siehe
     * persistence.DataStore). Verknuepft die Plots zusaetzlich mit den
     * passenden Gruppen, damit z. B. Schutzmechanismen sofort wieder greifen.
     */
    public void importPlots(Map<Integer, Plot> plots, GroupManager groupManager) {
        plotsByGroup.clear();
        plotsByGroup.putAll(plots);
        plots.forEach((groupId, plot) -> groupManager.getGroup(groupId).ifPresent(group -> group.setPlot(plot)));
    }

    /**
     * Verwirft alle Bauplatz-Zuordnungen, z. B. nachdem die Bauplatz-Welt
     * geloescht wurde (siehe WorldManager#delete, rules.md#spielablauf--ende)
     * und die bisherigen Plot-Grenzen damit gegenstandslos sind.
     */
    public void clear(GroupManager groupManager) {
        plotsByGroup.keySet().forEach(groupId -> groupManager.getGroup(groupId).ifPresent(group -> group.setPlot(null)));
        plotsByGroup.clear();
    }
}
