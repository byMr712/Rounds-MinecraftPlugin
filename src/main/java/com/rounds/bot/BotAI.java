package com.rounds.bot;

import com.rounds.RoundsPlugin;
import com.rounds.blocks.BlockListener;
import com.rounds.game.GameManager;
import com.rounds.player.PlayerData;
import com.rounds.teams.TeamManager.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    public static final int PURSUIT_TIMEOUT_TICKS = 120; // 6 seconds pursuit timeout

    private final FakeBot bot;
    private BotPlaystyle playstyle = BotPlaystyle.AGGRESSIVE_FLANKER;
    private boolean manualPlaystyle = false;
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

    // Room confinement & 2x2 doorway escape
    private Location roomAnchorLoc = null;
    private int roomConfinementTicks = 0;

    // Per-round unique navigation, gap jumping, and exploration memory
    private final Set<Long> visitedTerritory = new HashSet<>();
    private final Set<Long> blockedEdges = new HashSet<>();
    private final Set<Long> confirmedJumpGaps = new HashSet<>();
    private Location currentHuntTarget = null;
    private Location currentCorridorWaypoint = null;
    private int huntStuckTicks = 0;
    private int pursuitStuckTicks = 0;
    private Location lastHuntPos = null;

    // Optimized landing platform cache & room exit cooldown
    private Location cachedLandingPlatform = null;
    private int landingPlatformCacheTicks = 0;
    private int roomExitCooldown = 0;

    // AI LOD (Level of Detail) tick throttling for distant bots (> 35m)
    private int lodLevel = 1; // 1 = 20 TPS, 2 = 10 TPS, 3 = 7 TPS
    private int lodCheckTicks = 0;
    private double lastMoveX = 0.0;
    private double lastMoveZ = 0.0;

    // Individual patrol waypoint offset to diversify bot movement patterns
    private double personalOffsetX = ThreadLocalRandom.current().nextDouble(-15.0, 15.0);
    private double personalOffsetZ = ThreadLocalRandom.current().nextDouble(-15.0, 15.0);

    public BotAI(FakeBot bot) {
        this.bot = bot;
        this.padCooldown = 100;
    }

    public CombatProfile getProfile() {
        return CombatProfile.get(bot.getLevel(), playstyle);
    }

    public void onRoundStart() {
        // Random start-of-round pad ban between 3s (60t) and 15s (300t), avg ~5s (100t)
        this.padCooldown = 60 + (int) (240.0 * Math.pow(ThreadLocalRandom.current().nextDouble(), 5.0));
        if (!manualPlaystyle) {
            this.playstyle = BotPlaystyle.values()[ThreadLocalRandom.current().nextInt(BotPlaystyle.values().length)];
        }
        this.roundWarmupTicks = ThreadLocalRandom.current().nextInt(25, 45); // 1.25 - 2.25s warmup reaction
        this.tacticalJumpCooldown = 0;
        this.cachedLandingPlatform = null;
        this.landingPlatformCacheTicks = 0;
        this.roomExitCooldown = 0;
        this.lodLevel = 1;
        this.lodCheckTicks = 0;
        this.lastMoveX = 0.0;
        this.lastMoveZ = 0.0;

        // Waypoint positioning: prefer precomputed ArenaNavMesh nodes if available
        ArenaNavMesh mesh = bot.getManager() != null ? bot.getManager().getNavMesh() : null;
        Location center = bot.getPlugin() != null ? bot.getPlugin().getGameManager().getCurrentZoneCenter() : null;
        if (mesh != null && !mesh.isEmpty() && center != null) {
            ArenaNavMesh.NavNode node = (playstyle == BotPlaystyle.DEFENSIVE_TURTLE) ? mesh.getRandomCoverNode() : mesh.getRandomNode();
            if (node != null) {
                this.personalOffsetX = node.loc.getX() - center.getX();
                this.personalOffsetZ = node.loc.getZ() - center.getZ();
            }
        } else {
            // Differentiated patrol offsets fallback based on playstyle
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
        }

        this.patrolHeading = Float.NaN;
        this.lockedHeadingTicks = 0;
        this.consecutiveBounces = 0;
        this.lastTurnSign = 0f;
        this.currentTarget = null;
        this.lastKnownTargetLoc = null;
        this.isPursuing = false;
        this.isSeekingCover = false;
        this.isAmbushing = false;
        this.coverLoc = null;

        // Clear unique per-round exploration, gaps, and routing map
        this.visitedTerritory.clear();
        this.blockedEdges.clear();
        this.confirmedJumpGaps.clear();
        this.currentHuntTarget = null;
        this.currentCorridorWaypoint = null;
        this.huntStuckTicks = 0;
        this.pursuitStuckTicks = 0;
        this.lastHuntPos = null;
    }

    public static long packCell(int blockX, int blockZ) {
        return (((long) (blockX >> 2)) << 32) | ((blockZ >> 2) & 0xFFFFFFFFL);
    }

    public static long packEdge(int x1, int z1, int x2, int z2) {
        long c1 = packCell(x1, z1);
        long c2 = packCell(x2, z2);
        return (c1 < c2) ? (c1 * 31L + c2) : (c2 * 31L + c1);
    }

    public void recordJumpGap(Location from, Location to) {
        if (from == null || to == null) return;
        long edge = packEdge(from.getBlockX(), from.getBlockZ(), to.getBlockX(), to.getBlockZ());
        confirmedJumpGaps.add(edge);
    }

    public boolean isKnownJumpGap(Location from, Location to) {
        if (from == null || to == null) return false;
        long edge = packEdge(from.getBlockX(), from.getBlockZ(), to.getBlockX(), to.getBlockZ());
        return confirmedJumpGaps.contains(edge);
    }

    public boolean isPadBanned() {
        return padCooldown > 0;
    }

    public void setPadCooldown(int ticks) {
        this.padCooldown = Math.max(this.padCooldown, ticks);
    }

    public BotPlaystyle getPlaystyle() {
        return playstyle;
    }

    public void setPlaystyle(BotPlaystyle playstyle) {
        if (playstyle != null) {
            this.playstyle = playstyle;
            this.manualPlaystyle = true;
        }
    }

    private boolean isCombatAllowed() {
        RoundsPlugin plugin = bot.getPlugin();
        if (plugin == null) return true;
        if (plugin.getRoundsConfig().isFreeplay()) {
            return plugin.getGameManager().isGameStarted();
        }
        return plugin.getGameManager().getState() == GameManager.GameState.PLAYING;
    }

    public double getSmartBehaviorChance() {
        return getProfile().smartChance;
    }

    public double getPursuitChance() {
        return getProfile().pursuitChance;
    }

    public double getJumpErrorChance() {
        return getProfile().jumpErrorChance;
    }

    public double getLandingErrorChance() {
        return getProfile().landingErrorChance;
    }

    /**
     * AI Level of Detail (LOD) tick rate scaling:
     * When > 35m from active players/enemies and not in combat, drops decision tickrate to 10 TPS (LOD 2)
     * or 7 TPS (LOD 3) to allow running 30+ bots smoothly.
     */
    private void updateLod(Player player) {
        if (lodCheckTicks > 0) {
            lodCheckTicks--;
            return;
        }
        lodCheckTicks = 10;

        // Active combat, flight, or recent damage strictly enforces full 20 TPS fidelity (LOD 1)
        if (currentTarget != null || isPursuing || isSeekingCover || isAmbushing || bot.isAirborne()
                || (System.currentTimeMillis() - lastAttackedTime < 2500)) {
            lodLevel = 1;
            return;
        }

        double closestDistSq = Double.MAX_VALUE;
        Location myLoc = player.getLocation();

        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.isDead() || other.getGameMode() == GameMode.SPECTATOR) continue;
            double d = myLoc.distanceSquared(other.getLocation());
            if (d < closestDistSq) {
                closestDistSq = d;
            }
        }

        // Over 35 blocks away from any player/enemy: throttle AI to every 2nd or 3rd tick
        if (closestDistSq > 50.0 * 50.0) {
            lodLevel = 3;
        } else if (closestDistSq > 35.0 * 35.0) {
            lodLevel = 2;
        } else {
            lodLevel = 1;
        }
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

        // AI LOD (Level of Detail): throttle decision ticks for distant bots (> 35m from active players/enemies)
        updateLod(player);
        if (lodLevel > 1 && (tickCount % lodLevel != 0)) {
            // LOD throttled tick: maintain smooth movement and continuous gravity without heavy raycasts
            if (Math.abs(lastMoveX) > 0.001 || Math.abs(lastMoveZ) > 0.001) {
                bot.moveRel(lastMoveX, 0.0, lastMoveZ);
            } else {
                bot.moveRel(0.0, 0.0, 0.0);
            }
            tickCooldowns();
            return;
        }

        // Airborne / flight check: handles falling or special block launch
        boolean isAirborneFlight = bot.isPadLaunched()
                || (roundWarmupTicks <= 0 && !bot.isOnGround() && (bot.getVelY() < -0.30 || bot.getVelY() > 0.40 || player.getFallDistance() > 0.6));
        if (isAirborneFlight) {
            if (bot.isPadLaunched()) {
                padCooldown = 600; // Cooldown prevents repeated launch loops
            }

            if (cachedLandingPlatform == null || landingPlatformCacheTicks <= 0 || !isPlatformValid(cachedLandingPlatform)) {
                cachedLandingPlatform = findSafeLandingTarget(player);
                landingPlatformCacheTicks = 10;
            }

            CombatProfile profile = getProfile();
            boolean landingError = ThreadLocalRandom.current().nextDouble() < profile.landingErrorChance;
            double steerX = 0.0;
            double steerZ = 0.0;

            if (landingError) {
                // Landing error: slight imprecise drift or missing platform
                steerX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.14;
                steerZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.14;
            } else if (cachedLandingPlatform != null) {
                double pdx = cachedLandingPlatform.getX() - player.getLocation().getX();
                double pdz = cachedLandingPlatform.getZ() - player.getLocation().getZ();
                double pdist = Math.sqrt(pdx * pdx + pdz * pdz);
                if (pdist > 0.15) {
                    double steerSpeed = Math.min(0.32, Math.max(0.18, pdist * 0.30));
                    steerX = (pdx / pdist) * steerSpeed;
                    steerZ = (pdz / pdist) * steerSpeed;
                }
            }

            // CRITICAL: moveRel must ALWAYS be called so gravity and NMS movement tick every single airborne frame!
            bot.moveRel(steerX, 0.0, steerZ);

            // While airborne/pad launched: orient body and head strictly towards safe landing direction!
            if (Math.abs(steerX) > 0.01 || Math.abs(steerZ) > 0.01) {
                float flightYaw = (float) Math.toDegrees(Math.atan2(-steerX, steerZ));
                bot.getNmsPlayer().setRot(flightYaw, 5f);
                bot.getNmsPlayer().setYHeadRot(flightYaw);
                bot.getNmsPlayer().setYBodyRot(flightYaw);
                patrolHeading = flightYaw; // Preserve forward flight heading so bot moves away from edge upon landing
            }

            if (currentTarget != null && canSee(player, currentTarget) && bot.hasGun() && shootCooldown <= 0) {
                bot.shoot();
                shootCooldown = 6;
            }

            tickCooldowns();
            return;
        } else {
            cachedLandingPlatform = null;
            landingPlatformCacheTicks = 0;
        }

        // 1. Vision Update: check if current target is still in direct sight
        boolean targetCurrentlyVisible = (currentTarget != null) && canSee(player, currentTarget);

        if (targetCurrentlyVisible) {
            lastKnownTargetLoc = currentTarget.getLocation().clone();
            searchTicks = 0;
            isPursuing = true;
        } else {
            // Check if any other enemy is visible in vision cone (staggered across ticks to halve raycast overhead)
            int botId = Math.abs(player.getUniqueId().hashCode());
            boolean shouldScan = (tickCount + botId) % 2 == 0 || currentTarget == null;
            if (shouldScan) {
                Player spotted = findVisibleTarget();
                if (spotted != null) {
                    currentTarget = spotted;
                    lastKnownTargetLoc = spotted.getLocation().clone();
                    targetCurrentlyVisible = true;
                    searchTicks = 0;
                    isPursuing = true;
                }
            }
        }

        double smartChance = getSmartBehaviorChance();
        double factor = bot.getSkillFactor();
        PlayerData data = bot.getPlugin().getPlayerDataManager().getData(player.getUniqueId());
        double healthRatio = player.getHealth() / bot.getMaxHealth();

        // 2. Ambush Trap handling (waiting around corner with pre-aim)
        if (isAmbushing) {
            ambushTicks++;
            Location eye = player.getEyeLocation();
            RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 1.2, FluidCollisionMode.NEVER, true);
            boolean wallInFace = hit != null && hit.getHitBlock() != null && hit.getHitBlock().getType().isSolid();

            if (ambushTicks > 30 || wallInFace || (targetCurrentlyVisible && lastKnownTargetLoc != null && player.getLocation().distance(lastKnownTargetLoc) < 5.0)) {
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

            if (isPursuing && lastKnownTargetLoc != null && searchTicks < PURSUIT_TIMEOUT_TICKS) {
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

        // Accuracy, tracking, cooldowns and movement from precomputed CombatProfile
        CombatProfile profile = getProfile();
        float aimInaccuracy = profile.aimInaccuracy;
        float turnSpeed = profile.turnSpeed;
        int minShootCooldown = profile.minShootCooldown;
        double strafeSpeed = profile.strafeSpeed;
        int jukeThreshold = profile.jukeThreshold;
        double maxShootDist = profile.maxShootDist;
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
                    double rushThreshold = profile.rushThreshold;
                    double retreatThreshold = profile.retreatThreshold;
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
            if (ThreadLocalRandom.current().nextDouble() < profile.tacticalJumpChance) {
                bot.getBukkitPlayer().setVelocity(bot.getBukkitPlayer().getVelocity().setY(0.38));
                tacticalJumpCooldown = 20;
            }
        }

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
        double dist = Math.sqrt(mX * mX + mZ * mZ);
        if (dist < 0.001) return;

        if (lockedHeadingTicks > 0) {
            lockedHeadingTicks--;
            double rad = Math.toRadians(-patrolHeading);
            lastMoveX = Math.sin(rad) * dist;
            lastMoveZ = Math.cos(rad) * dist;
            bot.moveRel(lastMoveX, 0.0, lastMoveZ);
            return;
        }

        float moveHeading = (float) Math.toDegrees(Math.atan2(-mX, mZ));
        if (!isDirectionClear(myLoc, moveHeading, Math.max(dist, 1.0))) {
            float altHeading = findClearDirection(myLoc, moveHeading);
            patrolHeading = altHeading;
            lockedHeadingTicks = 6; // Maintain detour heading for 6 ticks (~0.3s) to slide smoothly around obstacle
            double rad = Math.toRadians(-altHeading);
            lastMoveX = Math.sin(rad) * dist;
            lastMoveZ = Math.cos(rad) * dist;
            bot.moveRel(lastMoveX, 0.0, lastMoveZ);
            return;
        }
        lastMoveX = mX;
        lastMoveZ = mZ;
        bot.moveRel(mX, 0.0, mZ);
    }

    private Location findCoverLocation(Player player, Location enemyLoc) {
        Location pLoc = player.getLocation();
        if (pLoc == null || enemyLoc == null) return null;

        // 1. Instant precomputed cover lookup from ArenaNavMesh (O(1), zero block raycasts!)
        ArenaNavMesh mesh = bot.getManager() != null ? bot.getManager().getNavMesh() : null;
        if (mesh != null && !mesh.isEmpty()) {
            Location navCover = mesh.findBestCover(pLoc, enemyLoc, 12.0);
            if (navCover != null) return navCover;
        }

        World world = pLoc.getWorld();
        if (world == null) return null;

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

        CombatProfile profile = getProfile();

        if (distXZ > 1.2) {
            // Abort pursuit immediately if stuck running into a wall on ground (not while in mid-air jumping up steps)
            if (bot.isOnGround() && bot.getNmsPlayer().horizontalCollision) {
                pursuitStuckTicks++;
                if (pursuitStuckTicks > 12) {
                    pursuitStuckTicks = 0;
                    isPursuing = false;
                    currentTarget = null;
                    lastKnownTargetLoc = null;
                    lockedHeadingTicks = 20;
                    patrolHeading = findClearDirection(myLoc, player.getLocation().getYaw() + (ThreadLocalRandom.current().nextBoolean() ? 90f : -90f));
                    return;
                }
            } else {
                pursuitStuckTicks = 0;
            }

            float moveYaw;
            if (lockedHeadingTicks > 0) {
                moveYaw = patrolHeading;
                lockedHeadingTicks--;
            } else {
                moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                // Obstacle avoidance during pursuit: route around blocking walls via NavMesh or clear direction
                if (!isDirectionClear(myLoc, moveYaw, 1.2)) {
                    ArenaNavMesh mesh = bot.getManager() != null ? bot.getManager().getNavMesh() : null;
                    Location nextWp = (mesh != null) ? mesh.findNextWaypoint(myLoc, targetLoc) : null;
                    if (nextWp != null) {
                        double wdx = nextWp.getX() - myLoc.getX();
                        double wdz = nextWp.getZ() - myLoc.getZ();
                        moveYaw = (float) Math.toDegrees(Math.atan2(-wdx, wdz));
                    } else {
                        moveYaw = findClearDirection(myLoc, moveYaw);
                    }
                    patrolHeading = moveYaw;
                    lockedHeadingTicks = 10; // Maintain detour heading for 10 ticks (~0.5s) to smoothly round corner
                }
            }

            double rad = Math.toRadians(-moveYaw);
            double mx = Math.sin(rad);
            double mz = Math.cos(rad);

            bot.getNmsPlayer().setRot(moveYaw, 0f);
            bot.getNmsPlayer().setYHeadRot(moveYaw);
            bot.getNmsPlayer().setYBodyRot(moveYaw);

            lastMoveX = mx * profile.pursuitSpeed;
            lastMoveZ = mz * profile.pursuitSpeed;
            bot.moveRel(lastMoveX, 0.0, lastMoveZ);
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
        Location myLoc = player.getLocation();
        if (myLoc == null || myLoc.getWorld() == null) return;

        // 1. Mark current location in this round's visited territory map
        long myCell = packCell(myLoc.getBlockX(), myLoc.getBlockZ());
        visitedTerritory.add(myCell);

        // 2. Track stuck status (detect getting blocked by walls/corners on ground)
        boolean physicallyBlocked = bot.isOnGround() && bot.getNmsPlayer().horizontalCollision;
        if (physicallyBlocked || (lastHuntPos != null && myLoc.getWorld().equals(lastHuntPos.getWorld()) && myLoc.distanceSquared(lastHuntPos) < 0.005)) {
            huntStuckTicks++;
        } else {
            huntStuckTicks = 0;
        }
        lastHuntPos = myLoc.clone();

        // 3. If stuck trying to reach current target, record edge as blocked and pick a new target
        if (huntStuckTicks > 12) {
            huntStuckTicks = 0;
            if (currentHuntTarget != null) {
                blockedEdges.add(packCell(currentHuntTarget.getBlockX(), currentHuntTarget.getBlockZ()));
            }
            currentHuntTarget = null;
            currentCorridorWaypoint = null;
            lockedHeadingTicks = 25;
            patrolHeading = findClearDirection(myLoc, patrolHeading + (ThreadLocalRandom.current().nextBoolean() ? 90f : -90f));
        }

        // 4. Macro target selection: Hunt enemy team spawn first, then unvisited NavMesh nodes across the whole arena
        ArenaNavMesh mesh = (bot.getManager() != null) ? bot.getManager().getNavMesh() : null;
        if (currentHuntTarget == null || myLoc.distanceSquared(currentHuntTarget) < 4.0) {
            currentHuntTarget = null;
            currentCorridorWaypoint = null;

            // Priority A: Search enemy team spawn
            GameManager gm = (bot.getPlugin() != null) ? bot.getPlugin().getGameManager() : null;
            GameTeam myTeam = (bot.getPlugin() != null) ? bot.getPlugin().getTeamManager().getPlayerTeam(player.getUniqueId()) : null;
            if (gm != null) {
                for (Map.Entry<GameTeam, Location> entry : gm.getTeamSpawns().entrySet()) {
                    if (myTeam == null || entry.getKey() != myTeam) {
                        Location sp = entry.getValue();
                        if (sp != null && sp.getWorld() != null && sp.getWorld().equals(myLoc.getWorld())) {
                            long spCell = packCell(sp.getBlockX(), sp.getBlockZ());
                            if (!visitedTerritory.contains(spCell) && !blockedEdges.contains(spCell)) {
                                currentHuntTarget = sp.clone();
                                break;
                            }
                        }
                    }
                }
            }

            // Priority B: Unvisited ArenaNavMesh node (pro bots / snipers prioritize elevated positions)
            if (currentHuntTarget == null && mesh != null && !mesh.isEmpty()) {
                ArenaNavMesh.NavNode unvisitedNode = mesh.findNearestUnvisitedNode(myLoc, visitedTerritory, blockedEdges, bot.getLevel(), playstyle == BotPlaystyle.TACTICAL_SNIPER);
                if (unvisitedNode != null) {
                    currentHuntTarget = unvisitedNode.loc.clone();
                }
            }

            // Priority C: If whole map / all nodes were visited this round, clear visited memory and loop
            if (currentHuntTarget == null) {
                visitedTerritory.clear();
                blockedEdges.clear();
                if (mesh != null && !mesh.isEmpty()) {
                    ArenaNavMesh.NavNode node = mesh.getRandomNode();
                    if (node != null) {
                        currentHuntTarget = node.loc.clone();
                    }
                } else if (gm != null && gm.getCurrentZoneCenter() != null) {
                    Location center = gm.getCurrentZoneCenter();
                    double ang = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                    double rad = ThreadLocalRandom.current().nextDouble(8.0, 20.0);
                    currentHuntTarget = center.clone().add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                }
            }
        }

        // 5. Corridor micro-routing using NavMesh BFS towards macro hunt target
        if (currentHuntTarget != null && mesh != null && !mesh.isEmpty()) {
            if (currentCorridorWaypoint == null || myLoc.distanceSquared(currentCorridorWaypoint) < 2.5) {
                currentCorridorWaypoint = mesh.findNextWaypoint(myLoc, currentHuntTarget);
                if (currentCorridorWaypoint == null && myLoc.distanceSquared(currentHuntTarget) > 16.0) {
                    blockedEdges.add(packCell(currentHuntTarget.getBlockX(), currentHuntTarget.getBlockZ()));
                    currentHuntTarget = null;
                }
            }
        }

        Location targetPt = (currentCorridorWaypoint != null) ? currentCorridorWaypoint : currentHuntTarget;
        if (targetPt != null) {
            double cdx = targetPt.getX() - myLoc.getX();
            double cdz = targetPt.getZ() - myLoc.getZ();
            float desiredHeading = (float) Math.toDegrees(Math.atan2(-cdx, cdz));

            if (lockedHeadingTicks > 0) {
                lockedHeadingTicks--;
                if (!isDirectionClear(myLoc, patrolHeading, 1.1)) {
                    lockedHeadingTicks = 0;
                    patrolHeading = findClearDirection(myLoc, patrolHeading);
                    lockedHeadingTicks = 12;
                }
            } else if (isDirectionClear(myLoc, desiredHeading, 1.2)) {
                patrolHeading = desiredHeading;
            } else {
                patrolHeading = findClearDirection(myLoc, desiredHeading);
                lockedHeadingTicks = 12; // Maintain detour heading so bot doesn't immediately re-face wall
            }
        } else if (Float.isNaN(patrolHeading)) {
            patrolHeading = myLoc.getYaw();
        }

        // 6. Smooth natural head/body turn towards patrol heading
        float currentYaw = player.getLocation().getYaw();
        float deltaYaw = ((patrolHeading - currentYaw + 540f) % 360f) - 180f;
        float maxTurn = 18f; // ~18 deg/tick smooth rotation for realistic navigation
        float newYaw = (Math.abs(deltaYaw) <= maxTurn) ? patrolHeading : currentYaw + Math.signum(deltaYaw) * maxTurn;
        bot.getNmsPlayer().setRot(newYaw, 0f);
        bot.getNmsPlayer().setYHeadRot(newYaw);
        bot.getNmsPlayer().setYBodyRot(newYaw);

        // 7. Continuous forward exploration movement along current yaw
        CombatProfile profile = getProfile();
        double walkSpeed = profile.walkSpeed;
        double radMove = Math.toRadians(-newYaw);
        lastMoveX = Math.sin(radMove) * walkSpeed;
        lastMoveZ = Math.cos(radMove) * walkSpeed;
        bot.moveRel(lastMoveX, 0.0, lastMoveZ);
    }

    private boolean isDirectionClear(Location loc, float heading, double dist) {
        return isDirectionClear(loc, heading, dist, true);
    }

    private boolean isDirectionClear(Location loc, float heading, double dist, boolean checkPlayers) {
        if (loc == null || loc.getWorld() == null) return true;
        World w = loc.getWorld();
        double rad = Math.toRadians(-heading);
        double dx = Math.sin(rad) * dist;
        double dz = Math.cos(rad) * dist;

        // Normal perpendicular vector for bot width check (hitbox radius ~0.3b)
        double perpX = Math.cos(rad) * 0.28;
        double perpZ = -Math.sin(rad) * 0.28;

        Location feet = loc.clone().add(dx, 0.2, dz);
        Location body = loc.clone().add(dx, 1.2, dz);
        Location head = loc.clone().add(dx, 1.8, dz);
        Location overhead = loc.clone().add(0, 2.1, 0);

        boolean feetSolid = feet.getBlock().getType().isSolid();
        boolean bodySolid = body.getBlock().getType().isSolid();
        boolean headSolid = head.getBlock().getType().isSolid();
        boolean overheadBlocked = overhead.getBlock().getType().isSolid();

        // 1. Center ray & step/stair check
        if (feetSolid) {
            // Check if the obstacle at feet is a climbable 1-block step/stair with open space above
            boolean spaceAboveStep = !feet.clone().add(0, 1.0, 0).getBlock().getType().isSolid()
                    && !feet.clone().add(0, 2.0, 0).getBlock().getType().isSolid();
            if (overheadBlocked || !spaceAboveStep) {
                return false; // >= 2-block high wall or ceiling overhead blocked -> impassable!
            }
            // Step is climbable! Check shoulder clearance above the step
            Location stepLeft = feet.clone().add(-perpX, 1.0, -perpZ);
            Location stepRight = feet.clone().add(perpX, 1.0, perpZ);
            if (stepLeft.getBlock().getType().isSolid() && stepRight.getBlock().getType().isSolid()) {
                return false;
            }
        } else {
            // Flat ground / open air
            if (bodySolid || headSolid) {
                return false;
            }
            // 2. Left and right shoulders width check at body height
            Location bodyLeft = loc.clone().add(dx - perpX, 1.2, dz - perpZ);
            Location bodyRight = loc.clone().add(dx + perpX, 1.2, dz + perpZ);
            if (bodyLeft.getBlock().getType().isSolid() || bodyRight.getBlock().getType().isSolid()) {
                return false;
            }
        }

        // 3. Midpoint width check for longer rays (catches diagonal pinches along the movement path)
        if (dist >= 0.8) {
            double midDx = dx * 0.5;
            double midDz = dz * 0.5;
            Location midBodyLeft = loc.clone().add(midDx - perpX, 1.1, midDz - perpZ);
            Location midBodyRight = loc.clone().add(midDx + perpX, 1.1, midDz + perpZ);
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

        // 5. Strictly avoid walking into jump blocks and up blocks in front on foot (prevents 20% max HP damage)
        com.rounds.blocks.BlockListener bl = bot.getPlugin() != null ? bot.getPlugin().getBlockListener() : null;
        if (bl != null) {
            Location checkLoc = loc.clone().add(dx, 0.1, dz);
            Location checkBelow = checkLoc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
            Location checkAt = checkLoc.getBlock().getLocation();
            boolean touchingPad = bl.isLocationTouchingJumpBlock(checkLoc) || bl.isJumpBlock(checkBelow) || bl.isJumpBlock(checkAt)
                    || bl.isLocationTouchingUpBlock(checkLoc) || bl.isUpBlock(checkBelow) || bl.isUpBlock(checkAt);
            if (touchingPad) {
                return false;
            }
        }

        // Avoid walking directly into another bot or player in front (avoids snake/conga line)
        if (checkPlayers && dist <= 1.5 && loc.getWorld() != null) {
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
            float roomExit = findRoomExit(loc);
            if (!Float.isNaN(roomExit)) {
                lockedHeadingTicks = 35;
                return roomExit;
            }
            lockedHeadingTicks = 35; // Lock heading for ~1.75s to run out of dead end
            return currentHeading + 180f;
        }

        // Try alternating angle deviations from current heading
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
                    // Ping-pong between walls detected: search for room exit or escape with 180
                    consecutiveBounces = 0;
                    lastTurnSign = 0f;
                    float roomExit = findRoomExit(loc);
                    if (!Float.isNaN(roomExit)) {
                        lockedHeadingTicks = 35;
                        return roomExit;
                    }
                    lockedHeadingTicks = 35;
                    return currentHeading + 180f;
                }
                return test;
            }
        }

        // If standard angle deviations fail, sweep 360 to find narrow doorway
        float roomExit = findRoomExit(loc);
        if (!Float.isNaN(roomExit)) {
            consecutiveBounces = 0;
            lastTurnSign = 0f;
            lockedHeadingTicks = 35;
            return roomExit;
        }

        consecutiveBounces = 0;
        lastTurnSign = 0f;
        lockedHeadingTicks = 35;
        return currentHeading + 180f;
    }

    /**
     * Sweep 360 degrees in 30-degree steps to detect narrow openings / doorways (e.g. 2x2 doors in a 3-walled room).
     * Throttled with roomExitCooldown and avoids entity allocations for peak performance.
     */
    private float findRoomExit(Location loc) {
        if (loc == null || loc.getWorld() == null || roomExitCooldown > 0) return Float.NaN;
        roomExitCooldown = 30; // 1.5s cooldown
        float bestHeading = Float.NaN;
        double maxClearDist = 0.0;

        for (float deg = 0f; deg < 360f; deg += 30f) {
            double clearDist = 0.0;
            for (double d = 1.5; d <= 4.5; d += 1.5) {
                if (isDirectionClear(loc, deg, d, false)) {
                    clearDist = d;
                } else {
                    break;
                }
            }
            if (clearDist > maxClearDist && clearDist >= 2.0) {
                maxClearDist = clearDist;
                bestHeading = deg;
            }
        }
        return bestHeading;
    }

    public void onJumpPadLaunched() {
        this.cachedLandingPlatform = null;
        this.landingPlatformCacheTicks = 0;
        this.padCooldown = 600;
    }

    public void onSafeLanding() {
        this.lockedHeadingTicks = 45; // Lock heading for 2.25s into arena interior so bot walks away from edge/wall
        this.currentHuntTarget = null;
        this.currentCorridorWaypoint = null;
        this.cachedLandingPlatform = null;
    }

    public void onCornerStuck(Location loc) {
        if (loc != null) {
            blockedEdges.add(packCell(loc.getBlockX(), loc.getBlockZ()));
        }
        currentHuntTarget = null;
        currentCorridorWaypoint = null;
        patrolStuckTicks = 0;
        patrolHeading = findClearDirection(loc, patrolHeading + (ThreadLocalRandom.current().nextBoolean() ? 90f : -90f));
        lockedHeadingTicks = 35; // Lock heading for 1.75s to walk away from corner
    }

    /**
     * Finds the nearest safe walkable location on the arena to land on during flight or jump pad launch.
     * Evaluates ArenaNavMesh nodes and map geometry, strictly steering towards arena center and away from walls/void.
     */
    public Location findSafeLandingTarget(Player player) {
        if (player == null || player.getWorld() == null) return null;
        Location pLoc = player.getLocation();
        World world = player.getWorld();
        BlockListener bl = bot.getPlugin() != null ? bot.getPlugin().getBlockListener() : null;

        double px = pLoc.getX();
        double py = pLoc.getY();
        double pz = pLoc.getZ();

        Location center = bot.getPlugin() != null && bot.getPlugin().getGameManager() != null
                ? bot.getPlugin().getGameManager().getCurrentZoneCenter()
                : null;

        double toCenterX = 0.0;
        double toCenterZ = 0.0;
        if (center != null && center.getWorld() != null && center.getWorld().equals(world)) {
            double cdx = center.getX() - px;
            double cdz = center.getZ() - pz;
            double cdist = Math.sqrt(cdx * cdx + cdz * cdz);
            if (cdist > 0.1) {
                toCenterX = cdx / cdist;
                toCenterZ = cdz / cdist;
            }
        }

        // 1. Prefer precomputed ArenaNavMesh nodes that lie in the direction of the arena center
        ArenaNavMesh mesh = bot.getManager() != null ? bot.getManager().getNavMesh() : null;
        if (mesh != null && !mesh.isEmpty()) {
            ArenaNavMesh.NavNode bestNode = null;
            double bestScore = -Double.MAX_VALUE;

            for (ArenaNavMesh.NavNode node : mesh.getAllNodes()) {
                if (node.loc.getY() > py + 35.0 || node.loc.getY() < py - 20.0) continue;
                if (bl != null && (bl.isJumpBlock(node.loc) || bl.isUpBlock(node.loc))) continue;

                double dx = node.loc.getX() - px;
                double dz = node.loc.getZ() - pz;
                double distSq = dx * dx + dz * dz;
                if (distSq < 2.0 * 2.0) continue; // Must be at least 2 blocks away horizontally from pad

                double dot = (toCenterX != 0.0 || toCenterZ != 0.0) ? (dx * toCenterX + dz * toCenterZ) : 1.0;
                if (dot < -0.2) continue; // Do not choose nodes behind the bot towards the void/wall

                // Score balances proximity, alignment towards arena center, and platform breadth (many walkable blocks)
                double score = dot * 20.0 - Math.sqrt(distSq) + node.breadth * 15.0;
                if (score > bestScore) {
                    bestScore = score;
                    bestNode = node;
                }
            }
            if (bestNode != null) {
                return bestNode.loc.clone();
            }
        }

        // 2. Fallback: Raymarch from bot towards arena center to find safe walkable floor
        if (center != null && center.getWorld() != null && center.getWorld().equals(world)) {
            double dirX = center.getX() - px;
            double dirZ = center.getZ() - pz;
            double distToCenter = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (distToCenter > 1.0) {
                double nx = dirX / distToCenter;
                double nz = dirZ / distToCenter;
                int startY = (int) Math.max(center.getY() + 3.0, py);

                for (double d = 2.5; d <= distToCenter; d += 1.0) {
                    int bx = (int) Math.floor(px + nx * d);
                    int bz = (int) Math.floor(pz + nz * d);

                    for (int dy = 0; dy >= -18; dy--) {
                        int by = startY + dy;
                        Block floor = world.getBlockAt(bx, by, bz);
                        if (!floor.getType().isSolid()) continue;
                        Block above1 = world.getBlockAt(bx, by + 1, bz);
                        Block above2 = world.getBlockAt(bx, by + 2, bz);
                        if (above1.getType().isSolid() || above2.getType().isSolid()) continue;

                        Location floorLoc = floor.getLocation();
                        if (bl != null && (bl.isJumpBlock(floorLoc) || bl.isUpBlock(floorLoc))) continue;

                        return new Location(world, bx + 0.5, by + 1.0, bz + 0.5);
                    }
                }
            }
            return center.clone();
        }

        // 3. Last resort fallback: check downward/nearby 8x8 blocks
        return findNearbySolidPlatform(pLoc, world, bl);
    }

    private Location findNearbySolidPlatform(Location pLoc, World world, BlockListener bl) {
        int px = pLoc.getBlockX();
        int py = pLoc.getBlockY();
        int pz = pLoc.getBlockZ();

        Location best = null;
        double minD = Double.MAX_VALUE;

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int bx = px + dx;
                    int bz = pz + dz;

                    for (int dy = 4; dy >= -12; dy--) {
                        int by = py + dy;
                        Block floor = world.getBlockAt(bx, by, bz);
                        if (!floor.getType().isSolid()) continue;
                        Block above1 = world.getBlockAt(bx, by + 1, bz);
                        Block above2 = world.getBlockAt(bx, by + 2, bz);
                        if (above1.getType().isSolid() || above2.getType().isSolid()) continue;

                        Location floorLoc = floor.getLocation();
                        if (bl != null && (bl.isJumpBlock(floorLoc) || bl.isUpBlock(floorLoc))) continue;

                        double d = dx * dx + dz * dz;
                        if (d < minD) {
                            minD = d;
                            best = new Location(world, bx + 0.5, by + 1.0, bz + 0.5);
                        }
                        break;
                    }
                }
            }
            if (best != null) return best;
        }
        return best;
    }

    private boolean isPlatformValid(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        BlockListener bl = bot.getPlugin() != null ? bot.getPlugin().getBlockListener() : null;
        if (bl != null && (bl.isJumpBlock(loc) || bl.isUpBlock(loc))) return false;
        Block b = loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        if (bl != null && (bl.isJumpBlock(b.getLocation()) || bl.isUpBlock(b.getLocation()))) return false;
        return b.getType().isSolid();
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
        if (roomExitCooldown > 0) {
            roomExitCooldown--;
        }
        if (landingPlatformCacheTicks > 0) {
            landingPlatformCacheTicks--;
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
