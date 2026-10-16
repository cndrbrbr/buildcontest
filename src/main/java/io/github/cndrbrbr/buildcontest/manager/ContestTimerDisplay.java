package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Zeigt die Restzeit eines Contests mit Zeitlimit als Bossbar bei allen
 * Online-Spielern an und warnt kurz vor Schluss im Chat. Das automatische
 * Beenden selbst uebernimmt der Timer in GameStateManager.
 */
public final class ContestTimerDisplay {

    private static final long[] WARN_AT_SECONDS = {300, 60, 10};

    private final GameStateManager gameStateManager;
    private final BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
    private long lastRemainingSeconds = Long.MAX_VALUE;

    public ContestTimerDisplay(BuildContestPlugin plugin, GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
    }

    private void update() {
        long remainingMillis = gameStateManager.getRemainingMillis();
        if (remainingMillis < 0) {
            bar.setVisible(false);
            lastRemainingSeconds = Long.MAX_VALUE;
            return;
        }
        long remainingSeconds = (remainingMillis + 999) / 1000;
        String time = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60);
        String pvp = gameStateManager.isPvpEnabled() ? "§cPvP an" : "§aPvP aus";
        bar.setTitle((gameStateManager.isPaused() ? "§ePausiert - " : "§f") + "Restzeit " + time + " §7| " + pvp);
        long total = gameStateManager.getDurationMillis();
        bar.setProgress(total > 0 ? Math.min(1.0, Math.max(0.0, remainingMillis / (double) total)) : 1.0);
        bar.setColor(remainingSeconds <= 60 ? BarColor.RED : remainingSeconds <= 300 ? BarColor.YELLOW : BarColor.GREEN);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
        bar.setVisible(true);

        // Erster Durchlauf (z. B. nach Neustart): keine nachgeholten Warnungen.
        if (!gameStateManager.isPaused() && lastRemainingSeconds != Long.MAX_VALUE) {
            for (long warnAt : WARN_AT_SECONDS) {
                if (lastRemainingSeconds > warnAt && remainingSeconds <= warnAt) {
                    Bukkit.broadcastMessage("§6§lNoch " + (warnAt >= 60 ? warnAt / 60 + " Minute" + (warnAt >= 120 ? "n" : "")
                            : warnAt + " Sekunden") + "! §rDanach endet der Buildcontest automatisch.");
                }
            }
        }
        lastRemainingSeconds = remainingSeconds;
    }
}
