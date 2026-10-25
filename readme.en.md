# 🎯 RoundsPlugin — The ROUNDS Minigame in Minecraft

> **Language:** [Русский](readme.md) · English

**RoundsPlugin** brings the chaotic, competitive 2D/3D platformer shooter **ROUNDS** directly to your Minecraft server!

No tedious sword clicking here: players wield fast-firing guns paired with projectile-deflecting shields, battle round by round, and losing teams draft game-changing upgrade cards (ricochets, lifesteal, poison clouds, chain lightning, saws, phantoms, wall-piercing drills, and much more).

The plugin is 100% self-contained, sets up in just 2 minutes using in-game physical blocks, and includes **advanced native AI bots** allowing players to duel and practice even completely solo!

---

## 💥 Key Features

- ⚔️ **Team Firefights (Up to 4 Teams):** Blue, Red, Yellow, Green teams + seamless spectator mode.
- 🔫 **Custom Weapon (Gun & Shield):**
  - **Left Click:** Fires physics-driven bullets with ricochets, bullet speed, and card effects.
  - **Right Click:** Raises shield to deflect bullets and trigger explosive counter-abilities.
  - **Q (Drop Key):** Manually reload your magazine with a live Action Bar progress bar.
- 🃏 **111+ Unique Upgrade Cards:**
  - Massive ability catalog: homing bullets, poison clouds, frost slow, wall drills, executions, close-range saw shredding, and second-wind revives.
  - 5 rarity tiers: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`.
  - **Comeback Mechanics:** The losing team drafts a new card between rounds. The more you lose, the stronger you become!
  - In-game deck inventory viewer (`/rdebug cards show`) and administrative card management.
- 🤖 **Native Smart Bots (NMS Fake Players):**
  - Real server-side players with 10 difficulty tiers — not dumb zombies.
  - They strafe, take cover, heal behind walls, hold open hallway angles in ambushes, ride jump pads and elevator lifts, and draft cards.
  - Bulk spawn up to 500 bots with zero server lag (profile matrix, multi-level NavMesh, and adaptive LOD tickrate).
  - Automatically detect placed team blocks on the arena and only join valid teams!
- 👁️ **Spectator Outlines & Team Glow:**
  - Spectators and eliminated players exclusively see glowing outlines around all alive players in their team colors.
  - Survival players do NOT see this glow — keeping stealth, sneaking, and the Chameleon card fair and effective!
  - Spectators receive clean, team-colored victory titles at the end of each round.
- 🧱 **2-Minute Arena Setup:** No messing around with coordinate math in configs — grab special blocks with `/rdebug giveblocks` and place them directly in the world.
- 🔌 **Full Server Integration:** Compatible with Paper / Purpur 1.20.4+ (up to 1.21.x / 26.2), full TAB and PlaceholderAPI support, plus instant multi-language switching.

---

## 🛠️ Quick Start: Setting up an Arena in 2 Minutes

You never need to copy-paste coordinates into YAML files. Everything is configured with physical blocks in-game:

### Step 1. Get the Setup Blocks
Join your server with OP permissions (or `rounds.admin`) and run:
```
/rdebug giveblocks
```
You will receive stacks of all special blocks, and your gamemode will automatically switch to **Creative**.

### Step 2. Build the Lobby
1. Place a **Lobby Block** (Emerald Block / Beacon) where players should spawn before and after matches.
2. Place colored wool blocks on the floor for team selection:
   - 🟦 **Light Blue Wool** — Join Blue Team.
   - 🟥 **Red Wool** — Join Red Team.
   - 🟨 **Yellow Wool** — Join Yellow Team.
   - 🟩 **Lime Wool** — Join Green Team.
   - ⬜ **White Wool** — Become a Spectator.
   > *Tip:* Building a 1v1 duel map? Simply place only Red and Blue wool — the plugin and bots will know only those two teams are active!

### Step 3. Mark the Combat Arena
1. Place a **Map Zone Block** (Diamond Block for 50x50, Emerald Block for 100x100) at the center of your battle arena. You can set up multiple zones — the plugin randomly picks one each round!
2. Place **Spawn Blocks** (Obsidian) within that zone where teams will appear each round.
3. Add interactive elements:
   - **Coal Block** — Jump pad (deals 20% max HP damage and launches entities high into the air).
   - **Glazed Terracotta / Prismarine** — Vertical elevator lift column.

### Step 4. Start the Battle!
Step on your team's wool block, then step on the **Iron Block** (Start Block) OR run:
```
/rdebug start
```
The countdown begins, players draft their opening cards, and the match goes live!

---

## 🧱 Special Blocks Reference

| In-Game Block | Material | Purpose |
| :--- | :--- | :--- |
| **Team Join** | Colored Wool (`LIGHT_BLUE`, `RED`, `YELLOW`, `LIME_WOOL`) | Players and bots step on this to instantly join that team. |
| **Spectator** | White Wool (`WHITE_WOOL`) | Steps into spectator mode. |
| **Lobby Center** | Emerald Block / Beacon | Central lobby spawn point. |
| **50x50 Map Zone** | Diamond Block (`DIAMOND_BLOCK`) | Center of a 50x50 battle zone. |
| **100x100 Map Zone** | Emerald Block (`EMERALD_BLOCK`) | Center of a 100x100 battle zone. |
| **Team Spawn Point** | Obsidian (`OBSIDIAN`) | Team spawn position on the active arena. |
| **Jump Pad** | Coal Block (`COAL_BLOCK`) | Deals 20% HP damage and propels players high into the air. |
| **Elevator Lift** | Purple Glazed Terracotta / Prismarine | Smoothly lifts players to upper floors. |
| **START Button** | Iron Block (`IRON_BLOCK`) | Starts the game when stepped on. |
| **STOP Button** | Redstone Block (`REDSTONE_BLOCK`) | Stops the game and returns all players to the lobby. |

---

## 🤖 Smart AI Combat Bots

The built-in Fake Player bot system runs natively on the Minecraft server tick without external bridge plugins:

- **10 Difficulty Levels:** Randomly assigned each round (1 = rookie with wide aim error, 10 = pinpoint sniper with instant flick reactions and shield parries).
- **4 Combat Playstyles:** Bots adapt between Aggressive Flanker, Tactical Sniper, Defensive Turtle, and Erratic Brawler.
- **Fair Vision:** Real 140° FOV, 60m range, 2.5m footstep hearing, and strict raytraced Line-of-Sight (no wallhacks).
- **Tactical Ambushes:** Bots hold open hallway angles (`ambushHoldYaw`), pre-charge shields for Empower damage boosts, and fire instantly when a target rounds the corner.
- **Card-Aware Parkour:** When bots draft jump upgrade cards, they recognize their new jump clearance and effortlessly leap over 2-block and 3-block walls, clear gaps, and ride elevator lifts.
- **Close-Range Saw Rush:** With the Saw card, bots sprint in close (< 2.8m, speed 0.46) while blocking to shred opponents in their flame ring.
- **Map Block Awareness:** Bots automatically detect which team spawn blocks are placed on the map and only balance into available teams.

### Bot Commands:
```bash
# Spawn a single bot (auto name and balanced team)
/rbot spawn

