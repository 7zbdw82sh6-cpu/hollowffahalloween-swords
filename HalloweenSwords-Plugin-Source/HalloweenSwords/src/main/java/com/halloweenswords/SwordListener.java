package com.halloweenswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class SwordListener implements Listener {
    private final HalloweenSwords plugin;
    private final SwordItems items;
    private final Cooldowns cooldowns;
    private final Abilities abilities;

    public SwordListener(HalloweenSwords plugin, SwordItems items, Cooldowns cooldowns, Abilities abilities) {
        this.plugin = plugin;
        this.items = items;
        this.cooldowns = cooldowns;
        this.abilities = abilities;
    }

    // ------------------------------------------------------------ casting

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        SwordType type = items.identify(p.getInventory().getItemInMainHand());
        if (type == null) return;
        if (!p.hasPermission("halloweenswords.use")) return;

        // Don't hijack chests, doors, buttons etc. unless sneaking
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) {
            return;
        }
        e.setCancelled(true);

        Ability ability = p.isSneaking() ? type.secondary() : type.primary();
        tryCast(p, ability);
    }

    private void tryCast(Player p, Ability ability) {
        long remaining = cooldowns.remainingMs(p.getUniqueId(), ability);
        if (remaining > 0) {
            double secs = Math.ceil(remaining / 100.0) / 10.0;
            p.sendActionBar(Component.text(ability.displayName() + " is on cooldown: " + secs + "s", NamedTextColor.RED));
            return;
        }
        if (!abilities.cast(p, ability)) return;

        long cdMs = plugin.cooldownSeconds(ability) * 1000L;
        cooldowns.set(p.getUniqueId(), ability, cdMs);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && cooldowns.remainingMs(p.getUniqueId(), ability) <= 100L) {
                p.sendActionBar(Component.text(ability.displayName() + " is ready!", NamedTextColor.GREEN));
            }
        }, plugin.cooldownSeconds(ability) * 20L);
    }

    // ------------------------------------------------------------ ability hooks

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        abilities.onProjectileHit(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player p && abilities.hasNoFall(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getFinalDamage() <= 0) return;
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        abilities.onDamage(damager, e.getEntity());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        abilities.onEntityDeath(e);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        abilities.onPlayerDeath(e);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        abilities.onTarget(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        abilities.onMove(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abilities.onQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        abilities.onBlockBreak(e);
    }
}
