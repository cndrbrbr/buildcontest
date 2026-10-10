package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Start/Ende des Contests inkl. optionalem Timer (siehe rules.md#spielablauf--ende). */
public final class GameStateManager {

    /**
     * Fuer die Persistenz (siehe persistence.DataStore). endTimestampMillis
     * &lt;= 0 bedeutet "kein Timer". teleportedPlayers siehe
     * {@link #markInitialTeleportDone}.
     */
    public record State(boolean running, boolean frozen, long endTimestampMillis, Set<UUID> teleportedPlayers) {
    }

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    private boolean running;
    private boolean frozen;
    private long endTimestampMillis;
    private BukkitTask timerTask;
    /**
     * Wer bereits seinen einmaligen Startpunkt-Teleport zum eigenen Bauplatz
     * bekommen hat (siehe rules.md#welten-und-fortbewegung: "Beim ersten
     * Eintritt in den Contest duerfen Teilnehmer einmalig an einen sicheren
     * Startpunkt ... gebracht werden. Danach gibt es keine Spieler-Teleports
     * durch Buildcontest"). Muss ueberleben, damit ein Wiedereinstieg (Logout/
     * Serverneustart) keine zweite Teleport-Abkuerzung ermoeglicht.
     */
    private final Set<UUID> teleportedPlayers = new LinkedHashSet<>();

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
        // Ein neuer Contest gibt JEDEM Spieler wieder einen frischen einmaligen
        // Startpunkt-Teleport, auch wenn er diesen in einem fruehreren Contest
        // schon verbraucht hatte (siehe teleportedPlayers-Javadoc).
        teleportedPlayers.clear();
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

    /** @return true, falls dies der erste Teleport dieses Spielers im aktuellen Contest ist. */
    public boolean markInitialTeleportDone(UUID playerId) {
        return teleportedPlayers.add(playerId);
    }

    public boolean hasReceivedInitialTeleport(UUID playerId) {
        return teleportedPlayers.contains(playerId);
    }

    public State exportState() {
        return new State(running, frozen, endTimestampMillis, new LinkedHashSet<>(teleportedPlayers));
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
        teleportedPlayers.clear();
        teleportedPlayers.addAll(state.teleportedPlayers());

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
