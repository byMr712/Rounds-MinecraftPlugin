# RoundsPlugin

> **Language:** English · [Русский](readme.md)

Minecraft plugin for the "Rounds" mini-game. 4 teams, a card system with the ability to add and modify cards, up to 20 rounds, and a system of special blocks for faster plugin setup on a map.

## Requirements

- Purpur/Paper 1.20.4 - 26.2
- Java 17+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.13006/) for placeholder support
- Optional: [TAB](https://www.spigotmc.org/resources/tab.57806/) for scoreboard and game status display

## Installation

1. Copy `RoundsPlugin.jar` into the `plugins/` folder
2. Restart the server
3. Configure `plugins/RoundsPlugin/config.yml` (already configured by default)
4. Set up cards in `plugins/RoundsPlugin/cards/` (65 cards by default)
5. Get the blocks with the `/rdebug giveblocks` command and place them in the world

## Quick start

### 1. Get the blocks

An administrator must run the command:

```
/rdebug giveblocks
```

This puts all special blocks into your inventory and switches you to creative mode.
Then place the blocks in the world (an explanation of how they work will be written later).

#### Team join blocks

| Block | Material | What it does |
|------|----------|-----------|
| **Blue entrance** | Blue wool | Join the blue team |
| **Red entrance** | Red wool | Join the red team |
| **Yellow entrance** | Yellow wool | Join the yellow team |
| **Green entrance** | Green wool | Join the green team |

#### Map and spawn blocks

| Block | Material | What it does |
|------|----------|-----------|
| **Lobby block** | Emerald block | Lobby center. Only one such block. At game start everyone teleports here. If a second one is placed, the second becomes the main one |
| **Map block 50x50** | Diamond block | Center of the 50x50 map zone. Spawns are searched for inside this zone |
| **Map block 100x100** | Emerald block | Center of the 100x100 map zone |
| **Spawn block** | Beacon | Team spawn point inside the map zone |

#### Control blocks

| Block | Material | What it does |
|------|----------|-----------|
| **Game start block** | Iron block | Start the game (`rounds.admin` only) |
| **Game end block** | Redstone block | End the game (`rounds.admin` only) |
| **Blue team lobby block** | Blue wool | Adds a player to the blue team |
| **Red team lobby block** | Red wool | Adds a player to the red team |
| **Yellow team lobby block** | Yellow wool | Adds a player to the yellow team |
| **Green team lobby block** | Green wool | Adds a player to the green team |

### 2. Place the map blocks

1. Place the **lobby block** — the center of your lobby zone
2. Place the **map blocks** (50x50 or 100x100) — the centers of the zones where the game takes place
3. Inside each map zone place **spawn blocks** — the points where teams appear
4. There can be several map blocks — each round the plugin picks a random zone

### 3. Players join teams

Players simply **step on** a colored wool block to join a team.
Teams can be changed until the game starts (just step on another block).
Team change cooldown — 1 second.

### 4. Start the game

```
/rdebug start
```

Or step on the **Tagshield CD** block (iron block) in the world.

### 5. How the game goes

1. All players teleport to the **lobby block** (+1 in height)
2. A **timer 5 → 1** (seconds) runs in chat
3. On every tick all players become **spectators** and teleport to a random spawn block for preview
4. After the timer each team teleports to its **own spawn block** (+1 in height), players receive **survival mode** and a gun
5. **Card selection** opens — each player picks 1 card out of 5
6. The round begins — players shoot each other
7. The last survivor of the round wins
8. **Next round** — the plugin picks a **random map zone** (with repeats), redistributes spawns, everyone teleports
9. When a team reaches the required number of wins — **end of game**, everyone is teleported to the lobby

## Configuration

### config.yml

```yaml
# Language: id of the file from plugins/RoundsPlugin/lang/ without .txt (the legacy "ru"/"en" are also accepted)
language: RU_ru

# Default player stats (reset every round)
defaults:
  damage: 3.0            # Base damage per bullet
  attack-speed: 20       # Attack cooldown in ticks (20 = 1 sec)
  ammo: 3                # Current ammo
  max-ammo: 3            # Maximum ammo
  bullets: 1             # Number of bullets per shot
  hp: 20                 # Health
  bullet-speed: 1.0      # Bullet speed
  reload-speed: 0        # Reload speed (0 = base 3 seconds)

game:
  default-rounds: 5      # Rounds to win by default
  max-rounds: 20         # Maximum number of rounds
  card-selection-time: 200 # Card selection time (200 ticks = 10 seconds)
  respawn-delay: 5       # Respawn delay

teams:
  enabled:
    - BLUE
    - RED
    - YELLOW
    - GREEN

gun:
  material: STICK        # Gun material
  base-cooldown: 20      # Base cooldown

cards:
  selection-count: 5     # Number of cards to choose from
  weighted-rarity: true  # Weighted selection by rarity

# World rules (applied automatically during the game)
game-rules:
  enabled: true          # Global switch
  instant-respawn: true  # Instant respawn (no death screen)
  keep-inventory: true   # Keep items on death
  freeze-time: true      # Time freeze (always day)
  disable-weather: true  # Disable weather changes
  disable-mob-spawning: true # Disable mob spawning

# Coloring nicknames in the team color
color-nicknames: true

# Built-in scoreboard (disabled by default, conflicts with TAB)
builtin-scoreboard:
  enabled: false
  title: "&6&lROUNDS"
```

### Card system

Cards are stored as **separate YAML files** in the `plugins/RoundsPlugin/cards/original/` folder. Custom cards are added to `plugins/RoundsPlugin/cards/custom/`.

#### Regular card format (example `burst.yml`):

```yaml
id: 8
name:
  ru: "&aОчередь"
  en: "&aBurst"
description:
  ru: "+2 пули, +3 патрона, -60% урона, +10% перезарядки"
  en: "+2 Bullets, +3 Ammo, -60% DMG, +10% Reload time"
material: ARROW
enabled: true
variations:
  - rarity: COMMON
    effects:
      bullets: 2
      ammo: 3
      damage: -0.6
      reload: 1
```

#### Card format with variations (example `combine.yml`):
```yaml
id: 13
name:
  ru: "&4Объединение"
  en: "&4Combine"
description:
  ru: "+{0} урона, -{1} патрона, +{2} перезарядки"
  en: "+{0} DMG, -{1} Ammo, +{2} Reload time"
material: REDSTONE
enabled: true
variations:
  - rarity: RARE
    values: ["100%", "2", "10%"]
    effects:
      damage: 1.0
      ammo: -2
      reload: 1
  - rarity: COMMON
    values: ["50%", "1", "5%"]
    effects:
      damage: 0.5
      ammo: -1
      reload: 0.5
  - rarity: UNCOMMON
    values: ["75%", "2", "8%"]
    effects:
      damage: 0.75
      ammo: -2
      reload: 0.8
  - rarity: EPIC
    values: ["125%", "3", "13%"]
    effects:
      damage: 1.25
      ammo: -3
      reload: 1.3
```


#### Effect parameters:

| Key | Description |
|------|----------|
| `damage` | Additional damage (multiplier: -0.7 = -70%) |
| `attack-speed` | Attack speed (lower = faster) |
| `attack-speed-reload` | Reload time (lower = faster) |
| `attack-range` | Attack range |
| `bullets` | Number of bullets per shot |
| `ammo` | Ammo |
| `bullet-speed` | Bullet speed |
| `bounce` | Ricochet off walls |
| `target-bounce` | Ricochet off targets (nearest enemy) |
| `hp` | Maximum health |
| `cold` + `cold-level` | Freeze chance and level |
| `poison` + `poison-level` | Poison chance and level |
| `parazit` + `parazit-level` | Exhaustion (wither) chance and level |
| `leech` | Health drain |
| `homing` | Bullet homing |
| `homing-on-block` | Bullet homing for N seconds after hitting a block |
| `empower` + `empower-charge` | Damage boost (consumed per shot) |
| `dark-strength` | Dark energy power (+0.5 damage per stack) |
| `big-bullet` | Big bullet (stackable): +70% visual size and hit radius per stack, reload time +30% per stack |
| `bomb-bullet` | Explosive bullets (TNT on hit) |
| `bomb-on-block` | Bomb on shield block |
| `explode-bullets` | Exploding bullets (AoE) |
| `shield` | Shield (blocking) |
| `shield-charge` | Shield charges |
| `shields-up` | Automatic shield when ammo = 0 |
| `truster` | Enhanced knockback |
| `grow` | Increases maximum HP |
| `speed` + `speed-boost` | Movement speed |
| `stun` | Stun on hit |
| `block-cd` | Shield block cooldown |
| `reload-speed` | Reload speed |
| `heal` | Healing |
| `damage-per-bounce` | Damage per ricochet |
| `double-block` | Double shield block |
| `auto-reload` | Automatic reload |
| `saw` | Saw (Damage field around when blocking) |
| `shockwave` | Shockwave when blocking (knockback) |
| `silence` | Silence (no shooting or blocking) |
| `sneaky` | Stealth |
| `emp` | Slows enemies down when blocking |
| `overpower` | Power (damage as % of HP when blocking) |
| `refresh` | Refreshes the block cooldown |
| `radiance` | Glow (enemies see you) |
| `lifesteal-aura` | Health drain aura |
| `phoenix` | Phoenix (revival after death) |
| `abyssal` | Bottomless (summons a phantom after 30 seconds of inactivity) |
| `implode` | Explosion on death |
| `echo` | Echo (second volley after 0.25 seconds) |
| `drill` | Drill bullets (pass through walls) |
| `splash` | Splash damage |
| `teleport` | Teleport when blocking |
| `tactical-reload` | Tactical reload (instant reload when blocking) |
| `ammo-per-hit` | Ammo per hit |
| `hp-boost-on-hit` | HP increase per hit |

#### Card rarity:

| Rarity | Drop chance |
|--------|------------|
| COMMON | 40 |
| UNCOMMON | 30 |
| RARE | 18 |
| EPIC | 9 |
| LEGENDARY | 3 |

#### Card wheel (Wheel)

The administrator can enable automatic card rotation during selection:

```
/rdebug wheel on
```

Every 6 seconds all open card-selection GUIs are refreshed with new random cards.

## Map block system

### Map blocks

Map blocks define zones on the map. A zone is a cube: `[centerX ± size/2, centerZ ± size/2]`, Y from -64 to 320.

- There can be **several** map blocks in different parts of the map
- Each round the plugin **randomly selects** one of the zones (with possible repeats)
- All **spawn blocks** inside the selected zone are located

### Spawn blocks

Spawn blocks define the points where teams appear:

- At the start of a round the plugin finds all spawn blocks inside the selected map zone
- Team A receives a **random** spawn block
- Team B — a **random** one from the remaining blocks
- If there are fewer spawns than teams, spawns are reused

### Lobby block

- One block per map, marks the center of the lobby
- If a second one is placed, the second becomes the main one
- With `/rdebug start` all players teleport to the lobby (+1 Y)
- At the **end of the game** all players return to the lobby

---

## Commands

All game control commands are grouped under `/rdebug`.

### Game management

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug start` | | Start the game (teleport to lobby → timer → spawn distribution) |
| `/rdebug stop` | | Stop the game and reset everything |
| `/rdebug status` | | Show the game state |
| `/rdebug rounds <number>` | | Set the number of rounds to win (1-20) |
| `/rdebug info` | | Plugin information |
| `/rdebug join` | | Join a team during the game |
| `/rdebug test` | | Check — "RoundsPlugin works!" |

### Map blocks

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug giveblocks [player]` | | Give all special blocks (teams, lobby, maps, spawns) |

### Cards

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug cards` | | Open the card selection GUI |
| `/rdebug cards reload` | | Reload cards from files |
| `/rdebug cards test [id]` | `[id]` | Apply a card by ID or open the GUI |
| `/rdebug cards giveall` | | Unlock all cards |
| `/rdebug applycard <name>` | | Apply a card by name |

### Items

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug givegun [player\|@a]` | | Give a gun to yourself, a player or everyone |
| `/rdebug giveall` | | Give all cards |
| `/rdebug wheel on\|off` | | Enable/disable card rotation |

### Scoreboard

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug tab on\|off` | | Enable/disable the built-in scoreboard |
| `/rdebug tab name <title>` | | Change the scoreboard title |

The built-in scoreboard shows: the current round, the player's team and the wins of all active teams (only teams with players).

### Debugging

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug help` | | Help on all subcommands |
| `/rdebug stats [player]` | | Show all player stats |
| `/rdebug setstat <stat> <value> [player]` | | Set a stat value |
| `/rdebug setteam <COLOR> [player]` | | Set the team |
| `/rdebug setlanguage <language>` | | Change the language; tab completion shows all languages found in the `lang/` folder |
| `/rdebug effect <type> <level> <duration>` | | Apply a potion effect |
| `/rdebug heal [amount]` | | Heal to maximum |
| `/rdebug spawnbomb/heal/toxic/shield` | | Spawn an entity |
| `/rdebug entities` | | Show custom entities within a 20-block radius |
| `/rdebug resetstats` | | Reset all stats and cards |
| `/rdebug reload` | | Reload config.yml, cards and language packs (lang/*.txt) |
| `/rdebug version` | | Plugin and server version |
| `/rdebug killround` | | Kill all enemies (only during a round) |
| `/rdebug iteminfo` | | Information about the item in hand |

**Stats for `setstat`:**
`dmg`, `atks`, `atks_reload`, `atkr`, `bounce`, `ammo`, `bullets`, `cold`, `poison`, `leech`, `homing`, `poison_lvl`, `cold_lvl`, `parazit`, `hp`, `bomb_bullet`, `explode_bullets`, `bullet_speed`, `empower`, `empower_charge`, `dark_strength`, `barage`, `big_bullet`, `grow`, `truster_lvl`, `dark`

**Potions for `effect`:**
`SPEED`, `SLOW`, `FAST_DIGGING`, `SLOW_DIGGING`, `INCREASE_DAMAGE`, `HEAL`, `HARM`, `JUMP`, `CONFUSION`, `BLINDNESS`, `NIGHT_VISION`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `POISON`, `REGENERATION`, `RESISTANCE`, `HEALTH_BOOST`, `ABSORPTION`, `SATURATION`, `WEAKNESS`, `WITHER`, `LUCK`, `UNLUCK`, `LEVITATION`, `DOLPHINS_GRACE`, `BAD_OMEN`, `HERO_OF_THE_VILLAGE`

---

## Placeholders (PlaceholderAPI)

The plugin provides placeholders for game information and player statistics.
The full list can be viewed in game: `/rdebug placeholders`

### Game information

| Placeholder | Returns |
|-------------|---------|
| `%rounds_round%` | Current round number |
| `%rounds_rounds_to_win%` | Rounds needed to win |
| `%rounds_round_display%` | Format "3/5" |
| `%rounds_state%` | Game state (PLAYING, CARDS, WAITING, etc.) |
| `%rounds_team%` | Player's team name |
| `%rounds_team_color%` | Team color code |
| `%rounds_team_adjective%` | Team adjective (blue, red, etc.) |
| `%rounds_team_wins%` | Player's team wins |
| `%rounds_blue_wins%` | Blue team wins |
| `%rounds_red_wins%` | Red team wins |
| `%rounds_yellow_wins%` | Yellow team wins |
| `%rounds_green_wins%` | Green team wins |
| `%rounds_blue_name%` | Localized blue team name |
| `%rounds_red_name%` | Localized red team name |
| `%rounds_yellow_name%` | Localized yellow team name |
| `%rounds_green_name%` | Localized green team name |

### Player stats

| Placeholder | Returns |
|-------------|---------|
| `%rounds_stat_hp%` | Health |
| `%rounds_stat_dmg%` | Damage |
| `%rounds_stat_atk_speed%` | Attack speed |
| `%rounds_stat_atkr%` | Attack radius |
| `%rounds_stat_ammo%` | Ammo |
| `%rounds_stat_max_ammo%` | Max ammo |
| `%rounds_stat_bullets%` | Bullets |
| `%rounds_stat_bullet_speed%` | Bullet speed |
| `%rounds_stat_bounce%` | Ricochet |
| `%rounds_stat_homing%` | Homing |
| `%rounds_stat_big_bullet%` | Big bullet |
| `%rounds_stat_cold%` | Frost |
| `%rounds_stat_cold_lvl%` | Frost level |
| `%rounds_stat_poison%` | Poison |
| `%rounds_stat_poison_lvl%` | Poison level |
| `%rounds_stat_parazit%` | Drain |
| `%rounds_stat_parazit_lvl%` | Drain level |
| `%rounds_stat_leech%` | Leech |
| `%rounds_stat_truster%` | Thruster |
| `%rounds_stat_empower%` | Empower |
| `%rounds_stat_empower_charge%` | Empower charge |
| `%rounds_stat_dark_strength%` | Dark power |
| `%rounds_stat_dark%` | Darkness |
| `%rounds_stat_grow%` | Growth |
| `%rounds_stat_bomb_bullet%` | Bombs |
| `%rounds_stat_bomb_on_block%` | Bombs on block |
| `%rounds_stat_shield_active%` | Shield active (1/0) |
| `%rounds_stat_shield_hp%` | Shield health |
| `%rounds_stat_shield_cd%` | Shield cooldown |
| `%rounds_stat_speed%` | Speed |
| `%rounds_stat_stun%` | Stun |
| `%rounds_stat_saw%` | Saw |
| `%rounds_stat_silence%` | Silence |
| `%rounds_stat_emp%` | EMP |
| `%rounds_stat_sneaky%` | Sneaky |
| `%rounds_stat_phoenix%` | Phoenix |
| `%rounds_stat_abyssal%` | Abyssal |

---

## TAB integration

The `TAB/` folder contains a ready-made configuration file for the TAB plugin. It shows:

- Team scores in the sidebar (only during the game)
- Round and progress display
- Player team indication with color
- Tablist, nametag and header/footer configuration

---

## State saving

The plugin automatically saves:

- **Game state** (`game-state.yml`) — round number, team wins, dead players
- **Player data** — stats, cards, team (via PersistentDataContainer + `active-players.yml`)
- **Map blocks** (`rounds-map-blocks.yml`) — positions of the lobby, map and spawn blocks

If the server restarts during an active game, the state is restored automatically.

---

## Localization

Language packs are stored in the `plugins/RoundsPlugin/lang/` folder — **one `<Name>.txt` file = one language** (for example, `RU_ru.txt`, `EN_en.txt`). Inside is a YAML-like interface string format; the file name without `.txt` is the language id.

Changing the language:

1. With the command `/rdebug setlanguage <language>` — tab completion shows all found languages
2. Or specify `language: RU_ru` in `config.yml` and restart the server

Adding your own language:

1. Copy `lang/RU_ru.txt`, rename it to `<Name>.txt` and translate the strings
2. Put the file into `plugins/RoundsPlugin/lang/`
3. Run `/rdebug reload` or restart the server — the new language will appear in the tab completion of `/rdebug setlanguage`

The plugin scans the folder on every startup and `/rdebug reload`, so third-party language packs are picked up automatically. The default `RU_ru.txt` / `EN_en.txt` are restored from the jar if deleted. If the current language file is unavailable, the plugin falls back to `EN_en`.

Cards support localization in the `name.ru` / `name.en` and `description.ru` / `description.en` format. The language code for cards is taken from the id part before `_` (`DE_de` → `de`); if such a section does not exist in the card, the English text is used.

## Permissions

| Permission | Description |
|------------|-------------|
| `rounds.admin` | All game management and debugging commands |
| `rounds.join` | Ability to join teams through blocks (granted to everyone by default) |

## Files

| File | Description |
|------|-------------|
| `plugins/RoundsPlugin/config.yml` | Main configuration |
| `plugins/RoundsPlugin/lang/` | Language packs (`*.txt`; one file = one language, third-party ones are picked up automatically) |
| `plugins/RoundsPlugin/cards/original/` | Standard cards (65 files) |
| `plugins/RoundsPlugin/cards/custom/` | Custom cards |
| `plugins/RoundsPlugin/playerdata/` | Player data (automatic) |
| `plugins/RoundsPlugin/game-state.yml` | Current game state |
| `plugins/RoundsPlugin/active-players.yml` | Active players in the session |
| `<world>/rounds-blocks.yml` | Team join blocks |
| `<world>/rounds-map-blocks.yml` | Lobby, map and spawn blocks |

## Building

Requirements: Java 17, Gradle 8.9+

```bash
./gradlew clean build
```

Output: `build/libs/RoundsPlugin-1.0.jar`
