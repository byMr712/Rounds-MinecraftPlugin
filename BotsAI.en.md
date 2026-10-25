# Combat Bot AI Architecture and Specification (Fake Player AI)

The Rounds plugin features a fully autonomous server-side Fake Player system built on native Minecraft NMS (`net.minecraft.server.level.ServerPlayer`). The bots operate purely on server ticks without external bridge plugins (zero external bridge) or client-side bot processes.

---

## 1. NMS Architecture and Entity Lifecycle

### 1.1. Initialization and Network Layer
- Each bot instantiates a native `ServerPlayer` instance with a synthetic profile `GameProfile(UUID, name)`.
- **Network Pipeline Stubs:**
  - `NoopConnection`: a custom no-op network pipeline that drops all outbound network packets, reducing client bandwidth overhead to zero.
  - `FakeConnection`: simulates client-side connection semantics, ensuring proper registration within Minecraft's `PlayerList`.
- Bots are registered in the server `PlayerList`, displayed on Tab lists, scoreboards, and targeting systems identically to human players.
- **Bulk Spawning (Up to 500 Bots):** bulk bot spawning is supported up to 500 bots in a single command via `/rbot spawn count <N>` with automatic team balancing.
- **Safe Despawn and Memory Release (Zero Memory Leak):** when the game stops, the round ends, or bots are despawned (`despawnBot`/`despawnAll`), all references across plugin managers (`PlayerDataManager.unregisterPlayer`, `GameManager.onPlayerQuit`, `CardManager`, `CardGUI`, `RoundsEntities`, `BlockListener`) are unlinked, NMS entities are cleanly disconnected and discarded (`discard()`, `removePlayerImmediately`), `ArenaNavMesh` is released, and `System.gc()` is triggered to return uncommitted virtual memory pages back to the hosting OS kernel.

### 1.2. Dynamic Entity Reference Safety on Respawn
In Spigot/Paper, when a player dies and respawns, the `ServerPlayer` instance is recreated with a new physical object. To prevent calls to stale (dead/invalid) references, `FakeBot` enforces dynamic entity getters:
```java
public Player getBukkitPlayer() {
    Player p = Bukkit.getPlayer(bukkitPlayer.getUniqueId());
    if (p != null) this.bukkitPlayer = p;
    return this.bukkitPlayer;
}

public ServerPlayer getNmsPlayer() {
    DedicatedServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
    ServerPlayer sp = nmsServer.getPlayerList().getPlayer(bukkitPlayer.getUniqueId());
    if (sp != null) this.nmsPlayer = sp;
    return this.nmsPlayer;
}
```
Upon a `PlayerRespawnEvent`, `BotManager` invokes `bot.updatePlayer(event.getPlayer())`, keeping the active entity reference synchronized.

### 1.3. Artificial Intelligence Tick Loop (`BotAI`)
Each active bot runs an independent 1-tick `BotAI` scheduled task:
```java
this.ai = new BotAI(this);
this.ai.runTaskTimer(manager.getPlugin(), 1L, 1L);
```
The tick loop executes in the following sequence:
1. Game state validation (`GameState.CARDS`, `GameState.ACTIVE`).
2. Jump pad trajectory and mid-air flight physics tracking.
3. Field of view scanning and target detection adhering to true line of sight.
4. Tactical positioning: corner ambushes, cover finding, retreating, or shield healing.
5. Combat maneuvers: strafing, juking, evasive jumps, aiming, and firing.
6. Navigation along the `ArenaNavMesh` waypoint graph, gap leaping, and wall detouring.
7. Cooldown timers: weapon reload, shield abilities, and jump pad parrying.

---

## 2. Difficulty Scaling System (Levels 1..10)

Bot combat proficiency is governed by an integer level $L \in [1..10]$:
- **Skill Factor:** `factor = level / 10.0` (ranging from `0.10` at Level 1 to `1.00` at Level 10).
- **Scaling Step:** exactly **+10%** combat capability per difficulty level.
- **Dynamic Reroll:** at the start of each round, `botManager.randomizeBotLevels()` redistributes random levels across all active bots for dynamic encounters.

