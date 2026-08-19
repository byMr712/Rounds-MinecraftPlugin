package com.rounds.item;

import com.rounds.RoundsKeys;
import com.rounds.RoundsPlugin;
import com.rounds.entity.RoundsEntities;
import com.rounds.player.PlayerData;
import com.rounds.player.PlayerDataManager.GunCooldowns;
import com.rounds.util.Messages;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class GunItem implements Listener {

    private static RoundsPlugin plugin;
    private static GunItem instance;
    private static final Map<UUID, ReloadTask> reloadTasks = new HashMap<>();
    private static final Map<UUID, Long> shieldCooldowns = new HashMap<>();
    private static final Set<UUID> activeShields = new HashSet<>();
    private static final Map<UUID, Integer> shieldCharges = new HashMap<>();
    private static final Set<UUID> silencedPlayers = new HashSet<>();
    private static final Set<UUID> reloadingPlayers = new HashSet<>();
    private static final Set<UUID> activeSaws = new HashSet<>();
    private static final Set<UUID> shotThisTick = new HashSet<>();

    private static final double SHIELD_DURATION_TICKS = 20;
    private static final long SHIELD_COOLDOWN_MS = 10000;

    public static void register(RoundsPlugin pl) {
        plugin = pl;
    }

    public static void setInstance(GunItem inst) {
        instance = inst;
    }

    public static GunItem getInstance() {
        return instance;
    }

    public static ItemStack createGunItem() {
        String materialName = plugin.getConfig().getString("gun.material", "STICK");
        Material mat;
        try {
            mat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.STICK;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + Messages.get("gun.name"));
        meta.setUnbreakable(true);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + Messages.get("gun.lore-1"));
        lore.add(ChatColor.GRAY + Messages.get("gun.lore-2"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(RoundsKeys.GUN, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isGun(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(RoundsKeys.GUN, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGun(item)) return;

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            shotThisTick.add(player.getUniqueId());
            doShoot(player);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            doBlock(player);
        }
    }

    public void doShoot(Player player) {
        UUID uuid = player.getUniqueId();

        if (silencedPlayers.contains(uuid)) {
            player.sendActionBar(ChatColor.RED + "You are silenced!");
            return;
        }

        if (isReloading(uuid)) {
            return;
        }

        if (reloadingPlayers.contains(uuid)) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player);

        if (data.ammo <= 0) {
            player.sendActionBar(ChatColor.RED + Messages.get("gun.no-ammo"));
            return;
        }

        double cooldownTicks = Math.max(data.atks * (1.0 - data.atkSpeed) + data.atksReload, 2);
        if (!GunCooldowns.canShoot(uuid, cooldownTicks)) {
            return;
        }
        GunCooldowns.recordShot(uuid);

        cancelReload(uuid);

        int bulletCount = (int) Math.max(data.bullets, 1);
        Vector direction = player.getLocation().getDirection();
        Location eyeLoc = player.getEyeLocation();

        for (int i = 0; i < bulletCount; i++) {
            final int idx = i;
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (!player.isOnline() || !player.isValid()) return;
                Vector vel = direction.clone();
                double spread = 0.05;
                vel.setX(vel.getX() + (Math.random() - 0.5) * spread);
                vel.setY(vel.getY() + (Math.random() - 0.5) * spread);
                vel.setZ(vel.getZ() + (Math.random() - 0.5) * spread);

                double speed = 3.0 * Math.max(data.bulletSpeed, 0.1);
                vel = vel.normalize().multiply(speed);

                RoundsEntities.spawnBullet(player, eyeLoc, vel, data);
            }, idx * 3L);
        }
        data.consumeEmpowerCharge();

        if (data.echo > 0) {
            int echoDelay = bulletCount * 3;
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (player.isOnline()) {
                    for (int i = 0; i < bulletCount; i++) {
                        Vector vel = direction.clone();
                        double spread = 0.05;
                        vel.setX(vel.getX() + (Math.random() - 0.5) * spread);
                        vel.setY(vel.getY() + (Math.random() - 0.5) * spread);
                        vel.setZ(vel.getZ() + (Math.random() - 0.5) * spread);
                        double speed = 3.0 * Math.max(data.bulletSpeed, 0.1);
                        vel = vel.normalize().multiply(speed);
                        RoundsEntities.spawnBullet(player, player.getEyeLocation(), vel, data);
                    }
                }
            }, echoDelay + 5L);
        }

        data.ammo -= 1;
        player.sendActionBar(ChatColor.GRAY + Messages.get("gun.ammo-display", (int) data.ammo, (int) data.maxAmmo));
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.5f);

        if (data.shieldsUp > 0 && data.ammo <= 0) {
            doBlock(player);
        }
    }

    private void doBlock(Player player) {
        UUID uuid = player.getUniqueId();

        if (silencedPlayers.contains(uuid)) {
            player.sendActionBar(ChatColor.RED + "You are silenced!");
            return;
        }

        if (activeShields.contains(uuid)) {
            player.sendActionBar(ChatColor.YELLOW + Messages.get("gun.shield-active"));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(uuid);
        double cooldownReduction = 1.0;
        if (data.shieldCooldown > 0) {
            cooldownReduction = Math.max(0.1, 1.0 - data.shieldCooldown * 0.1);
        }
        if (data.blockCd > 0) {
            cooldownReduction = Math.max(0.1, cooldownReduction - data.blockCd * 0.1);
        }
        long actualCooldown = (long) (SHIELD_COOLDOWN_MS * cooldownReduction);

        Long lastUse = shieldCooldowns.get(uuid);
        long now = System.currentTimeMillis();
        int extraCharges = (int) Math.max(data.shieldCharge + data.doubleBlock, 0);
        int currentCharges = shieldCharges.getOrDefault(uuid, 0);

        if (lastUse != null && now - lastUse < actualCooldown && currentCharges <= 0) {
            long remaining = (actualCooldown - (now - lastUse)) / 1000 + 1;
            player.sendActionBar(ChatColor.YELLOW + Messages.get("gun.shield-cooldown", remaining));
            return;
        }

        if (currentCharges > 0) {
            shieldCharges.put(uuid, currentCharges - 1);
        } else {
            shieldCooldowns.put(uuid, now);
            shieldCharges.put(uuid, extraCharges);
        }
        activeShields.add(uuid);

        if (data.empower > 0) {
            data.empowerCharge = 1;
        }

        long shieldDuration = (long) SHIELD_DURATION_TICKS;
        if (data.shieldsUp > 0) {
            shieldDuration += (long) (data.shieldsUp * 10);
        }

        spawnShield(player);

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            PotionEffectType.DAMAGE_RESISTANCE, 20, 255, true, false, false));

        if (data.bombOnBlock > 0) {
            Location bombCenter = player.getLocation();
            int bombCount = Math.max((int) data.bombOnBlock, 1);
            for (int i = 0; i < bombCount; i++) {
                double angle = 2 * Math.PI * i / bombCount;
                Location bombLoc = bombCenter.clone().add(Math.cos(angle) * 3.0, 0.5, Math.sin(angle) * 3.0);
                RoundsEntities.spawnBomb(bombLoc, uuid, 1.0);
            }
        }

        if (data.teleport > 0) {
            Location from = player.getLocation();
            Vector dir = from.getDirection().multiply(5.0);
            Location target = from.clone().add(dir);
            target.setY(target.getY() + 1.0);
            Block blockBelow = target.clone().subtract(0, 1, 0).getBlock();
            if (blockBelow.getType().isSolid()) {
                target.setY(blockBelow.getY() + 1.0);
            }
            if (!target.getBlock().getType().isSolid()) {
                player.teleport(target);
                player.setVelocity(new Vector(0, 0.3, 0));
            }
        }

        if (data.shieldCharge > 0) {
            Vector launchDir = player.getLocation().getDirection().multiply(1.5);
            player.setVelocity(player.getVelocity().add(launchDir));
        }

        if (data.tacticalReload > 0) {
            data.ammo = data.maxAmmo;
            cancelReload(uuid);
        }

        Location blockLoc = player.getLocation();

        if (data.saw > 0 && !activeSaws.contains(uuid)) {
            activeSaws.add(uuid);
            player.getWorld().playSound(blockLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    Player p = plugin.getServer().getPlayer(uuid);
                    if (p == null || !p.isOnline() || ticks >= 60) {
                        activeSaws.remove(uuid);
                        cancel();
                        return;
                    }
                    Location sawLoc = p.getLocation();
                    for (Entity entity : sawLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                        if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                            GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(uuid);
                            GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
                            if (myTeam != null && targetTeam != null && myTeam != targetTeam) {
                                target.damage(data.saw * 1.0);
                            }
                        }
                    }
                    sawLoc.getWorld().spawnParticle(Particle.FLAME, sawLoc.add(0, 1, 0),
                        10, 0.5, 0.5, 0.5, 0.02);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        if (data.emp > 0) {
            for (Entity entity : blockLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                    GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(uuid);
                    GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
                    if (myTeam != null && targetTeam != null && myTeam != targetTeam) {
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.SLOW, 60, 2));
                    }
                }
            }
            Location empCenter = blockLoc.clone().add(0, 0.5, 0);
            empCenter.getWorld().spawnParticle(Particle.SMOKE_LARGE, empCenter,
                40, 2.5, 0.5, 2.5, 0.03);
            player.getWorld().playSound(blockLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
        }

        if (data.shockwave > 0) {
            for (Entity entity : blockLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                    Vector push = target.getLocation().toVector()
                        .subtract(blockLoc.toVector()).normalize().multiply(1.5);
                    push.setY(0.5);
                    target.setVelocity(target.getVelocity().add(push));
                }
            }
            player.getWorld().playSound(blockLoc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        }

        if (data.implode > 0) {
            for (Entity entity : blockLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                    Vector pull = blockLoc.toVector()
                        .subtract(target.getLocation().toVector()).normalize().multiply(1.0);
                    pull.setY(0.3);
                    target.setVelocity(target.getVelocity().add(pull));
                }
            }
            player.getWorld().playSound(blockLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        }

        if (data.silence > 0) {
            for (Entity entity : blockLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                    GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(uuid);
                    GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
                    if (myTeam != null && targetTeam != null && myTeam != targetTeam && target instanceof Player silenceTarget) {
                        silencePlayer(silenceTarget.getUniqueId());
                        activeShields.remove(silenceTarget.getUniqueId());
                        for (Entity e : silenceTarget.getNearbyEntities(3.0, 3.0, 3.0)) {
                            if (isShield(e)) {
                                UUID shieldOwner = getShieldOwner(e);
                                if (shieldOwner != null && shieldOwner.equals(silenceTarget.getUniqueId())) {
                                    e.remove();
                                }
                            }
                        }
                        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                            unsilencePlayer(silenceTarget.getUniqueId());
                        }, (long) (data.silence * 40));
                    }
                }
            }
            player.getWorld().playSound(blockLoc, Sound.BLOCK_GLASS_BREAK, 0.5f, 2.0f);
        }

        if (data.overpower > 0) {
            double playerHP = player.getHealth();
            for (Entity entity : blockLoc.getNearbyEntities(5.0, 5.0, 5.0)) {
                if (entity instanceof LivingEntity target && !target.getUniqueId().equals(uuid)) {
                    GameTeam myTeam = plugin.getTeamManager().getPlayerTeam(uuid);
                    GameTeam targetTeam = plugin.getTeamManager().getPlayerTeam(target.getUniqueId());
                    if (myTeam != null && targetTeam != null && myTeam != targetTeam) {
                        double dmg = playerHP * data.overpower * 0.15;
                        target.setNoDamageTicks(0);
                        target.damage(dmg);
                    }
                }
            }
            player.getWorld().playSound(blockLoc, Sound.ENTITY_WITHER_HURT, 1.0f, 1.5f);
        }

        if (data.heal > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                PotionEffectType.REGENERATION, 100, 1));
        }

        player.getWorld().playSound(blockLoc, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.5f);
        player.sendActionBar(ChatColor.BLUE + Messages.get("gun.shield-activated"));

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            activeShields.remove(uuid);
        }, shieldDuration);
    }

    private void spawnShield(Player player) {
        Location spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5));

        ArmorStand shield = player.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setSmall(true);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(false);
            stand.setCustomName(null);
            stand.setItemInHand(null);
            stand.setRightArmPose(new org.bukkit.util.EulerAngle(0, 0, Math.toRadians(-90)));

            PersistentDataContainer pdc = stand.getPersistentDataContainer();
            pdc.set(RoundsKeys.SHIELD, PersistentDataType.BYTE, (byte) 1);
            pdc.set(RoundsKeys.SHIELD_OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
        });

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (shield.isValid()) {
                shield.remove();
            }
        }, (long) SHIELD_DURATION_TICKS + 5);
    }

    public static boolean isShield(Entity entity) {
        if (entity == null || !entity.isValid()) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(RoundsKeys.SHIELD, PersistentDataType.BYTE);
    }

    public static UUID getShieldOwner(Entity entity) {
        if (entity == null) return null;
        String owner = entity.getPersistentDataContainer().get(RoundsKeys.SHIELD_OWNER, PersistentDataType.STRING);
        return owner != null ? UUID.fromString(owner) : null;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!isGun(item)) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        if (data.ammo < data.maxAmmo && !isReloading(player.getUniqueId())) {
            startReload(player, data);
        }
    }

    private void startReload(Player player, PlayerData data) {
        UUID uuid = player.getUniqueId();
        reloadingPlayers.add(uuid);
        double reloadDurationTicks = Math.max(60 * (1.0 - Math.min(data.reloadSpeed, 0.95)) + data.atksReload * 2, 4);

        ReloadTask task = new ReloadTask(player, data, (int) reloadDurationTicks);
        reloadTasks.put(uuid, task);
        task.start();
    }

    public static void cancelReload(UUID uuid) {
        reloadingPlayers.remove(uuid);
        ReloadTask task = reloadTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public static boolean isReloading(UUID uuid) {
        ReloadTask task = reloadTasks.get(uuid);
        return task != null && task.isRunning();
    }

    public static boolean isShieldActive(UUID uuid) {
        return activeShields.contains(uuid);
    }

    public static void silencePlayer(UUID uuid) { silencedPlayers.add(uuid); }
    public static void unsilencePlayer(UUID uuid) { silencedPlayers.remove(uuid); }
    public static boolean isSilenced(UUID uuid) { return silencedPlayers.contains(uuid); }

    public static void resetBlockCooldown(UUID uuid) {
        shieldCooldowns.remove(uuid);
    }
    public static void resetShieldActive(UUID uuid) {
        activeShields.remove(uuid);
    }
    public static void resetRoundState() {
        activeShields.clear();
        shieldCooldowns.clear();
        shieldCharges.clear();
        silencedPlayers.clear();
        reloadingPlayers.clear();
        activeSaws.clear();
        shotThisTick.clear();
    }

    public static boolean consumeShotThisTick(UUID uuid) {
        return shotThisTick.remove(uuid);
    }

    public static class ReloadTask {
        private final UUID uuid;
        private final PlayerData data;
        private final int totalTicks;
        private int currentTick = 0;
        private int taskId = -1;
        private boolean running = false;

        ReloadTask(Player player, PlayerData data, int totalTicks) {
            this.uuid = player.getUniqueId();
            this.data = data;
            this.totalTicks = totalTicks;
        }

        void start() {
            running = true;
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline() || !running) {
                    cancel();
                    return;
                }

                currentTick++;
                int remaining = (int) Math.ceil((totalTicks - currentTick) / 20.0);
                int progress = (int) ((currentTick / (double) totalTicks) * 10);

                StringBuilder bar = new StringBuilder(ChatColor.RED + "[");
                for (int i = 0; i < 10; i++) {
                    bar.append(i < progress ? ChatColor.GREEN + "|" : ChatColor.GRAY + "|");
                }
                bar.append(ChatColor.RED + "] ");

                if (currentTick >= totalTicks) {
                    data.ammo = data.maxAmmo;
                    reloadingPlayers.remove(uuid);
                    player.sendActionBar(ChatColor.GREEN + Messages.get("gun.reloaded", (int) data.ammo));
                    player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
                    cancel();
                    return;
                }

                player.sendActionBar(bar.toString() + ChatColor.YELLOW + Messages.get("gun.reloading") + " " + ChatColor.GRAY + remaining + "s");
            }, 0L, 1L);
        }

        void cancel() {
            running = false;
            reloadingPlayers.remove(uuid);
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
            }
        }

        boolean isRunning() {
            return running;
        }
    }
}
