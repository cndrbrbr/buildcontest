package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Zustandsverwaltung des Contests inkl. optionalem Timer (siehe
 * rules.md#ablauf-unterbrechung-und-spielende). Modelliert die dort
 * genannten Zustaende SETUP/LOBBY/PREPARING als einen gemeinsamen
 * "noch nicht gestartet"-Zustand (running=false, frozen=false) - das Plugin
 * gated derzeit kein Verhalten unterschiedlich zwischen diesen drei Phasen,
 * eine feinere Unterteilung wuerde also keinen beobachtbaren Unterschied
 * machen. RUNNING/PAUSED/FINISHED sind dagegen vollstaendig abgebildet.
 */
public final class GameStateManager {

    /**
     * Fuer die Persistenz (siehe persistence.DataStore). endTimestampMillis
     * &lt;= 0 bedeutet "kein Timer". remainingMillisAtPause ist nur waehrend
     * einer Pause gueltig (siehe {@link #pause}). teleportedPlayers siehe
     * {@link #markInitialTeleportDone}.
     */
    public record State(boolean running, boolean paused, boolean frozen, long endTimestampMillis,
                         long remainingMillisAtPause, Set<UUID> teleportedPlayers) {
    }

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;

    private boolean running;
    private boolean paused;
    private boolean frozen;
    private long endTimestampMillis;
    /** Nur waehrend paused == true gueltig, siehe {@link #pause}/{@link #resume}. */
    private long remainingMillisAtPause;
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
    /**
     * Wird (statt eines reinen end()) aufgerufen, wenn der Timer von selbst
     * ablaeuft - damit auch ein natuerliches Spielende die volle
     * Ende-Sequenz ausloest (Endstand archivieren/bekanntgeben, Spieler
     * zurueckteleportieren, Automode-Statusanzeige zuruecksetzen), nicht nur
     * die internen running/frozen-Flags. Siehe BuildContestPlugin#onEnable.
     */
    private Runnable onTimerEnd;

    public GameStateManager(BuildContestPlugin plugin, MainConfig mainConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
    }

    public void setOnTimerEnd(Runnable onTimerEnd) {
        this.onTimerEnd = onTimerEnd;
    }

    /** True sowohl waehrend RUNNING als auch PAUSED - siehe {@link #isPaused} fuer die Unterscheidung. */
    public boolean isRunning() {
        return running;
    }

    /** PAUSED: Timer angehalten, keine wettbewerbsrelevanten Aktionen (siehe BlockListener). */
    public boolean isPaused() {
        return paused;
    }

    /** Nach Spielende wird der Baufortschritt eingefroren (siehe rules.md#spielablauf--ende). */
    public boolean isFrozen() {
        return frozen;
    }

    public void start() {
        start(-1);
    }

    /**
     * @param forcedDurationMinutes bei &gt; 0 wird diese Dauer statt der
     *                              konfigurierten game.duration-minutes
     *                              verwendet, OHNE die Konfiguration selbst
     *                              zu veraendern - fuer per Start-Stein
     *                              ausgeloeste Contests, die immer genau 30
     *                              Minuten laufen sollen (siehe
     *                              rules.md#schnellstart-presets-und-start-steine),
     *                              waehrend per /bc automode durch einen
     *                              Admin gestartete Contests weiterhin die
     *                              konfigurierte (meist unbegrenzte) Dauer
     *                              nutzen. &lt;= 0 bedeutet: konfigurierte
     *                              Dauer verwenden.
     */
    public void start(int forcedDurationMinutes) {
        running = true;
        paused = false;
        frozen = false;
        // Ein neuer Contest gibt JEDEM Spieler wieder einen frischen einmaligen
        // Startpunkt-Teleport, auch wenn er diesen in einem fruehreren Contest
        // schon verbraucht hatte (siehe teleportedPlayers-Javadoc).
        teleportedPlayers.clear();
        cancelTimer();

        int durationMinutes = forcedDurationMinutes > 0 ? forcedDurationMinutes : mainConfig.getDurationMinutes();
        if (durationMinutes > 0) {
            endTimestampMillis = System.currentTimeMillis() + durationMinutes * 60_000L;
            scheduleTimer(durationMinutes * 60L * 20L);
        } else {
            endTimestampMillis = -1;
        }
    }

    public void end() {
        running = false;
        paused = false;
        frozen = true;
        endTimestampMillis = -1;
        cancelTimer();
    }

    /**
     * Haelt Timer und wettbewerbsrelevante Spieleraktionen an (siehe
     * rules.md#ablauf-unterbrechung-und-spielende, BlockListener). Ohne
     * Effekt, wenn kein Contest laeuft oder bereits pausiert ist.
     */
    public void pause() {
        if (!running || paused) {
            return;
        }
        if (endTimestampMillis > 0) {
            remainingMillisAtPause = endTimestampMillis - System.currentTimeMillis();
        } else {
            remainingMillisAtPause = -1;
        }
        paused = true;
        cancelTimer();
    }

    /**
     * Setzt denselben Contest mit allen Punkten und derselben Welt fort
     * (siehe rules.md: "/bc resume setzt denselben Contest ... fort"). Die
     * verbleibende Zeit beim Pausieren wird 1:1 uebernommen - die Pausendauer
     * selbst zaehlt nicht gegen die Spielzeit.
     */
    public void resume() {
        if (!running || !paused) {
            return;
        }
        paused = false;
        if (remainingMillisAtPause > 0) {
            endTimestampMillis = System.currentTimeMillis() + remainingMillisAtPause;
            scheduleTimer(remainingMillisAtPause / 50L);
        }
    }

    private void scheduleTimer(long delayTicks) {
        timerTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (onTimerEnd != null) {
                onTimerEnd.run();
            } else {
                end();
            }
        }, Math.max(0, delayTicks));
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
        return new State(running, paused, frozen, endTimestampMillis, remainingMillisAtPause,
                new LinkedHashSet<>(teleportedPlayers));
    }

    /**
     * Stellt den Spielstatus nach einem Neustart wieder her (siehe
     * persistence.DataStore). War ein Timer aktiv UND nicht pausiert, wird
     * die verbleibende Restzeit anhand des gespeicherten Endzeitpunkts neu
     * berechnet - ist die Zeit waehrend des Neustarts bereits abgelaufen,
     * endet der Contest sofort. War der Contest pausiert, bleibt der Timer
     * bewusst angehalten (darf waehrend der Downtime nicht weiterlaufen,
     * siehe rules.md#persistenz-und-absturzsicherheit) - erst ein
     * ausdruecklicher /bc resume setzt ihn fort.
     */
    public void importState(State state) {
        cancelTimer();
        running = state.running();
        paused = state.paused();
        frozen = state.frozen();
        endTimestampMillis = state.endTimestampMillis();
        remainingMillisAtPause = state.remainingMillisAtPause();
        teleportedPlayers.clear();
        teleportedPlayers.addAll(state.teleportedPlayers());

        if (running && !paused && endTimestampMillis > 0) {
            long remainingMillis = endTimestampMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                end();
            } else {
                scheduleTimer(remainingMillis / 50L);
            }
        }
    }
}