# Bulk spawn 10 balanced bots across available map teams
/rbot spawn count 10

# Spawn a specific bot (e.g., lvl 10 sniper on Blue team)
/rbot spawn ProBot BLUE 10

# Despawn all active bots
/rbot despawn all

# List all active bots, their current health, and difficulty level
/rbot list
```

---

## 📋 Commands & Permissions

Primary admin command: `/rdebug`. Short bot management command: `/rbot`.

### For Players:
| Command | Purpose |
| :--- | :--- |
| `/rdebug join` | Join an ongoing game (places into the team with least players). |
| `/rdebug cards show [player]` | View your active card deck. |
| `/rdebug stats [player]` | View combat stats (damage, HP, ammo, attack speed). |
| `/rdebug info` | Display match and arena status. |

### For Administrators (`rounds.admin` or OP):
| Command | Purpose |
| :--- | :--- |
| `/rdebug start` | Start a match on the arena. |
| `/rdebug stop` | Stop the game, remove bots, and return everyone to lobby. |
| `/rdebug giveblocks` | Receive a full set of setup blocks and switch to Creative mode. |
| `/rdebug setlobby` | Set the lobby spawn point at your current position. |
| `/rdebug rounds <number>` | Set the number of round wins required to win the match (default: 5). |
| `/rdebug freeplay <on\|off>` | Allow non-admin players to run `/rdebug start`. |
| `/rdebug autodeath <on\|off>` | Toggle sudden-death elimination for overly long rounds (> 4 min). |
| `/rdebug cards add <player> <id>` | Grant a specific card to a player or bot. |
| `/rdebug givegun [player\|@a]` | Give the custom gun to yourself or everyone. |
| `/rdebug resetstats [player]` | Reset cards, HP, and stats to defaults. |
| `/rdebug setlanguage <EN_en\|RU_ru>` | Switch plugin language on the fly. |
| `/rdebug reload` | Hot-reload `config.yml` and card files without a server restart. |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Language pack from lang/ folder (EN_en, RU_ru, etc.)
language: EN_en

# Round 1 default stats
defaults:
  hp: 20                 # Health (20 = 10 hearts)
  damage: 3.0            # Bullet base damage
  ammo: 3                # Magazine size
  max-ammo: 3            # Max ammo capacity
  attack-speed: 20       # Firing cooldown in ticks (20 ticks = 1 second)
  bullet-speed: 1.0      # Projectile flight speed

# Match settings
game:
  default-rounds: 5      # Round wins needed for overall victory
  max-rounds: 20         # Maximum rounds cap
  card-selection-time: 200 # Card draft duration (200 ticks = 10 seconds)

# Automatic arena gamerules (isolated to arena players)
game-rules:
  enabled: true
  instant-respawn: true       # Instant respawn without death screen
  keep-inventory: true        # Keep inventory enabled
  freeze-time: true           # Fixed daytime on arena
  disable-weather: true       # Clear weather
  disable-mob-spawning: true  # Block mob spawning on arena

# Jump pad settings
jump-block:
  damage-percent: 20.0   # Percentage of max HP deducted on launch
  launch-height: 10.0    # Launch height in blocks
```

