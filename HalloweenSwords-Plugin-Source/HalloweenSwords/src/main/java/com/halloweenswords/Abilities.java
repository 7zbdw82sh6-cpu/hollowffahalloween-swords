package com.halloweenswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * All ability logic + the temporary state they need.
 * Everything here runs on the main server thread.
 */
public final class Abilities {

    private record Link(UUID owner, long until) {}

    private final HalloweenSwords plugin;
    private final Random random = new Random();
    private final NamespacedKey projKey;

    // Active-effect state
    private final Map<UUID, Long> ghostwalk = new HashMap<>();
    private final Map<UUID, Long> flightNoFall = new HashMap<>();
    private final Map<UUID, Long> rooted = new HashMap<>();
    private final Map<UUID, Mob> frozenMobs = new HashMap<>();
    private final Map<UUID, Link> bloodHunt = new HashMap<>();     // key = marked target
    private final Map<UUID, Long> bloodMoon = new HashMap<>();     // key = caster
    private final Map<UUID, Link> hex = new HashMap<>();           // key = cursed target
    private final Map<UUID, int[]> possessed = new HashMap<>();    // key = victim, value = hotbar permutation
    private final Map<Block, BlockData> webs = new HashMap<>();    // temp cobwebs -> original block data
    private final Map<UUID, Entity> temp = new HashMap<>();        // summoned/fake entities (always cleaned up)
    private final Map<UUID, UUID> undeadVictim = new HashMap<>();  // summoned undead -> who they hunt

    /** True while we are dealing damage ourselves, so our own damage doesn't trigger sword hooks. */
    private boolean internalDamage = false;

    public Abilities(HalloweenSwords plugin) {
        this.plugin = plugin;
        this.projKey = new NamespacedKey(plugin, "projectile_type");
    }

    // ================================================================ helpers

    private long now() { return System.currentTimeMillis(); }

    private double d(String path, double def) {
        return plugin.getConfig().getDouble("abilities." + path, def);
    }

    private int ticks(String path, double defSeconds) {
        return (int) Math.round(plugin.getConfig().getDouble("abilities." + path, defSeconds) * 20.0);
    }

    private void tell(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    private void give(LivingEntity e, PotionEffectType type, int ticks, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    private void track(Entity e) { temp.put(e.getUniqueId(), e); }

    private void untrack(Entity e) {
        temp.remove(e.getUniqueId());
        e.remove();
    }

    private boolean isTargetable(Player caster, Entity e) {
        return e instanceof LivingEntity
                && !e.equals(caster)
                && !(e instanceof ArmorStand)
                && !temp.containsKey(e.getUniqueId())
                && !(e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR);
    }

    /** Entity the caster is looking at (line-of-sight checked), or null. */
    private LivingEntity findTarget(Player p) {
        double range = plugin.getConfig().getDouble("targeting.range", 25.0);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTraceEntities(eye, dir, range, 0.6, e -> isTargetable(p, e));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return null;

        double dist = eye.toVector().distance(hit.getHitPosition());
        RayTraceResult wall = p.getWorld().rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        return wall == null ? target : null;
    }

    private LivingEntity requireTarget(Player p) {
        LivingEntity t = findTarget(p);
        if (t == null) tell(p, "No target in sight", NamedTextColor.RED);
        return t;
    }

    private void hurt(LivingEntity victim, double amount, Player source) {
        internalDamage = true;
        try {
            if (source != null && source.isOnline()) victim.damage(amount, source);
            else victim.damage(amount);
        } finally {
            internalDamage = false;
        }
    }

    private void heal(Player p, double amount) {
        if (p.isDead()) return;
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20.0 : attr.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }

    // ================================================================ dispatch

    /** @return true if the ability actually fired (so the cooldown should start) */
    public boolean cast(Player p, Ability ability) {
        return switch (ability) {
            case PUMPKIN_BOMB -> pumpkinBomb(p);
            case TRICK_OR_TREAT -> trickOrTreat(p);
            case HAUNT -> haunt(p);
            case GHOSTWALK -> ghostwalk(p);
            case GRAVE_GRIP -> graveGrip(p);
            case RISE_FROM_BELOW -> riseFromBelow(p);
            case BLOOD_HUNT -> bloodHunt(p);
            case BLOOD_MOON -> bloodMoon(p);
            case BAT_SWARM -> batSwarm(p);
            case NIGHT_FLIGHT -> nightFlight(p);
            case HEX -> hex(p);
            case POSSESSION -> possession(p);
            case WEB_SHOT -> webShot(p);
            case SPIDERS_FEAST -> spidersFeast(p);
        };
    }

    // ================================================================ JACK-O-BLADE

    private boolean pumpkinBomb(Player p) {
        Snowball ball = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.7));
        ball.setItem(new ItemStack(Material.JACK_O_LANTERN));
        ball.setVisualFire(true);
        ball.getPersistentDataContainer().set(projKey, PersistentDataType.STRING, "pumpkin_bomb");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);

