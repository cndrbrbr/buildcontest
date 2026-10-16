package io.github.cndrbrbr.buildcontest.manager;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Auswertung der Abstimmung vor dem Contest-Start (PvP, Spieldauer). */
class VoteManagerTest {

    @Test
    void noVotesMeansNoPvpAndDefaultDuration() {
        VoteManager.Result result = VoteManager.tally(Map.of(), Map.of());
        assertFalse(result.pvp());
        assertEquals(VoteManager.DEFAULT_DURATION, result.durationMinutes());
    }

    @Test
    void pvpNeedsRealMajority() {
        Map<UUID, Boolean> pvp = new HashMap<>();
        pvp.put(UUID.randomUUID(), true);
        pvp.put(UUID.randomUUID(), false);
        assertFalse(VoteManager.tally(pvp, Map.of()).pvp());
        pvp.put(UUID.randomUUID(), true);
        assertTrue(VoteManager.tally(pvp, Map.of()).pvp());
    }

    @Test
    void mostVotedDurationWinsShorterOnTie() {
        Map<UUID, Integer> durations = new HashMap<>();
        durations.put(UUID.randomUUID(), 60);
        durations.put(UUID.randomUUID(), 15);
        assertEquals(15, VoteManager.tally(Map.of(), durations).durationMinutes());
        durations.put(UUID.randomUUID(), 60);
        assertEquals(60, VoteManager.tally(Map.of(), durations).durationMinutes());
    }
}
