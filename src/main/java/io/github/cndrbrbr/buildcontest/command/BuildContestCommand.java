package io.github.cndrbrbr.buildcontest.command;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.manager.AutomodeManager;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.manager.WorldManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.model.Plot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Implementiert die in rules.md#admin-befehle-übersicht beschriebenen Unterbefehle von /bc. */
public final class BuildContestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "tp", "setgroups", "setgroupsize", "setplotsize",
            "setdistance", "setscore", "assign", "start", "end", "deleteworld", "automode", "reload");

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;
    private final ScoreConfig scoreConfig;
    private final GroupManager groupManager;
    private final PlotManager plotManager;
    private final GameStateManager gameStateManager;
    private final ScoreboardManager scoreboardManager;
    private final ScoreManager scoreManager;
    private final WorldManager worldManager;
    private final AutomodeManager automodeManager;

    public BuildContestCommand(BuildContestPlugin plugin, MainConfig mainConfig, ScoreConfig scoreConfig,
                                GroupManager groupManager, PlotManager plotManager,
                                GameStateManager gameStateManager, ScoreboardManager scoreboardManager,
                                ScoreManager scoreManager, WorldManager worldManager,
                                AutomodeManager automodeManager) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.scoreConfig = scoreConfig;
        this.groupManager = groupManager;
        this.plotManager = plotManager;
        this.gameStateManager = gameStateManager;
        this.scoreboardManager = scoreboardManager;
        this.scoreManager = scoreManager;
        this.worldManager = worldManager;
        this.automodeManager = automodeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eBenutzung: /bc <" + String.join("|", SUBCOMMANDS) + ">");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "join" -> handleJoin(sender, args);
            case "tp" -> handleTeleport(sender);
            case "setgroups" -> requireAdmin(sender) && handleSetGroups(sender, args);
            case "setgroupsize" -> requireAdmin(sender) && handleSetGroupSize(sender, args);
            case "setplotsize" -> requireAdmin(sender) && handleSetPlotSize(sender, args);
            case "setdistance" -> requireAdmin(sender) && handleSetDistance(sender, args);
            case "setscore" -> requireAdmin(sender) && handleSetScore(sender, args);
            case "assign" -> requireAdmin(sender) && handleAssign(sender, args);
            case "start" -> requireAdmin(sender) && handleStart(sender);
            case "end" -> requireAdmin(sender) && handleEnd(sender);
            case "deleteworld" -> requireAdmin(sender) && handleDeleteWorld(sender);
            case "automode" -> requireAdmin(sender) && handleAutomode(sender, args);
            case "reload" -> requireAdmin(sender) && handleReload(sender);
            default -> {
                sender.sendMessage("§cUnbekannter Unterbefehl: " + sub);
                yield true;
            }
        };
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("buildcontest.admin")) {
            return true;
        }
        sender.sendMessage("§cDafür fehlt dir die Berechtigung.");
        return false;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können einer Gruppe beitreten.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eBenutzung: /bc join <gruppe>");
            return true;
        }
        int groupId = parseIntOrMessage(sender, args[1]);
        if (groupId < 0) {
            return true;
        }

        GroupManager.JoinResult result = groupManager.join(player.getUniqueId(), groupId);
        switch (result) {
            case SUCCESS -> {
                sender.sendMessage("§aDu bist Gruppe " + groupId + " beigetreten.");
                scoreboardManager.refreshAll();
            }
            case ALREADY_IN_GROUP -> sender.sendMessage("§cDu bist bereits einer Gruppe zugeordnet.");
            case GROUP_FULL -> sender.sendMessage("§cDiese Gruppe ist bereits voll.");
            case NO_SUCH_GROUP -> sender.sendMessage("§cDiese Gruppe existiert nicht.");
            case WRONG_MODE ->
                    sender.sendMessage("§cDie freie Gruppenwahl ist deaktiviert (Configfile-Zuweisung aktiv).");
        }
        return true;
    }

    /**
     * Teleportiert alle online Mitglieder der eigenen Gruppe gemeinsam zur
     * Mitte ihres Bauplatzes (siehe rules.md#bauplaetze). Erfordert, dass der
     * Contest bereits gestartet wurde (sonst existiert noch kein Bauplatz).
     */
    private boolean handleTeleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können sich zu ihrem Bauplatz teleportieren lassen.");
            return true;
        }
        Optional<Group> groupOpt = groupManager.getGroupOf(player.getUniqueId());
        if (groupOpt.isEmpty()) {
            sender.sendMessage("§cDu bist noch keiner Gruppe zugeordnet.");
            return true;
        }
        Group group = groupOpt.get();

        Optional<Plot> plotOpt = plotManager.getPlot(group.getId());
        if (plotOpt.isEmpty()) {
            sender.sendMessage("§cFür eure Gruppe gibt es noch keinen Bauplatz - wurde der Contest schon mit /bc start begonnen?");
            return true;
        }
        Optional<Location> destinationOpt = plotCenterLocation(plotOpt.get());
        if (destinationOpt.isEmpty()) {
            sender.sendMessage("§cDie Bauplatz-Welt ist aktuell nicht geladen.");
            return true;
        }
        Location destination = destinationOpt.get();

        int teleported = 0;
        for (UUID memberId : group.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member == null) {
                continue;
            }
            member.teleport(destination);
            member.sendMessage("§aDu wurdest zu eurem Bauplatz teleportiert.");
            teleported++;
        }
        sender.sendMessage("§a" + teleported + " online Mitglied(er) eurer Gruppe wurden zum Bauplatz teleportiert.");
        return true;
    }

    private Optional<Location> plotCenterLocation(Plot plot) {
        World world = plugin.getServer().getWorld(plot.getWorldName());
        if (world == null) {
            return Optional.empty();
        }
        double centerX = (plot.getMinX() + plot.getMaxX()) / 2.0 + 0.5;
        double centerZ = (plot.getMinZ() + plot.getMaxZ()) / 2.0 + 0.5;
        return Optional.of(new Location(world, centerX, plot.getSurfaceY() + 1, centerZ));
    }

    /**
     * Teleportiert bei Contest-Start alle online Spieler in die Bauplatz-Welt
     * (siehe rules.md#spielablauf--ende): Mitglieder einer Gruppe direkt zu
     * ihrem Bauplatz, alle anderen (noch keiner Gruppe zugeordnet) an den
     * Welt-Spawnpunkt.
     */
    private void teleportAllToBuildWorld() {
        World buildWorld = plugin.getServer().getWorld(mainConfig.getWorldName());
        if (buildWorld == null) {
            return;
        }
        Location fallback = buildWorld.getSpawnLocation();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location destination = groupManager.getGroupOf(player.getUniqueId())
                    .flatMap(group -> plotManager.getPlot(group.getId()))
                    .flatMap(this::plotCenterLocation)
                    .orElse(fallback);
            player.teleport(destination);
        }
    }

    /**
     * Teleportiert bei Contest-Ende alle Spieler, die sich noch in der
     * Bauplatz-Welt befinden, zurueck in die Eingangswelt (Server-Hauptwelt),
     * siehe rules.md#spielablauf--ende.
     */
    private void teleportAllBackToEntryWorld() {
        World buildWorld = plugin.getServer().getWorld(mainConfig.getWorldName());
        List<World> worlds = plugin.getServer().getWorlds();
        if (buildWorld == null || worlds.isEmpty()) {
            return;
        }
        Location entrySpawn = worlds.get(0).getSpawnLocation();
        for (Player player : new ArrayList<>(buildWorld.getPlayers())) {
            player.teleport(entrySpawn);
        }
    }

    private boolean handleSetGroups(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eBenutzung: /bc setgroups <n>");
            return true;
        }
        int count = parseIntOrMessage(sender, args[1]);
        if (count <= 0) {
            return true;
        }
        plugin.getConfig().set("groups.count", count);
        plugin.saveConfig();
        mainConfig.reload();
        groupManager.rebuildGroups();
        sender.sendMessage("§aAnzahl der Gruppen auf " + count + " gesetzt. Bauplätze mit /bc start neu generieren.");
        return true;
    }

    private boolean handleSetGroupSize(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eBenutzung: /bc setgroupsize <n>");
            return true;
        }
        int size = parseIntOrMessage(sender, args[1]);
        if (size <= 0) {
            return true;
        }
        plugin.getConfig().set("groups.max-size", size);
        plugin.saveConfig();
        mainConfig.reload();
        groupManager.rebuildGroups();
        sender.sendMessage("§aMaximale Gruppengröße auf " + size + " gesetzt.");
        return true;
    }

    private boolean handleSetPlotSize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eBenutzung: /bc setplotsize <x> <z>");
            return true;
        }
        int x = parseIntOrMessage(sender, args[1]);
        int z = parseIntOrMessage(sender, args[2]);
        if (x <= 0 || z <= 0) {
            return true;
        }
        plugin.getConfig().set("plots.size-x", x);
        plugin.getConfig().set("plots.size-z", z);
        plugin.saveConfig();
        mainConfig.reload();
        sender.sendMessage("§aBauplatzgröße auf " + x + "x" + z + " gesetzt. Bauplätze mit /bc start neu generieren.");
        return true;
    }

    private boolean handleSetDistance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eBenutzung: /bc setdistance <min> <max>");
            return true;
        }
        int min = parseIntOrMessage(sender, args[1]);
        int max = parseIntOrMessage(sender, args[2]);
        if (min <= 0 || max <= 0 || min > max) {
            sender.sendMessage("§cUngültiger Bereich.");
            return true;
        }
        plugin.getConfig().set("plots.min-distance", min);
        plugin.getConfig().set("plots.max-distance", max);
        plugin.saveConfig();
        mainConfig.reload();
        sender.sendMessage("§aAbstand auf " + min + "-" + max + " Blöcke gesetzt.");
        return true;
    }

    private boolean handleSetScore(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eBenutzung: /bc setscore <block> <punkte>");
            return true;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            sender.sendMessage("§cUnbekanntes Material: " + args[1]);
            return true;
        }
        int points = parseIntOrMessage(sender, args[2]);
        if (points < 0) {
            return true;
        }

        scoreConfig.getAllScores().put(material, points);
        sender.sendMessage("§a" + material.name() + " ist jetzt " + points + " Punkte wert (nur zur Laufzeit).");
        sender.sendMessage("§eHinweis: Für eine dauerhafte Änderung zusätzlich in scoreboard-config.yml eintragen.");
        return true;
    }

    private boolean handleAssign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eBenutzung: /bc assign <spieler> <gruppe>");
            return true;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cSpieler nicht online gefunden: " + args[1]);
            return true;
        }
        int groupId = parseIntOrMessage(sender, args[2]);
        if (groupId < 0) {
            return true;
        }

        GroupManager.JoinResult result = groupManager.assign(target.getUniqueId(), groupId);
        switch (result) {
            case SUCCESS -> {
                sender.sendMessage("§a" + target.getName() + " wurde Gruppe " + groupId + " zugewiesen.");
                scoreboardManager.refreshAll();
            }
            case GROUP_FULL -> sender.sendMessage("§cDiese Gruppe ist bereits voll.");
            case NO_SUCH_GROUP -> sender.sendMessage("§cDiese Gruppe existiert nicht.");
            default -> sender.sendMessage("§cZuweisung fehlgeschlagen: " + result);
        }
        return true;
    }

    /** Oeffentlich, damit AutomodeManager denselben Start-Ablauf ausloesen kann, sobald beide Teams voll sind. */
    public boolean handleStart(CommandSender sender) {
        sender.sendMessage("§eErzeuge frische Bauplatz-Welt mit zufälligem Seed, das kann einen Moment dauern...");
        if (worldManager.regenerate().isEmpty()) {
            sender.sendMessage("§cAbgebrochen: plots.world ist in config.yml auf die Server-Hauptwelt gesetzt. "
                    + "Bitte dort eine andere, dedizierte Welt eintragen und /bc reload ausführen.");
            return true;
        }

        scoreManager.clear();
        plotManager.generatePlots(groupManager.getGroups().values());
        gameStateManager.start();
        scoreboardManager.refreshAll();
        teleportAllToBuildWorld();
        sender.sendMessage("§aBuildcontest gestartet. Neue Welt erzeugt, Bauplätze generiert und planiert.");
        return true;
    }

    private boolean handleEnd(CommandSender sender) {
        gameStateManager.end();
        teleportAllBackToEntryWorld();
        sender.sendMessage("§aBuildcontest beendet. Baufortschritt ist jetzt eingefroren.");

        List<Group> ranking = groupManager.getGroups().values().stream()
                .sorted(Comparator.comparingInt((Group g) -> scoreManager.getGroupScore(g.getId())).reversed())
                .toList();
        ranking.forEach(group -> plugin.getServer().broadcastMessage(
                group.getColor() + group.getName() + ": §e" + scoreManager.getGroupScore(group.getId()) + " Punkte"));

        // Siegergruppe zusaetzlich feiern (siehe rules.md#scoreboard-anzeige),
        // nicht nur nuechtern in der Rangliste auffuehren.
        if (!ranking.isEmpty()) {
            Group winner = ranking.get(0);
            plugin.getServer().broadcastMessage("§6§l*** " + winner.getColor() + winner.getName()
                    + "§6§l hat den Buildcontest gewonnen! ***");
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }

        if (mainConfig.isAutoDeleteWorldOnEnd()) {
            handleDeleteWorld(sender);
        }
        return true;
    }

    private boolean handleAutomode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eBenutzung: /bc automode <2x2|2x4>");
            return true;
        }
        Optional<AutomodeManager.Preset> preset = AutomodeManager.Preset.parse(args[1]);
        if (preset.isEmpty()) {
            sender.sendMessage("§cUnbekannter Modus: " + args[1] + " (erlaubt: 2x2, 2x4)");
            return true;
        }
        automodeManager.activate(preset.get(), sender);
        return true;
    }

    /**
     * Loescht die Bauplatz-Welt (siehe WorldManager#delete,
     * rules.md#spielablauf--ende) - entweder manuell per /bc deleteworld,
     * oder automatisch am Ende von /bc end, siehe
     * config.yml#game.auto-delete-world-on-end.
     */
    private boolean handleDeleteWorld(CommandSender sender) {
        if (!worldManager.delete()) {
            sender.sendMessage("§cAbgebrochen: plots.world ist in config.yml auf die Server-Hauptwelt gesetzt.");
            return true;
        }
        plotManager.clear(groupManager);
        sender.sendMessage("§aBauplatz-Welt gelöscht.");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        mainConfig.reload();
        scoreConfig.reload();
        plugin.scheduleAutosave();
        sender.sendMessage("§aKonfiguration neu geladen.");
        return true;
    }

    private int parseIntOrMessage(CommandSender sender, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cUngültige Zahl: " + value);
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            options.removeIf(option -> !option.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return options;
        }
        return List.of();
    }
}
