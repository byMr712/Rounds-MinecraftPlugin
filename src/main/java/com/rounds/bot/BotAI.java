package com.rounds.bot;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BotAI extends BukkitRunnable {

    public enum CombatTactic {
        RUSH,
        RETREAT,
        STRAFE,
        SNIPE,
        SHIELD,
        BALANCED
    }

    private final FakeBot bot;
    private int tickCount = 0;
    private int strafeDir = 1;
    private int dirTicks = 0;
    private int shootCooldown = 0;
    private int padCooldown = 0;

    // Realistic Vision and Pursuit Tracking (No wallhacks)
    private Player currentTarget = null;
    private Location lastKnownTargetLoc = null;
    private int searchTicks = 0;
    private float searchScanYaw = 0f;
    private boolean isPursuing = false;

    // Smart Tactical Behaviors (Incoming Fire Reaction, Cover Seeking, Ambush Trap)
    private long lastAttackedTime = 0;
    private Location coverLoc = null;
    private boolean isSeekingCover = false;
    private boolean isAmbushing = false;
    private int ambushTicks = 0;

    // Patrol exploration navigation
    private float patrolHeading = Float.NaN;
    private int patrolStuckTicks = 0;
    private Location lastPatrolPos = null;

    public BotAI(FakeBot bot) {
        this.bot = bot;
    }

    private boolean isCombatAllowed() {
        RoundsPlugin plugin = bot.getPlugin();
        if (plugin == null) return true;
        if (plugin.getRoundsConfig().isFreeplay()) {
            return plugin.getGameManager().isGameStarted();
        }
        return plugin.getGameManager().getState() == GameManager.GameState.PLAYING;
    }

    /**
     * Standard smart behavior efficiency factor across difficulty tiers:
     * Level 1-2: -50% (50% chance)
     * Level 3-4: -40% (60% chance)
     * Level 5-6: -30% (70% chance)
     * Level 6-7: -30% (70% chance)
     * Level 8-9: -20% (80% chance)
     * Level 10:  -10% (90% chance)
     */
    public double getSmartBehaviorChance() {
        int lvl = bot.getLevel();
        if (lvl <= 2) return 0.50;
        if (lvl <= 4) return 0.60;
        if (lvl <= 7) return 0.70;
        if (lvl <= 9) return 0.80;
        return 0.90;
    }

    public double getPursuitChance() {
        return getSmartBehaviorChance();
    }

    /**
     * Realistic vision cone (130 degrees), 40m distance, 2.5m footstep proximity, and block raycast LOS.
     */
    public boolean canSee(Player botPlayer, Player target) {
        if (target == null || !target.isOnline() || target.isDead()) return false;
        if (target.getGameMode() == GameMode.SPECTATOR || target.getGameMode() == GameMode.CREATIVE) return false;

        Location botEye = botPlayer.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        if (botEye.getWorld() == null || !botEye.getWorld().equals(targetEye.getWorld())) return false;

        double distSq = botEye.distanceSquared(targetEye);
        if (distSq > 40.0 * 40.0) return false; // Beyond vision range (40 blocks)

        // Close proximity (under 2.5m): audible footsteps or proximity allows detection if raycast is clear
        if (distSq < 2.5 * 2.5) {
            return botPlayer.hasLineOfSight(target);
        }

        // Field of View check: 130 degree viewing cone (65 degrees from look vector)
        Vector dirToTarget = targetEye.toVector().subtract(botEye.toVector()).normalize();
        Vector lookDir = botEye.getDirection().normalize();
        double angle = Math.toDegrees(dirToTarget.angle(lookDir));
        if (angle > 65.0) {
            return false;
        }

        return botPlayer.hasLineOfSight(target);
    }

    /**
     * Reaction to incoming fire / damage source.
     * Turns towards the attacker with angular precision scaled by difficulty.
     */
    public void onAttacked(Location origin, UUID attackerId) {
        if (origin == null) return;
        long now = System.currentTimeMillis();
        if (now - lastAttackedTime < 250) return; // Debounce rapid hits
        lastAttackedTime = now;

        double smartChance = getSmartBehaviorChance();

        // 1. Chance to react towards the incoming fire direction
        if (ThreadLocalRandom.current().nextDouble() > smartChance) {
            return; // Lower level bot panics or ignores shot direction
        }

        Player player = bot.getBukkitPlayer();
        Location myLoc = player.getLocation();

        double dx = origin.getX() - myLoc.getX();
        double dy = origin.getY() - myLoc.getY();
        double dz = origin.getZ() - myLoc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float idealYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float idealPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(0.001, distXZ)));

        // Angular error based on difficulty: level 10 has +-4.5 deg error, level 1-2 has up to +-45 deg error
        float maxError = (float) ((1.0 - smartChance) * 90.0);
        float yawError = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * (maxError * 0.5f);
        float pitchError = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * (maxError * 0.3f);

        float reactYaw = idealYaw + yawError;
        float reactPitch = idealPitch + pitchError;

        bot.getNmsPlayer().setRot(reactYaw, reactPitch);
        bot.getNmsPlayer().setYHeadRot(reactYaw);

        lastKnownTargetLoc = origin.clone();
        searchTicks = 0;
        isPursuing = true;

        if (attackerId != null) {
            Player attacker = Bukkit.getPlayer(attackerId);
            if (attacker != null && canSee(player, attacker)) {
                currentTarget = attacker;
            }
        }
    }

    @Override
    public void run() {
        Player player = bot.getBukkitPlayer();
        if (player.isDead() || !player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isCombatAllowed()) {
            if (tickCount % 40 == 0) {
                float rndYaw = player.getLocation().getYaw() + (ThreadLocalRandom.current().nextFloat() * 40f - 20f);
                bot.getNmsPlayer().setRot(rndYaw, 0f);
            }
            tickCount++;
            return;
        }

        // 1. Vision Update: check if current target is still in direct sight
        boolean targetCurrentlyVisible = (currentTarget != null) && canSee(player, currentTarget);

        if (targetCurrentlyVisible) {
            lastKnownTargetLoc = currentTarget.getLocation().clone();
            searchTicks = 0;
            isPursuing = true;
        } else {
            // Check if any other enemy is visible in vision cone
            Player spotted = findVisibleTarget();
            if (spotted != null) {
                currentTarget = spotted;
                lastKnownTargetLoc = spotted.getLocation().clone();
                targetCurrentlyVisible = true;
                searchTicks = 0;
                isPursuing = true;
            }
        }

        double smartChance = getSmartBehaviorChance();
        double factor = bot.getSkillFactor();
        PlayerData data = bot.getPlugin().getPlayerDataManager().getData(player.getUniqueId());
        double healthRatio = player.getHealth() / bot.getMaxHealth();

        // 2. Ambush Trap handling (waiting around corner with pre-aim)
        if (isAmbushing) {
            ambushTicks++;
            if (ambushTicks > 60 || (targetCurrentlyVisible && lastKnownTargetLoc != null && player.getLocation().distance(lastKnownTargetLoc) < 5.0)) {
                isAmbushing = false;
            } else {
                // Hold position behind corner, pre-aim at corner where enemy is expected to emerge
                if (lastKnownTargetLoc != null) {
                    bot.aimAt(lastKnownTargetLoc.clone().add(0, 1.4, 0), 45.0f, 0.5f);
                }

                // If bot has Empower card, pre-charge shield!
                if (data != null && data.empower > 0 && !data.shieldActive && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
                    bot.block();
                }

                if (targetCurrentlyVisible) {
                    // AMBUSH TRIGGERED: instant point-blank shot!
                    if (bot.hasGun()) {
                        bot.shoot();
                        shootCooldown = 4;
                    }
                    isAmbushing = false;
                } else {
                    tickCooldowns();
                    return;
                }
            }
        }

        // 3. Seeking Cover / Hiding (to heal or reload)
        if (isSeekingCover && coverLoc != null) {
            double cdx = coverLoc.getX() - player.getLocation().getX();
            double cdz = coverLoc.getZ() - player.getLocation().getZ();
            double cdist = Math.sqrt(cdx * cdx + cdz * cdz);

            if (cdist > 0.8) {
                bot.moveRel((cdx / cdist) * 0.36, 0.0, (cdz / cdist) * 0.36);
                if (lastKnownTargetLoc != null) {
                    bot.aimAt(lastKnownTargetLoc, 20.0f, 2.0f);
                }
                tickCooldowns();
                return;
            } else {
                // Reached cover!
                isSeekingCover = false;

                // Heal on block behind cover if card allows
                if (data != null && data.heal > 0 && healthRatio < 0.85) {
                    bot.block();
                }

                // Chameleon: stand still behind cover to turn invisible
                if (data != null && data.chameleon > 0 && !targetCurrentlyVisible) {
                    tickCooldowns();
                    return;
                }

                // Set up an Ambush Trap around the corner!
                if (ThreadLocalRandom.current().nextDouble() < smartChance) {
                    isAmbushing = true;
                    ambushTicks = 0;
                    tickCooldowns();
                    return;
                }
            }
        }

        // 4. If target is NOT directly visible: handle pursuit or patrol search mode
        if (!targetCurrentlyVisible) {
            if (isPursuing && lastKnownTargetLoc != null) {
                if (searchTicks == 0) {
                    boolean keepChasing = ThreadLocalRandom.current().nextDouble() < getPursuitChance();
                    if (!keepChasing) {
                        isPursuing = false;
                        currentTarget = null;
                        lastKnownTargetLoc = null;
                    }
                }
            }

            if (isPursuing && lastKnownTargetLoc != null && searchTicks < 80) {
                searchTicks++;
                executePursuit(player, lastKnownTargetLoc);
                tickCooldowns();
                return;
            } else {
                isPursuing = false;
                currentTarget = null;
                lastKnownTargetLoc = null;
                executeSearchPatrol(player);
                tickCooldowns();
                return;
            }
        }

        // 5. Target IS visible: Tactical combat execution
        Player target = currentTarget;
        boolean hasLos = player.hasLineOfSight(target);

        Location p1Loc = player.getLocation();
        Location p2Loc = target.getLocation();

        double dx = p2Loc.getX() - p1Loc.getX();
        double dy = p2Loc.getY() - p1Loc.getY();
        double dz = p2Loc.getZ() - p1Loc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double lenXZ = (distXZ > 0.05) ? distXZ : 1.0;
        double u1_x = dx / lenXZ;
        double u1_z = dz / lenXZ;
        double p1_x = -u1_z;
        double p1_z = u1_x;

        // Tactical decision: check if bot should seek cover to heal or reload
        boolean needsCover = (healthRatio < 0.35 || (data != null && data.ammo <= 0 && data.reloadSpeed > 0) || (data != null && data.heal > 0 && healthRatio < 0.65));
        if (needsCover && !isSeekingCover && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            Location cover = findCoverLocation(player, p2Loc);
            if (cover != null) {
                coverLoc = cover;
                isSeekingCover = true;
                tickCooldowns();
                return;
            }
        }

        // Accuracy: 1.2 deg at lvl 10, 12.0 deg at lvl 1 (-90% precision)
        float aimInaccuracy = (float) (1.2f / factor);
        // Turning tracking speed: 45 deg/tick at lvl 10, 13.5 deg/tick at lvl 1
        float turnSpeed = (float) (10.0f + 35.0f * factor);
        // Minimum shooting cooldown: 4 ticks at lvl 10, up to 18 ticks at lvl 1
        int minShootCooldown = 4 + (int) ((1.0 - factor) * 14.0);
        // Evasion strafe agility: 0.35 at lvl 10, 0.08 at lvl 1 (-90% evasion)
        double strafeSpeed = 0.05 + 0.30 * factor;
        // Direction change / juking: every 8-12 ticks at lvl 10, 40+ ticks at lvl 1 (predictable)
        int jukeThreshold = (int) (8 + (1.0 - factor) * 32.0);

        // Maximum shooting engagement distance: lvl 1 won't shoot beyond 13m, lvl 10 shoots up to 40m
        double maxShootDist = 10.0 + 30.0 * factor;
        // Distance shooting chance: at dist > 8m, chance to fire scales with factor (-90% at lvl 1)
        boolean distanceShootAllowed = (dist3D <= 8.0) || (ThreadLocalRandom.current().nextDouble() < factor);
        boolean canShootAtTarget = hasLos && bot.hasGun() && (dist3D <= maxShootDist) && distanceShootAllowed;

        // Card ability synergy: Empower next shot right before firing
        if (data != null && data.empower > 0 && !data.shieldActive && shootCooldown <= 1 && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            bot.block();
        }

        // Card ability synergy: Heal on block when damaged
        if (data != null && data.heal > 0 && healthRatio < 0.75 && (tickCount % 20 == 0) && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            bot.block();
        }

        // Card ability synergy: Close-range Shockwave or Saw blast
        if (data != null && (data.shockwave > 0 || data.saw > 0) && dist3D < 4.5 && (tickCount % 15 == 0) && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            bot.block();
        }

        // Determine tactic
        CombatTactic tactic;

        // Card ability synergy: Grow card prefers sniping distance
        if (data != null && data.grow > 0 && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            tactic = CombatTactic.SNIPE;
        } else if (data != null && data.lifestealAura > 0 && dist3D > 4.5 && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            // Lifesteal Aura card prefers close proximity (3.5 - 4.5m)
            tactic = CombatTactic.RUSH;
        } else {
            boolean smartRetreat = (healthRatio < 0.37) && (ThreadLocalRandom.current().nextDouble() < (0.2 + 0.8 * factor));
            if (smartRetreat) {
                tactic = CombatTactic.RETREAT;
            } else {
                boolean blindRush = ThreadLocalRandom.current().nextDouble() > factor;
                if (blindRush) {
                    tactic = CombatTactic.RUSH;
                } else {
                    if (dist3D > 14.0) {
                        tactic = CombatTactic.RUSH;
                    } else if (dist3D < 5.0) {
                        tactic = CombatTactic.RETREAT;
                    } else {
                        tactic = (tickCount % 16 < 4) ? CombatTactic.SNIPE : CombatTactic.STRAFE;
                    }
                }
            }
        }

        // Map blocks awareness (Jump Pads) - deliberate sniping usage scales with factor
        boolean allowJumpPadSeeking = (tactic != CombatTactic.RETREAT && healthRatio > 0.65 && padCooldown <= 0)
                && (ThreadLocalRandom.current().nextDouble() < factor);
        Location nearestPad = allowJumpPadSeeking ? bot.findNearestJumpPad(6.0) : null;
        double minPadDist = (nearestPad != null) ? Math.sqrt(Math.pow(nearestPad.getX() - p1Loc.getX(), 2) + Math.pow(nearestPad.getZ() - p1Loc.getZ(), 2)) : 999.0;

        // Movement and combat execution
        boolean airborne = !bot.isOnGround() && player.getFallDistance() > 1.2;

        if (airborne) {
            bot.aimAt(p2Loc, turnSpeed * 1.2f, aimInaccuracy * 1.2f);
            if (shootCooldown <= 0 && canShootAtTarget) {
                bot.shoot();
                shootCooldown = minShootCooldown;
            }
            bot.moveRel(u1_x * (0.05 + 0.10 * factor), 0.0, u1_z * (0.05 + 0.10 * factor));
        } else if (nearestPad != null && minPadDist < 5.0 && (tickCount % 20 < 5)) {
            double pdx = nearestPad.getX() - p1Loc.getX();
            double pdz = nearestPad.getZ() - p1Loc.getZ();
            double plen = Math.sqrt(pdx * pdx + pdz * pdz);
            if (plen > 0.3) {
                bot.moveRel((pdx / plen) * (0.15 + 0.20 * factor), 0.0, (pdz / plen) * (0.15 + 0.20 * factor));
            }
            if (minPadDist < 1.2) {
                padCooldown = 120;
            }
            bot.aimAt(p2Loc, turnSpeed, aimInaccuracy);
            if (shootCooldown <= 0 && canShootAtTarget) {
                bot.shoot();
                shootCooldown = minShootCooldown;
            }
        } else {
            // Ground tactical combat
            switch (tactic) {
                case RUSH -> {
                    bot.moveRel(u1_x * 0.40 + p1_x * (strafeDir * strafeSpeed * 0.3), 0.0, u1_z * 0.40 + p1_z * (strafeDir * strafeSpeed * 0.3));
                    bot.aimAt(p2Loc, turnSpeed, aimInaccuracy);
                    if (shootCooldown <= 0 && canShootAtTarget) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                }
                case RETREAT -> {
                    bot.moveRel(-u1_x * 0.38 + p1_x * (strafeDir * strafeSpeed), 0.0, -u1_z * 0.38 + p1_z * (strafeDir * strafeSpeed));
                    if (tickCount % 30 == 0 && hasLos && ThreadLocalRandom.current().nextDouble() < factor) {
                        bot.block();
                    }
                    bot.aimAt(p2Loc, turnSpeed * 0.8f, aimInaccuracy);
                    if (shootCooldown <= 0 && canShootAtTarget && tickCount % 2 == 0) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                }
                case SHIELD -> {
                    if (tickCount % 20 == 0 && hasLos && ThreadLocalRandom.current().nextDouble() < factor) {
                        bot.block();
                    }
                    bot.moveRel(p1_x * (strafeDir * strafeSpeed * 0.6), 0.0, p1_z * (strafeDir * strafeSpeed * 0.6));
                    bot.aimAt(p2Loc, turnSpeed * 0.7f, aimInaccuracy);
                }
                case SNIPE -> {
                    if (dist3D < 8.0) {
                        bot.moveRel(-u1_x * 0.3, 0.0, -u1_z * 0.3);
                    }
                    bot.aimAt(p2Loc, turnSpeed * 0.7f, aimInaccuracy * 0.7f);
                    if (shootCooldown <= 0 && canShootAtTarget) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                }
                case STRAFE, BALANCED -> {
                    bot.moveRel(p1_x * (strafeDir * strafeSpeed) + u1_x * 0.05, 0.0, p1_z * (strafeDir * strafeSpeed) + u1_z * 0.05);
                    bot.aimAt(p2Loc, turnSpeed, aimInaccuracy);
                    if (shootCooldown <= 0 && canShootAtTarget) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                    if (tickCount % 40 == 3 && hasLos && ThreadLocalRandom.current().nextDouble() < factor) {
                        bot.block();
                    }
                }
            }
        }

        // Reverse strafe direction periodically
        dirTicks++;
        if (dirTicks > jukeThreshold) {
            strafeDir = -strafeDir;
            dirTicks = 0;
        }

        tickCooldowns();
    }

    private Location findCoverLocation(Player player, Location enemyLoc) {
        Location pLoc = player.getLocation();
        World world = pLoc.getWorld();
        if (world == null || enemyLoc == null) return null;

        double[] distances = {4.0, 6.5};
        double bestDist = -1.0;
        Location bestCover = null;

        for (double dist : distances) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 45) {
                double rad = Math.toRadians(angleDeg);
                double cx = pLoc.getX() + Math.cos(rad) * dist;
                double cz = pLoc.getZ() + Math.sin(rad) * dist;
                Location cand = new Location(world, cx, pLoc.getY(), cz);

                if (cand.getBlock().getType().isSolid()) continue;
                if (!cand.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;

                Location candEye = cand.clone().add(0, 1.62, 0);
                Location enemyEye = enemyLoc.clone().add(0, 1.62, 0);
                Vector toEnemy = enemyEye.toVector().subtract(candEye.toVector());
                double len = toEnemy.length();
                if (len < 1.0) continue;

                RayTraceResult hit = world.rayTraceBlocks(candEye, toEnemy.normalize(), len, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitBlock() != null) {
                    double d = cand.distanceSquared(enemyLoc);
                    if (d > bestDist) {
                        bestDist = d;
                        bestCover = cand;
                    }
                }
            }
        }
        return bestCover;
    }

    private void executePursuit(Player player, Location targetLoc) {
        Location myLoc = player.getLocation();
        double dx = targetLoc.getX() - myLoc.getX();
        double dz = targetLoc.getZ() - myLoc.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        double factor = bot.getSkillFactor();
        float turnSpeed = (float) (15.0f + 30.0f * factor);

        bot.aimAt(targetLoc.clone().add(0, 1.4, 0), turnSpeed, 1.0f);

        if (distXZ > 1.2) {
            double speed = 0.25 + 0.12 * factor;
            bot.moveRel((dx / distXZ) * speed, 0.0, (dz / distXZ) * speed);
        } else {
            // Reached last known location: scan left and right for hiding enemy
            searchScanYaw += 15f;
            float yaw = player.getLocation().getYaw() + (float) Math.sin(Math.toRadians(searchScanYaw)) * 40f;
            bot.getNmsPlayer().setRot(yaw, 0f);
        }
    }

    private void executeSearchPatrol(Player player) {
        double factor = bot.getSkillFactor();
        Location myLoc = player.getLocation();

        // 1. Initialize or maintain patrol heading
        if (Float.isNaN(patrolHeading)) {
            patrolHeading = myLoc.getYaw();
        }

        // 2. Track stuck status
        if (lastPatrolPos != null && myLoc.getWorld() != null && myLoc.getWorld().equals(lastPatrolPos.getWorld())) {
            double movedDistSq = Math.pow(myLoc.getX() - lastPatrolPos.getX(), 2) + Math.pow(myLoc.getZ() - lastPatrolPos.getZ(), 2);
            if (movedDistSq < 0.005) {
                patrolStuckTicks++;
            } else {
                patrolStuckTicks = 0;
            }
        }
        lastPatrolPos = myLoc.clone();

        // 3. Obstacle avoidance in front of heading
        double radHead = Math.toRadians(-patrolHeading);
        double fdx = Math.sin(radHead);
        double fdz = Math.cos(radHead);
        Location ahead = myLoc.clone().add(fdx * 1.2, 0.1, fdz * 1.2);
        boolean obstacleAhead = ahead.getBlock().getType().isSolid();

        if (obstacleAhead || patrolStuckTicks > 10) {
            patrolStuckTicks = 0;
            // Prefer steering towards arena center if available
            Location center = bot.getPlugin() != null ? bot.getPlugin().getGameManager().getCurrentZoneCenter() : null;
            if (center != null && ThreadLocalRandom.current().nextDouble() < 0.6) {
                double cdx = center.getX() - myLoc.getX();
                double cdz = center.getZ() - myLoc.getZ();
                patrolHeading = (float) Math.toDegrees(Math.atan2(-cdx, cdz)) + (ThreadLocalRandom.current().nextFloat() * 40f - 20f);
            } else {
                patrolHeading += 90f + ThreadLocalRandom.current().nextFloat() * 60f;
            }
        }

        // 4. Smooth sinusoidal head sweeps looking around forward movement path
        searchScanYaw += (float) (4.0 + 6.0 * factor);
        float sweep = (float) Math.sin(Math.toRadians(searchScanYaw)) * (float) (35.0 + 20.0 * factor);
        float lookYaw = patrolHeading + sweep;
        bot.getNmsPlayer().setRot(lookYaw, 0f);

        // 5. Continuous forward exploration movement along patrol heading
        double walkSpeed = 0.20 + 0.12 * factor;
        double radMove = Math.toRadians(-patrolHeading);
        bot.moveRel(Math.sin(radMove) * walkSpeed, 0.0, Math.cos(radMove) * walkSpeed);
    }

    private void tickCooldowns() {
        if (shootCooldown > 0) {
            shootCooldown--;
        }
        if (padCooldown > 0) {
            padCooldown--;
        }
        tickCount++;
    }

    private Player findVisibleTarget() {
        Player player = bot.getBukkitPlayer();
        Location myLoc = player.getLocation();
        Player closest = null;
        double closestDist = 45.0 * 45.0;

        RoundsPlugin plugin = bot.getPlugin();
        GameTeam myRoundsTeam = (plugin != null) ? plugin.getTeamManager().getPlayerTeam(player.getUniqueId()) : null;
        Team myScoreboardTeam = player.getScoreboard().getEntryTeam(player.getName());

        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player)) continue;
            if (other.isDead() || !other.isOnline()) continue;
            if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE) continue;

            // Check RoundsPlugin team
            if (myRoundsTeam != null && plugin != null) {
                GameTeam otherRoundsTeam = plugin.getTeamManager().getPlayerTeam(other.getUniqueId());
                if (otherRoundsTeam != null && myRoundsTeam == otherRoundsTeam) {
                    continue; // Teammate
                }
            }

            // Check Scoreboard team
            Team otherScoreboardTeam = other.getScoreboard().getEntryTeam(other.getName());
            if (myScoreboardTeam != null && otherScoreboardTeam != null && myScoreboardTeam.equals(otherScoreboardTeam)) {
                continue; // Teammate
            }

            // Must be visible within realistic FOV cone and line of sight!
            if (!canSee(player, other)) {
                continue;
            }

            double d = myLoc.distanceSquared(other.getLocation());
            if (d < closestDist) {
                closestDist = d;
                closest = other;
            }
        }

        return closest;
    }
}
