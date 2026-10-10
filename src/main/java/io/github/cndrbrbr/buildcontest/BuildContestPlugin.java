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
import io.github.cndrbrbr.buildcontest.manager.WorldManager;
import io.github.cndrbrbr.buildcontest.persistence.DataStore;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Siehe rules.md im Projekt-Root fuer die vollstaendige Spielbeschreibung. */
public final class BuildContestPlugin extends JavaPlugin {

    private MainConfig mainConfig;
    private ScoreConfig scoreConfig;
    private GroupManager groupManager;
    private PlotManager plotManager;
    private ScoreManager scoreManager;
    private ScoreboardManager scoreboardManager;
    private GameStateManager gameStateManager;
    private WorldManager worldManager;
    private DataStore dataStore;
    private BukkitTask autosaveTask;

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
        worldManager = new WorldManager(this, mainConfig);
        worldManager.ensurePrimaryWorldSpawnOnGround();
        worldManager.ensureLoaded();
        dataStore = new DataStore(this, groupManager, plotManager, scoreManager, gameStateManager);

        if (dataStore.load()) {
            getLogger().info("Gespeicherter Spielstand aus data.yml geladen.");
        }
        scheduleAutosave();

        getServer().getPluginManager().registerEvents(
                new BlockListener(scoreManager, plotManager, gameStateManager, scoreboardManager), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(plotManager, groupManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(mainConfig, groupManager, scoreboardManager), this);

        BuildContestCommand command = new BuildContestCommand(
                this, mainConfig, scoreConfig, groupManager, plotManager,
                gameStateManager, scoreboardManager, scoreManager, worldManager);
        getCommand("bc").setExecutor(command);
        getCommand("bc").setTabCompleter(command);

        getLogger().info("Buildcontest aktiviert.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (dataStore != null) {
            dataStore.save();
        }
        if (scoreboardManager != null) {
            scoreboardManager.clearAll();
        }
        getLogger().info("Buildcontest deaktiviert.");
    }

    /**
     * Sichert den Spielstand regelmaessig zusaetzlich zum Speichern bei
     * onDisable, damit ein Server-Absturz (kein sauberes onDisable) moeglichst
     * wenig Fortschritt kostet (siehe persistence.DataStore,
     * config.yml#game.autosave-minutes).
     */
    public void scheduleAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        int minutes = mainConfig.getAutosaveMinutes();
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, dataStore::save, ticks, ticks);
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

    public WorldManager getWorldManager() {
        return worldManager;
    }
}
