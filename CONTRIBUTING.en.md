# Contributing to Rounds-MinecraftPlugin

Thank you for your interest in contributing to the open-source Rounds plugin. This guide will help you set up your developer environment, create your first card, or contribute code.


## 1. Quick Start & Build

The project uses Gradle with a multi-module architecture.

### Environment Requirements
- **JDK 17+** (`rounds-core` and `rounds-api` use Java 17 toolchain; JDK 25 is supported to link against newest Purpur 26.2 NMS mappings).
- All source code strictly targets **Java 17** bytecode (`release = 17`).

### Key Gradle Commands

- **Quick Build for Local Testing:**
  Builds only `rounds-core` and one target NMS module (defaults to Purpur 26.2):
  ```bash
  ./gradlew quickBuild
  ```
  To target a different server version, supply the `-PtargetNms` flag:
  ```bash
  ./gradlew quickBuild -PtargetNms=1.20.4
  ```

- **Run Unit Tests:**
  ```bash
  ./gradlew :rounds-api:test :rounds-core:test
  ```

- **Cross-Platform Resource Pack Builder:**
  Generates `Rounds-CardPack-RU.zip`, `Rounds-CardPack-EN.zip`, and `Rounds-CardPack.zip` in `build/distributions/cardpacks/`:
  ```bash
  ./gradlew buildCardPack
  ```
  To synchronize to Desktop and ATLauncher client instances, append:
  ```bash
  ./gradlew buildCardPack -PdeployDesktop -PdeployInstances
  ```


## 2. Creating a New Card in 2 Minutes (Data-Driven Cards)

No Java code is required to add custom cards. You only need to create a YAML configuration.

### Step 1. Create a YAML File
Place your file in `rounds-core/src/main/resources/cards/custom/my-card.yml`.

### Step 2. Define the Card with Autocompletion
Reference the schema on the first line to enable autocompletion in IntelliJ IDEA and VS Code:

```yaml
# yaml-language-server: $schema=../../cards.schema.json
id: 501
name:
  ru: "Громовержец"
  en: "Storm Bringer"
description:
  ru: "При блоке щитом наносит 4 урона врагам и отталкивает их"
  en: "Shield block deals 4 damage to enemies and pushes them back"
rarity: RARE
material: IRON_SWORD
enabled: true

# Base stat modifiers
effects:
  bullets: 1
  ammo: 2

# Declarative combat triggers (executed with zero tick overhead)
triggers:
  - on: ON_SHIELD_BLOCK
    action: DAMAGE_VICTIM
    value: 4.0
  - on: ON_SHIELD_BLOCK
    action: PUSH_VICTIM
    value: 1.5
  - on: ON_SHIELD_BLOCK
    action: PLAY_SOUND
    value: 1.0
    param: "entity.lightning_bolt.thunder"
```

### Supported Triggers
- `ON_SHOOT`: Fired when shooting a weapon.
- `ON_BULLET_HIT`: Fired when a bullet hits an enemy living entity.
- `ON_DAMAGE_TAKEN`: Fired when the player takes incoming damage.
- `ON_SHIELD_BLOCK`: Fired when successfully blocking with the shield.
- `ON_RELOAD`: Fired when weapon reload completes.

### Supported Actions
- `RESTORE_AMMO`: Restores ammo to the magazine (`value` = count).
- `HEAL`: Restores health to the player (`value` = HP amount).
- `DAMAGE_VICTIM`: Inflicts direct damage to the target (`value` = damage amount).
- `PUSH_SHOOTER`: Applies recoil impulse to the shooter (`value` = multiplier).
- `PUSH_VICTIM`: Knockbacks the target entity (`value` = multiplier).
- `PLAY_SOUND`: Plays an audio clip (`param` = sound name, `value` = pitch).
- `SPAWN_PARTICLE`: Spawns visual particles (`param` = particle type, `value` = count).
- `APPLY_POTION`: Applies a potion effect to the target (`param` = effect type, `value` = duration in seconds).

### Step 3. Bake Description onto Card Art (Optional)
Run the texture generation tool:
```bash
python tools/generate_card_textures.py
```
This automatically bakes title and description text for all 118 cards at the standard 160x250 resolution (original art size, no resampling) onto card PNG textures in `cards/ru` and `cards/en` with high-contrast drop shadows without backdrop boxes. Each rarity variation gets its own texture `card<id>-<rarity>.png` with `{0}` placeholders filled from the variation's `values` list.


## 3. Code Standards & Pull Request Guidelines

1. **Bytecode Compatibility:** All compiled classes must produce Java 17 bytecode.
2. **NMS Isolation:** Direct imports of `net.minecraft.*` or `org.bukkit.craftbukkit.*` in `rounds-core` are strictly forbidden. Use the `NmsBridge` interface.
3. **No Emojis Rule:** In `readme.md`, `AGENTS.md`, `CONTRIBUTING.md`, and all markdown documents, emojis and emoticon glyphs are strictly forbidden.
4. **Pass All Tests:** Ensure tests pass before opening a pull request:
   ```bash
   ./gradlew :rounds-api:test :rounds-core:test
   ```
   Automated GitHub Actions CI verifies build integrity, test suites, and resource pack packaging on every PR.