        new BukkitRunnable() {
            int life = 0;
            @Override public void run() {
                if (!ball.isValid() || life++ > 200) { cancel(); return; }
                Location l = ball.getLocation();
                l.getWorld().spawnParticle(Particle.FLAME, l, 5, 0.1, 0.1, 0.1, 0.01);
                l.getWorld().spawnParticle(Particle.SMOKE, l, 2, 0.05, 0.05, 0.05, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void explodePumpkin(Location loc, Player owner) {
        World w = loc.getWorld();
        double radius = d("pumpkin-bomb.radius", 4.0);
        double damage = d("pumpkin-bomb.damage", 8.0);
        int fireTicks = ticks("pumpkin-bomb.fire-seconds", 5);

        w.spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.0);
        w.spawnParticle(Particle.FLAME, loc, 90, 1.2, 1.0, 1.2, 0.08);
        w.spawnParticle(Particle.LAVA, loc, 25, 1.0, 0.6, 1.0, 0.0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

        for (Entity en : w.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le)) continue;
            if (le.equals(owner) || en instanceof ArmorStand || temp.containsKey(en.getUniqueId())) continue;
            double dist = le.getLocation().distance(loc);
            if (dist > radius) continue;

            double scale = 1.0 - Math.min(dist / radius, 1.0) * 0.5; // 100% at center, 50% at edge
            hurt(le, damage * scale, owner);
            le.setFireTicks(fireTicks);

            Vector push = le.getLocation().toVector().subtract(loc.toVector());
            if (push.lengthSquared() > 0.0001) {
                push.normalize().multiply(0.6).setY(0.35);
                le.setVelocity(le.getVelocity().add(push));
            }
        }
    }

    private boolean trickOrTreat(Player p) {
        boolean bad = random.nextDouble() < d("trick-or-treat.bad-chance", 0.12);
        int pick = random.nextInt(4);
        String label;
        if (bad) {
            label = switch (pick) {
                case 0 -> { give(p, PotionEffectType.SLOWNESS, 160, 1); yield "Slowness II"; }
                case 1 -> { give(p, PotionEffectType.WEAKNESS, 200, 0); yield "Weakness"; }
                case 2 -> { give(p, PotionEffectType.NAUSEA, 140, 0); yield "Nausea"; }
                default -> { give(p, PotionEffectType.BLINDNESS, 100, 0); yield "Blindness"; }
            };
        } else {
            label = switch (pick) {
                case 0 -> { give(p, PotionEffectType.SPEED, 400, 1); yield "Speed II"; }
                case 1 -> { give(p, PotionEffectType.STRENGTH, 240, 1); yield "Strength II"; }
                case 2 -> { give(p, PotionEffectType.RESISTANCE, 240, 1); yield "Resistance II"; }
                default -> { give(p, PotionEffectType.REGENERATION, 200, 2); yield "Regeneration III"; }
            };
        }

        p.showTitle(Title.title(
                Component.text(bad ? "TRICK!" : "TREAT!", bad ? NamedTextColor.DARK_RED : NamedTextColor.GOLD),
                Component.text(label, bad ? NamedTextColor.RED : NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1100), Duration.ofMillis(350))));

        Location l = p.getLocation().add(0, 1, 0);
        if (bad) {
            p.getWorld().spawnParticle(Particle.SMOKE, l, 30, 0.4, 0.6, 0.4, 0.02);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.6f, 0.5f);
        } else {
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 30, 0.5, 0.7, 0.5, 0.0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        return true;
    }

