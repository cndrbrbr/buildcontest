package io.github.cndrbrbr.buildcontest;

import io.github.cndrbrbr.buildcontest.command.BuildContestCommand;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.listener.BlockListener;
import io.github.cndrbrbr.buildcontest.listener.PlayerConnectionListener;
import io.github.cndrbrbr.buildcontest.listener.ProtectionListener;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Siehe rules.md im Projekt-Root fuer die vollstaendige Spielbeschreibung. */
public final class BuildContestPlugin extends JavaPlugin {

    private MainConfig mainConfig;
    private ScoreConfig scoreConfig;
    private GroupManager groupManager;
    private PlotManager plotManager;
    private ScoreManager scoreManager;
    private ScoreboardManager scoreboardManager;
    private GameStateManager gameStateManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("scoreboard-config.yml", false);

        mainConfig = new MainConfig(this);
        scoreConfig = new ScoreConfig(this);
        groupManager = new GroupManager(mainConfig);
        plotManager = new PlotManager(this, mainConfig);
        scoreManager = new ScoreManager(scoreConfig);
        scoreboardManager = new ScoreboardManager(groupManager, scoreManager);
        gameStateManager = new GameStateManager(this, mainConfig);

        getServer().getPluginManager().registerEvents(
                new BlockListener(scoreManager, plotManager, gameStateManager, scoreboardManager), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(plotManager, groupManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(mainConfig, groupManager, scoreboardManager), this);

        BuildContestCommand command = new BuildContestCommand(
                this, mainConfig, scoreConfig, groupManager, plotManager,
                gameStateManager, scoreboardManager, scoreManager);
        getCommand("bc").setExecutor(command);
        getCommand("bc").setTabCompleter(command);

        getLogger().info("Buildcontest aktiviert.");
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.clearAll();
        }
        getLogger().info("Buildcontest deaktiviert.");
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public ScoreConfig getScoreConfig() {
        return scoreConfig;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }

    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public GameStateManager getGameStateManager() {
        return gameStateManager;
    }
}
