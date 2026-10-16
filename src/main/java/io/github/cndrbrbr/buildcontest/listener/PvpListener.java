package io.github.cndrbrbr.buildcontest.listener;

import io.github.cndrbrbr.buildcontest.config.MainConfig;
import io.github.cndrbrbr.buildcontest.manager.GameStateManager;
import io.github.cndrbrbr.buildcontest.manager.GroupManager;
import io.github.cndrbrbr.buildcontest.model.Group;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

/**
 * PvP in der Contest-Welt waehrend eines laufenden Contests: nur wenn die
 * Spieler es vor dem Start per Abstimmung erlaubt haben (siehe VoteManager),
 * und auch dann nur gegen Spieler anderer Teams - das eigene Team kann sich
 * nie verletzen. Pfeile, Dreizacke usw. zaehlen wie direkte Angriffe.
 */
public final class PvpListener implements Listener {

    private final MainConfig mainConfig;
    private final GameStateManager gameStateManager;
    private final GroupManager groupManager;

    public PvpListener(MainConfig mainConfig, GameStateManager gameStateManager, GroupManager groupManager) {
        this.mainConfig = mainConfig;
        this.gameStateManager = gameStateManager;
        this.groupManager = groupManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!gameStateManager.isRunning() || !victim.getWorld().getName().equals(mainConfig.getWorldName())) {
            return;
        }
        if (!gameStateManager.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }
        Optional<Group> attackerGroup = groupManager.getGroupOf(attacker.getUniqueId());
        Optional<Group> victimGroup = groupManager.getGroupOf(victim.getUniqueId());
        if (attackerGroup.isPresent() && attackerGroup.equals(victimGroup)) {
            event.setCancelled(true);
        }
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