### Scaling Formulas:
| Parameter | Formula | Level 1 (`factor=0.1`) | Level 10 (`factor=1.0`) |
| :--- | :--- | :--- | :--- |
| **Aim Error Spread** | `(1.2f / factor) * 0.90f` | $\pm 10.8^\circ$ (noticeable misses) | $\pm 1.08^\circ$ (sniper precision) |
| **Aim Tracking Speed** | `(6.0f + 8.0f * factor) * 1.10f` | $7.48^\circ$ / tick (smooth tracking) | $15.4^\circ$ / tick (fast human flick) |
| **Fire Cooldown** | `round((4 + (1.0 - factor) * 14.0) * 0.90)` | 15 ticks (deliberate fire) | 4 ticks (maximum DPS) |
| **Strafe Velocity (Agility)** | `(0.12 + 0.04 * factor) * 1.10` | 0.136 blocks/tick | 0.176 blocks/tick |
| **Strafe Direction Change (Juking)** | based on playstyle | every 50–85 ticks (2.5–4.2s) | every 26–50 ticks (1.3–2.5s) |
| **Maximum Shooting Distance** | `10.0 + 30.0 * factor` | 13.0 meters | 40.0 meters |
| **Long-Range Shot Chance (>8m)** | `rnd < factor` | 10% chance | 100% confident fire |
| **Shield Block Chance on Hit** | `rnd < factor` | 10% chance | 100% reflexive block |
| **Jump Pad Shield Parry** | `level >= 5 && rnd < 0.50` | 0% (always takes 20% dmg) | 50% chance to parry pad damage |
| **Parkour Leap Error Chance** | stepped tiers (-10%) | 27% (Lv 1–3), 18% (Lv 4–7) | 0% (Lv 10, perfect leaps) |
| **Air Control Landing Error Chance** | stepped tiers (-10%) | 9% (Lv 1–3), 6.3% (Lv 4–7) | 0.9% (Lv 10) |

---

## 3. Combat Personality Archetypes (Playstyles)

At round start (`BotAI.onRoundStart()`), each bot is assigned one of four distinct archetypes:

### 3.1. `AGGRESSIVE_FLANKER`
- **Combat Range:** 5.0–9.0 meters. Actively closes distance to flank targets from sides or rear.
- **Patrol Route:** patrols the outer perimeter of the arena (15–22 blocks from center).
- **Maneuvers:** moderate juking frequency (34–58 ticks), evasive jumps with chance `0.015 * factor` under fire.

### 3.2. `TACTICAL_SNIPER`
- **Combat Range:** 16.0–24.0 meters. If an enemy closes within 10 meters, immediately enters tactical retreat (`CombatTactic.RETREAT`).
- **Patrol Route:** prioritizes elevated platforms, stairs, and arena vantage points.
- **Maneuvers:** avoids jumping while aiming to preserve uninterrupted line-of-fire accuracy.

### 3.3. `DEFENSIVE_TURTLE`
- **Combat Range:** mid-range 10.0–15.0 meters.
- **Defense:** raises shield or retreats behind cover upon emptying ammo (`ammo <= 0`) or taking heavy damage.
- **Synergies:** maximizes cards such as `Heal` on block, lifesteal, and pre-charged `Empower`.

### 3.4. `ERRATIC_BRAWLER`
- **Combat Range:** aggressive close-quarters dueling at 7.0–11.0 meters.
- **Juking:** frequent, unpredictable strafe reversals every 26–46 ticks with smooth velocity interpolation.
- **Dodging:** tactical hops (`velY = 0.38`) while firing with probability `0.025 * factor`.

---

## 4. Fair Vision and Anti-Wallhack Target Detection

