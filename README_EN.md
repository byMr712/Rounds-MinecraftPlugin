# RoundsPlugin v1.2.1

Minecraft minigame plugin "Rounds". 4 teams, card system, shooting, up to 20 rounds.

---

## Requirements

- Paper 1.20.1 - 26.2+
- Java 17+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.13006/) for placeholder support

---

## Installation

1. Copy `RoundsPlugin-1.2.1.jar` to the `plugins/` folder
2. Restart the server
3. Configure `plugins/RoundsPlugin/config.yml`
4. Configure `plugins/RoundsPlugin/cards.yml` (43 cards by default)
5. Get the special blocks and place them in the world

---

## Quick Start

### 1. Create team join blocks

Admin runs:

```
/rdebug giveall
```

This gives all special blocks to inventory. Place them in the world:

| Block | Material | Function |
|-------|----------|----------|
| **Blue Join** | Blue Wool | Right-click to join the Blue team |
| **Red Join** | Red Wool | Right-click to join the Red team |
| **Yellow Join** | Yellow Wool | Right-click to join the Yellow team |
| **Green Join** | Green Wool | Right-click to join the Green team |
| **Counter** | Clock | Right-click to receive all blocks (requires `rounds.admin`) |
| **Tagshields** | Barrier | Right-click to pause/unpause the game (anyone can use) |
| **Tagshields CD** | Iron Block | Right-click to start the game (requires `rounds.admin`) |

### 2. Players join teams

Players simply **right-click** a colored wool block to join a team.
They can change teams before the game starts by clicking a different block.

### 3. Start the game

```
/rdebug start
```

Or right-click the **Tagshields CD** block (iron block) in the world.

### 4. How the game works

1. All players receive a gun (stick)
2. A round begins — players shoot each other
3. Last surviving team wins the round
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
```

### cards.yml

Each card is defined as:

```yaml
cards:
  1:
    name: "&8Dark Strength"
    description: "+1 dark energy damage"
    material: COAL
    custom-model-data: 10001
    rarity: UNCOMMON
    enabled: true
    effects:
      dark-strength: 1
    potion-effects: []
    commands: []
```

**Effect parameters:**

| Key | Description |
|-----|-------------|
| `damage` | Bonus damage |
| `attack-speed` | Attack speed (lower = faster) |
| `attack-speed-reload` | Reload time (lower = faster) |
| `bullets` | Bullets per shot |
| `ammo` | Ammo capacity |
| `bullet-speed` | Bullet speed |
| `bounce` | Ricochet count |
| `hp` | Maximum health |
| `cold` | Freeze chance |
| `cold-level` | Freeze level |
| `poison` | Poison chance |
| `poison-level` | Poison level |
| `homing` | Homing shots |
| `leech` | Life steal |
| `empower` | Empower |
| `empower-charge` | Empower charges |
| `dark-strength` | Dark energy power |
| `barage` | Barrage |
| `big-bullet` | Big bullet |
| `bomb-bullet` | Explosive bullets |
| `explode-bullets` | Detonating bullets |
| `target-bounce` | Target ricochet |
| `shield` | Shield (cooldown) |
| `truster` | Thruster |
| `grow` | Size increase |
| `parazit` | Parasite |
| `parazit-level` | Parasite level |

**Card rarities:**

| Rarity | Weight |
|--------|--------|
| COMMON | 40 |
| UNCOMMON | 30 |
| RARE | 18 |
| EPIC | 9 |
| LEGENDARY | 3 |

---

## Commands

All game management commands are under `/rdebug`.

### Game Management

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug start` | | Start the game |
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
| `/rdebug cards reload` | | Reload cards from cards.yml |
| `/rdebug cards test [id]` | `[id]` | Apply card by ID or open GUI |
| `/rdebug cards giveall` | | Unlock all 43 cards |

### Items

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug givegun [player]` | | Give a gun to self or target player |
| `/rdebug blocks` | | Give all special blocks |
| `/rdebug applycard <id>` | | Apply a card by ID |

### Debug

| Subcommand | Arguments | Description |
|-----------|-----------|-------------|
| `/rdebug help` | | Show help for all subcommands |
| `/rdebug stats [player]` | | Show full PlayerData stats |
| `/rdebug setstat <stat> <value>` | | Set a specific stat value |
| `/rdebug effect <type> <amp> <dur>` | | Apply a potion effect |
| `/rdebug heal [amount]` | | Heal to given amount (default 20) |
| `/rdebug spawnbomb` | | Spawn a bomb entity |
| `/rdebug spawnheal` | | Spawn a heal ring |
| `/rdebug spawntoxic` | | Spawn a toxic ring |
| `/rdebug spawnshield` | | Spawn a shield bomb |
| `/rdebug entities` | | List custom entities within 20 blocks |
| `/rdebug resetstats` | | Reset all stats and cards |
| `/rdebug reload` | | Reload config.yml, messages.yml, cards.yml |
| `/rdebug version` | | Plugin version, server version |
| `/rdebug killround` | | Kill all enemies (only during PLAYING) |
| `/rdebug iteminfo` | | Show held item info (material, PDC keys) |

**Stats for `setstat`:**
`dmg`, `atks`, `atkr`, `bounce`, `ammo`, `bullets`, `cold`, `poison`, `leech`, `homing`, `poison_lvl`, `cold_lvl`, `parazit`, `hp`, `bomb_bullet`, `explode_bullets`, `bullet_speed`, `empower`, `empower_charge`, `dark_strength`, `barage`, `big_bullet`, `grow`, `truster_lvl`, `dark`, `atks_reload`

**Potion types for `effect`:**
`SPEED`, `SLOW`, `FAST_DIGGING`, `SLOW_DIGGING`, `INCREASE_DAMAGE`, `HEAL`, `HARM`, `JUMP`, `CONFUSION`, `BLINDNESS`, `NIGHT_VISION`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `POISON`, `REGENERATION`, `RESISTANCE`, `HEALTH_BOOST`, `ABSORPTION`, `SATURATION`, `WEAKNESS`, `WITHER`, `LUCK`, `UNLUCK`, `LEVITATION`, `DOLPHINS_GRACE`, `BAD_OMEN`, `HERO_OF_THE_VILLAGE`

---

## Localization

The plugin supports **Russian** and **English**. To change:

1. Open `plugins/RoundsPlugin/config.yml`
2. Change `language: ru` to `language: en`
3. Restart the server

All texts are in `plugins/RoundsPlugin/messages.yml`. To add a new language — copy the `ru:` or `en:` section and replace the texts.

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
| `plugins/RoundsPlugin/cards.yml` | All 43 card definitions |
| `plugins/RoundsPlugin/messages.yml` | UI texts (ru/en) |
| `plugins/RoundsPlugin/playerdata/` | Player data (automatic) |
