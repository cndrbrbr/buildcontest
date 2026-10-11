package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.command.BuildContestCommand;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.model.Group;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Schnellstart-Automatikmodus "Rot gegen Blau" (siehe
 * rules.md#schnellstart-presets-und-start-steine): immer zwei Gruppen (Rot/
 * Blau), vier Presets mit unterschiedlicher Gruppengroesse und Teamwahl-Art.
 * Auslösbar per {@code /bc automode <preset>} (Admin, konfigurierte
 * Spieldauer) oder per Rechtsklick auf einen beschrifteten Start-Stein
 * (jeder Spieler ohne Admin, siehe {@link #placeStartStones}, immer feste
 * 30-Minuten-Dauer).
 *
 * Bekannte Einschraenkung: Der Wartezustand (wer schon gewaehlt hat, die
 * Team-Tafel) wird NICHT persistiert - ueberlebt ein Server-Neustart waehrend
 * der Wartephase nicht; der Admin muesste das Preset danach erneut
 * aktivieren.
 */
public final class AutomodeManager implements Listener {

    private static final int STONE_STARTED_DURATION_MINUTES = 30;

    public enum Preset {
        TWO_BY_ONE("2x1", 1, 10, SelectionMode.RANDOM_ON_PRESENCE),
        TWO_BY_TWO("2x2", 2, 10, SelectionMode.BALL),
        TWO_BY_FOUR("2x4", 4, 20, SelectionMode.BALL),
        /** Gruppengroesse wird erst bei der Aktivierung aus der Anzahl anwesender Spieler bestimmt. */
        TWO_BY_N("2xn", 0, 10, SelectionMode.INSTANT_SPLIT);

        enum SelectionMode { BALL, RANDOM_ON_PRESENCE, INSTANT_SPLIT }

        private final String label;
        private final int groupSize;
        private final int plotSize;
        private final SelectionMode selectionMode;

        Preset(String label, int groupSize, int plotSize, SelectionMode selectionMode) {
            this.label = label;
            this.groupSize = groupSize;
            this.plotSize = plotSize;
            this.selectionMode = selectionMode;
        }

        public static Optional<Preset> parse(String arg) {
            for (Preset preset : values()) {
                if (preset.label.equalsIgnoreCase(arg)) {
                    return Optional.of(preset);
                }
            }
            return Optional.empty();
        }
    }

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;
    private final GroupManager groupManager;
    private final GameStateManager gameStateManager;
    private final NamespacedKey ballTeamKey;
    private final NamespacedKey startStoneKey;

    private BuildContestCommand command;
    private boolean waiting;
    /** Welches Preset gerade in der Wartephase steckt, siehe {@link #onJoin}. Nur gueltig waehrend waiting == true. */
    private Preset activePreset;
    private TextDisplay board;
    private Location boardLocation;
    /** Feste Spieldauer fuer den aktuell aktivierten/laufenden Durchgang, siehe {@link #activate}. */
    private int forcedDurationMinutes = -1;

    public AutomodeManager(BuildContestPlugin plugin, MainConfig mainConfig, GroupManager groupManager,
                            GameStateManager gameStateManager) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.groupManager = groupManager;
        this.gameStateManager = gameStateManager;
        this.ballTeamKey = new NamespacedKey(plugin, "team_ball");
        this.startStoneKey = new NamespacedKey(plugin, "start_stone_preset");
    }

    /** Spaet gebunden, um die zirkulaere Abhaengigkeit mit BuildContestCommand aufzuloesen. */
    public void setCommand(BuildContestCommand command) {
        this.command = command;
    }

    /** Admin-Aktivierung per /bc automode - nutzt die konfigurierte (meist unbegrenzte) Spieldauer. */
    public void activate(Preset preset, CommandSender sender) {
        activate(preset, sender, -1);
    }

    /** Aktivierung per Start-Stein durch einen beliebigen Spieler - immer mit fester 30-Minuten-Dauer. */
    private void activateFromStartStone(Preset preset, Player clicker) {
        activate(preset, clicker, STONE_STARTED_DURATION_MINUTES);
    }

    private void activate(Preset preset, CommandSender sender, int durationMinutesOverride) {
        if (gameRunningOrSelectionInProgress()) {
            sender.sendMessage("§cEs läuft bereits ein Contest oder eine Teamwahl.");
            return;
        }
        this.forcedDurationMinutes = durationMinutesOverride;

        if (preset.selectionMode == Preset.SelectionMode.INSTANT_SPLIT) {
            activateInstantSplit(preset, sender);
            return;
        }

        waiting = true;
        activePreset = preset;
        configureGroups(preset, preset.groupSize);
        updateBoard();

        List<Player> candidates = onlinePlayersWithoutGroup();
        if (preset.selectionMode == Preset.SelectionMode.BALL) {
            candidates.forEach(this::giveBalls);
        } else {
            // RANDOM_ON_PRESENCE (2x1): keine Ballwahl, direkt zufaellig zuweisen.
            for (Player player : candidates) {
                if (!waiting) {
                    break;
                }
                randomAssign(player);
            }
        }

        sender.sendMessage("§aPreset " + preset.label + " aktiviert: 2 Teams (Rot/Blau), " + preset.groupSize
                + " Spieler je Team, Bauplatzgröße " + preset.plotSize + "x" + preset.plotSize + ".");
    }

    /**
     * 2xn: alle aktuell anwesenden Spieler ohne Gruppe werden sofort zufaellig
     * auf zwei Teams aufgeteilt (bei ungerader Anzahl bekommt Blau einen
     * mehr) - keine Ballwahl, keine Wartephase, der Contest startet direkt.
     */
    private void activateInstantSplit(Preset preset, CommandSender sender) {
        List<Player> candidates = onlinePlayersWithoutGroup();
        if (candidates.size() < 2) {
            sender.sendMessage("§cZu wenige Spieler ohne Gruppe online (mindestens 2 nötig für 2xn).");
            return;
        }
        Collections.shuffle(candidates);
        int sizeRed = candidates.size() / 2;
        int sizeBlue = candidates.size() - sizeRed;
        configureGroups(preset, Math.max(sizeRed, sizeBlue));

        for (int i = 0; i < candidates.size(); i++) {
            groupManager.assign(candidates.get(i).getUniqueId(), i < sizeRed ? 1 : 2);
        }

        sender.sendMessage("§a2xn aktiviert: " + candidates.size()
                + " anwesende Spieler zufällig in Rot/Blau aufgeteilt. Contest startet sofort.");
        startContestNow();
    }

    private void configureGroups(Preset preset, int maxSize) {
        plugin.getConfig().set("groups.count", 2);
        plugin.getConfig().set("groups.max-size", maxSize);
        plugin.getConfig().set("groups.assignment-mode", "FREE");
        plugin.getConfig().set("plots.size-x", preset.plotSize);
        plugin.getConfig().set("plots.size-z", preset.plotSize);
        plugin.saveConfig();
        mainConfig.reload();
        groupManager.rebuildGroups();

        groupManager.getGroup(1).ifPresent(group -> {
            group.setName("Rot");
            group.setColor(ChatColor.RED);
        });
        groupManager.getGroup(2).ifPresent(group -> {
            group.setName("Blau");
            group.setColor(ChatColor.BLUE);
        });
    }

    private List<Player> onlinePlayersWithoutGroup() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (groupManager.getGroupOf(player.getUniqueId()).isEmpty()) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean gameRunningOrSelectionInProgress() {
        return waiting || gameStateManager.isRunning();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!waiting) {
            return;
        }
        Player player = event.getPlayer();
        if (groupManager.getGroupOf(player.getUniqueId()).isPresent()) {
            return;
        }
        if (activePreset != null && activePreset.selectionMode == Preset.SelectionMode.RANDOM_ON_PRESENCE) {
            randomAssign(player);
        } else {
            giveBalls(player);
        }
    }

    /** Weist einen Spieler ohne Ballwahl zufaellig einem noch nicht vollen Team zu (siehe Preset#RANDOM_ON_PRESENCE). */
    private void randomAssign(Player player) {
        List<Integer> candidateGroupIds = new ArrayList<>();
        groupManager.getGroups().values().forEach(group -> {
            if (!group.isFull()) {
                candidateGroupIds.add(group.getId());
            }
        });
        if (candidateGroupIds.isEmpty()) {
            return;
        }
        int groupId = candidateGroupIds.get((int) (Math.random() * candidateGroupIds.size()));
        groupManager.assign(player.getUniqueId(), groupId);
        groupManager.getGroup(groupId).ifPresent(group ->
                player.sendMessage("§aDu wurdest zufällig Team " + group.getColor() + group.getName() + "§a zugewiesen."));
        updateBoard();
        checkAutoStart();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && handleStartStoneInteract(clickedBlock, event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        if (!waiting) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String team = item.getItemMeta().getPersistentDataContainer().get(ballTeamKey, PersistentDataType.STRING);
        if (team == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (groupManager.getGroupOf(player.getUniqueId()).isPresent()) {
            return;
        }

        int groupId = "RED".equals(team) ? 1 : 2;
        GroupManager.JoinResult result = groupManager.join(player.getUniqueId(), groupId);
        if (result != GroupManager.JoinResult.SUCCESS) {
            player.sendMessage("§cDieses Team ist bereits voll.");
            return;
        }

        removeBalls(player);
        groupManager.getGroup(groupId).ifPresent(group ->
                player.sendMessage("§aDu bist Team " + group.getColor() + group.getName() + "§a beigetreten."));
        updateBoard();
        checkAutoStart();
    }

    /** @return true, falls der Block ein Start-Stein-Schild war (unabhaengig davon, ob die Aktivierung geklappt hat). */
    private boolean handleStartStoneInteract(Block block, Player player) {
        if (!(block.getState() instanceof Sign sign)) {
            return false;
        }
        String presetLabel = sign.getPersistentDataContainer().get(startStoneKey, PersistentDataType.STRING);
        if (presetLabel == null) {
            return false;
        }
        Optional<Preset> preset = Preset.parse(presetLabel);
        if (preset.isEmpty()) {
            return true;
        }
        if (gameRunningOrSelectionInProgress()) {
            player.sendMessage("§cEs läuft bereits ein Contest oder eine Teamwahl.");
            return true;
        }
        activateFromStartStone(preset.get(), player);
        return true;
    }

    /**
     * Bricht eine evtl. noch laufende Teamwahl ab (siehe
     * {@link #gameRunningOrSelectionInProgress}) - z. B. wenn ein Admin
     * {@code /bc end} ausfuehrt, waehrend ein per Start-Stein begonnenes
     * Preset noch auf weitere Spieler wartet. Ohne das wuerde der
     * Wartezustand fuer immer "haengen bleiben" und jede weitere
     * Aktivierung blockieren, obwohl gar kein Contest mehr laeuft.
     */
    public void cancelWaitingPhase() {
        if (!waiting) {
            return;
        }
        waiting = false;
        activePreset = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            removeBalls(online);
        }
        showIdleStatus();
    }

    private void giveBalls(Player player) {
        player.getInventory().addItem(
                createBall(Material.RED_WOOL, ChatColor.RED, "Rot", "RED"),
                createBall(Material.BLUE_WOOL, ChatColor.BLUE, "Blau", "BLUE"));
    }

    private ItemStack createBall(Material material, ChatColor color, String label, String team) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color + "" + ChatColor.BOLD + label + "er Ball");
        meta.getPersistentDataContainer().set(ballTeamKey, PersistentDataType.STRING, team);
        item.setItemMeta(meta);
        return item;
    }

    private void removeBalls(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.hasItemMeta()
                    && stack.getItemMeta().getPersistentDataContainer().has(ballTeamKey, PersistentDataType.STRING)) {
                inventory.setItem(slot, null);
            }
        }
    }

    /**
     * Sobald beide Teams voll sind: Waehl-Phase beenden, uebrig gebliebene
     * Baelle bei Zuschauern einsammeln (wer jetzt noch keinem Team zugeordnet
     * ist, bleibt Zuschauer, bis der Admin manuell eingreift) und den Contest
     * wie /bc start beginnen.
     */
    private void checkAutoStart() {
        boolean allFull = groupManager.getGroups().values().stream().allMatch(Group::isFull);
        if (!allFull) {
            return;
        }
        waiting = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            removeBalls(online);
        }
        startContestNow();
    }

    /**
     * Startet den vorbereiteten Contest (siehe {@link #forcedDurationMinutes}
     * fuer die Spieldauer) und setzt die Statusanzeige auf "laeuft".
     * Automatikmodus will immer einen NEUEN Contest - die Bestaetigung zum
     * Ersetzen einer evtl. noch vorhandenen alten Welt (siehe
     * BuildContestCommand#handleStart) gilt hier implizit als erteilt.
     */
    private void startContestNow() {
        showRunningStatus();
        command.handleStart(Bukkit.getConsoleSender(), true, forcedDurationMinutes);
    }

    /**
     * Stellt fuer jedes Preset einen beschrifteten Stein (Stein-Block +
     * Schild) an der aktuellen Position des Admins auf, plus eine feste
     * Statusanzeige (siehe rules.md#schnellstart-presets-und-start-steine).
     * Ein Rechtsklick auf das Schild loest {@link #activateFromStartStone}
     * aus. Merkt sich dauerhaft (config.yml), dass Steine existieren, damit
     * {@link #placeStartStonesIfMissing} sie nicht zusaetzlich am Weltspawn
     * aufstellt.
     */
    public void placeStartStones(Player admin) {
        Location base = admin.getLocation();
        if (placeStartStonesAt(base)) {
            admin.sendMessage("§aStart-Steine (2x1/2x2/2x4/2xn) aufgestellt.");
        }
    }

    /**
     * Stellt die Start-Steine automatisch am Weltspawn der Eingangswelt auf,
     * falls das noch nie passiert ist (weder manuell per
     * {@link #placeStartStones} noch bereits automatisch) - damit ein
     * Contest auch ganz ohne jeden manuellen Admin-Schritt per Stein
     * begonnen werden kann. Wird einmalig beim Serverstart aufgerufen, siehe
     * BuildContestPlugin#onEnable.
     */
    public void placeStartStonesIfMissing() {
        if (plugin.getConfig().getBoolean("startstones.placed", false)) {
            return;
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return;
        }
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        if (placeStartStonesAt(spawn)) {
            plugin.getLogger().info("Start-Steine (2x1/2x2/2x4/2xn) automatisch am Weltspawn der Eingangswelt "
                    + "aufgestellt (siehe /bc placestartstones, um sie woanders neu zu platzieren).");
        }
    }

    private boolean placeStartStonesAt(Location base) {
        World world = base.getWorld();
        if (world == null) {
            return false;
        }
        int baseX = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();

        Preset[] presets = Preset.values();
        for (int i = 0; i < presets.length; i++) {
            int x = baseX + i * 2;
            Block stoneBlock = world.getBlockAt(x, y, z);
            stoneBlock.setType(Material.STONE);
            Block signBlock = world.getBlockAt(x, y + 1, z);
            signBlock.setType(Material.OAK_SIGN);
            if (signBlock.getState() instanceof Sign sign) {
                sign.setLine(0, ChatColor.BOLD + presets[i].label);
                sign.setLine(1, "Rechtsklick");
                sign.setLine(2, "zum Start");
                sign.setLine(3, "(30 Minuten)");
                sign.getPersistentDataContainer().set(startStoneKey, PersistentDataType.STRING, presets[i].label);
                sign.update(true);
            }
        }

        boardLocation = new Location(world, baseX + (presets.length - 1) / 2.0, y + 2.5, z);
        spawnStatusBoard();

        plugin.getConfig().set("startstones.placed", true);
        plugin.saveConfig();
        return true;
    }

    private void spawnStatusBoard() {
        if (boardLocation == null) {
            return;
        }
        World world = boardLocation.getWorld();
        if (world == null) {
            return;
        }
        removeBoard();
        board = world.spawn(boardLocation, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
        });
        showIdleStatus();
    }

    private void removeBoard() {
        if (board != null && !board.isDead()) {
            board.remove();
        }
        board = null;
    }

    /** Siehe rules.md#schnellstart-presets-und-start-steine: Anzeige muss den aktuellen Status zeigen. Vom Contest-Ende aufgerufen. */
    public void showIdleStatus() {
        if (board == null) {
            return;
        }
        board.setText(ChatColor.BOLD + "Buildcontest\n" + ChatColor.GREEN + "Bereit - Start-Stein benutzen!");
    }

    private void showRunningStatus() {
        if (board == null) {
            return;
        }
        board.setText(ChatColor.BOLD + "Buildcontest\n" + ChatColor.RED + "Contest läuft - bitte warten");
    }

    private void updateBoard() {
        if (board == null) {
            return;
        }
        StringBuilder text = new StringBuilder(ChatColor.BOLD + "Team-Übersicht\n");
        for (Group group : groupManager.getGroups().values()) {
            text.append(group.getColor()).append(ChatColor.BOLD).append(group.getName())
                    .append(ChatColor.RESET).append(group.getColor())
                    .append(" (").append(group.getMembers().size()).append('/').append(group.getMaxSize())
                    .append(")\n");
            for (UUID memberId : group.getMembers()) {
                String name = Bukkit.getOfflinePlayer(memberId).getName();
                text.append(group.getColor()).append(name != null ? name : "?").append('\n');
            }
        }
        board.setText(text.toString());
    }
}
