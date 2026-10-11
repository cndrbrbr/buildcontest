package io.github.cndrbrbr.buildcontest.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sichert das mitgebrachte Inventar eines Spielers beim erstmaligen Eintritt
 * in einen Contest und leert es (siehe rules.md#welten-und-fortbewegung:
 * "jeder beginnt mit leerem Inventar"), damit niemand einen Vorteil durch
 * vorab gehortete Ressourcen hat. Beim Verlassen des Contests (Rueckkehr in
 * die Eingangswelt) wird das gesicherte Inventar wiederhergestellt - im
 * Contest gesammelte/getragene Gegenstaende gehen dabei bewusst verloren
 * (ersetzen statt zusammenfuehren, kein Ueberlauf-Risiko).
 *
 * Bekannte Einschraenkung: wie der Automode-Wartezustand (siehe
 * AutomodeManager) wird dieser Tresor NICHT ueber einen Serverneustart
 * hinweg persistiert - ein Neustart waehrend ein Spieler "eingecheckt" ist,
 * verliert das gesicherte alte Inventar.
 */
public final class InventoryVaultManager {

    private record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offHand) {
    }

    private final Map<UUID, Snapshot> saved = new HashMap<>();

    /** Sichert das aktuelle Inventar und leert es danach vollstaendig (inkl. Ruestung/Nebenhand). */
    public void saveAndClear(Player player) {
        PlayerInventory inventory = player.getInventory();
        saved.put(player.getUniqueId(), new Snapshot(
                inventory.getContents().clone(), inventory.getArmorContents().clone(),
                inventory.getItemInOffHand().clone()));
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
    }

    /** Stellt ein zuvor gesichertes Inventar wieder her und ERSETZT dabei das aktuelle. Kein Effekt, falls nichts gesichert war. */
    public void restoreIfPresent(Player player) {
        Snapshot snapshot = saved.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(snapshot.contents());
        inventory.setArmorContents(snapshot.armor());
        inventory.setItemInOffHand(snapshot.offHand());
    }
}
