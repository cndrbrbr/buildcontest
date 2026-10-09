package io.github.cndrbrbr.buildcontest.model;

/**
 * Rechteckiger, flach planierter Bauplatz einer Gruppe (siehe rules.md#bauplaetze).
 * Nur die X/Z-Grundflaeche begrenzt die Wertung, Y ist nach oben unbegrenzt
 * (siehe rules.md#bauregeln).
 */
public final class Plot {

    private final int groupId;
    private final String worldName;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int surfaceY;

    public Plot(int groupId, String worldName, int minX, int minZ, int maxX, int maxZ, int surfaceY) {
        this.groupId = groupId;
        this.worldName = worldName;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.surfaceY = surfaceY;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getSurfaceY() {
        return surfaceY;
    }

    public boolean containsColumn(String world, int x, int z) {
        return this.worldName.equals(world)
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ;
    }
}
