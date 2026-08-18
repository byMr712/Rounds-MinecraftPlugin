# RoundsPlugin

Paper plugin ported from the Forge mod [Rounds 1.2](https://www.curseforge.com/minecraft/mc-mods/rounds) (MCreator, Forge 1.20.1, GeckoLib).

**Target Paper version:** 1.20.4 (Java 17)

## What is this

A card-based minigame with gun gameplay. 4-player teams, 43 ability cards, 8 management commands.

### Core gameplay

- Players stand on colored blocks to pick a team (Blue / Red / Yellow / Green)
- `/start_g` to start the match
- Each round: everyone gets a gun, shoots each other
- Last survivor wins the round
- Between rounds: card selection (GUI with 5 options)
- First team to reach the round limit wins

---

## Project structure

```
src/main/java/com/rounds/
├── RoundsPlugin.java          # Plugin entry point
├── RoundsConfig.java          # config.yml reader
├── RoundsKeys.java            # Centralized NamespacedKey constants
├── blocks/
│   └── BlockListener.java     # Join blocks, TimeScore, Shield, CDShoot
├── cards/
│   ├── Card.java              # Card: effects, potions, commands
│   ├── CardManager.java       # Card selection orchestration
│   └── CardRegistry.java      # YAML loader for 43 built-in + custom cards
├── command/
│   └── RoundsCommands.java    # 8 commands: /rounds, /start_g, /gamep etc.
├── effects/
│   └── RoundsEffects.java     # Stub (custom effects require NMS)
├── entity/
│   ├── BulletProjectile.java  # Arrow + ItemDisplay wrapper
│   └── RoundsEntities.java    # Bullet/bomb/ring spawning, homing AI
├── game/
│   └── GameManager.java       # Game loop, rounds, win conditions
├── gui/
│   └── CardGUIListener.java   # Vanilla chest inventory GUI
├── item/
│   └── GunItem.java           # Gun: shooting, ammo, reload, cooldown
├── player/
│   ├── PlayerData.java        # All player stats (60+ fields)
│   └── PlayerDataManager.java # PDC persistence + cache + GunCooldowns
├── teams/
│   └── TeamManager.java       # 4 teams, scoreboard, win tracking
└── util/
    └── ResourcePackGenerator.java  # Resource pack model generator
```

---

## Card system

43 built-in cards + unlimited custom cards via `cards.yml`.

### Rarity weights

| Rank | Weight |
|------|--------|
| COMMON | 40 |
| UNCOMMON | 30 |
| RARE | 18 |
| EPIC | 9 |
| LEGENDARY | 3 |

### Available effects

| Key | Description |
|-----|-------------|
| `damage` | Base damage per shot |
| `attack-speed` | Fire rate (lower = faster) |
| `attack-speed-reload` | Reload time modifier |
| `attack-range` | Shot range |
| `bullets` | Projectiles per shot |
| `ammo` | Ammo pool |
| `bullet-speed` | Projectile velocity |
| `bounce` | Wall ricochet count |
| `target-bounce` | Ricochet toward nearest enemy |
| `hp` | Max health |
| `cold` + `cold-level` | Slowness on hit |
| `poison` + `poison-level` | Poison on hit |
| `parazit` + `parazit-level` | Wither on hit |
| `leech` | Lifesteal on hit |
| `homing` | Bullet homing strength |
| `empower` + `empower-charge` | Damage multiplier (consumed per shot) |
| `dark-strength` | Dark strength (+0.5 damage per stack) |
| `big-bullet` | Larger projectiles |
| `bomb-bullet` | TNT on hit |
| `explode-bullets` | AoE explosion on hit |
| `shield` | Shield ability |
| `truster` | Enhanced knockback |
| `grow` | Increased max HP |

### Adding a custom card

Edit `plugins/RoundsPlugin/cards.yml`:

```yaml
cards:
  100:
    name: '&cMy Custom Card'
    description: 'Does something cool'
    material: DIAMOND_SWORD
    custom-model-data: 10100
    rarity: EPIC
    enabled: true
    effects:
      damage: 3.0
      attack-speed: -5
    potion-effects:
      - 'SPEED 1 200'
    commands:
      - 'say %player% got a custom card!'
```

---

## Configuration

File: `plugins/RoundsPlugin/config.yml`

```yaml
game:
  default-rounds: 5
  max-rounds: 20
  card-selection-time: 200
  respawn-delay: 5

gun:
  material: CROSSBOW
  custom-model-data: 9999
  base-cooldown: 20

cards:
  selection-count: 5
  weighted-rarity: true
```

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/rounds <number>` | Set round count | `rounds.admin` |
| `/start_g` | Start the game | `rounds.admin` |
| `/gamep [pause\|reset]` | Game management | `rounds.admin` |
| `/clearcommand` | Full reset | `rounds.admin` |
| `/inf` | Plugin info | everyone |
| `/relc` | Reload cards | `rounds.admin` |
| `/test` | Test command | everyone |
| `/testcard [id]` | Give/select a card | everyone |

---

## Build

Requirements: Java 17, Gradle 8.9+

```bash
./gradlew clean build
```

Output: `build/libs/RoundsPlugin-1.2.1.jar`

---

## Credits

- Original mod: `Rounds_1.2.jar` (MCreator, Forge 1.20.1)
- Textures extracted from mod JAR via `ResourcePackGenerator.extractTexturesFromMod()`