Bots cannot see through opaque solid blocks and follow strict visual mechanics:
1. **Field of View (FOV):** 140° (70° on each side of gaze direction). Calculated via dot product:
   ```java
   Vector toTarget = targetEye.toVector().subtract(botEye.toVector()).normalize();
   Vector dir = botEye.getDirection().normalize();
   double dot = dir.dot(toTarget);
   double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
   if (angle > FOV_HALF_ANGLE_DEG) return false;
   ```
2. **Maximum Vision Range:** 60 blocks (`MAX_VISION_DISTANCE_SQ = 3600.0`).
3. **Footstep Proximity Hearing:** 2.5 meters. Within this radius, bots can detect enemy footsteps from behind unless separated by solid walls.
4. **Line-of-Sight Ray Tracing:** `botPlayer.hasLineOfSight(target)` is mandatory. Any solid block blocks detection.

---

## 5. Incoming Fire Awareness (Damage Direction Reaction)

When damaged, bots determine the shot trajectory origin via `onAttacked(Location origin, UUID attackerId)`:
1. **Debouncing:** reactions trigger at most once every 250ms to prevent rapid-tick spam.
2. **Reaction Chance:** governed by `smartChance = getSmartBehaviorChance()`.
3. **Angular Error Margin:** bots compute angles to the shooter and introduce level-scaled error:
   ```java
   float maxError = (float) ((1.0 - smartChance) * 90.0);
   float yawError = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * (maxError * 0.5f);
   float pitchError = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * (maxError * 0.3f);
   ```
   - At Level 10, error is within $\pm 4.5^\circ$ (near-instant target lock).
   - At Levels 1–2, error reaches $\pm 45^\circ$.
4. **Natural Turn Interpolation:** rotational velocity is smoothly eased across head and body without instant 0ms snaps.

---

## 6. Pursuit Tenacity and Search Timeouts

When line of sight is broken (target retreats behind a wall), bots initiate pursuit:
1. **Continuation Probability:** scaled per difficulty tier:
   - **Levels 1–2:** **69.0%**
   - **Levels 3–4:** **82.8%**
   - **Levels 5–7:** **96.6%**
   - **Levels 8–10:** **100%**
2. **Pursuit Timeout (`PURSUIT_TIMEOUT_TICKS = 120`):** bots pursue the last known target location for up to 6.0 seconds.
3. Upon arriving (`distXZ <= 1.2`), bots halt for 25 ticks to scan adjacent corridors (`searchScanYaw`). If the target is not found within 120 ticks, the bot transitions back to macro arena patrol.

---

## 7. Tactical Cover, Corner Ambushes, and Healing

1. **Cover Search:**
   - Triggers when health drops to $\le 20\%$ HP (or $\le 25\%$ with the `Heal` card).
   - Uses precomputed `coverNodes` from `ArenaNavMesh` or radial raymarching across 8 directions at 4.0–6.5m.
2. **Cover Behavior:**
   - With `Heal`, raises shield every 20 ticks to regenerate HP.
   - With `Chameleon`, stands motionless to activate invisibility.
3. **Corner Ambush Trap:**
   - When pursuing an enemy around a corner, bots can establish an ambush (`isAmbushing`):
     - Does not face the wall: computes open corridor clearance (`ambushHoldYaw`), aiming into the enemy's expected entry angle.
     - Pre-raises shield to charge the `Empower` bonus.
     - Crouching on Shift is disabled as redundant (player names are globally hidden).
     - Fires an empowered burst the instant the target rounds the corner.
     - Once the ambush timer expires (`ambushTicksLeft <= 0`), the bot smoothly resumes patrol.

---

## 8. 3D Movement Engine and Terrain Traversal (`FakeBot.moveRel`)

