package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Gruppenverwaltung: Mitgliedschaft und Zuweisungsmodus (siehe rules.md#gruppen). */
public final class GroupManager {

    private final MainConfig mainConfig;
    private final Map<Integer, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, Integer> membership = new LinkedHashMap<>();
    private final Random random = new Random();

    public GroupManager(MainConfig mainConfig) {
        this.mainConfig = mainConfig;
        rebuildGroups();
    }

    /** Legt die Gruppen anhand der aktuellen Konfiguration neu an. Bestehende Mitgliedschaften gehen dabei verloren. */
    public void rebuildGroups() {
        groups.clear();
        membership.clear();
        for (int i = 1; i <= mainConfig.getGroupCount(); i++) {
            groups.put(i, new Group(i, "Gruppe " + i, mainConfig.getGroupMaxSize()));
        }
    }

    public Map<Integer, Group> getGroups() {
        return groups;
    }

    public Optional<Group> getGroup(int id) {
        return Optional.ofNullable(groups.get(id));
    }

    public Optional<Group> getGroupOf(UUID playerId) {
        Integer id = membership.get(playerId);
        return id == null ? Optional.empty() : getGroup(id);
    }

    /** Freie Wahl per /bc join, nur im Modus FREE (siehe rules.md#gruppen). */
    public JoinResult join(UUID playerId, int groupId) {
        if (mainConfig.getAssignmentMode() != MainConfig.AssignmentMode.FREE) {
            return JoinResult.WRONG_MODE;
        }
        if (membership.containsKey(playerId)) {
            return JoinResult.ALREADY_IN_GROUP;
        }
        Group group = groups.get(groupId);
        if (group == null) {
            return JoinResult.NO_SUCH_GROUP;
        }
        if (group.isFull()) {
            return JoinResult.GROUP_FULL;
        }
        if (group.getMembers().size() > smallestOpenGroupSize()) {
            return JoinResult.UNBALANCED;
        }
        group.addMember(playerId);
        membership.put(playerId, groupId);
        return JoinResult.SUCCESS;
    }

    /**
     * Teilt einen Spieler ohne Gruppe der kleinsten noch nicht vollen Gruppe
     * zu (bei Gleichstand zufaellig), damit die Teams gleich gross bleiben.
     * Leer, wenn der Spieler schon in einer Gruppe ist oder alle voll sind.
     */
    public Optional<Group> assignBalanced(UUID playerId) {
        if (membership.containsKey(playerId)) {
            return Optional.empty();
        }
        List<Group> smallest = smallestOpenGroups();
        if (smallest.isEmpty()) {
            return Optional.empty();
        }
        Group group = smallest.get(random.nextInt(smallest.size()));
        group.addMember(playerId);
        membership.put(playerId, group.getId());
        return Optional.of(group);
    }

    /** Nicht volle Gruppen mit der aktuell geringsten Mitgliederzahl. */
    public List<Group> smallestOpenGroups() {
        int min = smallestOpenGroupSize();
        List<Group> result = new ArrayList<>();
        for (Group group : groups.values()) {
            if (!group.isFull() && group.getMembers().size() == min) {
                result.add(group);
            }
        }
        return result;
    }

    private int smallestOpenGroupSize() {
        int min = Integer.MAX_VALUE;
        for (Group group : groups.values()) {
            if (!group.isFull()) {
                min = Math.min(min, group.getMembers().size());
            }
        }
        return min;
    }

    /** Admin-Zuweisung per Befehl oder Configfile-Zuordnung (siehe rules.md#gruppen). */
    public JoinResult assign(UUID playerId, int groupId) {
        Group group = groups.get(groupId);
        if (group == null) {
            return JoinResult.NO_SUCH_GROUP;
        }
        if (group.isFull() && !group.hasMember(playerId)) {
            return JoinResult.GROUP_FULL;
        }
        getGroupOf(playerId).ifPresent(current -> current.removeMember(playerId));
        group.addMember(playerId);
        membership.put(playerId, groupId);
        return JoinResult.SUCCESS;
    }

    public Optional<Integer> resolvePresetGroup(String playerName) {
        Integer groupId = mainConfig.getPresetAssignments().get(playerName.toLowerCase());
        return Optional.ofNullable(groupId);
    }

    /** Fuer die Persistenz (siehe persistence.DataStore): Gruppe -> Mitglieder. */
    public Map<Integer, Set<UUID>> exportMembership() {
        Map<Integer, Set<UUID>> export = new LinkedHashMap<>();
        groups.forEach((id, group) -> export.put(id, new LinkedHashSet<>(group.getMembers())));
        return export;
    }

    /**
     * Stellt Gruppenmitgliedschaften nach einem Neustart wieder her. Muss nach
     * {@link #rebuildGroups()} aufgerufen werden. Mitglieder von Gruppen, die
     * es in der aktuellen Konfiguration nicht mehr gibt (z. B. Gruppenanzahl
     * zwischenzeitlich verkleinert), werden stillschweigend verworfen.
     */
    public void importMembership(Map<Integer, Set<UUID>> membershipByGroup) {
        membershipByGroup.forEach((groupId, playerIds) -> {
            Group group = groups.get(groupId);
            if (group == null) {
                return;
            }
            for (UUID playerId : playerIds) {
                if (group.restoreMember(playerId)) {
                    membership.put(playerId, groupId);
                }
            }
        });
    }

    public enum JoinResult {
        SUCCESS, ALREADY_IN_GROUP, GROUP_FULL, NO_SUCH_GROUP, WRONG_MODE,
        /** Die gewaehlte Gruppe hat schon mehr Mitglieder als eine andere, nicht volle Gruppe. */
        UNBALANCED
    }
}
