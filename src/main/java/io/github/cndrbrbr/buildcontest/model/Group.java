package io.github.cndrbrbr.buildcontest.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Eine Gruppe mit fester maximaler Groesse und genau einem Bauplatz (siehe rules.md#gruppen). */
public final class Group {

    private final int id;
    private final String name;
    private final int maxSize;
    private final Set<UUID> members = new LinkedHashSet<>();
    private Plot plot;

    public Group(int id, String name, int maxSize) {
        this.id = id;
        this.name = name;
        this.maxSize = maxSize;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public boolean addMember(UUID playerId) {
        if (isFull()) {
            return false;
        }
        return members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean hasMember(UUID playerId) {
        return members.contains(playerId);
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Plot getPlot() {
        return plot;
    }

    public void setPlot(Plot plot) {
        this.plot = plot;
    }
}
