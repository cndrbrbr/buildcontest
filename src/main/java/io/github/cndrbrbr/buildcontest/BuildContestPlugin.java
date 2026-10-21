package io.github.cndrbrbr.buildcontest;

import io.github.cndrbrbr.buildcontest.command.BuildContestCommand;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.listener.BlockListener;
import io.github.cndrbrbr.buildcontest.listener.PlayerConnectionListener;
import io.github.cndrbrbr.buildcontest.listener.ProtectionListener;
import io.github.cndrbrbr.buildcontest.listener.PvpListener;
import io.github.cndrbrbr.buildcontest.manager.AutomodeManager;
import io.github.cndrbrbr.buildcontest.manager.ContestTimerDisplay;
import io.github.cndrbrbr.buildcontest.manager.EntryTeleportManager;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.InventoryVaultManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.manager.TopPlacersBoard;
import io.github.cndrbrbr.buildcontest.manager.VoteManager;
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
    private AutomodeManager automodeManager;
    private EntryTeleportManager entryTeleportManager;
    private InventoryVaultManager inventoryVaultManager;
    private DataStore dataStore;
    private VoteManager voteManager;
    private TopPlacersBoard topPlacersBoard;
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
        automodeManager = new AutomodeManager(this, mainConfig, groupManager, gameStateManager);
        inventoryVaultManager = new InventoryVaultManager();
        entryTeleportManager = new EntryTeleportManager(gameStateManager, groupManager, plotManager,
                inventoryVaultManager);

        voteManager = new VoteManager(this);
        new ContestTimerDisplay(this, gameStateManager);

        if (dataStore.load()) {
            getLogger().info("Gespeicherter Spielstand aus data.yml geladen.");
        }
        scheduleAutosave();

        getServer().getPluginManager().registerEvents(
                new BlockListener(scoreManager, plotManager, gameStateManager, scoreboardManager, mainConfig), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(plotManager, groupManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(mainConfig, groupManager, scoreboardManager, entryTeleportManager,
                        gameStateManager), this);
        getServer().getPluginManager().registerEvents(automodeManager, this);
        getServer().getPluginManager().registerEvents(voteManager, this);
        getServer().getPluginManager().registerEvents(
                new PvpListener(mainConfig, gameStateManager, groupManager), this);

        BuildContestCommand command = new BuildContestCommand(
                this, mainConfig, scoreConfig, groupManager, plotManager,
                gameStateManager, scoreboardManager, scoreManager, worldManager, automodeManager,
                entryTeleportManager, dataStore, inventoryVaultManager);
        automodeManager.setCommand(command);
        // Ein natuerlicher Timer-Ablauf muss denselben vollen Ende-Ablauf
        // ausloesen wie /bc end (Endstand archivieren/bekanntgeben,
        // Rueckteleport, Statusanzeige) - nicht nur die internen
        // running/frozen-Flags umschalten (siehe GameStateManager#setOnTimerEnd).
        gameStateManager.setOnTimerEnd(() -> command.handleEnd(getServer().getConsoleSender()));
        getCommand("bc").setExecutor(command);
        getCommand("bc").setTabCompleter(command);

        // Start-Steine sollen auch ganz ohne manuellen Admin-Schritt
        // nutzbar sein - beim allerersten Serverstart automatisch am
        // Weltspawn der Eingangswelt aufstellen, falls noch nie passiert.
        automodeManager.placeStartStonesIfMissing();
        automodeManager.findBoardLocationInLoadedChunks();
        topPlacersBoard = new TopPlacersBoard(this, automodeManager);

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

    public VoteManager getVoteManager() {
        return voteManager;
    }

    public TopPlacersBoard getTopPlacersBoard() {
        return topPlacersBoard;
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
