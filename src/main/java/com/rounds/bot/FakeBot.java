package com.rounds.bot;

import com.rounds.RoundsPlugin;
import com.rounds.blocks.BlockListener;
import com.rounds.item.GunItem;
import com.rounds.nms.FakeConnection;
import com.rounds.nms.NoopConnection;
import com.rounds.player.PlayerData;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FakeBot {

    private final BotManager manager;
    private Player bukkitPlayer;
    private ServerPlayer nmsPlayer;
    private final BotAI ai;
    private BotStrategy strategy = BotStrategy.BALANCED;
    private int level;
    private com.rounds.teams.TeamManager.GameTeam assignedTeam;
    private double velY = 0.0;
    private double impulseX = 0.0;
    private double impulseZ = 0.0;
    private double currentMoveX = 0.0;
    private double currentMoveZ = 0.0;
    private int padLaunchTicks = 0;
    private int upBlockLiftTicks = 0;
    private Location cachedUpBlockExit = null;
    private int consecutiveJumpAttempts = 0;
    private int jumpStuckCooldown = 0;
    private int jumpAscentTicks = 0;
    private Location lastJumpAttemptLoc = null;
    private boolean attemptNearLimitJumps = false;
    private float aimDriftYaw = 0f;
    private float aimDriftPitch = 0f;
    private float targetDriftYaw = 0f;
    private float targetDriftPitch = 0f;
    private int driftTicks = 0;

    public double getVelY() {
        return velY;
    }

    public double getImpulseX() {
        return impulseX;
    }

    public double getImpulseZ() {
        return impulseZ;
    }

    public boolean isPadLaunched() {
        return padLaunchTicks > 0;
    }

    public FakeBot(BotManager manager, Player bukkitPlayer, ServerPlayer nmsPlayer) {
        this.manager = manager;
        this.bukkitPlayer = bukkitPlayer;
        this.nmsPlayer = nmsPlayer;
        this.level = ThreadLocalRandom.current().nextInt(1, 11); // Random difficulty level 1..10
        resetAttemptDecision();
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
                ensureConnection(sp);
            }
        } catch (Throwable ignored) {}
        return nmsPlayer;
    }

    public static void ensureConnection(ServerPlayer sp) {
        if (sp != null && sp.connection == null) {
            try {
                MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
                NoopConnection noopConn = new NoopConnection(PacketFlow.SERVERBOUND);
                CommonListenerCookie cookie = CommonListenerCookie.createInitial(sp.getGameProfile(), false);
                sp.connection = new FakeConnection(nmsServer, noopConn, sp, cookie);
            } catch (Throwable ignored) {}
        }
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
        this.strategy = strategy != null ? strategy : BotStrategy.AGGRESSIVE_FLANKER;
        if (ai != null && strategy != null) {
            ai.setPlaystyle(BotStrategy.toPlaystyle(strategy.name()));
        }
    }

    public BotAI.BotPlaystyle getPlaystyle() {
        return (ai != null) ? ai.getPlaystyle() : BotAI.BotPlaystyle.AGGRESSIVE_FLANKER;
    }

    public void setPlaystyle(BotAI.BotPlaystyle playstyle) {
        if (ai != null && playstyle != null) {
            ai.setPlaystyle(playstyle);
        }
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(10, level));
        resetAttemptDecision();
        if (manager != null && manager.getPlugin() != null && manager.getPlugin().getGameManager() != null) {
            manager.getPlugin().getGameManager().showNameTagsInGame();
        }
    }

    public void randomizeLevel() {
        this.level = ThreadLocalRandom.current().nextInt(1, 11);
        resetAttemptDecision();
    }

    public void resetAttemptDecision() {
        this.currentMoveX = 0.0;
        this.currentMoveZ = 0.0;
        // High level bots (8-10) never attempt impossible heights; lower bots might risk it
        this.attemptNearLimitJumps = ThreadLocalRandom.current().nextDouble() < ((1.0 - getSkillFactor()) * 0.90);
    }

    public PlayerData getPlayerData() {
        if (manager == null || manager.getPlugin() == null) return null;
        var pdm = manager.getPlugin().getPlayerDataManager();
        return (pdm != null && bukkitPlayer != null) ? pdm.getData(bukkitPlayer.getUniqueId()) : null;
    }

    public double getJumpMultiplier() {
        PlayerData data = getPlayerData();
        if (data == null || data.jumpHeight <= 0) return 1.0;
        return Math.sqrt(Math.max(0.0, 1.0 + data.jumpHeight));
    }

    public int getMaxClimbableBlocks() {
        PlayerData data = getPlayerData();
        double jh = (data != null) ? Math.max(0.0, data.jumpHeight) : 0.0;
        return Math.max(1, (int) Math.floor(1.25 * (1.0 + jh)));
    }

    public boolean considersObstacleClimbable(int obstacleHeight) {
        int maxClimb = getMaxClimbableBlocks();
        return obstacleHeight <= maxClimb;
    }

    public boolean hasParkourTargetAhead(double dirX, double dirZ) {
        return findParkourLandingFloor(dirX, dirZ) != null;
    }

    public double getSpeedMultiplier() {
        PlayerData data = getPlayerData();
        double speedBonus = 0.0;
        if (data != null) {
            speedBonus += data.speed * 0.35 + data.speedBoost * 0.25;
        }
        Player p = getBukkitPlayer();
        if (p != null) {
            var speedEff = p.getPotionEffect(PotionEffectType.SPEED);
            if (speedEff != null) {
                speedBonus += (speedEff.getAmplifier() + 1) * 0.20;
            }
        }
        if (speedBonus <= 0.0) return 1.0;
        double factor = getSkillFactor();
        return 1.0 + speedBonus * (0.50 + 0.50 * factor);
    }

    public double getSkillFactor() {
        return this.level / 10.0;
    }

    public BotAI getAi() {
        return ai;
    }

    public com.rounds.teams.TeamManager.GameTeam getAssignedTeam() {
        if (manager != null && manager.getPlugin() != null && manager.getPlugin().getTeamManager() != null) {
            Player bp = getBukkitPlayer();
            if (bp != null) {
                var tmTeam = manager.getPlugin().getTeamManager().getPlayerTeam(bp.getUniqueId());
                if (tmTeam != null) {
                    this.assignedTeam = tmTeam;
                    return tmTeam;
                }
            }
        }
        return assignedTeam;
    }

    public void setAssignedTeam(com.rounds.teams.TeamManager.GameTeam assignedTeam) {
        this.assignedTeam = assignedTeam;
    }

    public String getTeamName() {
        com.rounds.teams.TeamManager.GameTeam gt = getAssignedTeam();
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
        if (sp == null) return false;
        if (sp.onGround() || sp.verticalCollisionBelow) return true;
        // Strict fallback only when entity is stationary (not falling or leaping)
        if (Math.abs(velY) > 0.08) {
            return false;
        }
        Player p = getBukkitPlayer();
        if (p == null || p.getFallDistance() > 0.05f) return false;
        Location loc = p.getLocation();
        if (loc.getWorld() == null) return false;
        double y = loc.getY();
        double fractional = y - Math.floor(y);
        // If entity is floating mid-block in the air (not resting on block boundary), it cannot be on ground
        if (fractional > 0.08) {
            return false;
        }
        Location below = new Location(loc.getWorld(), loc.getX(), Math.floor(y) - 0.1, loc.getZ());
        return below.getBlock().getType().isSolid();
    }

    public boolean isAirborne() {
        return !isOnGround() && (Math.abs(velY) > 0.15 || getBukkitPlayer().getFallDistance() > 0.2);
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

    public boolean canBlock() {
        Player p = getBukkitPlayer();
        return p != null && !p.isDead() && p.isOnline() && hasGun() && GunItem.canBlock(p.getUniqueId(), getPlayerData());
    }

    public boolean isSawActive() {
        Player p = getBukkitPlayer();
        return p != null && GunItem.isSawActive(p.getUniqueId());
    }

    public void setRotation(float yaw, float pitch) {
        ServerPlayer sp = getNmsPlayer();
        if (sp != null) {
            sp.setRot(yaw, pitch);
            sp.setYHeadRot(yaw);
            sp.setYBodyRot(yaw);
        }
    }

    public void resetPhysics() {
        this.velY = 0.0;
        this.impulseX = 0.0;
        this.impulseZ = 0.0;
        this.padLaunchTicks = 0;
        this.upBlockLiftTicks = 0;
        this.cachedUpBlockExit = null;
        this.consecutiveJumpAttempts = 0;
        this.jumpStuckCooldown = 0;
        this.jumpAscentTicks = 0;
        this.lastJumpAttemptLoc = null;
        ServerPlayer sp = getNmsPlayer();
        if (sp != null) {
            sp.setDeltaMovement(Vec3.ZERO);
        }
        Player p = getBukkitPlayer();
        if (p != null) {
            p.setFallDistance(0f);
        }
    }

    public void applyJumpPad(double launchHeight) {
        this.velY = launchHeight * 0.2;
        this.padLaunchTicks = 50;
        Player p = getBukkitPlayer();
        if (p != null) {
            Location safeTarget = (ai != null) ? ai.findSafeLandingTarget(p) : null;
            if (safeTarget != null) {
                double dx = safeTarget.getX() - p.getLocation().getX();
                double dz = safeTarget.getZ() - p.getLocation().getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 1.2) {
                    // Strong forward impulse (~0.38 blocks/tick) directed towards safe map interior
                    double normX = dx / dist;
                    double normZ = dz / dist;
                    this.impulseX = normX * 0.38;
                    this.impulseZ = normZ * 0.38;

                    float launchYaw = (float) Math.toDegrees(Math.atan2(-normX, normZ));
                    setRotation(launchYaw, -10f);
                } else {
                    double rad = Math.toRadians(-p.getLocation().getYaw());
                    this.impulseX = Math.sin(rad) * 0.40;
                    this.impulseZ = Math.cos(rad) * 0.40;
                }
            } else {
                double rad = Math.toRadians(-p.getLocation().getYaw());
                this.impulseX = Math.sin(rad) * 0.40;
                this.impulseZ = Math.cos(rad) * 0.40;
            }
        }
        if (ai != null) {
            ai.onJumpPadLaunched();
        }
    }

    public void applyUpBlock(double speed) {
        this.velY = speed;
        this.upBlockLiftTicks = 8;
        Player p = getBukkitPlayer();
        if (p != null) {
            Location exit = findUpBlockExitPlatform(p);
            if (exit != null) {
                this.cachedUpBlockExit = exit;
                double dx = exit.getX() - p.getLocation().getX();
                double dz = exit.getZ() - p.getLocation().getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0.25) {
                    double normX = dx / dist;
                    double normZ = dz / dist;
                    // Apply gentle guiding horizontal impulse towards upper exit platform
                    this.impulseX = normX * 0.28;
                    this.impulseZ = normZ * 0.28;
                    float exitYaw = (float) Math.toDegrees(Math.atan2(-normX, normZ));
                    setRotation(exitYaw, 0f);
                }
            }
        }
    }

    public Location findUpBlockExitPlatform(Player p) {
        Location loc = p.getLocation();
        World world = loc.getWorld();
        if (world == null) return null;
        BlockListener bl = (manager != null && manager.getPlugin() != null) ? manager.getPlugin().getBlockListener() : null;

        Location bestPlatform = null;
        double bestDistSq = Double.MAX_VALUE;

        // Check radial directions around current position (distances 1.2 to 2.6m, vertical delta -0.5 to +2.5m)
        double[] checkDistances = {1.2, 1.8, 2.6};
        for (double d : checkDistances) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 45) {
                double rad = Math.toRadians(angleDeg);
                double cx = loc.getX() + Math.cos(rad) * d;
                double cz = loc.getZ() + Math.sin(rad) * d;

                for (double dy = -0.5; dy <= 2.5; dy += 1.0) {
                    Location candFloor = new Location(world, cx, Math.floor(loc.getY() + dy), cz);
                    Location candFeet = candFloor.clone().add(0, 1.0, 0);
                    Location candHead = candFloor.clone().add(0, 2.0, 0);

                    if (candFloor.getBlock().getType().isSolid()
                            && !candFeet.getBlock().getType().isSolid()
                            && !candHead.getBlock().getType().isSolid()) {
                        if (bl != null && (bl.isJumpBlock(candFloor) || bl.isUpBlock(candFloor))) {
                            continue;
                        }
                        double dSq = candFloor.distanceSquared(loc);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestPlatform = candFloor.clone().add(0, 1.0, 0);
                        }
                    }
                }
            }
            if (bestPlatform != null) break;
        }
        return bestPlatform;
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

        // Smooth humanized aim drift (replaces 20Hz vibrating jitter with natural hand drift)
        if (inaccuracyDeg > 0.05f) {
            if (--driftTicks <= 0) {
                targetDriftYaw = ((ThreadLocalRandom.current().nextFloat() * 2f) - 1f) * inaccuracyDeg;
                targetDriftPitch = ((ThreadLocalRandom.current().nextFloat() * 2f) - 1f) * (inaccuracyDeg * 0.6f);
                driftTicks = ThreadLocalRandom.current().nextInt(8, 16);
            }
            aimDriftYaw += (targetDriftYaw - aimDriftYaw) * 0.18f;
            aimDriftPitch += (targetDriftPitch - aimDriftPitch) * 0.18f;
            idealYaw += aimDriftYaw;
            idealPitch += aimDriftPitch;
        } else {
            aimDriftYaw = 0f;
            aimDriftPitch = 0f;
        }

        // Smooth rotation interpolation with humanized acceleration curve (avoids 1-tick robotic snaps)
        float curYaw = myLoc.getYaw();
        float curPitch = myLoc.getPitch();

        float dyaw = idealYaw - curYaw;
        while (dyaw < -180.0f) dyaw += 360.0f;
        while (dyaw > 180.0f) dyaw -= 360.0f;

        float turnFactor = (0.32f + 0.16f * (float) getSkillFactor()) * 1.10f;
        float stepYaw = dyaw * turnFactor;
        float minYawStep = Math.min(Math.abs(dyaw), (1.2f + 1.2f * (float) getSkillFactor()) * 1.10f);
        if (Math.abs(stepYaw) < minYawStep) stepYaw = Math.signum(dyaw) * minYawStep;
        if (stepYaw > maxTurnDeg) stepYaw = maxTurnDeg;
        if (stepYaw < -maxTurnDeg) stepYaw = -maxTurnDeg;

        float dpitch = idealPitch - curPitch;
        float stepPitch = dpitch * (turnFactor * 0.85f);
        float minPitchStep = Math.min(Math.abs(dpitch), (0.8f + 0.8f * (float) getSkillFactor()) * 1.10f);
        if (Math.abs(stepPitch) < minPitchStep) stepPitch = Math.signum(dpitch) * minPitchStep;
        float maxPitchDeg = maxTurnDeg * 0.65f;
        if (stepPitch > maxPitchDeg) stepPitch = maxPitchDeg;
        if (stepPitch < -maxPitchDeg) stepPitch = -maxPitchDeg;

        float newYaw = curYaw + stepYaw;
        float newPitch = curPitch + stepPitch;

        sp.setRot(newYaw, newPitch);
        sp.setYHeadRot(newYaw);

        // Natural player body rotation: lags behind head rotation unless turning sharply
        float bodyYaw = sp.yBodyRot;
        float bodyDiff = newYaw - bodyYaw;
        while (bodyDiff < -180.0f) bodyDiff += 360.0f;
        while (bodyDiff > 180.0f) bodyDiff -= 360.0f;
        if (Math.abs(bodyDiff) > 30.0f) {
            sp.setYBodyRot(bodyYaw + (bodyDiff > 0 ? bodyDiff - 30.0f : bodyDiff + 30.0f));
        }
    }

    public void moveRel(double dx, double dy, double dz) {
        Player p = getBukkitPlayer();
        ServerPlayer sp = getNmsPlayer();
        if (p.isDead() || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) return;
        if (manager != null && manager.getPlugin() != null && manager.getPlugin().getGameManager().getState() == com.rounds.game.GameManager.GameState.CARDS) return;
        if (com.rounds.listener.LegendaryEffects.isBulletTimeActiveFor(p.getUniqueId())
                || p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW)) {
            dx *= 0.15;
            dz *= 0.15;
        }
        BlockListener bl = (manager != null && manager.getPlugin() != null) ? manager.getPlugin().getBlockListener() : null;

        // Tick and expire active potion effects for FakeBot (e.g. GLOWING, SLOW)
        if (!p.getActivePotionEffects().isEmpty()) {
            for (org.bukkit.potion.PotionEffect eff : new java.util.ArrayList<>(p.getActivePotionEffects())) {
                if (eff.getDuration() <= 1) {
                    p.removePotionEffect(eff.getType());
                } else {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(eff.getType(), eff.getDuration() - 1, eff.getAmplifier(), eff.isAmbient(), eff.hasParticles(), eff.hasIcon()), true);
                }
            }
        }

        // 1. Pick up external vertical impulses (jump pads, up blocks, explosions)
        boolean onGround = isOnGround();
        Vec3 delta = sp.getDeltaMovement();
        if (Math.abs(delta.y) > 0.20) {
            velY = delta.y;
        }
        double deltaHorizSq = delta.x * delta.x + delta.z * delta.z;
        if (deltaHorizSq > 0.04) {
            impulseX += delta.x;
            impulseZ += delta.z;
        }

        if (padLaunchTicks > 0) {
            padLaunchTicks--;
        }
        if (upBlockLiftTicks > 0) {
            upBlockLiftTicks--;
        }

        // 2. Ground check (do not abort pad launch or up-block lift while ascending or over hazard blocks)
        boolean padActive = padLaunchTicks > 0 || (upBlockLiftTicks > 0 && velY > 0.0);
        if (onGround && velY <= 0.0 && (!padActive || padLaunchTicks < 30)) {
            boolean onHazard = (bl != null) && (bl.isJumpBlock(p.getLocation()) || bl.isJumpBlock(p.getLocation().subtract(0, 0.5, 0)));
            if (!onHazard) {
                if (padActive && ai != null && padLaunchTicks > 0) {
                    ai.onSafeLanding();
                }
                padLaunchTicks = 0;
                upBlockLiftTicks = 0;
                cachedUpBlockExit = null;
                velY = 0.0;
                impulseX = 0.0;
                impulseZ = 0.0;
            } else {
                velY = Math.max(velY - 0.08, -3.92) * 0.98;
            }
        } else {
            velY = Math.max(velY - 0.08, -3.92) * 0.98;
        }

        if (jumpAscentTicks > 0) {
            jumpAscentTicks--;
            if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
                double moveLen = Math.sqrt(dx * dx + dz * dz);
                impulseX += (dx / moveLen) * 0.04;
                impulseZ += (dz / moveLen) * 0.04;
            }
        }

        // Smooth step down: if walking down stairs/slope, smoothly descend to the lower block
        if (!onGround && velY <= 0.0 && padLaunchTicks <= 0 && jumpAscentTicks <= 0 && upBlockLiftTicks <= 0) {
            Location checkStepBelow = p.getLocation().clone().subtract(0, 1.1, 0);
            if (checkStepBelow.getBlock().getType().isSolid()) {
                velY = -0.32;
            }
        }

        // Steer towards upper platform if descending above up-block column (prevents infinite elevator loop)
        if (bl != null && !onGround && velY <= 0.0 && cachedUpBlockExit != null) {
            Location curLoc = p.getLocation();
            Location down1 = curLoc.clone().subtract(0, 1.0, 0);
            Location down2 = curLoc.clone().subtract(0, 2.0, 0);
            if (bl.isUpBlock(down1) || bl.isUpBlock(down2)) {
                double ex = cachedUpBlockExit.getX() - curLoc.getX();
                double ez = cachedUpBlockExit.getZ() - curLoc.getZ();
                double edist = Math.sqrt(ex * ex + ez * ez);
                if (edist > 0.2) {
                    impulseX += (ex / edist) * 0.22;
                    impulseZ += (ez / edist) * 0.22;
                }
            }
        }

        // 3. Ground movement: obstacle auto-jump & parkour across gaps and platforms
        if (jumpStuckCooldown > 0) {
            jumpStuckCooldown--;
        }

        if (onGround && velY <= 0.0 && (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01)) {
            Location loc = p.getLocation();
            double moveLen = Math.sqrt(dx * dx + dz * dz);
            double dirX = dx / moveLen;
            double dirZ = dz / moveLen;
            double stepDist = Math.max(moveLen * 2.0, 0.72);
            Location aheadFeet = loc.clone().add(dirX * stepDist, 0.2, dirZ * stepDist);
            Location headSpace = loc.clone().add(0, 2.1, 0);

            // Anti-stacking: check if bot is standing on another player/bot, or if another player is directly ahead
            boolean entityAhead = false;
            if (loc.getWorld() != null) {
                for (Player other : loc.getWorld().getPlayers()) {
                    if (other.getUniqueId().equals(p.getUniqueId()) || other.getGameMode() == GameMode.SPECTATOR || other.isDead()) continue;
                    Location oLoc = other.getLocation();
                    double dxOther = oLoc.getX() - loc.getX();
                    if (Math.abs(dxOther) > 1.2) continue;
                    double dzOther = oLoc.getZ() - loc.getZ();
                    if (Math.abs(dzOther) > 1.2) continue;
                    double dyOther = oLoc.getY() - loc.getY();

                    // If standing on another player's head (other player directly below us), push off sideways immediately!
                    if (dyOther >= -1.95 && dyOther <= -0.4 && (dxOther * dxOther + dzOther * dzOther) < 0.64) {
                        double pushAng = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                        impulseX += Math.cos(pushAng) * 0.32;
                        impulseZ += Math.sin(pushAng) * 0.32;
                    }

                    // Player directly in front in direction of movement
                    if (Math.abs(dyOther) <= 1.2 && (dxOther * dirX + dzOther * dirZ) > 0.1 && (dxOther * dxOther + dzOther * dzOther) < 1.0) {
                        entityAhead = true;
                    }
                }
            }

            // Jump blocks are hazards: NEVER treat them as walkable steps to auto-jump onto!
            boolean hazardAhead = (bl != null) && (bl.isJumpBlock(aheadFeet.getBlock().getLocation())
                    || bl.isJumpBlock(aheadFeet.clone().subtract(0, 1.0, 0).getBlock().getLocation())
                    || bl.isLocationTouchingJumpBlock(aheadFeet));

            // Only auto-jump over actual solid blocks; never treat other players or hazard pads as jumpable steps!
            Location aheadEye = loc.clone().add(dirX * stepDist, 1.8, dirZ * stepDist);
            boolean wallAtEye = aheadEye.getBlock().getType().isSolid();

            int obstacleHeight = 0;
            while (obstacleHeight < 4 && aheadFeet.clone().add(0, obstacleHeight, 0).getBlock().getType().isSolid() && !hazardAhead) {
                obstacleHeight++;
            }
            if (obstacleHeight == 0 && sp.horizontalCollision && !hazardAhead && !wallAtEye) {
                obstacleHeight = 1;
            }

            boolean solidBlockAhead = (obstacleHeight > 0) && !hazardAhead && !wallAtEye;
            boolean stepAhead = solidBlockAhead || (sp.horizontalCollision && !entityAhead && !hazardAhead && !wallAtEye);

            boolean willAttempt = (obstacleHeight > 0) && considersObstacleClimbable(obstacleHeight) && !wallAtEye;
            boolean landingClear = (obstacleHeight > 0)
                    && !aheadFeet.clone().add(0, obstacleHeight, 0).getBlock().getType().isSolid()
                    && !aheadFeet.clone().add(0, obstacleHeight + 1.0, 0).getBlock().getType().isSolid();
            Location overheadObstacle = loc.clone().add(0, 2.1 + Math.max(0, obstacleHeight - 1) * 0.8, 0);

            if (jumpStuckCooldown <= 0 && stepAhead && willAttempt && landingClear && !wallAtEye
                    && !headSpace.getBlock().getType().isSolid() && !overheadObstacle.getBlock().getType().isSolid()) {
                if (lastJumpAttemptLoc != null && loc.distanceSquared(lastJumpAttemptLoc) < 0.05) {
                    consecutiveJumpAttempts++;
                } else if (lastJumpAttemptLoc != null && loc.getY() >= lastJumpAttemptLoc.getY() + 0.4) {
                    consecutiveJumpAttempts = 0; // Successfully climbed up step!
                } else {
                    consecutiveJumpAttempts = 1;
                }
                lastJumpAttemptLoc = loc.clone();

                if (consecutiveJumpAttempts >= 3) {
                    // Bot jumped 3 times against an obstacle without gaining height
                    // Abort jumps, apply cooldown, and trigger AI detour
                    jumpStuckCooldown = 25;
                    consecutiveJumpAttempts = 0;
                    this.attemptNearLimitJumps = false;
                    if (ai != null) {
                        ai.onCornerStuck(loc);
                    }
                } else {
                    double jumpMult = getJumpMultiplier();
                    velY = 0.42 * jumpMult; // Jump velocity scaled by jump cards
                    jumpAscentTicks = Math.max(8, (int) Math.round(8 * jumpMult));
                    // Solid forward step impulse across all levels to smoothly clear block steps
                    double forwardImpulse = (0.24 + 0.08 * (jumpMult - 1.0)) * Math.sqrt(getSpeedMultiplier());
                    impulseX += dirX * forwardImpulse;
                    impulseZ += dirZ * forwardImpulse;
                }
            } else if (!stepAhead && !headSpace.getBlock().getType().isSolid()) {
                // Check if approaching a 1-4 block gap or parkour platform ahead (or leaping over hazard/jump block)
                Location edgeCheck = loc.clone().add(dirX * 0.6, -0.4, dirZ * 0.6);
                Location edgeDrop = loc.clone().add(dirX * 0.6, -1.4, dirZ * 0.6);
                boolean isEdge = !edgeCheck.getBlock().getType().isSolid() && !edgeDrop.getBlock().getType().isSolid();
                if (isEdge || hazardAhead) {
                    Location bestLandingFloor = findParkourLandingFloor(dirX, dirZ);

                    if (bestLandingFloor != null) {
                        double factor = getSkillFactor();
                        double speedMult = getSpeedMultiplier();
                        double jumpMult = getJumpMultiplier();
                        if (ai != null) {
                            ai.recordJumpGap(loc, bestLandingFloor);
                        }
                        double errorChance = (ai != null) ? ai.getJumpErrorChance() : 0.0;
                        boolean jumpError = ThreadLocalRandom.current().nextDouble() < errorChance;
                        if (!jumpError) {
                            // Scale leap velocity with jump and speed multipliers
                            velY = ((bestLandingFloor.getY() > loc.getY()) ? 0.46 : 0.42) * jumpMult;
                            jumpAscentTicks = Math.max(8, (int) Math.round(8 * jumpMult));
                            double sprintImpulse = (0.16 + 0.08 * factor) * speedMult;
                            impulseX += dirX * sprintImpulse;
                            impulseZ += dirZ * sprintImpulse;
                        } else {
                            velY = 0.22 * jumpMult; // Under-jump error: weak jump resulting in fall
                            jumpAscentTicks = 4;
                            impulseX += dirX * 0.08;
                            impulseZ += dirZ * 0.08;
                        }
                    }
                }
            }
        }

        double finalVelY = (onGround && velY <= 0.0) ? -0.0784 : velY;

        // Humanized horizontal movement inertia (smooth acceleration and deceleration)
        double accel = onGround ? 0.42 : 0.28;
        currentMoveX += (dx - currentMoveX) * accel;
        currentMoveZ += (dz - currentMoveZ) * accel;
        if (Math.abs(currentMoveX) < 0.001) currentMoveX = 0.0;
        if (Math.abs(currentMoveZ) < 0.001) currentMoveZ = 0.0;

        double moveX = currentMoveX + impulseX;
        double moveZ = currentMoveZ + impulseZ;
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

        // Turn body towards movement direction when running
        if (Math.abs(moveX) > 0.04 || Math.abs(moveZ) > 0.04) {
            sp.setYBodyRot(sp.getYHeadRot());
        }

        // 4. Trigger Jump Pads and Up Blocks directly on landing / stepping (percentage damage & lift)
        Location locBelow = to.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
        Location locAt = to.getBlock().getLocation();
        if (bl != null) {
            checkJumpPadAnticipation(bl, to, dx, dz);

            boolean touchingJump = bl.isLocationTouchingJumpBlock(to) || bl.isJumpBlock(locBelow) || bl.isJumpBlock(locAt);
            if (touchingJump) {
                bl.handleJumpBlock(p);
                if (ai != null) ai.setPadCooldown(600);
            }
            if (bl.isLocationTouchingUpBlock(to) || bl.isUpBlock(locBelow) || bl.isUpBlock(locAt)) {
                bl.handleUpBlock(p);
            }
        }

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Bukkit.getPluginManager().callEvent(new PlayerMoveEvent(p, from, to));
        }
    }

    public Location findParkourLandingFloor(double dirX, double dirZ) {
        Player p = getBukkitPlayer();
        if (p == null) return null;
        Location loc = p.getLocation();
        BlockListener bl = (manager != null && manager.getPlugin() != null) ? manager.getPlugin().getBlockListener() : null;
        double factor = getSkillFactor();
        double speedMult = getSpeedMultiplier();
        double jumpMult = getJumpMultiplier();
        int maxClimb = getMaxClimbableBlocks();

        double baseParkourDist = 3.6 + factor * 1.2;
        double maxParkourDist = baseParkourDist * Math.sqrt(speedMult) * Math.sqrt(jumpMult);

        for (double gapDist = 1.0; gapDist <= maxParkourDist; gapDist += 0.5) {
            double maxUp = 1.0 + (maxClimb - 1) * 1.0;
            double[] checkDys = (maxClimb > 1) ? new double[]{maxUp, 1.0, 0.0, -1.0, -2.0} : new double[]{1.0, 0.0, -1.0, -2.0};
            for (double cdy : checkDys) {
                Location landingFloor = loc.clone().add(dirX * gapDist, cdy, dirZ * gapDist);
                Location landingBody = landingFloor.clone().add(0, 1.0, 0);
                Location landingHead = landingFloor.clone().add(0, 2.0, 0);
                if (landingFloor.getBlock().getType().isSolid() && !landingBody.getBlock().getType().isSolid() && !landingHead.getBlock().getType().isSolid()) {
                    if (bl == null || (!bl.isJumpBlock(landingFloor.getBlock().getLocation()) && !bl.isUpBlock(landingFloor.getBlock().getLocation()) && !bl.isLocationTouchingJumpBlock(landingFloor))) {
                        return landingFloor;
                    }
                }
            }
        }
        return null;
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

    private void checkJumpPadAnticipation(BlockListener bl, Location currentLoc, double dx, double dz) {
        int minLevel = manager != null && manager.getPlugin() != null ? manager.getPlugin().getRoundsConfig().getBotJumpPadParryMinLevel() : 5;
        if (level < minLevel) return;
        Player p = getBukkitPlayer();
        if (p == null || !canBlock() || GunItem.isShieldActive(p.getUniqueId())) return;

        double chance = manager != null && manager.getPlugin() != null ? manager.getPlugin().getRoundsConfig().getBotJumpPadParryChance() : 0.50;

        // 1. Vertical descent anticipation (simulates falling trajectory 10-20 ticks ahead)
        if (velY < -0.05) {
            double simY = currentLoc.getY();
            double simVelY = velY;
            double simX = currentLoc.getX();
            double simZ = currentLoc.getZ();
            for (int tick = 1; tick <= 20; tick++) {
                simVelY = Math.max(simVelY - 0.08, -3.92) * 0.98;
                simY += simVelY;
                simX += dx;
                simZ += dz;
                Location checkLoc = new Location(currentLoc.getWorld(), simX, simY, simZ);
                Location checkBelow = checkLoc.clone().subtract(0, 0.5, 0);
                if (bl.isLocationTouchingJumpBlock(checkLoc) || bl.isJumpBlock(checkBelow) || bl.isJumpBlock(checkLoc)) {
                    if (tick >= 10 && tick <= 20) {
                        if (ThreadLocalRandom.current().nextDouble() < chance) {
                            block();
                        }
                    }
                    return;
                }
            }
        }

        // 2. Horizontal approach anticipation (10-20 ticks ahead)
        double moveLen = Math.sqrt(dx * dx + dz * dz);
        if (moveLen > 0.01 && isOnGround()) {
            double dirX = dx / moveLen;
            double dirZ = dz / moveLen;
            for (int tick = 10; tick <= 20; tick += 2) {
                double testDist = moveLen * tick;
                Location stepLoc = currentLoc.clone().add(dirX * testDist, 0, dirZ * testDist);
                Location stepBelow = stepLoc.clone().subtract(0, 0.8, 0);
                if (bl.isLocationTouchingJumpBlock(stepLoc) || bl.isJumpBlock(stepBelow) || bl.isJumpBlock(stepLoc)) {
                    if (ThreadLocalRandom.current().nextDouble() < chance) {
                        block();
                    }
                    return;
                }
            }
        }
    }

    public void pickCard(int slotIndex) {
        if (slotIndex < 0 || slotIndex > 4) return;
        Player p = getBukkitPlayer();
        manager.getPlugin().getCardManager().selectCard(p, slotIndex);
        p.closeInventory();
    }

    public void cleanup() {
        if (ai != null) {
            if (!ai.isCancelled()) {
                ai.cancel();
            }
            ai.cleanup();
        }
        this.bukkitPlayer = null;
        this.nmsPlayer = null;
    }
}