---

## 📊 PlaceholderAPI Support

Full PlaceholderAPI integration under the `%rounds_...%` identifier:

- `%rounds_round%` — Current round number.
- `%rounds_rounds_to_win%` — Wins needed for match victory.
- `%rounds_round_display%` — Formatted round progress (e.g. `3/5`).
- `%rounds_state%` — Match state (`WAITING`, `CARDS`, `PLAYING`, `ROUND_END`, `GAME_END`).
- `%rounds_team%` — Player's team name (Blue, Red...).
- `%rounds_team_color%` — Team color code (`§9`, `§c`, `§e`, `§a`).
- `%rounds_blue_wins%`, `%rounds_red_wins%`, `%rounds_yellow_wins%`, `%rounds_green_wins%` — Win counts per team.
- `%rounds_stat_hp%`, `%rounds_stat_dmg%`, `%rounds_stat_ammo%` — Live player attributes including all card modifiers.
- `%rounds_stat_cards%` — Total count of drafted cards.

---

## 💻 Requirements & Installation

1. **Server:** Paper, Purpur, or compatible forks 1.20.4+ (compatible up to 1.21.x / 26.2).
2. **Java:** Java 17 or newer.
3. Drop `RoundsPlugin.jar` into your `plugins/` directory and restart your server.
4. Enjoy! 🚀
