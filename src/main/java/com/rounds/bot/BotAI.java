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

    public enum BotPlaystyle {
        AGGRESSIVE_FLANKER,
        TACTICAL_SNIPER,
        DEFENSIVE_TURTLE,
        ERRATIC_BRAWLER
    }

    public static final double MAX_VISION_DISTANCE_SQ = 3600.0; // 60 * 60 (covers entire arena)
    public static final double PROXIMITY_HEARING_DISTANCE_SQ = 6.25; // 2.5 * 2.5
    public static final double FOV_HALF_ANGLE_DEG = 70.0; // 140 degree FOV cone
    public static final double LOW_HEALTH_COVER_THRESHOLD = 0.20; // 20% HP (user requested: 15-20%)

    private final FakeBot bot;
    private BotPlaystyle playstyle = BotPlaystyle.AGGRESSIVE_FLANKER;
    private int tickCount = 0;
    private int strafeDir = 1;
    private int dirTicks = 0;
    private int shootCooldown = 0;
    private int padCooldown = 100;
    private int tacticalJumpCooldown = 0;
    private int coverSearchCooldown = 0;
    private int coverSeekTicks = 0;

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

    // Round start warmup reaction delay
    private int roundWarmupTicks = 0;

    // Corridor cul-de-sac & ping-pong wall escape
    private int consecutiveBounces = 0;
    private float lastTurnSign = 0f;
    private int lockedHeadingTicks = 0;

    // Tactical Jump Pad seeking state
    private Location seekingPadLoc = null;
    private int seekingPadTicks = 0;

    // Individual patrol waypoint offset to diversify bot movement patterns
    private double personalOffsetX = ThreadLocalRandom.current().nextDouble(-15.0, 15.0);
    private double personalOffsetZ = ThreadLocalRandom.current().nextDouble(-15.0, 15.0);

    public BotAI(FakeBot bot) {
        this.bot = bot;
        this.padCooldown = 100;
    }

    public void onRoundStart() {
        // Random start-of-round pad ban between 3s (60t) and 15s (300t), avg ~5s (100t)
        this.padCooldown = 60 + (int) (240.0 * Math.pow(ThreadLocalRandom.current().nextDouble(), 5.0));
        this.playstyle = BotPlaystyle.values()[ThreadLocalRandom.current().nextInt(BotPlaystyle.values().length)];
        this.roundWarmupTicks = ThreadLocalRandom.current().nextInt(25, 45); // 1.25 - 2.25s warmup reaction
        this.tacticalJumpCooldown = 0;

        // Differentiated patrol offsets based on playstyle
        switch (playstyle) {
            case AGGRESSIVE_FLANKER -> {
                double ang = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                double rad = ThreadLocalRandom.current().nextDouble(15.0, 22.0);
                this.personalOffsetX = Math.cos(ang) * rad;
                this.personalOffsetZ = Math.sin(ang) * rad;
            }
            case TACTICAL_SNIPER -> {
                this.personalOffsetX = (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * ThreadLocalRandom.current().nextDouble(14.0, 20.0);
                this.personalOffsetZ = (ThreadLocalRandom.current().nextBoolean() ? 1 : -1) * ThreadLocalRandom.current().nextDouble(14.0, 20.0);
            }
            case ERRATIC_BRAWLER -> {
                this.personalOffsetX = ThreadLocalRandom.current().nextDouble(-10.0, 10.0);
                this.personalOffsetZ = ThreadLocalRandom.current().nextDouble(-10.0, 10.0);
            }
            case DEFENSIVE_TURTLE -> {
                this.personalOffsetX = ThreadLocalRandom.current().nextDouble(-12.0, 12.0);
                this.personalOffsetZ = ThreadLocalRandom.current().nextDouble(-12.0, 12.0);
            }
        }

        this.patrolHeading = Float.NaN;
        this.lockedHeadingTicks = 0;
        this.consecutiveBounces = 0;
        this.lastTurnSign = 0f;
        this.seekingPadLoc = null;
        this.seekingPadTicks = 0;
        this.currentTarget = null;
        this.lastKnownTargetLoc = null;
        this.isPursuing = false;
        this.isSeekingCover = false;
        this.isAmbushing = false;
        this.coverLoc = null;
    }

    public boolean isPadBanned() {
        return padCooldown > 0;
    }

    public void setPadCooldown(int ticks) {
        this.padCooldown = Math.max(this.padCooldown, ticks);
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
        if (distSq > MAX_VISION_DISTANCE_SQ) return false; // Beyond vision range (40 blocks)

        // Close proximity (under 2.5m): audible footsteps or proximity allows detection if raycast is clear
        if (distSq < PROXIMITY_HEARING_DISTANCE_SQ) {
            return botPlayer.hasLineOfSight(target);
        }

        // Field of View check: 130 degree viewing cone (65 degrees from look vector)
        Vector dirToTarget = targetEye.toVector().subtract(botEye.toVector()).normalize();
        Vector lookDir = botEye.getDirection().normalize();
        double angle = Math.toDegrees(dirToTarget.angle(lookDir));
        if (angle > FOV_HALF_ANGLE_DEG) {
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

        // Round start warmup delay: natural player-like hesitation / look around
        if (roundWarmupTicks > 0) {
            roundWarmupTicks--;
            float wobbleYaw = player.getLocation().getYaw() + (float) Math.sin(tickCount * 0.2) * 5.0f;
            bot.getNmsPlayer().setRot(wobbleYaw, 0f);
            bot.getNmsPlayer().setYHeadRot(wobbleYaw);
            bot.getNmsPlayer().setYBodyRot(wobbleYaw);
            tickCooldowns();
            return;
        }

        // Jump pad flight check: only active when actually launched by a pad or launched high upwards (never when simply stepping down stairs!)
        boolean inPadFlight = bot.isPadLaunched() || (bot.getVelY() > 0.45 && !bot.isOnGround());
        if (inPadFlight) {
            padCooldown = 600; // 30s cooldown prevents repeated launch loops
            Location center = bot.getPlugin() != null ? bot.getPlugin().getGameManager().getCurrentZoneCenter() : null;
            if (center == null || center.getWorld() == null || !center.getWorld().equals(player.getWorld())) {
                center = player.getWorld().getSpawnLocation();
            }

            // Direction vector towards arena center
            double cdx = center.getX() - player.getLocation().getX();
            double cdz = center.getZ() - player.getLocation().getZ();
            double clen = Math.sqrt(cdx * cdx + cdz * cdz);

            double pushX;
            double pushZ;
            if (clen > 2.0) {
                // Propel towards map center
                pushX = (cdx / clen) * 0.38;
                pushZ = (cdz / clen) * 0.38;
            } else {
                // Bot is already near center: push forward along look direction
                double rad = Math.toRadians(-player.getLocation().getYaw());
                pushX = Math.sin(rad) * 0.35;
                pushZ = Math.cos(rad) * 0.35;
            }
            bot.moveRel(pushX, 0.0, pushZ);

            if (currentTarget != null && canSee(player, currentTarget)) {
                double factor = bot.getSkillFactor();
                float turnSpeed = (float) (10.0f + 35.0f * factor);
                float aimInaccuracy = (float) (1.2f / factor);
                bot.aimAt(currentTarget.getLocation(), turnSpeed, aimInaccuracy);
                if (bot.hasGun() && shootCooldown <= 0) {
                    bot.shoot();
                    shootCooldown = 6;
                }
            } else {
                float flightYaw = (float) Math.toDegrees(Math.atan2(-pushX, pushZ));
                bot.getNmsPlayer().setRot(flightYaw, 0f);
                bot.getNmsPlayer().setYHeadRot(flightYaw);
                bot.getNmsPlayer().setYBodyRot(flightYaw);
            }
            tickCooldowns();
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
            if (ambushTicks > 40 || (targetCurrentlyVisible && lastKnownTargetLoc != null && player.getLocation().distance(lastKnownTargetLoc) < 5.0)) {
                isAmbushing = false;
                coverSearchCooldown = 120;
            } else {
                // Hold position behind corner, pre-aim at corner where enemy is expected to emerge (no jitter)
                if (lastKnownTargetLoc != null) {
                    bot.aimAt(lastKnownTargetLoc.clone().add(0, 1.4, 0), 45.0f, 0.0f);
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
                    coverSearchCooldown = 120;
                } else {
                    tickCooldowns();
                    return;
                }
            }
        }

        // 3. Seeking Cover / Hiding (to heal or reload)
        if (isSeekingCover && coverLoc != null) {
            coverSeekTicks++;
            double cdx = coverLoc.getX() - player.getLocation().getX();
            double cdz = coverLoc.getZ() - player.getLocation().getZ();
            double cdist = Math.sqrt(cdx * cdx + cdz * cdz);

            if (cdist > 0.8 && coverSeekTicks < 50) {
                float moveYaw = (float) Math.toDegrees(Math.atan2(-cdx, cdz));
                bot.getNmsPlayer().setRot(moveYaw, 0f);
                bot.getNmsPlayer().setYHeadRot(moveYaw);
                bot.getNmsPlayer().setYBodyRot(moveYaw);
                moveCombatRel((cdx / cdist) * 0.36, (cdz / cdist) * 0.36);
                tickCooldowns();
                return;
            } else {
                // Reached cover or timed out (prevent getting stuck against walls)
                isSeekingCover = false;
                coverLoc = null;
                coverSearchCooldown = 120;

                if (cdist <= 1.2) {
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

        // Tactical decision: check if bot should seek cover to heal (only when very low HP: 15-20%)
        boolean needsCover = (healthRatio <= LOW_HEALTH_COVER_THRESHOLD || (data != null && data.heal > 0 && healthRatio <= 0.25));
        if (needsCover && !isSeekingCover && coverSearchCooldown <= 0 && (ThreadLocalRandom.current().nextDouble() < smartChance)) {
            Location cover = findCoverLocation(player, p2Loc);
            if (cover != null) {
                coverLoc = cover;
                isSeekingCover = true;
                coverSeekTicks = 0;
                tickCooldowns();
                return;
            } else {
                coverSearchCooldown = 40; // Don't raycast 16 lines every tick if no cover exists
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
        // Direction change / juking: tailored by playstyle and factor
        int jukeThreshold = switch (playstyle) {
            case ERRATIC_BRAWLER -> (int) (6 + (1.0 - factor) * 12.0);
            case AGGRESSIVE_FLANKER -> (int) (10 + (1.0 - factor) * 18.0);
            case DEFENSIVE_TURTLE -> (int) (18 + (1.0 - factor) * 28.0);
            case TACTICAL_SNIPER -> (int) (24 + (1.0 - factor) * 36.0);
        };

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
            } else if (data != null && data.ammo <= 0) {
                // Tactical reload: avoid charging without ammo
                tactic = (playstyle == BotPlaystyle.DEFENSIVE_TURTLE) ? CombatTactic.SHIELD : CombatTactic.RETREAT;
            } else {
                boolean blindRush = ThreadLocalRandom.current().nextDouble() > factor;
                if (blindRush && playstyle != BotPlaystyle.TACTICAL_SNIPER) {
                    tactic = CombatTactic.RUSH;
                } else {
                    double rushThreshold = switch (playstyle) {
                        case AGGRESSIVE_FLANKER -> 9.0;
                        case TACTICAL_SNIPER -> 24.0;
                        case DEFENSIVE_TURTLE -> 15.0;
                        case ERRATIC_BRAWLER -> 11.0;
                    };
                    double retreatThreshold = switch (playstyle) {
                        case AGGRESSIVE_FLANKER -> 4.0;
                        case TACTICAL_SNIPER -> 10.0;
                        case DEFENSIVE_TURTLE -> 7.0;
                        case ERRATIC_BRAWLER -> 4.5;
                    };
                    if (dist3D > rushThreshold) {
                        tactic = CombatTactic.RUSH;
                    } else if (dist3D < retreatThreshold) {
                        tactic = (playstyle == BotPlaystyle.DEFENSIVE_TURTLE) ? CombatTactic.SHIELD : CombatTactic.RETREAT;
                    } else {
                        tactic = (playstyle == BotPlaystyle.TACTICAL_SNIPER || tickCount % 16 < 4) ? CombatTactic.SNIPE : CombatTactic.STRAFE;
                    }
                }
            }
        }

        // Tactical combat jumping (dodging bullet fire while strafing/moving)
        if (bot.isOnGround() && tacticalJumpCooldown <= 0 && tactic != CombatTactic.SNIPE) {
            double jumpChance = switch (playstyle) {
                case ERRATIC_BRAWLER -> 0.08 * factor;
                case AGGRESSIVE_FLANKER -> 0.04 * factor;
                default -> 0.01 * factor;
            };
            if (ThreadLocalRandom.current().nextDouble() < jumpChance) {
                bot.getBukkitPlayer().setVelocity(bot.getBukkitPlayer().getVelocity().setY(0.38));
                tacticalJumpCooldown = 20;
            }
        }

        // Map blocks awareness (Jump Pads) - jump pads deal 20% max HP, so it's a hazardous risk!
        // Evaluated only once every 5 seconds (100 ticks), with halved chance (0.075 * factor)
        if (padCooldown <= 0 && healthRatio > 0.85 && tactic != CombatTactic.RETREAT && tickCount % 100 == 0) {
            if (ThreadLocalRandom.current().nextDouble() < 0.075 * factor) {
                seekingPadLoc = bot.findNearestJumpPad(5.0);
                seekingPadTicks = 0;
            }
        }

        // Movement and combat execution
        if (seekingPadLoc != null) {
            seekingPadTicks++;
            double pdx = seekingPadLoc.getX() - p1Loc.getX();
            double pdz = seekingPadLoc.getZ() - p1Loc.getZ();
            double padDist = Math.sqrt(pdx * pdx + pdz * pdz);

            if (padDist < 1.2 || seekingPadTicks > 20) {
                seekingPadLoc = null;
                padCooldown = 600; // 30s cooldown once reached
            } else if (padDist > 0.3) {
                bot.moveRel((pdx / padDist) * (0.15 + 0.20 * factor), 0.0, (pdz / padDist) * (0.15 + 0.20 * factor));
                bot.aimAt(p2Loc, turnSpeed, aimInaccuracy);
                if (shootCooldown <= 0 && canShootAtTarget) {
                    bot.shoot();
                    shootCooldown = minShootCooldown;
                }
            }
        } else {
            // Ground tactical combat
            switch (tactic) {
                case RUSH -> {
                    moveCombatRel(u1_x * 0.40 + p1_x * (strafeDir * strafeSpeed * 0.3), u1_z * 0.40 + p1_z * (strafeDir * strafeSpeed * 0.3));
                    bot.aimAt(p2Loc, turnSpeed, aimInaccuracy);
                    if (shootCooldown <= 0 && canShootAtTarget) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                }
                case RETREAT -> {
                    moveCombatRel(-u1_x * 0.38 + p1_x * (strafeDir * strafeSpeed), -u1_z * 0.38 + p1_z * (strafeDir * strafeSpeed));
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
                    moveCombatRel(p1_x * (strafeDir * strafeSpeed * 0.6), p1_z * (strafeDir * strafeSpeed * 0.6));
                    bot.aimAt(p2Loc, turnSpeed * 0.7f, aimInaccuracy);
                }
                case SNIPE -> {
                    if (dist3D < 8.0) {
                        moveCombatRel(-u1_x * 0.3, -u1_z * 0.3);
                    }
                    bot.aimAt(p2Loc, turnSpeed * 0.7f, aimInaccuracy * 0.7f);
                    if (shootCooldown <= 0 && canShootAtTarget) {
                        bot.shoot();
                        shootCooldown = minShootCooldown;
                    }
                }
                case STRAFE, BALANCED -> {
                    moveCombatRel(p1_x * (strafeDir * strafeSpeed) + u1_x * 0.05, p1_z * (strafeDir * strafeSpeed) + u1_z * 0.05);
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

    private void moveCombatRel(double mX, double mZ) {
        Location myLoc = bot.getBukkitPlayer().getLocation();
        float moveHeading = (float) Math.toDegrees(Math.atan2(-mX, mZ));
        double dist = Math.sqrt(mX * mX + mZ * mZ);
        if (dist > 0.01 && seekingPadLoc == null && !isDirectionClear(myLoc, moveHeading, Math.max(dist, 1.0))) {
            float altHeading = findClearDirection(myLoc, moveHeading);
            double rad = Math.toRadians(-altHeading);
            bot.moveRel(Math.sin(rad) * dist, 0.0, Math.cos(rad) * dist);
            return;
        }
        bot.moveRel(mX, 0.0, mZ);
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

        if (distXZ > 1.2) {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

            // Obstacle avoidance during pursuit: route around blocking walls and jump pads
            if (!isDirectionClear(myLoc, moveYaw, 1.2)) {
                moveYaw = findClearDirection(myLoc, moveYaw);
                double rad = Math.toRadians(-moveYaw);
                dx = Math.sin(rad);
                dz = Math.cos(rad);
                distXZ = 1.0;
            }

            bot.getNmsPlayer().setRot(moveYaw, 0f);
            bot.getNmsPlayer().setYHeadRot(moveYaw);
            bot.getNmsPlayer().setYBodyRot(moveYaw);

            double speed = 0.25 + 0.12 * factor;
            bot.moveRel((dx / distXZ) * speed, 0.0, (dz / distXZ) * speed);
        } else {
            // Reached last known location: scan left and right briefly
            searchScanYaw += 15f;
            float yaw = player.getLocation().getYaw() + (float) Math.sin(Math.toRadians(searchScanYaw)) * 30f;
            bot.getNmsPlayer().setRot(yaw, 0f);
            bot.getNmsPlayer().setYHeadRot(yaw);
            bot.getNmsPlayer().setYBodyRot(yaw);
            if (searchTicks > 25) {
                // Done searching this spot, resume patrol exploration
                isPursuing = false;
                currentTarget = null;
                lastKnownTargetLoc = null;
            }
        }
    }

    private void executeSearchPatrol(Player player) {
        double factor = bot.getSkillFactor();
        Location myLoc = player.getLocation();

        // 1. Initialize or maintain patrol heading towards personal waypoint in arena zone
        Location center = bot.getPlugin() != null ? bot.getPlugin().getGameManager().getCurrentZoneCenter() : null;
        if (center != null) {
            Location targetPt = center.clone().add(personalOffsetX, 0, personalOffsetZ);
            double cdx = targetPt.getX() - myLoc.getX();
            double cdz = targetPt.getZ() - myLoc.getZ();
            double distToTarget = Math.sqrt(cdx * cdx + cdz * cdz);
            if (distToTarget < 3.5) {
                // Reached personal waypoint, pick another diverse waypoint
                personalOffsetX = ThreadLocalRandom.current().nextDouble(-18.0, 18.0);
                personalOffsetZ = ThreadLocalRandom.current().nextDouble(-18.0, 18.0);
                targetPt = center.clone().add(personalOffsetX, 0, personalOffsetZ);
                cdx = targetPt.getX() - myLoc.getX();
                cdz = targetPt.getZ() - myLoc.getZ();
                patrolHeading = Float.NaN;
            }
            if (Float.isNaN(patrolHeading)) {
                patrolHeading = (float) Math.toDegrees(Math.atan2(-cdx, cdz));
            }
        } else if (Float.isNaN(patrolHeading)) {
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

        // 3. Obstacle avoidance in front of heading (checks feet + body)
        if (lockedHeadingTicks > 0) {
            lockedHeadingTicks--;
            if (!isDirectionClear(myLoc, patrolHeading, 1.1)) {
                lockedHeadingTicks = 0;
                patrolHeading = findClearDirection(myLoc, patrolHeading);
            }
        } else {
            boolean blocked = !isDirectionClear(myLoc, patrolHeading, 1.2);
            if (patrolStuckTicks > 8) {
                patrolStuckTicks = 0;
                personalOffsetX = ThreadLocalRandom.current().nextDouble(-18.0, 18.0);
                personalOffsetZ = ThreadLocalRandom.current().nextDouble(-18.0, 18.0);
                lockedHeadingTicks = 25; // Lock heading for 1.25s to walk away from corner/wall
                patrolHeading = findClearDirection(myLoc, patrolHeading + (ThreadLocalRandom.current().nextBoolean() ? 90f : -90f));
            } else if (blocked) {
                patrolHeading = findClearDirection(myLoc, patrolHeading);
            }
        }

        // 4. Smooth natural head/body turn towards patrol heading
        float currentYaw = player.getLocation().getYaw();
        float deltaYaw = ((patrolHeading - currentYaw + 540f) % 360f) - 180f;
        float maxTurn = 18f; // ~18 deg/tick smooth rotation for realistic navigation
        float newYaw = (Math.abs(deltaYaw) <= maxTurn) ? patrolHeading : currentYaw + Math.signum(deltaYaw) * maxTurn;
        bot.getNmsPlayer().setRot(newYaw, 0f);
        bot.getNmsPlayer().setYHeadRot(newYaw);
        bot.getNmsPlayer().setYBodyRot(newYaw);

        // 5. Continuous forward exploration movement along current yaw
        double walkSpeed = 0.20 + 0.12 * factor;
        double radMove = Math.toRadians(-newYaw);
        bot.moveRel(Math.sin(radMove) * walkSpeed, 0.0, Math.cos(radMove) * walkSpeed);
    }

    private boolean isDirectionClear(Location loc, float heading, double dist) {
        if (loc == null || loc.getWorld() == null) return true;
        World w = loc.getWorld();
        double rad = Math.toRadians(-heading);
        double dx = Math.sin(rad) * dist;
        double dz = Math.cos(rad) * dist;

        // Normal perpendicular vector for bot width check (hitbox radius ~0.3b)
        double perpX = Math.cos(rad) * 0.32;
        double perpZ = -Math.sin(rad) * 0.32;

        Location feet = loc.clone().add(dx, 0.2, dz);
        Location body = loc.clone().add(dx, 1.2, dz);
        Location head = loc.clone().add(dx, 2.2, dz);
        Location overhead = loc.clone().add(0, 2.1, 0);

        boolean feetSolid = feet.getBlock().getType().isSolid();
        boolean bodySolid = body.getBlock().getType().isSolid();
        boolean headSolid = head.getBlock().getType().isSolid();
        boolean overheadBlocked = overhead.getBlock().getType().isSolid();

        // 1. Center ray solid check: 1-block steps are passable if body & head are clear!
        if (bodySolid || headSolid || (feetSolid && overheadBlocked)) {
            return false;
        }

        // 2. Left and right shoulders width check (prevents jamming into narrow diagonal gaps)
        Location bodyLeft = loc.clone().add(dx - perpX, 1.2, dz - perpZ);
        if (bodyLeft.getBlock().getType().isSolid()) {
            return false;
        }

        Location bodyRight = loc.clone().add(dx + perpX, 1.2, dz + perpZ);
        if (bodyRight.getBlock().getType().isSolid()) {
            return false;
        }

        // 3. Midpoint width check for longer rays (catches diagonal pinches along the movement path)
        if (dist >= 0.8) {
            double midDx = dx * 0.5;
            double midDz = dz * 0.5;
            Location midBodyLeft = loc.clone().add(midDx - perpX, 1.2, midDz - perpZ);
            Location midBodyRight = loc.clone().add(midDx + perpX, 1.2, midDz + perpZ);
            if (midBodyLeft.getBlock().getType().isSolid() || midBodyRight.getBlock().getType().isSolid()) {
                return false;
            }
        }

        // 4. Diagonal corner convergence check (two diagonally adjacent solid blocks touching at vertex)
        int curX = loc.getBlockX();
        int curY = loc.getBlockY();
        int curZ = loc.getBlockZ();
        int targetX = feet.getBlockX();
        int targetZ = feet.getBlockZ();

        if (curX != targetX && curZ != targetZ) {
            boolean corner1Solid = w.getBlockAt(targetX, curY + 1, curZ).getType().isSolid();
            boolean corner2Solid = w.getBlockAt(curX, curY + 1, targetZ).getType().isSolid();
            if (corner1Solid && corner2Solid) {
                return false;
            }
        }

        // 5. Avoid walking directly into jump blocks and up blocks in front unless actively seeking one in combat
        com.rounds.blocks.BlockListener bl = bot.getPlugin() != null ? bot.getPlugin().getBlockListener() : null;
        if (bl != null && seekingPadLoc == null) {
            Location checkLoc = loc.clone().add(dx, 0.1, dz);
            Location checkBelow = checkLoc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
            Location checkAt = checkLoc.getBlock().getLocation();
            if (bl.isLocationTouchingJumpBlock(checkLoc) || bl.isJumpBlock(checkBelow) || bl.isJumpBlock(checkAt)
                    || bl.isLocationTouchingUpBlock(checkLoc) || bl.isUpBlock(checkBelow) || bl.isUpBlock(checkAt)) {
                return false;
            }
        }

        // Avoid walking directly into another bot or player in front (avoids snake/conga line)
        if (loc.getWorld() != null) {
            for (Player other : loc.getWorld().getPlayers()) {
                if (other.getUniqueId().equals(bot.getBukkitPlayer().getUniqueId()) || other.getGameMode() == GameMode.SPECTATOR || other.isDead()) continue;
                Location otherLoc = other.getLocation();
                if (Math.abs(otherLoc.getY() - loc.getY()) > 1.5) continue;
                double odx = otherLoc.getX() - feet.getX();
                double odz = otherLoc.getZ() - feet.getZ();
                if (odx * odx + odz * odz < 1.0) {
                    return false;
                }
            }
        }

        return true;
    }

    private float findClearDirection(Location loc, float currentHeading) {
        // 1. Check for 3-sided dead end (cul-de-sac): front, left and right blocked
        boolean frontBlocked = !isDirectionClear(loc, currentHeading, 1.2);
        boolean leftBlocked = !isDirectionClear(loc, currentHeading - 90f, 1.1);
        boolean rightBlocked = !isDirectionClear(loc, currentHeading + 90f, 1.1);
        if (frontBlocked && leftBlocked && rightBlocked) {
            consecutiveBounces = 0;
            lastTurnSign = 0f;
            lockedHeadingTicks = 35; // Lock heading for ~1.75s to run out of dead end
            return currentHeading + 180f;
        }

        Location center = bot.getPlugin() != null ? bot.getPlugin().getGameManager().getCurrentZoneCenter() : null;
        float idealHeading = currentHeading;
        if (center != null) {
            Location targetPt = center.clone().add(personalOffsetX, 0, personalOffsetZ);
            double cdx = targetPt.getX() - loc.getX();
            double cdz = targetPt.getZ() - loc.getZ();
            idealHeading = (float) Math.toDegrees(Math.atan2(-cdx, cdz));
        }

        // Check ideal heading towards center first
        if (isDirectionClear(loc, idealHeading, 1.2)) {
            consecutiveBounces = 0;
            lastTurnSign = 0f;
            return idealHeading;
        }

        // Try alternating angle deviations
        float[] offsets = {45f, -45f, 90f, -90f, 135f, -135f, 180f};
        for (float off : offsets) {
            float test = currentHeading + off;
            if (isDirectionClear(loc, test, 1.2)) {
                float sign = Math.signum(off);
                if (sign != 0 && lastTurnSign != 0 && sign != lastTurnSign) {
                    consecutiveBounces++;
                } else if (sign != 0) {
                    consecutiveBounces = 0;
                }
                lastTurnSign = sign;

                if (consecutiveBounces >= 3) {
                    // Ping-pong between walls detected: escape with 180 turn
                    consecutiveBounces = 0;
                    lastTurnSign = 0f;
                    lockedHeadingTicks = 35;
                    return currentHeading + 180f;
                }
                return test;
            }
        }
        consecutiveBounces = 0;
        lastTurnSign = 0f;
        lockedHeadingTicks = 35;
        return currentHeading + 180f;
    }

    private void tickCooldowns() {
        if (shootCooldown > 0) {
            shootCooldown--;
        }
        if (padCooldown > 0) {
            padCooldown--;
        }
        if (coverSearchCooldown > 0) {
            coverSearchCooldown--;
        }
        if (tacticalJumpCooldown > 0) {
            tacticalJumpCooldown--;
        }
        tickCount++;
    }

    private Player findVisibleTarget() {
        Player player = bot.getBukkitPlayer();
        Location myLoc = player.getLocation();
        Player closest = null;
        double closestDist = 65.0 * 65.0;

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
