package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.model.Group;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Comparator;
import java.util.List;

/**
 * Sidebar-Scoreboard pro Spieler (siehe rules.md#scoreboard-anzeige): Punktestand
 * aller Gruppen (fuer alle Spieler identisch) plus der persoenliche Beitrag des
 * jeweiligen Betrachters zu seiner eigenen Gruppe.
 */
public final class ScoreboardManager {

    private final GroupManager groupManager;
    private final ScoreManager scoreManager;

    public ScoreboardManager(GroupManager groupManager, ScoreManager scoreManager) {
        this.groupManager = groupManager;
        this.scoreManager = scoreManager;
    }

    public void setup(Player player) {
        refresh(player);
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "buildcontest", "dummy", ChatColor.GOLD + "Buildcontest");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Group> sorted = groupManager.getGroups().values().stream()
                .sorted(Comparator.comparingInt((Group g) -> scoreManager.getGroupScore(g.getId())).reversed())
                .toList();

        int line = sorted.size() + 2;
        boolean leader = true;
        for (Group group : sorted) {
            // Fuehrende Gruppe bekommt einen Stern statt einer festen Farbe
            // (siehe rules.md#scoreboard-anzeige) - jede Gruppe behaelt sonst
            // immer ihre eigene Farbe (z. B. Rot/Blau im Automatikmodus).
            String prefix = leader ? ChatColor.BOLD + "★ " : "";
            leader = false;
            objective.getScore(prefix + group.getColor() + group.getName() + ": "
                    + scoreManager.getGroupScore(group.getId())).setScore(line--);
        }

        objective.getScore(" ").setScore(line--);

        int personalScore = scoreManager.getPersonalScore(player.getUniqueId());
        objective.getScore(ChatColor.AQUA + "Dein Beitrag: " + personalScore).setScore(line);

        player.setScoreboard(scoreboard);
    }
}
