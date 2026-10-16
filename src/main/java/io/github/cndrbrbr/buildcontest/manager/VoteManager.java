package io.github.cndrbrbr.buildcontest.manager;

import io.github.cndrbrbr.buildcontest.BuildContestPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstimmung vor jedem Contest-Start: Die Spieler entscheiden, ob es PvP
 * (nur gegen andere Teams) gibt und wie lange der Contest dauert. Laeuft
 * {@link #VOTE_SECONDS} Sekunden oder endet frueher, sobald alle Online-
 * Spieler beide Fragen beantwortet haben. Abgestimmt wird per Klick im Chat
 * bzw. {@code /bc vote pvp ja|nein} und {@code /bc vote zeit <Minuten>}.
 */
public final class VoteManager implements Listener {

    public static final int VOTE_SECONDS = 60;
    public static final int[] DURATIONS = {15, 30, 45, 60};
    public static final int DEFAULT_DURATION = 30;

    /** Ergebnis der Abstimmung. */
    public record Result(boolean pvp, int durationMinutes) {
    }

    private final BuildContestPlugin plugin;
    private final Map<UUID, Boolean> pvpVotes = new LinkedHashMap<>();
    private final Map<UUID, Integer> durationVotes = new LinkedHashMap<>();
    private Consumer<Result> onDone;
    private BukkitTask task;
    private int secondsLeft;

    public VoteManager(BuildContestPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return onDone != null;
    }

    /** Startet die Abstimmung; {@code onDone} wird mit dem Ergebnis aufgerufen. */
    public void start(Consumer<Result> onDone) {
        cancel();
        this.onDone = onDone;
        secondsLeft = VOTE_SECONDS;
        Bukkit.broadcastMessage("§6§l=== Abstimmung vor dem Start (" + VOTE_SECONDS + " Sekunden) ===");
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBallot(player);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Bricht eine laufende Abstimmung ohne Ergebnis ab (z. B. /bc end). */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        onDone = null;
        pvpVotes.clear();
        durationVotes.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isActive()) {
            sendBallot(event.getPlayer());
        }
    }

    /** Stimmzettel fuer einen (auch spaeter dazugekommenen) Spieler. */
    public void sendBallot(Player player) {
        player.sendMessage("§ePvP gegen das andere Team?");
        player.spigot().sendMessage(new ComponentBuilder("  ")
                .append(button("§a§l[Ja]", "/bc vote pvp ja", "PvP an"))
                .append("  ", ComponentBuilder.FormatRetention.NONE)
                .append(button("§c§l[Nein]", "/bc vote pvp nein", "PvP aus"))
                .create());
        player.sendMessage("§eWie lange soll der Contest dauern?");
        ComponentBuilder durations = new ComponentBuilder("  ");
        for (int minutes : DURATIONS) {
            durations.append(button("§b§l[" + minutes + " min]", "/bc vote zeit " + minutes, minutes + " Minuten"))
                    .append("  ", ComponentBuilder.FormatRetention.NONE);
        }
        player.spigot().sendMessage(durations.create());
    }

    private static TextComponent button(String label, String command, String hover) {
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(label));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
        return component;
    }

    public void votePvp(Player player, boolean pvp) {
        if (!isActive()) {
            player.sendMessage("§cGerade läuft keine Abstimmung.");
            return;
        }
        pvpVotes.put(player.getUniqueId(), pvp);
        player.sendMessage("§aDeine Stimme: PvP " + (pvp ? "§aan" : "§caus"));
        finishEarlyIfEveryoneVoted();
    }

    public void voteDuration(Player player, int minutes) {
        if (!isActive()) {
            player.sendMessage("§cGerade läuft keine Abstimmung.");
            return;
        }
        boolean allowed = false;
        for (int option : DURATIONS) {
            allowed |= option == minutes;
        }
        if (!allowed) {
            player.sendMessage("§cErlaubt sind 15, 30, 45 oder 60 Minuten.");
            return;
        }
        durationVotes.put(player.getUniqueId(), minutes);
        player.sendMessage("§aDeine Stimme: §b" + minutes + " Minuten");
        finishEarlyIfEveryoneVoted();
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft == 30 || secondsLeft == 10) {
            Bukkit.broadcastMessage("§eNoch " + secondsLeft + " Sekunden abstimmen! §7(/bc vote)");
        }
        if (secondsLeft <= 0) {
            finish();
        }
    }

    private void finishEarlyIfEveryoneVoted() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!pvpVotes.containsKey(player.getUniqueId()) || !durationVotes.containsKey(player.getUniqueId())) {
                return;
            }
        }
        finish();
    }

    private void finish() {
        Consumer<Result> callback = onDone;
        Result result = tally(pvpVotes, durationVotes);
        long yes = pvpVotes.values().stream().filter(Boolean::booleanValue).count();
        long no = pvpVotes.size() - yes;
        cancel();
        Bukkit.broadcastMessage("§6§lErgebnis: §rPvP " + (result.pvp() ? "§aan" : "§caus")
                + " §7(" + yes + " ja / " + no + " nein)§r, Dauer §b" + result.durationMinutes() + " Minuten");
        if (callback != null) {
            callback.accept(result);
        }
    }

    /**
     * Auswertung: PvP nur bei echter Mehrheit fuer Ja (Gleichstand = aus).
     * Dauer = meistgewaehlte Option, bei Gleichstand die kuerzere; ohne
     * Stimmen {@link #DEFAULT_DURATION}.
     */
    static Result tally(Map<UUID, Boolean> pvpVotes, Map<UUID, Integer> durationVotes) {
        long yes = pvpVotes.values().stream().filter(Boolean::booleanValue).count();
        long no = pvpVotes.size() - yes;

        int best = DEFAULT_DURATION;
        long bestCount = 0;
        for (int option : DURATIONS) {
            long count = durationVotes.values().stream().filter(v -> v == option).count();
            if (count > bestCount) {
                best = option;
                bestCount = count;
            }
        }
        return new Result(yes > no, best);
    }
}