    // ================================================================ PHANTOM FANG

    private boolean haunt(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("haunt.duration-seconds", 5);
        give(t, PotionEffectType.DARKNESS, dur, 0);
        t.getWorld().spawnParticle(Particle.SOUL, t.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);

        if (!(t instanceof Player victim)) return true;
        victim.sendActionBar(Component.text("Something is watching you...", NamedTextColor.DARK_PURPLE));
        spawnGhosts(victim, dur);
        return true;
    }

    /** Vexes that only the victim can see (per-player entity visibility). */
    private void spawnGhosts(Player victim, int duration) {
        int count = 6;
        List<Vex> ghosts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vex v = victim.getWorld().spawn(victim.getLocation().add(0, 1, 0), Vex.class, vx -> {
                vx.setVisibleByDefault(false);
                vx.setAI(false);
                vx.setGravity(false);
                vx.setInvulnerable(true);
                vx.setSilent(true);
                vx.setPersistent(false);
                vx.setCollidable(false);
                vx.setCharging(true);
            });
            victim.showEntity(plugin, v);
            track(v);
            ghosts.add(v);
        }

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= duration || !victim.isOnline() || victim.isDead()) {
                    for (Vex v : ghosts) untrack(v);
                    cancel();
                    return;
                }
                double radius = 4.5 - 2.0 * (t / (double) duration);
                Location base = victim.getLocation();
                for (int i = 0; i < ghosts.size(); i++) {
                    double a = t * 0.12 + i * (Math.PI * 2 / ghosts.size());
                    double y = 1.0 + Math.sin(t * 0.2 + i) * 0.6;
                    Location loc = base.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                    loc.setDirection(base.clone().add(0, 1, 0).toVector().subtract(loc.toVector()));
                    ghosts.get(i).teleport(loc);
                }
                if (t % 20 == 0) {
                    victim.playSound(victim.getLocation(), Sound.ENTITY_VEX_AMBIENT, 0.8f, 0.6f);
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private boolean ghostwalk(Player p) {
        int dur = ticks("ghostwalk.duration-seconds", 6);
        long until = now() + dur * 50L;
        ghostwalk.put(p.getUniqueId(), until);

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1, false, false, true));

        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.03);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.7f);
        tell(p, "You fade into the mist...", NamedTextColor.GRAY);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long cur = ghostwalk.get(p.getUniqueId());
            if (cur != null && cur == until) endGhostwalk(p, false);
        }, dur);
        return true;
    }

    private void endGhostwalk(Player p, boolean early) {
        if (ghostwalk.remove(p.getUniqueId()) == null) return;
        if (early) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
            tell(p, "Ghostwalk broken!", NamedTextColor.RED);
        } else {
            tell(p, "Ghostwalk ended", NamedTextColor.GRAY);
        }
    }

    public boolean hasNoFall(Player p) {
        long n = now();
        Long g = ghostwalk.get(p.getUniqueId());
        Long f = flightNoFall.get(p.getUniqueId());
        return (g != null && g > n) || (f != null && f > n);
    }

    // ================================================================ GRAVEKEEPER

    private boolean graveGrip(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("grave-grip.duration-seconds", 3);
        UUID id = t.getUniqueId();
        rooted.put(id, now() + dur * 50L);
        if (t instanceof Mob m) {
            frozenMobs.put(id, m);
            m.setAI(false);
        }
        t.getWorld().playSound(t.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.6f);
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_SKELETON_AMBIENT, 1f, 0.5f);
        if (t instanceof Player victim) tell(victim, "Skeletal hands drag you down!", NamedTextColor.DARK_RED);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= dur || t.isDead() || !t.isValid()) {
                    releaseRoot(t);
                    cancel();
                    return;
                }
                Location base = t.getLocation();
                World w = base.getWorld();
                double rise = (tick % 12) * 0.1;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + tick * 0.15;
                    Location l = base.clone().add(Math.cos(a) * 0.8, 0.1 + rise, Math.sin(a) * 0.8);
                    w.spawnParticle(Particle.BLOCK, l, 4, 0.08, 0.25, 0.08, 0.0, Material.COARSE_DIRT.createBlockData());
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(0, 0.4, 0), 1, 0.05, 0.1, 0.05, 0.0);
                }
                tick += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return true;
    }

    private void releaseRoot(LivingEntity t) {
        rooted.remove(t.getUniqueId());
        Mob m = frozenMobs.remove(t.getUniqueId());
        if (m != null && m.isValid()) m.setAI(true);
    }

    public void onMove(PlayerMoveEvent e) {
        if (rooted.isEmpty()) return;
        Long until = rooted.get(e.getPlayer().getUniqueId());
        if (until == null) return;
        if (until <= now()) { rooted.remove(e.getPlayer().getUniqueId()); return; }

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        boolean moved = from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY();
        if (!moved) return;

        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        if (to.getY() < from.getY()) fixed.setY(to.getY()); // still allow falling
        e.setTo(fixed);
    }

    private boolean riseFromBelow(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("rise-from-below.duration-seconds", 8);
        int count = Math.max(1, plugin.getConfig().getInt("abilities.rise-from-below.count", 5));
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON};
        World w = t.getWorld();
        List<Mob> minions = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double a = i * (Math.PI * 2 / count) + random.nextDouble() * 0.5;
            Location spot = t.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            if (!spot.getBlock().isPassable() || !spot.clone().add(0, 1, 0).getBlock().isPassable()) {
                spot = t.getLocation();
            }
            Entity ent = w.spawnEntity(spot, types[i % types.length]);
            if (!(ent instanceof Mob m)) { ent.remove(); continue; }

            m.setPersistent(false);
            m.setRemoveWhenFarAway(true);
            m.setCanPickupItems(false);
            m.setTarget(t);

            EntityEquipment eq = m.getEquipment();
            if (eq != null) {
                // Carved pumpkin = Halloween look + stops them burning in daylight
                eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                eq.setHelmetDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
                if (m instanceof AbstractSkeleton && eq.getItemInMainHand().getType().isAir()) {
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                }
            }

            track(m);
            undeadVictim.put(m.getUniqueId(), t.getUniqueId());
            minions.add(m);

            w.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.2, 0), 25, 0.3, 0.2, 0.3, 0.0, Material.DIRT.createBlockData());
            w.spawnParticle(Particle.SOUL, spot.clone().add(0, 0.5, 0), 6, 0.2, 0.3, 0.2, 0.02);
        }
        w.playSound(t.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.2f, 0.5f);
        w.playSound(t.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.5f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                boolean over = tick >= dur || t.isDead() || !t.isValid();
                if (over) {
                    for (Mob m : minions) {
                        if (m.isValid()) {
                            m.getWorld().spawnParticle(Particle.LARGE_SMOKE, m.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                        }
                        undeadVictim.remove(m.getUniqueId());
                        untrack(m);
                    }
                    cancel();
                    return;
                }
                for (Mob m : minions) {
                    if (m.isValid() && m.getTarget() != t) m.setTarget(t);
                }
                tick += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L);
        return true;
    }

    public void onTarget(EntityTargetEvent e) {
        UUID victim = undeadVictim.get(e.getEntity().getUniqueId());
        if (victim == null) return;
        if (e.getTarget() == null || !e.getTarget().getUniqueId().equals(victim)) e.setCancelled(true);
    }

    // ================================================================ BLOODMOON BLADE

    private boolean bloodHunt(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("blood-hunt.duration-seconds", 10);
        Link link = new Link(p.getUniqueId(), now() + dur * 50L);
        bloodHunt.put(t.getUniqueId(), link);
        give(t, PotionEffectType.GLOWING, dur, 0);

        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.7f);
        tell(p, "Marked " + t.getName() + " for the hunt", NamedTextColor.DARK_RED);
        if (t instanceof Player victim) tell(victim, "You are being hunted...", NamedTextColor.DARK_RED);

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.3f);
        new BukkitRunnable() {
            @Override public void run() {
                if (now() >= link.until() || !t.isValid()) {
                    bloodHunt.remove(t.getUniqueId(), link);
                    cancel();
                    return;
                }
                t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, 2.2, 0), 6, 0.3, 0.2, 0.3, 0.0, dust);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    private boolean bloodMoon(Player p) {
        int dur = ticks("blood-moon.duration-seconds", 8);
        UUID id = p.getUniqueId();
        long until = now() + dur * 50L;
        bloodMoon.put(id, until);
        give(p, PotionEffectType.STRENGTH, dur, 0);
        give(p, PotionEffectType.SPEED, dur, 0);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.6f);
        tell(p, "The Blood Moon rises!", NamedTextColor.DARK_RED);

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 0, 20), 1.2f);
        new BukkitRunnable() {
            @Override public void run() {
                Long cur = bloodMoon.get(id);
                if (!p.isOnline() || cur == null || cur != until || now() >= until) {
                    if (cur != null && cur == until) {
                        bloodMoon.remove(id);
                        if (p.isOnline()) tell(p, "The Blood Moon sets", NamedTextColor.GRAY);
                    }
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.5, 0.8, 0.5, 0.0, dust);
            }
        }.runTaskTimer(plugin, 0L, 5L);
        return true;
    }

    // ================================================================ NIGHTFANG

    private boolean batSwarm(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("bat-swarm.duration-seconds", 6);
        int count = Math.max(1, plugin.getConfig().getInt("abilities.bat-swarm.count", 8));
        int interval = Math.max(1, plugin.getConfig().getInt("abilities.bat-swarm.damage-interval-ticks", 20));
        double damage = d("bat-swarm.damage", 1.0);

        give(t, PotionEffectType.DARKNESS, dur, 0);

        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Bat b = t.getWorld().spawn(t.getLocation().add(0, 1.5, 0), Bat.class, bat -> {
                bat.setAI(false);
                bat.setAwake(true);
                bat.setInvulnerable(true);
                bat.setSilent(true);
                bat.setPersistent(false);
            });
            track(b);
            bats.add(b);
        }
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.5f, 0.6f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= dur || !t.isValid()) {
                    for (Bat b : bats) untrack(b);
                    cancel();
                    return;
                }
                Location c = t.getLocation();
                for (int i = 0; i < bats.size(); i++) {
                    double a = tick * 0.35 + i * (Math.PI * 2 / bats.size());
                    double r = 1.3 + 0.6 * Math.sin(tick * 0.1 + i);
                    double y = 1.0 + 0.8 * Math.sin(tick * 0.25 + i * 1.7);
                    Location loc = c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                    loc.setYaw((float) Math.toDegrees(a) + 90f);
                    bats.get(i).teleport(loc);
                }
                if (tick > 0 && tick % interval == 0) {
                    hurt(t, damage, p);
                    t.getWorld().spawnParticle(Particle.SMOKE, c.clone().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.02);
                    t.getWorld().playSound(c, Sound.ENTITY_BAT_HURT, 0.6f, 1.2f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean nightFlight(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        Vector v = new Vector(dir.getX(), 0, dir.getZ());
        if (v.lengthSquared() < 0.01) v = new Vector(0, 0, 0); else v.normalize().multiply(d("night-flight.forward", 1.3));
        v.setY(d("night-flight.up", 0.9));
        p.setVelocity(v);

        flightNoFall.put(p.getUniqueId(), now() + ticks("night-flight.no-fall-seconds", 8) * 50L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.5f, 0.8f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 24 || !p.isOnline()) { cancel(); return; }
                Location l = p.getLocation().add(0, 1, 0);
                World w = l.getWorld();
                w.spawnParticle(Particle.LARGE_SMOKE, l, 6, 0.3, 0.3, 0.3, 0.02);
                w.spawnParticle(Particle.SOUL, l, 2, 0.2, 0.2, 0.2, 0.01);
                if (tick % 3 == 0) {
                    Bat b = w.spawn(l, Bat.class, bat -> {
                        bat.setInvulnerable(true);
                        bat.setSilent(true);
                        bat.setPersistent(false);
                        bat.setAwake(true);
                    });
                    track(b);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> untrack(b), 25L);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ================================================================ CURSED BLADE

    private boolean hex(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;

        int dur = ticks("hex.duration-seconds", 8);
        Link link = new Link(p.getUniqueId(), now() + dur * 50L);
        hex.put(t.getUniqueId(), link);

        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.6f);
        tell(p, "Hexed " + t.getName(), NamedTextColor.DARK_PURPLE);
        if (t instanceof Player victim) tell(victim, "You have been hexed! Attacking " + p.getName() + " hurts you.", NamedTextColor.DARK_PURPLE);

        new BukkitRunnable() {
            @Override public void run() {
                if (now() >= link.until() || !t.isValid()) {
                    hex.remove(t.getUniqueId(), link);
                    cancel();
                    return;
                }
                t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1.2, 0), 8, 0.4, 0.6, 0.4, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    private boolean possession(Player p) {
        LivingEntity t = requireTarget(p);
        if (t == null) return false;
        if (!(t instanceof Player victim)) {
            tell(p, "Possession only works on players", NamedTextColor.RED);
            return false;
        }
        if (possessed.containsKey(victim.getUniqueId())) {
            tell(p, "Target is already possessed", NamedTextColor.RED);
            return false;
        }

        int dur = ticks("possession.duration-seconds", 4);

        // perm[i] = which ORIGINAL slot's item now sits in slot i
        int[] perm = new int[9];
        for (int i = 0; i < 9; i++) perm[i] = i;
        for (int i = 8; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
        }

        PlayerInventory inv = victim.getInventory();
        ItemStack[] cur = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            cur[i] = it == null ? null : it.clone();
        }
        for (int i = 0; i < 9; i++) inv.setItem(i, cur[perm[i]]);
        inv.setHeldItemSlot(random.nextInt(9));
        possessed.put(victim.getUniqueId(), perm);

        give(victim, PotionEffectType.DARKNESS, dur, 0);
        victim.sendActionBar(Component.text("Something is moving your things...", NamedTextColor.DARK_PURPLE));
        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.03);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.2f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> endPossession(victim, true), dur);
        return true;
    }

    /** Puts every hotbar item back into its original slot (keeps any changes made meanwhile). */
    private void endPossession(Player p, boolean notify) {
        int[] perm = possessed.remove(p.getUniqueId());
        if (perm == null) return;

        PlayerInventory inv = p.getInventory();
        ItemStack[] nowItems = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            nowItems[i] = it == null ? null : it.clone();
        }
        ItemStack[] restored = new ItemStack[9];
        for (int i = 0; i < 9; i++) restored[perm[i]] = nowItems[i];
        for (int i = 0; i < 9; i++) inv.setItem(i, restored[i]);

        if (notify && p.isOnline()) tell(p, "Your hotbar returns to normal", NamedTextColor.GRAY);
    }

    // ================================================================ WIDOWMAKER

    private boolean webShot(Player p) {
        Snowball ball = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
        ball.setItem(new ItemStack(Material.COBWEB));
        ball.getPersistentDataContainer().set(projKey, PersistentDataType.STRING, "web_shot");
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1.4f);

        ItemStack webItem = new ItemStack(Material.COBWEB);
        new BukkitRunnable() {
            int life = 0;
            @Override public void run() {
                if (!ball.isValid() || life++ > 200) { cancel(); return; }
                Location l = ball.getLocation();
                l.getWorld().spawnParticle(Particle.ITEM, l, 3, 0.08, 0.08, 0.08, 0.02, webItem);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void webHit(Location loc, Entity hit, Player owner) {
        World w = loc.getWorld();
        w.playSound(loc, Sound.BLOCK_COBWEB_PLACE, 1f, 1f);
        w.spawnParticle(Particle.ITEM, loc, 25, 0.3, 0.3, 0.3, 0.05, new ItemStack(Material.COBWEB));

        if (hit instanceof LivingEntity victim && !victim.equals(owner)) {
            int ticks = ticks("web-shot.web-seconds", 5);
            Block feet = victim.getLocation().getBlock();
            int placed = 0;
            if (placeWeb(feet, ticks)) placed++;
            if (placeWeb(feet.getRelative(BlockFace.UP), ticks)) placed++;
            if (placed == 0) give(victim, PotionEffectType.SLOWNESS, ticks, 3); // couldn't place webs here
            if (victim instanceof Player vp) tell(vp, "You're stuck in a web!", NamedTextColor.GRAY);
        }
    }

    private boolean spidersFeast(Player p) {
        int dur = ticks("spiders-feast.duration-seconds", 8);
        double r = d("spiders-feast.radius", 5.0);
        boolean placeWebs = plugin.getConfig().getBoolean("abilities.spiders-feast.place-cobwebs", true);
        Location c = p.getLocation();
        World w = c.getWorld();

        give(p, PotionEffectType.SPEED, dur, 1);

        if (placeWebs) {
            int rr = (int) Math.ceil(r);
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double dist = Math.hypot(dx, dz);
                    if (dist > r || dist < 2.0 || random.nextDouble() > 0.28) continue;
                    Block b = w.getBlockAt(c.getBlockX() + dx, c.getBlockY(), c.getBlockZ() + dz);
                    if (b.getRelative(BlockFace.DOWN).getType().isSolid()) placeWeb(b, dur);
                }
            }
        }
        w.playSound(c, Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.6f);
        w.playSound(c, Sound.BLOCK_COBWEB_PLACE, 1.2f, 0.8f);
        tell(p, "The spider's feast begins", NamedTextColor.GRAY);

        ItemStack webItem = new ItemStack(Material.COBWEB);
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= dur || !p.isOnline()) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    w.spawnParticle(Particle.ITEM, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 2, 0.2, 0.1, 0.2, 0.02, webItem);
                }
                for (int i = 0; i < 8; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double rad = random.nextDouble() * r;
                    w.spawnParticle(Particle.ITEM, c.clone().add(Math.cos(a) * rad, 0.15, Math.sin(a) * rad), 1, 0.1, 0.05, 0.1, 0.01, webItem);
                }
                for (Entity en : w.getNearbyEntities(c, r, r, r)) {
                    if (isTargetable(p, en) && en.getLocation().distanceSquared(c) <= r * r) {
                        give((LivingEntity) en, PotionEffectType.SLOWNESS, 30, 1);
                    }
                }
                tick += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    // ---- temporary cobwebs

    private boolean placeWeb(Block b, long ticks) {
        if (webs.containsKey(b)) return false;
        if (b.isLiquid() || !(b.getType().isAir() || b.isReplaceable())) return false;
        webs.put(b, b.getBlockData());
        b.setType(Material.COBWEB, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeWeb(b), ticks);
        return true;
    }

    private void removeWeb(Block b) {
        BlockData original = webs.remove(b);
        if (original != null && b.getType() == Material.COBWEB) b.setBlockData(original, false);
    }

    public void onBlockBreak(BlockBreakEvent e) {
        if (webs.remove(e.getBlock()) != null) e.setDropItems(false);
    }

    // ================================================================ event hooks

    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        String type = proj.getPersistentDataContainer().get(projKey, PersistentDataType.STRING);
        if (type == null) return;

        Player owner = proj.getShooter() instanceof Player pl ? pl : null;
        Entity hit = e.getHitEntity();

        // Fly straight through the caster and through our own summoned entities
        if (hit != null && (hit.equals(owner) || temp.containsKey(hit.getUniqueId()))) {
            e.setCancelled(true);
            return;
        }

        switch (type) {
            case "pumpkin_bomb" -> explodePumpkin(proj.getLocation(), owner);
            case "web_shot" -> webHit(proj.getLocation(), hit, owner);
            default -> { }
        }
        proj.remove();
    }

    /** Called (at MONITOR priority) after a successful entity-vs-entity hit. attacker is already resolved from projectiles. */
    public void onDamage(Entity attacker, Entity victim) {
        if (internalDamage) return;

        if (attacker instanceof Player ap) {
            // Ghostwalk: attacking ends it
            if (ghostwalk.containsKey(ap.getUniqueId())) endGhostwalk(ap, true);

            // Blood Hunt: hits on the marked target heal the marker
            if (victim instanceof LivingEntity lv) {
                Link mark = bloodHunt.get(lv.getUniqueId());
                if (mark != null && mark.until() > now() && mark.owner().equals(ap.getUniqueId())) {
                    heal(ap, d("blood-hunt.heal-per-hit", 2.0));
                    ap.getWorld().spawnParticle(Particle.HEART, ap.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0.0);
                }
            }
        }

        // Hex: the cursed entity hurts itself when it hits the person who cursed it
        if (attacker instanceof LivingEntity al && victim instanceof Player vp) {
            Link curse = hex.get(al.getUniqueId());
            if (curse != null && curse.until() > now() && curse.owner().equals(vp.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!al.isValid()) return;
                    hurt(al, d("hex.reflect-damage", 2.0), vp);
                    al.getWorld().spawnParticle(Particle.WITCH, al.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.02);
                });
            }
        }
    }

    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();

        if (temp.containsKey(dead.getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }

        // Blood Moon: kills restore health
        Player killer = dead.getKiller();
        if (killer != null) {
            Long until = bloodMoon.get(killer.getUniqueId());
            if (until != null && until > now()) {
                heal(killer, d("blood-moon.heal-per-kill", 6.0));
                killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4, 0.0);
            }
        }

        // Clean up any marks/roots on the dead entity
        bloodHunt.remove(dead.getUniqueId());
        hex.remove(dead.getUniqueId());
        rooted.remove(dead.getUniqueId());
        frozenMobs.remove(dead.getUniqueId());
    }

    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        ghostwalk.remove(p.getUniqueId());
        flightNoFall.remove(p.getUniqueId());
        bloodMoon.remove(p.getUniqueId());
        if (possessed.containsKey(p.getUniqueId())) {
            if (e.getKeepInventory()) endPossession(p, false);   // inventory stays -> put it back in order
            else possessed.remove(p.getUniqueId());              // items drop; nothing to restore
        }
    }

    public void onQuit(Player p) {
        endPossession(p, false);
        UUID id = p.getUniqueId();
        ghostwalk.remove(id);
        flightNoFall.remove(id);
        rooted.remove(id);
        bloodMoon.remove(id);
        bloodHunt.remove(id);
        hex.remove(id);
    }

    // ================================================================ shutdown

    /** Undo everything temporary (called from onDisable). */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) endPossession(p, false);
        for (Entity e : new ArrayList<>(temp.values())) e.remove();
        temp.clear();
        for (Mob m : frozenMobs.values()) if (m.isValid()) m.setAI(true);
        frozenMobs.clear();
        for (Block b : new ArrayList<>(webs.keySet())) removeWeb(b);
        webs.clear();
        undeadVictim.clear();
        rooted.clear();
        ghostwalk.clear();
        flightNoFall.clear();
        bloodHunt.clear();
        bloodMoon.clear();
        hex.clear();
        possessed.clear();
    }
}
