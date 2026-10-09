package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.scheduler.BukkitTask;

/** Start/Ende des Contests inkl. optionalem Timer (siehe rules.md#spielablauf--ende). */
public final class GameStateManager {

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    private boolean running;
    private boolean frozen;
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

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        int durationMinutes = mainConfig.getDurationMinutes();
        if (durationMinutes > 0) {
            timerTask = plugin.getServer().getScheduler().runTaskLater(
                    plugin, this::end, durationMinutes * 60L * 20L);
        }
    }

    public void end() {
        running = false;
        frozen = true;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }
}