1. **Movement Integration and Horizontal Momentum:**
   - Movement executes through native NMS `sp.move(MoverType.SELF, new Vec3(moveX, finalVelY, moveZ))`.
   - Gravity: `velY = Math.max(velY - 0.08, -3.92) * 0.98`.
   - **Smooth WASD Momentum:** horizontal velocity interpolates with acceleration `accel = onGround ? 0.42 : 0.28`. Eliminates abrupt 1-tick velocity snaps.
   - **Eased Aim Tracking:** exponential tracking curve `turnFactor = 0.32 + 0.16 * factor`.

2. **Step Jumping and Block Stair Traversal (+30%):**
   - The `isBlockStaircase(loc, heading, dist)` algorithm classifies 1-block terrain ascents and staircases as walkable steps when headroom >= 1.8m is clear, distinguishing them from solid walls.
   - Step lookahead distance is increased by +30% (from 0.55 to 0.72 blocks), and forward jump acceleration impulse is boosted by +30% (`velX/velZ * 1.30`), preventing bots from getting stuck on staircase steps.
   - **Smooth Stair Descent:** if a bot steps off a ledge and solid ground exists 1.1m below, downward velocity is stabilized at `velY = -0.32`, preventing mid-air float.
   - **Corner Anti-Stuck:** `consecutiveJumpAttempts` aborts jumping after 3 failed attempts, rerouting the bot along a detour.

3. **Entity Anti-Stacking:**
   - If a bot lands on another player's head (`dy` between -1.95 and -0.4, `distXZ < 0.64`), an immediate lateral push impulse of `0.32` is applied, preventing players from standing on top of each other.

4. **Parkour Gap Leaping (`findParkourLandingFloor`):**
   - Scans forward terrain up to 4.2m (scaled by speed and jump multiplier cards).
   - When a valid landing platform is found, the bot sprint-leaps (`velY = 0.42..0.46 * jumpMult`, `impulse = (0.16 + 0.08 * factor) * speedMult`).
   - Under-jump failure rate: 30% at Lv 1–3, 20% at Lv 4–7, 10% at Lv 8–9, 0% at Lv 10.
   - **Cliff Drop Prevention:** ledges with drops over 2 blocks are treated as impassable unless a safe landing platform exists ahead.

---

## 9. Narrow Passage Navigation, Red Zones, and Wall Detours

1. **Red Zones Blacklist for 3x3–5x5 Rooms and 1x1x1 Pockets:**
   - **Room Red Zones (3x3, 4x4, 5x5):** upon exiting an enclosed room, the bot marks its center as a Red Zone for **60 seconds** (`RedZone`). During patrol and search, entering this zone is strictly forbidden, eliminating endless re-entry loops. When already inside the zone, movement directed outward away from center is always permitted to allow escaping. Entry from outside is only permitted during active pursuit of a combat target (`currentTarget != null`).
   - **Vertical Room Exit Priority:** before choosing a doorway, `findRoomExit` sweeps 360° to detect block stairs (`isBlockStaircase`) or parkour platforms leading upward, prioritizing them over doorways to explore higher floors.
   - **Single 1x1x1 Pockets (`checkSinglePocketEscape`):** when 3 solid walls surround the block, the bot immediately snaps its head and body toward the open exit (without slowly staring into the corner), locks its escape heading for 35 ticks (1.75s), and blacklists the pocket as a Red Zone for **60 seconds**.
   - **Zero Wall & Corner Staring:** when facing an obstacle, the bot rapidly rotates away at up to 45°/tick (instead of slowly panning across the wall at 12°/tick) and pauses forward movement until turned away, avoiding inside corners between connected blocks (`isInsideCorner`).
   - **Seamless Block Stair & Parkour Traversal:** `isBlockStaircase` evaluates elevation changes with rise <= 1 block and clear headroom. Increased forward jump impulse (0.24) and momentum persistence during ascent (`jumpAscentTicks`) ensure smooth first-attempt clearance of steps without rollbacks.
   - **Doorway Scanning (`findRoomExit`):** 360° radial scan in 22.5° steps identifies the deepest open passage, locking heading for 45 ticks.

