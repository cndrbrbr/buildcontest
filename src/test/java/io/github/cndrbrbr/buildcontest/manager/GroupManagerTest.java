package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Prueft die Gruppenverwaltung aus rules.md#gruppen (Freie Wahl vs. Configfile, Gruppengroesse). */
@ExtendWith(MockitoExtension.class)
class GroupManagerTest {

    @Mock
    private MainConfig mainConfig;

    private GroupManager groupManager;

    @BeforeEach
    void setUp() {
        lenient().when(mainConfig.getGroupCount()).thenReturn(2);
        lenient().when(mainConfig.getGroupMaxSize()).thenReturn(2);
        lenient().when(mainConfig.getAssignmentMode()).thenReturn(MainConfig.AssignmentMode.FREE);
        groupManager = new GroupManager(mainConfig);
    }

    @Test
    void rebuildGroupsCreatesConfiguredAmount() {
        assertEquals(2, groupManager.getGroups().size());
        assertTrue(groupManager.getGroup(1).isPresent());
        assertTrue(groupManager.getGroup(2).isPresent());
    }

    @Test
    void joinSucceedsInFreeModeWhileSpaceAvailable() {
        UUID player = UUID.randomUUID();
        assertEquals(GroupManager.JoinResult.SUCCESS, groupManager.join(player, 1));
        assertEquals(1, groupManager.getGroupOf(player).orElseThrow().getId());
    }

    @Test
    void joinFailsWhenAlreadyInAGroup() {
        UUID player = UUID.randomUUID();
        groupManager.join(player, 1);
        assertEquals(GroupManager.JoinResult.ALREADY_IN_GROUP, groupManager.join(player, 2));
    }

    @Test
    void joinFailsWhenGroupIsFull() {
        groupManager.join(UUID.randomUUID(), 1);
        groupManager.join(UUID.randomUUID(), 2);
        groupManager.join(UUID.randomUUID(), 1);
        groupManager.join(UUID.randomUUID(), 2);
        assertEquals(GroupManager.JoinResult.GROUP_FULL, groupManager.join(UUID.randomUUID(), 1));
    }

    @Test
    void joinRejectsGroupThatIsAlreadyLarger() {
        assertEquals(GroupManager.JoinResult.SUCCESS, groupManager.join(UUID.randomUUID(), 1));
        assertEquals(GroupManager.JoinResult.UNBALANCED, groupManager.join(UUID.randomUUID(), 1));
        assertEquals(GroupManager.JoinResult.SUCCESS, groupManager.join(UUID.randomUUID(), 2));
        assertEquals(GroupManager.JoinResult.SUCCESS, groupManager.join(UUID.randomUUID(), 1));
    }

    @Test
    void assignBalancedKeepsGroupsEqual() {
        for (int i = 0; i < 4; i++) {
            assertTrue(groupManager.assignBalanced(UUID.randomUUID()).isPresent());
            int sizeA = groupManager.getGroup(1).orElseThrow().getMembers().size();
            int sizeB = groupManager.getGroup(2).orElseThrow().getMembers().size();
            assertTrue(Math.abs(sizeA - sizeB) <= 1);
        }
        // max-size 2 je Gruppe: beide voll
        assertTrue(groupManager.assignBalanced(UUID.randomUUID()).isEmpty());
    }

    @Test
    void assignBalancedIgnoresPlayersAlreadyInAGroup() {
        UUID player = UUID.randomUUID();
        groupManager.assign(player, 1);
        assertTrue(groupManager.assignBalanced(player).isEmpty());
        assertEquals(1, groupManager.getGroupOf(player).orElseThrow().getId());
    }

    @Test
    void joinFailsForUnknownGroup() {
        assertEquals(GroupManager.JoinResult.NO_SUCH_GROUP, groupManager.join(UUID.randomUUID(), 99));
    }

    @Test
    void joinIsRejectedInConfigMode() {
        when(mainConfig.getAssignmentMode()).thenReturn(MainConfig.AssignmentMode.CONFIG);
        assertEquals(GroupManager.JoinResult.WRONG_MODE, groupManager.join(UUID.randomUUID(), 1));
    }

    @Test
    void assignMovesPlayerBetweenGroupsRegardlessOfMode() {
        UUID player = UUID.randomUUID();
        groupManager.assign(player, 1);
        assertEquals(GroupManager.JoinResult.SUCCESS, groupManager.assign(player, 2));
        assertEquals(2, groupManager.getGroupOf(player).orElseThrow().getId());
        assertTrue(groupManager.getGroup(1).orElseThrow().getMembers().isEmpty());
    }

    @Test
    void rebuildGroupsClearsExistingMembership() {
        UUID player = UUID.randomUUID();
        groupManager.join(player, 1);

        groupManager.rebuildGroups();

        assertTrue(groupManager.getGroupOf(player).isEmpty());
    }

    @Test
    void exportImportMembershipRoundTrips() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        groupManager.join(playerA, 1);
        groupManager.join(playerB, 2);

        Map<Integer, Set<UUID>> exported = groupManager.exportMembership();

        groupManager.rebuildGroups();
        assertTrue(groupManager.getGroupOf(playerA).isEmpty());

        groupManager.importMembership(exported);

        assertEquals(1, groupManager.getGroupOf(playerA).orElseThrow().getId());
        assertEquals(2, groupManager.getGroupOf(playerB).orElseThrow().getId());
    }

    @Test
    void importMembershipIgnoresUnknownGroups() {
        Map<Integer, Set<UUID>> membership = new HashMap<>();
        membership.put(99, Set.of(UUID.randomUUID()));

        groupManager.importMembership(membership);

        assertEquals(0, groupManager.getGroup(1).orElseThrow().getMembers().size());
    }
}
