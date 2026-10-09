package io.github.cndrbrbr.buildcontest.command;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Implementiert die in rules.md#admin-befehle-übersicht beschriebenen Unterbefehle von /bc. */
public final class BuildContestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "setgroups", "setgroupsize", "setplotsize",
            "setdistance", "setscore", "assign", "start", "end", "reload");

    private final BuildContestPlugin plugin;
    private final MainConfig mainConfig;
    private final ScoreConfig scoreConfig;
    private final GroupManager groupManager;
    private final PlotManager plotManager;
    private final GameStateManager gameStateManager;
    private final ScoreboardManager scoreboardManager;
    private final ScoreManager scoreManager;

    public BuildContestCommand(BuildContestPlugin plugin, MainConfig mainConfig, ScoreConfig scoreConfig,
                                GroupManager groupManager, PlotManager plotManager,
                                GameStateManager gameStateManager, ScoreboardManager scoreboardManager,
                                ScoreManager scoreManager) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.scoreConfig = scoreConfig;
        this.groupManager = groupManager;
        this.plotManager = plotManager;
        this.gameStateManager = gameStateManager;
        this.scoreboardManager = scoreboardManager;
        this.scoreManager = scoreManager;
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
            case "setgroups" -> requireAdmin(sender) && handleSetGroups(sender, args);
            case "setgroupsize" -> requireAdmin(sender) && handleSetGroupSize(sender, args);
            case "setplotsize" -> requireAdmin(sender) && handleSetPlotSize(sender, args);
            case "setdistance" -> requireAdmin(sender) && handleSetDistance(sender, args);
            case "setscore" -> requireAdmin(sender) && handleSetScore(sender, args);
            case "assign" -> requireAdmin(sender) && handleAssign(sender, args);
            case "start" -> requireAdmin(sender) && handleStart(sender);
            case "end" -> requireAdmin(sender) && handleEnd(sender);
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

    private boolean handleStart(CommandSender sender) {
        plotManager.generatePlots(groupManager.getGroups().values());
        gameStateManager.start();
        scoreboardManager.refreshAll();
        sender.sendMessage("§aBuildcontest gestartet. Bauplätze wurden generiert und planiert.");
        return true;
    }

    private boolean handleEnd(CommandSender sender) {
        gameStateManager.end();
        sender.sendMessage("§aBuildcontest beendet. Baufortschritt ist jetzt eingefroren.");

        groupManager.getGroups().values().stream()
                .sorted(Comparator.comparingInt((Group g) -> scoreManager.getGroupScore(g.getId())).reversed())
                .forEach(group -> plugin.getServer().broadcastMessage(
                        "§6" + group.getName() + ": §e" + scoreManager.getGroupScore(group.getId()) + " Punkte"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        mainConfig.reload();
        scoreConfig.reload();
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