2. **Flat Wall Detouring Without Oscillation (Persistent Detour & Dynamic Corner Bypass):**
   - When hitting a flat wall, `findClearDirection` selects a detour sign (-1 for left, +1 for right) and stores it in `wallDetourSign`.
   - Subsequent checks in `findClearDirection` prioritize offsets matching `wallDetourSign`, eliminating back-and-forth ping-pong oscillations along flat walls.
   - Detour heading is locked for **35 ticks** (~1.75s, covering 5–6 meters).
   - **Dynamic Corner Clearing:** every tick tests `isDirectionClear(myLoc, desiredHeading, 2.0)`. The moment the wall corner is cleared, heading lock drops to 0, `wallDetourSign` resets, and the bot immediately resumes course toward its target.
   - In `ArenaNavMesh.getNearestNode(loc)`, a 6-meter line-of-sight walkability check (`isWalkableConnection`) prevents Dijkstra from choosing a starting node located on the opposite side of a solid wall.

---

## 10. Smart Jump Pads and Elevator Up-Blocks

1. **Rounds Jump Pad Mechanics:**
   - Stepping on a jump pad launches the entity upward and inflicts 20% max HP damage.
   - **Round Start Delay:** intentional jump pad targeting is frozen for 3 to 15 seconds (average 5.0 seconds), preventing bots from leaping off spawn at the start of rounds.
   - **Accidental Triggers:** if a bot steps on a jump pad during combat or patrol, it launches normally.

2. **Shield Parry Jump:**
   - Bots of Level 5 and above (`level >= 5`) have a **50%** chance to raise their shield 10–20 ticks before landing on a jump pad.
   - A successful parry yields full launch impulse while completely negating pad damage (0 damage) with a shield block sound.

3. **Center 1-Block Jump Pad Trap Escape:**
   - Jump pads are never treated as walkable solid steps to auto-jump onto.
   - In `isDirectionClear`, if a jump pad is directly ahead but a safe arena floor exists across the pad within parkour leap range (`hasParkourTargetAhead`), the direction is permitted, allowing the bot to sprint-jump over the jump pad ring.
   - `findSafeLandingTarget` and `findNearbySolidPlatform` enforce platform breadth (`breadth >= 3`) and reject blocks touching jump pads (`isLocationTouchingJumpBlock`). An isolated 1-block center island is never selected as a landing target. When launched from the center, horizontal impulses steer outward toward open arena quadrants.

4. **Natural Air Control:**
   - In mid-air, bots steer toward the target platform with speeds up to 0.24 blocks/tick.
   - Supports double jumping across intermediate ledges when platforms exceed 8–10 blocks away.
   - Continuous gravity and movement calls prevent floating.
   - Landing error rates scale from 10% (Lv 1–3) down to 1% (Lv 10).

5. **Up-Blocks (Elevators):**
   - Automatically identifies upper floor exit platforms (`findUpBlockExitPlatform`), receiving a gentle lateral push onto the floor and a downward landing impulse of `velY = -0.32`.

---

## 11. Card Synergies and Shield Block Abilities

Bots inspect active card modifiers in `PlayerData` to adjust combat behavior:
1. **Saw:** while holding shield, enters a sprint charge (`speed = 0.46`), closing distance (< 2.8 blocks) to shred enemies with the fiery saw ring.
2. **Active Shield Block Abilities:**
   - `Implode`: blocks at 4–7m to pull opponents into an explosive center.
   - `Silence` / `SilenceAura`: blocks within 5m to silence enemy guns and abilities.
   - `EMP`: blocks when closing within 6m to inflict heavy slowness.
   - `Overpower`: blocks at point-blank range for percentage damage.
   - `BombOnBlock` / `ShieldCharge`: spawns explosive bombs and extends parry windows.
   - `Teleport`: blinks upon blocking to dodge shots or close distance.
   - `Empower`: if weapon cooldown is $\le 1$ tick, briefly raises shield before firing to pre-charge bonus damage.
   - `Heal`: if HP < 75%, regularly shields behind cover to regenerate health.
   - `Shockwave`: blasts targets backward within 4.5m.
   - `HomingOnBlock`: activates shield to enable temporary bullet homing.
   - `TacticalReload`: activates shield when ammo is depleted (`ammo <= 0`) for an instant reload.
