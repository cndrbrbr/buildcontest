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

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Schnellstart-Automatikmodus "Rot gegen Blau" (siehe
 * rules.md#automatikmodus-rot-gegen-blau): Admin aktiviert per
 * {@code /bc automode 2x2} bzw. {@code /bc automode 2x4} ein Preset (Gruppen-
 * anzahl/-groesse, Bauplatzgroesse); Spieler waehlen ihr Team per Rechtsklick
 * auf einen roten oder blauen Wolle-"Ball" statt per {@code /bc join}.
 * Sobald beide Teams voll sind, startet der Contest automatisch.
 *
 * Bekannte Einschraenkung: Der Wartezustand (wer schon gewaehlt hat, die
 * Team-Tafel) wird NICHT persistiert - ueberlebt ein Server-Neustart waehrend
 * der Wartephase nicht; der Admin muesste {@code /bc automode} danach erneut
 * ausfuehren.
 */
public final class AutomodeManager implements Listener {

    public enum Preset {
        TWO_BY_TWO(2, 2, 10),
        TWO_BY_FOUR(2, 4, 20);

        private final int groupCount;
        private final int groupSize;
        private final int plotSize;

        Preset(int groupCount, int groupSize, int plotSize) {
            this.groupCount = groupCount;
            this.groupSize = groupSize;
            this.plotSize = plotSize;
        }

        public static Optional<Preset> parse(String arg) {
            return switch (arg.toLowerCase(Locale.ROOT)) {
                case "2x2" -> Optional.of(TWO_BY_TWO);
                case "2x4" -> Optional.of(TWO_BY_FOUR);
                default -> Optional.empty();
            };
        }
    }

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;
    private final GroupManager groupManager;
    private final NamespacedKey ballTeamKey;

    private BuildContestCommand command;
    private boolean waiting;
    private TextDisplay board;

    public AutomodeManager(BuildContestPlugin plugin, MainConfig mainConfig, GroupManager groupManager) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.groupManager = groupManager;
        this.ballTeamKey = new NamespacedKey(plugin, "team_ball");
    }

    /** Spaet gebunden, um die zirkulaere Abhaengigkeit mit BuildContestCommand aufzuloesen. */
    public void setCommand(BuildContestCommand command) {
        this.command = command;
    }

    /**
     * Konfiguriert Gruppenanzahl/-groesse und Bauplatzgroesse fuer das
     * gewaehlte Preset, benennt die beiden Gruppen "Rot"/"Blau", stellt die
     * Team-Tafel an der aktuellen Position des Ausfuehrenden auf und gibt
     * allen online Spielern ohne Gruppe die beiden Waehl-Baelle.
     */
    public void activate(Preset preset, CommandSender sender) {
        waiting = true;

        plugin.getConfig().set("groups.count", preset.groupCount);
        plugin.getConfig().set("groups.max-size", preset.groupSize);
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

        Location boardLocation = (sender instanceof Player player)
                ? player.getLocation()
                : Bukkit.getWorlds().get(0).getSpawnLocation();
        spawnBoard(boardLocation);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (groupManager.getGroupOf(online.getUniqueId()).isEmpty()) {
                giveBalls(online);
            }
        }

        sender.sendMessage("§aAutomatikmodus aktiviert: 2 Teams (Rot/Blau), " + preset.groupSize
                + " Spieler je Team, Bauplatzgröße " + preset.plotSize + "x" + preset.plotSize
                + ". Spieler wählen per Ball ihr Team.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!waiting) {
            return;
        }
        Player player = event.getPlayer();
        if (groupManager.getGroupOf(player.getUniqueId()).isEmpty()) {
            giveBalls(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
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
     * Baelle bei Zuschauern einsammeln (siehe Klassen-Javadoc - wer jetzt noch
     * keinem Team zugeordnet ist, kommt in keines der beiden vollen Teams
     * mehr und bleibt Zuschauer, bis der Admin manuell eingreift), Team-Tafel
     * entfernen und den Contest wie /bc start beginnen.
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
        removeBoard();
        // Automatikmodus will immer einen NEUEN Contest - die Bestaetigung
        // zum Ersetzen einer evtl. noch vorhandenen alten Welt (siehe
        // BuildContestCommand#handleStart) gilt hier implizit als erteilt.
        command.handleStart(Bukkit.getConsoleSender(), true);
    }

    private void spawnBoard(Location location) {
        removeBoard();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        board = world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
        });
        updateBoard();
    }

    private void removeBoard() {
        if (board != null && !board.isDead()) {
            board.remove();
        }
        board = null;
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
