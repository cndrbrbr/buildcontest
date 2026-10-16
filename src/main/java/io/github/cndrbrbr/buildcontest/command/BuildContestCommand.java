package io.github.cndrbrbr.buildcontest.command;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.config.ScoreConfig;
import io.github.cndrbrbr.buildcontest.manager.AutomodeManager;
import io.github.cndrbrbr.buildcontest.manager.EntryTeleportManager;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.manager.InventoryVaultManager;
import io.github.cndrbrbr.buildcontest.manager.PlotManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreManager;
import io.github.cndrbrbr.buildcontest.manager.ScoreboardManager;
import io.github.cndrbrbr.buildcontest.manager.WorldManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import io.github.cndrbrbr.buildcontest.persistence.DataStore;
import org.bukkit.GameMode;
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

/** Implementiert die in rules.md#admin-befehle-übersicht beschriebenen Unterbefehle von /bc. */
public final class BuildContestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "join", "setgroups", "setgroupsize", "setplotsize",
            "setdistance", "setscore", "assign", "start", "pause", "resume", "end", "deleteworld", "automode",
            "placestartstones", "reload");

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
    private final EntryTeleportManager entryTeleportManager;
    private final DataStore dataStore;
    private final InventoryVaultManager inventoryVaultManager;

    public BuildContestCommand(BuildContestPlugin plugin, MainConfig mainConfig, ScoreConfig scoreConfig,
                                GroupManager groupManager, PlotManager plotManager,
                                GameStateManager gameStateManager, ScoreboardManager scoreboardManager,
                                ScoreManager scoreManager, WorldManager worldManager,
                                AutomodeManager automodeManager, EntryTeleportManager entryTeleportManager,
                                DataStore dataStore, InventoryVaultManager inventoryVaultManager) {
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
        this.entryTeleportManager = entryTeleportManager;
        this.dataStore = dataStore;
        this.inventoryVaultManager = inventoryVaultManager;
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
            case "setgroups" -> requireAdmin(sender) && requireNotRunning(sender) && handleSetGroups(sender, args);
            case "setgroupsize" ->
                    requireAdmin(sender) && requireNotRunning(sender) && handleSetGroupSize(sender, args);
            case "setplotsize" ->
                    requireAdmin(sender) && requireNotRunning(sender) && handleSetPlotSize(sender, args);
            case "setdistance" ->
                    requireAdmin(sender) && requireNotRunning(sender) && handleSetDistance(sender, args);
            case "setscore" -> requireAdmin(sender) && requireNotRunning(sender) && handleSetScore(sender, args);
            case "assign" -> requireAdmin(sender) && handleAssign(sender, args);
            case "start" -> requireAdmin(sender)
                    && handleStart(sender, args.length > 1 && "confirm".equalsIgnoreCase(args[1]));
            case "pause" -> requireAdmin(sender) && handlePause(sender);
            case "resume" -> requireAdmin(sender) && handleResume(sender);
            case "end" -> requireAdmin(sender) && handleEnd(sender);
            case "deleteworld" -> requireAdmin(sender) && handleDeleteWorld(sender);
            case "automode" -> requireAdmin(sender) && requireNotRunning(sender) && handleAutomode(sender, args);
            case "placestartstones" -> requireAdmin(sender) && handlePlaceStartStones(sender);
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

    /**
     * Sperrt spielentscheidende Einstellungen (Gruppenanzahl/-größe,
     * Bauplatzgröße/-abstand, Punktwerte, Automatikmodus) waehrend ein
     * Contest laeuft (siehe rules.md#punktesystem: "Änderungen an
     * Punktwerten während RUNNING/PAUSED sind gesperrt" sowie TODO.md P0-4).
     * {@code /bc assign} ist bewusst ausgenommen - Admin-Teamwechsel
     * waehrend RUNNING sind explizit erlaubt (rules.md#gruppen-und-handel).
     */
    private boolean requireNotRunning(CommandSender sender) {
        if (!gameStateManager.isRunning()) {
            return true;
        }
        sender.sendMessage("§cDiese Einstellung kann nicht geändert werden, während ein Contest läuft.");
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
                // Laeuft der Contest bereits (spaeter Beitritt), bekommt der
                // Spieler jetzt seinen einmaligen Startpunkt-Teleport (siehe
                // EntryTeleportManager).
                entryTeleportManager.teleportIfFirstEntry(player);
            }
            case ALREADY_IN_GROUP -> sender.sendMessage("§cDu bist bereits einer Gruppe zugeordnet.");
            case GROUP_FULL -> sender.sendMessage("§cDiese Gruppe ist bereits voll.");
            case UNBALANCED -> sender.sendMessage("§cDiese Gruppe hat schon mehr Spieler - bitte eine kleinere wählen.");
            case NO_SUCH_GROUP -> sender.sendMessage("§cDiese Gruppe existiert nicht.");
            case WRONG_MODE ->
                    sender.sendMessage("§cDie freie Gruppenwahl ist deaktiviert (Configfile-Zuweisung aktiv).");
        }
        return true;
    }

    /**
     * Teleportiert bei Contest-Ende alle Spieler, die sich noch in der
     * Bauplatz-Welt befinden, zurueck in die Eingangswelt (Server-Hauptwelt),
     * siehe rules.md#spielablauf--ende. Dabei wird ihr vor Contest-Beginn
     * gesichertes Inventar wiederhergestellt (siehe InventoryVaultManager)
     * und der Adventure-Modus der Eingangswelt gesetzt (siehe
     * rules.md#welten-und-fortbewegung).
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
            player.setGameMode(GameMode.ADVENTURE);
            inventoryVaultManager.restoreIfPresent(player);
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
                entryTeleportManager.teleportIfFirstEntry(target);
            }
            case GROUP_FULL -> sender.sendMessage("§cDiese Gruppe ist bereits voll.");
            case NO_SUCH_GROUP -> sender.sendMessage("§cDiese Gruppe existiert nicht.");
            default -> sender.sendMessage("§cZuweisung fehlgeschlagen: " + result);
        }
        return true;
    }

    /**
     * Oeffentlich, damit AutomodeManager denselben Start-Ablauf ausloesen
     * kann, sobald beide Teams voll sind (dort immer mit confirmed=true, da
     * die automatische Aktivierung bewusst einen neuen Contest will).
     *
     * @param confirmed ob der Admin das unwiderrufliche Ersetzen einer
     *                  evtl. noch vorhandenen alten Bauplatz-Welt bereits
     *                  bestaetigt hat (siehe rules.md#spielablauf--ende:
     *                  "Vor dem Ersetzen der bisherigen Welt muss der Admin
     *                  ausdruecklich bestaetigen...").
     */
    public boolean handleStart(CommandSender sender, boolean confirmed) {
        return handleStart(sender, confirmed, -1);
    }

    /**
     * @param forcedDurationMinutes bei &gt; 0 ueberschreibt dies die
     *                              konfigurierte game.duration-minutes fuer
     *                              diesen einen Contest (siehe
     *                              GameStateManager#start(int)) - genutzt
     *                              von AutomodeManager fuer per Start-Stein
     *                              ausgeloeste Contests (immer 30 Minuten,
     *                              siehe rules.md#schnellstart-presets-und-start-steine).
     */
    public boolean handleStart(CommandSender sender, boolean confirmed, int forcedDurationMinutes) {
        if (gameStateManager.isRunning()) {
            sender.sendMessage("§cEin Contest läuft bereits. Erst /bc end ausführen, bevor ein neuer gestartet wird.");
            return true;
        }
        if (worldManager.worldExists() && !confirmed) {
            sender.sendMessage("§eEs existiert noch eine Bauplatz-Welt eines vorherigen Contests (evtl. noch "
                    + "sichtbare alte Bauten). /bc start erzeugt eine KOMPLETT NEUE Welt mit zufälligem Seed - die "
                    + "bisherige geht dabei unwiderruflich verloren.");
            sender.sendMessage("§eZum Bestätigen: §c/bc start confirm");
            return true;
        }

        sender.sendMessage("§eErzeuge frische Bauplatz-Welt mit zufälligem Seed, das kann einen Moment dauern...");
        if (worldManager.regenerate().isEmpty()) {
            sender.sendMessage("§cAbgebrochen: plots.world ist in config.yml auf die Server-Hauptwelt gesetzt. "
                    + "Bitte dort eine andere, dedizierte Welt eintragen und /bc reload ausführen.");
            return true;
        }

        scoreManager.clear();
        plotManager.generatePlots(groupManager.getGroups().values());
        // Wer beim Start noch keine Gruppe hat, wird auf die Teams verteilt,
        // damit sie gleich gross sind (nur bei freier Wahl; volle Teams
        // bleiben voll, Uebrige bleiben dann Zuschauer).
        if (mainConfig.getAssignmentMode() == MainConfig.AssignmentMode.FREE) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                groupManager.assignBalanced(online.getUniqueId()).ifPresent(group -> online.sendMessage(
                        "§aDu wurdest Team " + group.getColor() + group.getName() + "§a zugeteilt."));
            }
        }
        gameStateManager.start(forcedDurationMinutes);
        scoreboardManager.refreshAll();
        entryTeleportManager.teleportAllNewEntrants();
        sender.sendMessage("§aBuildcontest gestartet. Neue Welt erzeugt, Bauplätze generiert und planiert.");
        return true;
    }

    /**
     * Haelt Timer und wettbewerbsrelevante Spieleraktionen an (siehe
     * rules.md#ablauf-unterbrechung-und-spielende, BlockListener#isBlockedByPause).
     */
    private boolean handlePause(CommandSender sender) {
        if (!gameStateManager.isRunning()) {
            sender.sendMessage("§cEs läuft aktuell kein Contest.");
            return true;
        }
        if (gameStateManager.isPaused()) {
            sender.sendMessage("§cDer Contest ist bereits pausiert.");
            return true;
        }
        gameStateManager.pause();
        plugin.getServer().broadcastMessage("§eDer Buildcontest wurde pausiert.");
        return true;
    }

    /** Setzt denselben Contest mit allen Punkten und derselben Welt fort (siehe GameStateManager#resume). */
    private boolean handleResume(CommandSender sender) {
        if (!gameStateManager.isRunning()) {
            sender.sendMessage("§cEs läuft aktuell kein Contest.");
            return true;
        }
        if (!gameStateManager.isPaused()) {
            sender.sendMessage("§cDer Contest ist nicht pausiert.");
            return true;
        }
        gameStateManager.resume();
        plugin.getServer().broadcastMessage("§aDer Buildcontest wurde fortgesetzt.");
        return true;
    }

    /**
     * Oeffentlich, damit GameStateManager den vollen Ende-Ablauf (Endstand
     * archivieren/bekanntgeben, Rueckteleport, Statusanzeige) auch bei einem
     * natuerlichen Timer-Ablauf ausloesen kann, nicht nur bei explizitem
     * {@code /bc end} (siehe GameStateManager#setOnTimerEnd).
     */
    public boolean handleEnd(CommandSender sender) {
        gameStateManager.end();
        teleportAllBackToEntryWorld();
        sender.sendMessage("§aBuildcontest beendet. Baufortschritt ist jetzt eingefroren.");

        List<Group> ranking = groupManager.getGroups().values().stream()
                .sorted(Comparator.comparingInt((Group g) -> scoreManager.getGroupScore(g.getId())).reversed())
                .toList();
        ranking.forEach(group -> plugin.getServer().broadcastMessage(
                group.getColor() + group.getName() + ": §e" + scoreManager.getGroupScore(group.getId()) + " Punkte"));

        // Endstand dauerhaft archivieren, unabhaengig von einem spaeteren
        // /bc start (setzt scoreManager zurueck) oder /bc deleteworld (siehe
        // rules.md#persistenz-und-absturzsicherheit: "Nur Ergebnisse und
        // Statistiken werden archiviert, kein Weltbackup").
        dataStore.appendResult(ranking.stream()
                .map(group -> new DataStore.ResultEntry(group.getId(), group.getName(), scoreManager.getGroupScore(group.getId())))
                .toList());

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
        automodeManager.cancelWaitingPhase();
        automodeManager.showIdleStatus();
        return true;
    }

    private boolean handleAutomode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eBenutzung: /bc automode <2x1|2x2|2x4|2xn>");
            return true;
        }
        Optional<AutomodeManager.Preset> preset = AutomodeManager.Preset.parse(args[1]);
        if (preset.isEmpty()) {
            sender.sendMessage("§cUnbekannter Modus: " + args[1] + " (erlaubt: 2x1, 2x2, 2x4, 2xn)");
            return true;
        }
        automodeManager.activate(preset.get(), sender);
        return true;
    }

    /**
     * Stellt fuer jedes Automode-Preset einen beschrifteten Start-Stein an
     * der aktuellen Position des Admins auf (siehe AutomodeManager#placeStartStones,
     * rules.md#schnellstart-presets-und-start-steine) - damit ein Contest
     * auch ohne anwesenden Admin beginnen kann.
     */
    private boolean handlePlaceStartStones(CommandSender sender) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("§cNur als Spieler ausführbar (braucht eine Position in der Eingangswelt).");
            return true;
        }
        automodeManager.placeStartStones(admin);
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