3. **Movement Modifiers:** jump cards enable recognition of 2–3 block obstacles; speed cards proportionally accelerate strafing and pursuit.
4. **Stances:** sniper stance (`Grow`), aura rush (`LifestealAura`), camouflage ambush (`Chameleon`).

---

## 12. Card Draft Phase (`GameState.CARDS`)

1. **Complete Immobility and Invulnerability:**
   - During card selection, all players and bots are completely frozen: `PlayerMoveEvent` cancels X/Y/Z translation (retaining camera rotation), velocity is zeroed, and `FakeBot.moveRel` is disabled.
   - All incoming damage is canceled at `LOWEST` priority, health is restored to 100%, and fire is extinguished.
   - Gun shooting, shield blocking, jump pad launches, and up-block lifts are completely disabled.
2. **Automated AI Card Selection:**
   - `findBestCardSlot(Inventory inv)` scans the draft inventory and weights cards by rarity:
     `LEGENDARY` (5) > `EPIC` (4) > `RARE` (3) > `UNCOMMON` (2) > `COMMON` (1).
   - With probability `factor`, bots select the highest-tier card available; otherwise, they select a random valid card.
   - Deliberation delay ranges from 1 to 10 seconds (average 3–5 seconds), simulating natural player decision-making.

---

## 13. High-Performance Optimization (Stable 20 TPS with 192+ and up to 500 Bots)

1. **Immutable Combat Profile Matrix (`CombatProfile`):**
   - All accuracy, tracking, cooldown, and strafe parameters are precomputed into an immutable `CombatProfile[11][4]` matrix at class load time, enabling $O(1)$ zero-allocation lookups during ticks.
2. **Tick Interleaving for Vision Scans:**
   - Expensive line-of-sight checks alternate between bots via `(tickCount + entityId) % 2 == 0`, cutting server raycast overhead in half.
3. **Room Scan Throttling:**
   - Doorway searches have a 30-tick cooldown (`roomExitCooldown`) and a 30° radial step.
4. **Precomputed Arena NavMesh (`ArenaNavMesh`):**
   - The waypoint graph is built once per round (~1ms for 120–160 nodes) and shared across all bots, providing fast Dijkstra corridor routing and reputation tracking (`pathReputation`).
5. **Adaptive 4-Tier AI Level of Detail (LOD):**
   - LOD 1 (0–12m from enemies): 20 TPS (every tick).
   - LOD 2 (12–25m from enemies): 10 TPS (every 2 ticks).
   - LOD 3 (25–40m from enemies): 7 TPS (every 3 ticks).
   - LOD 4 (>40m, or >25m when active bots >= 50): 5 TPS (every 4 ticks).
   - Distance checks filter out teammates: bots grouped with allies far from opponents properly enter power-saving LOD mode. Movement physics and gravity remain continuous on every tick.
6. **Two-Stage Target Filtering (FOV & Distance-First in `findVisibleTarget`):**
   - Rather than executing expensive block raycasts (`hasLineOfSight`) across all arena entities, targets are pre-filtered via $O(1)$ mathematical checks:
     1) Distance squared is strictly closer than the current best candidate (`distSq < bestDistSq`).
     2) Target falls within the 140° visual cone via dot product calculation.
   - Heavy NMS `hasLineOfSight` raycasts are only invoked for candidates that pass both criteria, eliminating over 99% of raycast computations during large battles.
   - The `isTeammate` helper compares `bot.getAssignedTeam()` directly for bots, skipping player map lookups and UUID equality overhead.
