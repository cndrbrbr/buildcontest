package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Prueft Start/Ende und die Wiederherstellung nach einem Neustart (siehe rules.md#spielablauf--ende). */
@ExtendWith(MockitoExtension.class)
class GameStateManagerTest {

    @Mock
    private BuildContestPlugin plugin;
    @Mock
    private MainConfig mainConfig;
    @Mock
    private Server server;
    @Mock
    private BukkitScheduler scheduler;

    private GameStateManager gameStateManager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        lenient().when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        gameStateManager = new GameStateManager(plugin, mainConfig);
    }

    @Test
    void startWithoutDurationRunsIndefinitely() {
        when(mainConfig.getDurationMinutes()).thenReturn(0);

        gameStateManager.start();

        assertTrue(gameStateManager.isRunning());
        assertFalse(gameStateManager.isFrozen());
    }

    @Test
    void endFreezesTheContest() {
        when(mainConfig.getDurationMinutes()).thenReturn(0);
        gameStateManager.start();

        gameStateManager.end();

        assertFalse(gameStateManager.isRunning());
        assertTrue(gameStateManager.isFrozen());
    }

    @Test
    void exportImportRoundTripsFrozenState() {
        when(mainConfig.getDurationMinutes()).thenReturn(0);
        gameStateManager.start();
        gameStateManager.end();
        GameStateManager.State exported = gameStateManager.exportState();

        GameStateManager restored = new GameStateManager(plugin, mainConfig);
        restored.importState(exported);

        assertFalse(restored.isRunning());
        assertTrue(restored.isFrozen());
    }

    @Test
    void importingAnAlreadyExpiredTimerEndsImmediately() {
        // Simuliert: Contest lief mit Timer, Server war waehrend des
        // eigentlichen Enddatums neu gestartet/offline.
        GameStateManager.State expired = new GameStateManager.State(
                true, false, System.currentTimeMillis() - 1_000, Set.of());

        gameStateManager.importState(expired);

        assertFalse(gameStateManager.isRunning());
        assertTrue(gameStateManager.isFrozen());
    }

    @Test
    void importingARunningTimerReschedulesWithRemainingTime() {
        GameStateManager.State stillRunning = new GameStateManager.State(
                true, false, System.currentTimeMillis() + 60_000, Set.of());

        gameStateManager.importState(stillRunning);

        assertTrue(gameStateManager.isRunning());
        assertFalse(gameStateManager.isFrozen());
    }

    @Test
    void initialTeleportIsOnlyGrantedOnce() {
        when(mainConfig.getDurationMinutes()).thenReturn(0);
        gameStateManager.start();
        UUID player = UUID.randomUUID();

        assertTrue(gameStateManager.markInitialTeleportDone(player));
        assertTrue(gameStateManager.hasReceivedInitialTeleport(player));
        assertFalse(gameStateManager.markInitialTeleportDone(player));
    }

    @Test
    void startingANewContestResetsTeleportTracking() {
        when(mainConfig.getDurationMinutes()).thenReturn(0);
        gameStateManager.start();
        UUID player = UUID.randomUUID();
        gameStateManager.markInitialTeleportDone(player);

        gameStateManager.end();
        gameStateManager.start();

        assertFalse(gameStateManager.hasReceivedInitialTeleport(player));
    }
}
