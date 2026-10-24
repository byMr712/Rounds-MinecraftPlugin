package com.rounds.bot;

import com.rounds.RoundsPlugin;
import com.rounds.blocks.BlockListener;
import com.rounds.item.GunItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FakeBot {

    private final BotManager manager;
    private Player bukkitPlayer;
    private ServerPlayer nmsPlayer;
    private final BotAI ai;
    private BotStrategy strategy = BotStrategy.BALANCED;
    private int level;
    private double velY = 0.0;
    private double impulseX = 0.0;
    private double impulseZ = 0.0;
    private int padLaunchTicks = 0;

    public double getVelY() {
        return velY;
    }

    public boolean isPadLaunched() {
        return padLaunchTicks > 0;
    }

    public FakeBot(BotManager manager, Player bukkitPlayer, ServerPlayer nmsPlayer) {
        this.manager = manager;
        this.bukkitPlayer = bukkitPlayer;
        this.nmsPlayer = nmsPlayer;
        this.level = ThreadLocalRandom.current().nextInt(1, 11); // Random difficulty level 1..10
        this.ai = new BotAI(this);
        this.ai.runTaskTimer(manager.getPlugin(), 1L, 1L);
    }

    public String getName() {
        return bukkitPlayer.getName();
    }

    public Player getBukkitPlayer() {
        Player p = Bukkit.getPlayer(bukkitPlayer.getUniqueId());
        if (p != null) {
            this.bukkitPlayer = p;
        }
        return bukkitPlayer;
    }

    public ServerPlayer getNmsPlayer() {
        try {
            MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
            ServerPlayer sp = nmsServer.getPlayerList().getPlayer(bukkitPlayer.getUniqueId());
            if (sp != null) {
                this.nmsPlayer = sp;
            }
        } catch (Throwable ignored) {}
        return nmsPlayer;
    }

    public void updatePlayer(Player player) {
        if (player != null) {
            this.bukkitPlayer = player;
            player.setInvulnerable(false);
            player.setNoDamageTicks(0);
            getNmsPlayer();
        }
    }

    public BotManager getManager() {
        return manager;
    }

    public RoundsPlugin getPlugin() {
        return manager.getPlugin();
    }

    public BotStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(BotStrategy strategy) {
        this.strategy = strategy != null ? strategy : BotStrategy.BALANCED;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(10, level));
    }

    public void randomizeLevel() {
        this.level = ThreadLocalRandom.current().nextInt(1, 11);
    }

    public double getSkillFactor() {
        return this.level / 10.0;
    }

    public BotAI getAi() {
        return ai;
    }

    public String getTeamName() {
        if (manager == null || manager.getPlugin() == null) return "NONE";
        com.rounds.teams.TeamManager.GameTeam gt = manager.getPlugin().getTeamManager().getPlayerTeam(getBukkitPlayer().getUniqueId());
        return (gt != null) ? gt.name() : "NONE";
    }

    public void onAttacked(Location origin, UUID attackerId) {
        if (ai != null) {
            ai.onAttacked(origin, attackerId);
        }
    }

    public double getX() {
        return getBukkitPlayer().getLocation().getX();
    }

    public double getY() {
        return getBukkitPlayer().getLocation().getY();
    }

    public double getZ() {
        return getBukkitPlayer().getLocation().getZ();
    }

    public Location getLocation() {
        return getBukkitPlayer().getLocation();
    }

    public double getMaxHealth() {
        var attr = getBukkitPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    public boolean isOnGround() {
        ServerPlayer sp = getNmsPlayer();
        if (sp.onGround()) return true;
        Location loc = getBukkitPlayer().getLocation();
        if (loc.getWorld() == null) return false;
        double y = loc.getY();
        double fractional = y - Math.floor(y);
        // If entity is floating mid-block in the air, it cannot be on ground
        if (fractional > 0.08 && fractional < 0.92) {
            return false;
        }
        Location below = new Location(loc.getWorld(), loc.getBlockX() + 0.5, Math.floor(y) - 0.5, loc.getBlockZ() + 0.5);
        return below.getBlock().getType().isSolid();
    }

    public boolean isAirborne() {
        return !isOnGround() && getBukkitPlayer().getFallDistance() > 0.5;
    }

    public boolean hasGun() {
        Player p = getBukkitPlayer();
        if (p.isDead() || !p.isOnline()) return false;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (GunItem.isGun(hand)) return true;

        for (int i = 0; i < 9; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (GunItem.isGun(item)) {
                p.getInventory().setHeldItemSlot(i);
                return true;
            }
        }
        return false;
    }

    public void shoot() {
        Player p = getBukkitPlayer();
        if (p.isDead() || !p.isOnline()) return;
        if (!hasGun()) return;
        p.swingMainHand();
        GunItem.getInstance().doShoot(p);
    }

    public void block() {
        Player p = getBukkitPlayer();
        if (p.isDead() || !p.isOnline()) return;
        if (!hasGun()) return;
        GunItem.getInstance().doBlock(p);
    }

    public void applyJumpPad(double launchHeight) {
        this.velY = launchHeight * 0.2;
        this.padLaunchTicks = 45;
        Player p = getBukkitPlayer();
        if (p != null) {
            Location center = manager.getPlugin() != null ? manager.getPlugin().getGameManager().getCurrentZoneCenter() : null;
            if (center != null && center.getWorld() != null && center.getWorld().equals(p.getWorld())) {
                double dx = center.getX() - p.getLocation().getX();
                double dz = center.getZ() - p.getLocation().getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1.5) {
                    this.impulseX = (dx / dist) * 0.45;
                    this.impulseZ = (dz / dist) * 0.45;
                } else {
                    double rad = Math.toRadians(-p.getLocation().getYaw());
                    this.impulseX = Math.sin(rad) * 0.45;
                    this.impulseZ = Math.cos(rad) * 0.45;
                }
            } else {
                double rad = Math.toRadians(-p.getLocation().getYaw());
                this.impulseX = Math.sin(rad) * 0.40;
                this.impulseZ = Math.cos(rad) * 0.40;
            }
        }
        if (ai != null) {
            ai.setPadCooldown(600);
        }
    }

    public void applyUpBlock(double speed) {
        this.velY = speed;
    }

    public void aimAt(Location targetLoc, float maxTurnDeg, float inaccuracyDeg) {
        Player p = getBukkitPlayer();
        ServerPlayer sp = getNmsPlayer();
        if (p.isDead() || !p.isOnline()) return;

        Location myLoc = p.getLocation();
        double dx = targetLoc.getX() - myLoc.getX();
        double dy = (targetLoc.getY() + 1.35) - (myLoc.getY() + 1.62); // Aim at upper torso / head
        double dz = targetLoc.getZ() - myLoc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float idealPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(0.001, distXZ)));

        if (idealPitch > 89.0f) idealPitch = 89.0f;
        if (idealPitch < -89.0f) idealPitch = -89.0f;

        // Realistic inaccuracy
        if (inaccuracyDeg > 0.05f) {
            float randY = ((ThreadLocalRandom.current().nextFloat() * 2f) - 1f) * inaccuracyDeg;
            float randP = ((ThreadLocalRandom.current().nextFloat() * 2f) - 1f) * (inaccuracyDeg * 0.7f);
            idealYaw += randY;
            idealPitch += randP;
        }

        // Smooth rotation interpolation
        float curYaw = myLoc.getYaw();
        float curPitch = myLoc.getPitch();

        float dyaw = idealYaw - curYaw;
        while (dyaw < -180.0f) dyaw += 360.0f;
        while (dyaw > 180.0f) dyaw -= 360.0f;
        if (dyaw > maxTurnDeg) dyaw = maxTurnDeg;
        if (dyaw < -maxTurnDeg) dyaw = -maxTurnDeg;

        float dpitch = idealPitch - curPitch;
        if (dpitch > maxTurnDeg * 0.8f) dpitch = maxTurnDeg * 0.8f;
        if (dpitch < -maxTurnDeg * 0.8f) dpitch = -maxTurnDeg * 0.8f;

        float newYaw = curYaw + dyaw;
        float newPitch = curPitch + dpitch;

        sp.setRot(newYaw, newPitch);
        sp.setYHeadRot(newYaw);
        sp.setYBodyRot(newYaw);
    }

    public void moveRel(double dx, double dy, double dz) {
        Player p = getBukkitPlayer();
        ServerPlayer sp = getNmsPlayer();
        if (p.isDead() || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) return;

        // 1. Pick up external vertical impulses (jump pads, up blocks, explosions)
        Vec3 delta = sp.getDeltaMovement();
        if (Math.abs(delta.y) > 0.05) {
            velY = delta.y;
        }

        if (padLaunchTicks > 0) {
            padLaunchTicks--;
        }

        // 2. Ground check
        boolean onGround = isOnGround();
        if (onGround && velY <= 0.0) {
            padLaunchTicks = 0;
            velY = 0.0;
            impulseX = 0.0;
            impulseZ = 0.0;
        } else {
            velY = Math.max(velY - 0.08, -3.92) * 0.98;
        }

        // Smooth step down: if walking down stairs/slope, smoothly descend to the lower block
        if (!onGround && velY <= 0.0 && padLaunchTicks <= 0) {
            Location checkStepBelow = p.getLocation().clone().subtract(0, 1.1, 0);
            if (checkStepBelow.getBlock().getType().isSolid()) {
                velY = -0.32;
            }
        }

        // 3. Auto-jump over 1-block obstacles/steps when moving on ground (not in mid-air!)
        if (onGround && velY <= 0.0 && (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01)) {
            Location loc = p.getLocation();
            double moveLen = Math.sqrt(dx * dx + dz * dz);
            double stepDist = Math.max(moveLen * 1.5, 0.55);
            Location ahead = loc.clone().add((dx / moveLen) * stepDist, 0.2, (dz / moveLen) * stepDist);
            Location aheadUp = ahead.clone().add(0, 1.0, 0);
            Location headSpace = loc.clone().add(0, 2.0, 0);

            boolean obstacleAhead = ahead.getBlock().getType().isSolid() || sp.horizontalCollision;
            if (obstacleAhead && !aheadUp.getBlock().getType().isSolid() && !headSpace.getBlock().getType().isSolid()) {
                velY = 0.42; // Minecraft jump velocity
                impulseX += (dx / moveLen) * 0.18;
                impulseZ += (dz / moveLen) * 0.18;
            }
        }

        double finalVelY = (onGround && velY <= 0.0) ? -0.0784 : velY;

        double moveX = dx + impulseX;
        double moveZ = dz + impulseZ;
        impulseX *= 0.82;
        impulseZ *= 0.82;
        if (Math.abs(impulseX) < 0.01) impulseX = 0.0;
        if (Math.abs(impulseZ) < 0.01) impulseZ = 0.0;

        Location from = p.getLocation().clone();
        sp.move(MoverType.SELF, new Vec3(moveX, finalVelY, moveZ));
        Location to = p.getLocation().clone();

        // Check if ceiling was hit
        if (sp.verticalCollision && !sp.verticalCollisionBelow && velY > 0.0) {
            velY = 0.0;
        }
        if (sp.verticalCollisionBelow && velY < 0.0) {
            velY = 0.0;
        }
        // Clear deltaMovement so move's residual vector doesn't pollute next tick
        sp.setDeltaMovement(Vec3.ZERO);

        // 4. Trigger Jump Pads and Up Blocks directly on landing / stepping (percentage damage & lift)
        Location locBelow = to.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
        Location locAt = to.getBlock().getLocation();
        BlockListener bl = manager.getPlugin().getBlockListener();
        if (bl != null) {
            boolean touchingJump = bl.isLocationTouchingJumpBlock(to) || bl.isJumpBlock(locBelow) || bl.isJumpBlock(locAt);
            if (touchingJump) {
                if (ai != null && ai.isPadBanned()) {
                    // Start of round ban active: Jump block does NOT trigger!
                    // Safely slide the bot away from the jump block towards the arena center
                    Location center = manager.getPlugin() != null ? manager.getPlugin().getGameManager().getCurrentZoneCenter() : null;
                    if (center != null && center.getWorld() != null && center.getWorld().equals(p.getWorld())) {
                        double dxCenter = center.getX() - p.getLocation().getX();
                        double dzCenter = center.getZ() - p.getLocation().getZ();
                        double d = Math.sqrt(dxCenter * dxCenter + dzCenter * dzCenter);
                        if (d > 0.5) {
                            impulseX = (dxCenter / d) * 0.25;
                            impulseZ = (dzCenter / d) * 0.25;
                        }
                    }
                } else {
                    bl.handleJumpBlock(p);
                    if (ai != null) ai.setPadCooldown(600);
                }
            }
            if (bl.isLocationTouchingUpBlock(to) || bl.isUpBlock(locBelow) || bl.isUpBlock(locAt)) {
                bl.handleUpBlock(p);
            }
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(p, from, to));
        }
    }

    public Location findNearestJumpPad(double maxDist) {
        Location best = null;
        double minD = maxDist * maxDist;
        Location myLoc = getBukkitPlayer().getLocation();

        // Direct check of registered jump blocks from RoundsPlugin BlockListener (O(K), fast)
        BlockListener bl = manager.getPlugin().getBlockListener();
        if (bl != null) {
            for (Location loc : bl.getJumpBlocks()) {
                if (loc.getWorld() != null && loc.getWorld().equals(myLoc.getWorld())) {
                    double d = myLoc.distanceSquared(loc);
                    if (d < minD) {
                        minD = d;
                        best = loc;
                    }
                }
            }
        }

        return best;
    }

    public void pickCard(int slotIndex) {
        if (slotIndex < 0 || slotIndex > 4) return;
        Player p = getBukkitPlayer();
        manager.getPlugin().getCardManager().selectCard(p, slotIndex);
        p.closeInventory();
    }

    public void cleanup() {
        if (!ai.isCancelled()) {
            ai.cancel();
        }
    }
}
