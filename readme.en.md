# RoundsPlugin v1.0

Minecraft minigame plugin "Rounds". 4 teams, card system with 65 cards, shooting, up to 20 rounds.

---

## Requirements

- Paper 1.20.1 - 26.2+
- Java 17+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.13006/) for placeholder support
- Optional: [TAB](https://www.spigotmc.org/resources/tab.57806/) for scoreboard and game status display

---

## Installation

1. Copy `RoundsPlugin-1.0.jar` to the `plugins/` folder
2. Restart the server
3. Configure `plugins/RoundsPlugin/config.yml` (pre-configured by default)
4. Configure cards in `plugins/RoundsPlugin/cards/` (65 cards by default)
5. Get the special blocks with `/rdebug giveblocks` and place them in the world

---

## Quick Start

### 1. Create team join blocks

Admin runs:

```
/rdebug giveblocks
```

This gives all special blocks to inventory and switches to survival mode. Place blocks in the world:

| Block | Material | Function |
|-------|----------|----------|
| **Blue Join** | Blue Wool | Step on to join the Blue team |
| **Red Join** | Red Wool | Step on to join the Red team |
| **Yellow Join** | Yellow Wool | Step on to join the Yellow team |
| **Green Join** | Green Wool | Step on to join the Green team |
| **Tagshields CD** | Iron Block | Step on to start the game (requires `rounds.admin`) |

### 2. Players join teams

Players simply **step on** a colored wool block to join a team.
They can change teams before the game starts by stepping on a different block.
Team change has a 1-second cooldown.

### 3. Start the game

```
/rdebug start
```

Or step on the **Tagshields CD** block (iron block) in the world.

### 4. How the game works

1. All players receive a gun (stick)
2. A round begins — players shoot each other
3. Last surviving player wins the round
4. After the round, **card selection** opens (10 seconds)
5. Each player picks 1 card from 5 — it upgrades their stats
6. Next round begins
7. First team to reach the required round wins — **game over**

---

## Configuration

### config.yml

```yaml
# Language: "ru" or "en"
language: ru

game:
  default-rounds: 5      # Default rounds to win
  max-rounds: 20          # Maximum rounds
  card-selection-time: 200 # Card selection time (200 ticks = 10 sec)
  respawn-delay: 5        # Respawn delay (5 ticks = 0.25 sec)

teams:
  enabled:
    - BLUE
    - RED
    - YELLOW
    - GREEN

gun:
  material: STICK           # Gun material (STICK, CROSSBOW, etc.)
  custom-model-data: 9999   # CustomModelData for resource pack
  base-cooldown: 20         # Base cooldown (not used yet)

cards:
  selection-count: 5        # Number of cards offered for selection
  weighted-rarity: true     # Weighted random by rarity

resource-pack:
  auto-send: true
  port: 41071

# Built-in scoreboard (disabled by default, conflicts with TAB)
builtin-scoreboard:
  enabled: false
  title: "&6&lROUNDS"
```

### Card System

Cards are stored as **individual YAML files** in `plugins/RoundsPlugin/cards/original/`. Custom cards go in `plugins/RoundsPlugin/cards/custom/`.

#### Card format (example `barrage.yml`):

```yaml
id: 1
name:
  ru: "&cБараж"
  en: "&cBarrage"
description:
  ru: "Стреляет 5 пулями, -70% урона"
  en: "Fires 5 bullets, -70% damage"
material: ARROW
custom-model-data: 10001
rarity: RARE
enabled: true
effects:
  damage: -0.7
  bullets: 5
potion-effects: []
commands: []
```

#### Effect parameters:

| Key | Description |
|-----|-------------|
| `damage` | Bonus damage (multiplier: -0.7 = -70%) |
| `attack-speed` | Attack speed (lower = faster) |
| `attack-speed-reload` | Reload time (lower = faster) |
| `attack-range` | Shot range |
| `bullets` | Projectiles per shot |
| `ammo` | Ammo pool |
| `bullet-speed` | Projectile velocity |
| `bounce` | Wall ricochet count |
| `target-bounce` | Ricochet toward nearest enemy |
| `hp` | Max health |
| `cold` + `cold-level` | Slowness chance and level on hit |
| `poison` + `poison-level` | Poison chance and level on hit |
| `parazit` + `parazit-level` | Wither chance and level on hit |
| `leech` | Lifesteal on hit |
| `homing` | Bullet homing strength |
| `empower` + `empower-charge` | Damage multiplier (consumed per shot) |
| `dark-strength` | Dark energy power (+0.5 damage per stack) |
| `big-bullet` | Larger projectiles |
| `bomb-bullet` | TNT on hit |
| `bomb-on-block` | Bomb on shield block |
| `explode-bullets` | AoE explosion on hit |
| `shield` | Shield ability (block) |
| `shield-charge` | Shield charges |
| `shields-up` | Auto-block when ammo = 0 |
| `truster` | Enhanced knockback |
| `grow` | Increased max HP |
| `speed` + `speed-boost` | Movement speed |
| `stun` | Stun on hit (blindness + slowness + nausea) |
| `block-cd` | Shield cooldown |
| `reload-speed` | Reload speed modifier |
| `heal` | Healing |
| `damage-per-bounce` | Damage per ricochet |
| `double-block` | Double shield block |
| `auto-reload` | Auto-reload |
| `saw` | Spinning saw AoE on block |
| `shockwave` | Knockback wave on block |
| `silence` | Silence (prevents shooting and blocking) |
| `sneaky` | Stealth |
| `emp` | Slow enemies on block |
| `overpower` | %HP damage on block |
| `refresh` | Reset block cooldown |
| `radiance` | Glowing on enemies |
| `lifesteal-aura` | Lifesteal aura |
| `phoenix` | Revive after death |
| `abyssal` | Abyssal phantom (summons after 30s idle) |
| `implode` | Death explosion |
| `echo` | Echo (second volley 0.25s later) |
| `drill` | Drill ammo (passes through walls) |
| `remote` | Remote control |
| `splash` | Splash damage |
| `teleport` | Teleport on block |
| `tactical-reload` | Instant reload on block |
| `ammo-per-hit` | Ammo restored per hit |
| `hp-boost-on-hit` | HP increase per hit |

#### Card rarities:

| Rarity | Weight |
|--------|--------|
| COMMON | 40 |
| UNCOMMON | 30 |
| RARE | 18 |
| EPIC | 9 |
| LEGENDARY | 3 |

#### Card Rotation Wheel

Admins can enable automatic card rotation during selection:

```
/rdebug wheel on
```

Every 6 seconds, all open card GUIs are refreshed with new random cards.

---

## Commands

All game management commands are under `/rdebug`.

### Game Management

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug start` | | Start the game |
| `/rdebug stop` | | Stop the game and reset everything |
| `/rdebug status` | | Show game state |
| `/rdebug pause` | | Pause / unpause game |
| `/rdebug reset` | | Reset the game (without resetting players) |
| `/rdebug resetallmap` | | Full clear (game + teams + players) |
| `/rdebug rounds <number>` | | Set rounds to win (1-20) |
| `/rdebug info` | | Plugin info |
| `/rdebug test` | | Check — "RoundsPlugin is working!" |

### Cards

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug cards` | | Open card selection GUI |
| `/rdebug cards reload` | | Reload cards from files |
| `/rdebug cards test [id]` | `[id]` | Apply card by ID or open GUI |
| `/rdebug cards giveall` | | Unlock all cards |
| `/rdebug applycard <name>` | | Apply a card by name |

### Items

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug givegun [player\|@a]` | | Give a gun to self, player, or all |
| `/rdebug giveblocks [player]` | | Give all special blocks |
| `/rdebug wheel on\|off` | | Toggle card rotation in GUI |

### Debug

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug help` | | Show help for all subcommands |
| `/rdebug stats [player]` | | Show full PlayerData stats |
| `/rdebug setstat <stat> <value>` | | Set a specific stat value |
| `/rdebug effect <type> <amp> <dur>` | | Apply a potion effect |
| `/rdebug heal` | | Heal to max HP |
| `/rdebug spawnbomb` | | Spawn a bomb entity |
| `/rdebug spawnheal` | | Spawn a heal ring |
| `/rdebug spawntoxic` | | Spawn a toxic ring |
| `/rdebug spawnshield` | | Spawn a shield bomb |
| `/rdebug entities` | | List custom entities within 20 blocks |
| `/rdebug resetstats` | | Reset all stats and cards |
| `/rdebug reload` | | Reload config.yml, messages.yml, cards |
| `/rdebug version` | | Plugin version, server version |
| `/rdebug killround` | | Kill all enemies (only during PLAYING) |
| `/rdebug iteminfo` | | Show held item info (material, PDC keys) |

**Stats for `setstat`:**
`dmg`, `atks`, `atks_reload`, `atkr`, `bounce`, `ammo`, `bullets`, `cold`, `poison`, `leech`, `homing`, `poison_lvl`, `cold_lvl`, `parazit`, `hp`, `bomb_bullet`, `explode_bullets`, `bullet_speed`, `empower`, `empower_charge`, `dark_strength`, `barage`, `big_bullet`, `grow`, `truster_lvl`, `dark`

**Potion types for `effect`:**
`SPEED`, `SLOW`, `FAST_DIGGING`, `SLOW_DIGGING`, `INCREASE_DAMAGE`, `HEAL`, `HARM`, `JUMP`, `CONFUSION`, `BLINDNESS`, `NIGHT_VISION`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `POISON`, `REGENERATION`, `RESISTANCE`, `HEALTH_BOOST`, `ABSORPTION`, `SATURATION`, `WEAKNESS`, `WITHER`, `LUCK`, `UNLUCK`, `LEVITATION`, `DOLPHINS_GRACE`, `BAD_OMEN`, `HERO_OF_THE_VILLAGE`

---

## Placeholders (PlaceholderAPI)

The plugin provides 15 placeholders:

| Placeholder | Returns |
|------------|---------|
| `%rounds_round%` | Current round number |
| `%rounds_rounds_to_win%` | Rounds needed to win |
| `%rounds_round_display%` | "3/5" format string |
| `%rounds_state%` | Game state (PLAYING, CARDS, WAITING, etc.) |
| `%rounds_team%` | Player's team name (localized) |
| `%rounds_team_color%` | Chat color code of the team |
| `%rounds_team_adjective%` | Adjective form (blue, red, etc.) |
| `%rounds_team_wins%` | Player's team win count |
| `%rounds_blue_wins%` | Blue team wins |
| `%rounds_red_wins%` | Red team wins |
| `%rounds_yellow_wins%` | Yellow team wins |
| `%rounds_green_wins%` | Green team wins |
| `%rounds_blue_name%` | Localized blue team name |
| `%rounds_red_name%` | Localized red team name |
| `%rounds_yellow_name%` | Localized yellow team name |
| `%rounds_green_name%` | Localized green team name |

---

## TAB Plugin Integration

A ready-made TAB plugin configuration is included in the `TAB/` directory. It demonstrates:

- Scoreboard with team scores (visible only during gameplay)
- Round progress display
- Player team indicator with color
- Tablist, nametag, and header/footer customization

---

## State Persistence

The plugin automatically saves:

- **Game state** (`game-state.yml`) — current round, team wins, dead players
- **Player data** — stats, cards, team (via PersistentDataContainer + `active-players.yml`)

When the server restarts during an active game, the state is restored automatically.

---

## Localization

The plugin supports **Russian** and **English**. To change:

1. Open `plugins/RoundsPlugin/config.yml`
2. Change `language: ru` to `language: en`
3. Restart the server

All texts are in `plugins/RoundsPlugin/messages.yml`. To add a new language — copy the `ru:` or `en:` section and replace the texts.

Cards support localization via `name.ru` / `name.en` and `description.ru` / `description.en` fields.

---

## Permissions

| Permission | Description |
|-----------|-------------|
| `rounds.admin` | All game management and debug commands |
| `rounds.join` | Ability to join teams via blocks (default: true for everyone) |

---

## Files

| File | Description |
|------|-------------|
| `plugins/RoundsPlugin/config.yml` | Main configuration |
| `plugins/RoundsPlugin/messages.yml` | UI texts (ru/en) |
| `plugins/RoundsPlugin/cards/original/` | Built-in cards (65 files) |
| `plugins/RoundsPlugin/cards/custom/` | Custom cards |
| `plugins/RoundsPlugin/playerdata/` | Player data (automatic) |
| `plugins/RoundsPlugin/game-state.yml` | Current game state |
| `plugins/RoundsPlugin/active-players.yml` | Active session players |

---

## Build

Requirements: Java 17, Gradle 8.9+

```bash
./gradlew clean build
```

Output: `build/libs/RoundsPlugin-1.0.jar`