7. **Batch Team Synchronization & Scoreboard Exclusion ($O(1)$):**
   - During `startRound()` with 192+ bots, team color updates are applied in batch mode (`applyTeamColor(p, false)` with a single final `refreshTeamScoreboards()`), eliminating $O(N \cdot T)$ hash table rebuild cascades and preventing server watchdog timeouts.
   - Bots (`FakeBot`) have no network connections and are excluded from `playerScoreboards` tracking, preventing useless allocation of scoreboard objects.
   - Entry lookups in `setupTeamsOnScoreboard` use direct $O(1)$ access (`sb.getEntryTeam(name)` and `sb.getTeam(name)`) instead of rebuilding and iterating through `sb.getTeams()`.
8. **Anti-Stacking Branch Pruning in `FakeBot.moveRel`:**
   - Entity dislodging loops filter candidates using fast 1D bounding box prunes (`Math.abs(dx) > 1.2 || Math.abs(dz) > 1.2`), skipping Euclidean distance calculations on hundreds of surrounding entities.

---

## 14. Team Balancing and Map Wool Block Affinity

- When spawning bots, the plugin inspects the team wool blocks placed on the arena (`joinBlocks`).
- If an arena only features specific colors (e.g. Red and Blue wool), bots only populate teams `RED` and `BLUE`.
- If a command specifies a team not present on the map, the bot is automatically reassigned to an available team with the fewest members.

---

## 15. Comprehensive Bot Command Reference

### `/rbot` Commands:
- `/rbot spawn`: spawns a single bot with an auto-generated name, balanced team, and random level (1..10).
- `/rbot spawn <N>` or `/rbot spawn count <N>`: bulk spawns up to 500 bots with balanced team distribution.
- `/rbot spawn <name> [team] [1..10]`: spawns a bot with explicit name, team, and level.
- `/rbot despawn [name]`: removes a specific bot or all bots if no name is provided.
- `/rbot list`: displays all active bots with health, team, difficulty level, and archetype.
- `/rbot level <name> <1..10>`: sets the difficulty level for a specific bot.
- `/rbot level all <1..10>`: sets the difficulty level for all active bots.
- `/rbot <name> level <1..10>`: alternative syntax for level setting.
- `/rbot <name> strategy <flanker|sniper|turtle|brawler>`: overrides a bot's behavioral archetype.

### `/rdebug bot` Commands:
- `/rdebug bot spawn <count>`: spawns the specified number of bots.
- `/rdebug bot clear`: despawns all bots on the server.
- `/rdebug bot list`: diagnostic listing of bot entities.
- `/rdebug bot level <1..10>`: sets the global default difficulty for subsequent spawns.

---

## 16. Combat Hit Visuals, Highlights, and Nametag Formatting

1. **Nametag Formatting and Bot Level Tag:**
   - Display in Tab list and overhead: `<hearts>♥ <Name>` for players and `<hearts>♥ <Name> [L<level>]` for bots.
   - The bot level tag is located after the bot name, styled in bold team color (`§l[L1]`).
   - Heart count represents proportional health rounded up.
2. **Team-Only Nametags:**
   - During active rounds, enemy nametags are completely invisible to players (`Team.OptionStatus.FOR_OWN_TEAM` / `NEVER`). Players only see their teammates.
   - Spectators and eliminated players can see nametags for all participants.
3. **Combat Hit Visual Feedback:**
   - When hitting an enemy, `victim.playHurtAnimation(yaw)` is triggered, flashing their 3D model bright red and tilting away from damage vector.
   - Players taking damage experience camera tilt toward incoming fire and a smooth red screen border vignette (`WorldBorder` warning).
4. **Card Highlight Duration and FakeBot Potion Ticking:**
   - The "Mark Was Good" card limits enemy highlights to 10 seconds (200 ticks) upon moving away.
   - `FakeBot` ticks active potion effect durations each tick, ensuring highlights and slowness effects expire naturally.
