package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.scheduler.BukkitTask;

/** Start/Ende des Contests inkl. optionalem Timer (siehe rules.md#spielablauf--ende). */
public final class GameStateManager {

    /** Fuer die Persistenz (siehe persistence.DataStore). endTimestampMillis &lt;= 0 bedeutet "kein Timer". */
    public record State(boolean running, boolean frozen, long endTimestampMillis) {
    }

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    private boolean running;
    private boolean frozen;
    private long endTimestampMillis;
    private BukkitTask timerTask;

    public GameStateManager(BuildContestPlugin plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    public boolean isRunning() {
        return running;
    }

    /** Nach Spielende wird der Baufortschritt eingefroren (siehe rules.md#spielablauf--ende). */
    public boolean isFrozen() {
        return frozen;
    }

    public void start() {
        running = true;
        frozen = false;
        cancelTimer();

        int durationMinutes = mainConfig.getDurationMinutes();
        if (durationMinutes > 0) {
            endTimestampMillis = System.currentTimeMillis() + durationMinutes * 60_000L;
            scheduleTimer(durationMinutes * 60L * 20L);
        } else {
            endTimestampMillis = -1;
        }
    }

    public void end() {
        running = false;
        frozen = true;
        endTimestampMillis = -1;
        cancelTimer();
    }

    private void scheduleTimer(long delayTicks) {
        timerTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::end, Math.max(0, delayTicks));
    }

    private void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public State exportState() {
        return new State(running, frozen, endTimestampMillis);
    }

    /**
     * Stellt den Spielstatus nach einem Neustart wieder her (siehe
     * persistence.DataStore). War ein Timer aktiv, wird die verbleibende
     * Restzeit anhand des gespeicherten Endzeitpunkts neu berechnet - ist die
     * Zeit waehrend des Neustarts bereits abgelaufen, endet der Contest sofort.
     */
    public void importState(State state) {
        cancelTimer();
        running = state.running();
        frozen = state.frozen();
        endTimestampMillis = state.endTimestampMillis();

        if (running && endTimestampMillis > 0) {
            long remainingMillis = endTimestampMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                end();
            } else {
                scheduleTimer(remainingMillis / 50L);
            }
        }
    }
}
