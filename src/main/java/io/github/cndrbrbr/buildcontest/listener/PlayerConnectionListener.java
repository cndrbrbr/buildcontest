package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Scoreboard-Setup und Configfile-Zuweisung beim Beitreten (siehe rules.md#gruppen). */
public final class PlayerConnectionListener implements Listener {

    private final MainConfig mainConfig;
    private final GroupManager groupManager;
    private final ScoreboardManager scoreboardManager;

    public PlayerConnectionListener(MainConfig mainConfig, GroupManager groupManager,
                                     ScoreboardManager scoreboardManager) {
        this.mainConfig = mainConfig;
        this.groupManager = groupManager;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (mainConfig.getAssignmentMode() == MainConfig.AssignmentMode.CONFIG
                && groupManager.getGroupOf(player.getUniqueId()).isEmpty()) {
            groupManager.resolvePresetGroup(player.getName())
                    .ifPresent(groupId -> groupManager.assign(player.getUniqueId(), groupId));
        }

        scoreboardManager.setup(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboardManager.clear(event.getPlayer());
    }
}
