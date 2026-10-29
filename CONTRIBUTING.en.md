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
  Generates `Rounds-ResourcePack-RU.zip`, `Rounds-ResourcePack-EN.zip`, and base `Rounds-ResourcePack.zip` (clean unbaked templates, [download on Google Drive](https://drive.google.com/file/d/1pwpCzmDIfc6RpAlirZPlN_j6W_areW-v/view?usp=sharing)) in `resourcepacks_builds/`:
  ```bash
  ./gradlew buildResourcePacks
  ```
  To synchronize to Desktop, add the flag:
  ```bash
  ./gradlew buildResourcePacks -PdeployDesktop
  ```


## 2. Creating New Cards

Rounds supports two methods for creating new cards:
1. **Declarative Method in 2 Minutes (Data-Driven Cards):** Fast YAML configuration without writing Java code.
2. **Programmatic Method via Java API (Rounds Addon):** A standalone plugin using `rounds-api` and `CardBuilder` for custom game mechanics of any complexity.

### Method 1. Creating a Card in 2 Minutes via YAML (Data-Driven Cards)

No Java code is required to add custom cards. You only need to create a YAML configuration.

#### Step 1. Create a YAML File
Place your file in `rounds-core/src/main/resources/cards/custom/my-card.yml`.

#### Step 2. Define the Card with Autocompletion
Reference the schema on the first line to enable autocompletion in IntelliJ IDEA and VS Code:

```yaml
# yaml-language-server: $schema=../../cards.schema.json # Schema reference for syntax highlighting and IDE autocompletion
id: 501 # Unique numeric card ID in the game (use 500+ for custom cards)
name: # Localized card display names block
  ru: "Громовержец" # Card display name in Russian
  en: "Storm Bringer" # Card display name in English
description: # Localized card ability descriptions block
  ru: "При блоке щитом наносит 4 урона врагам и отталкивает их" # Effect description in Russian
  en: "Shield block deals 4 damage to enemies and pushes them back" # Effect description in English
rarity: RARE # Card rarity tier (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
material: IRON_SWORD # Item icon material for inventory GUI display
enabled: true # Card active flag (true - available in draft, false - disabled)

# Base stat modifiers for weapon and player
effects: # Block of passive stat bonuses applied when picking the card
  bullets: 1 # Increases the number of bullets per shot by +1 (shotgun spread)
  ammo: 2 # Increases magazine capacity by +2 bullets

# Declarative combat triggers (event-driven, zero tick overhead)
triggers: # List of triggers and actions executed on combat events
  - on: ON_SHIELD_BLOCK # Trigger event: successful damage block with shield
    action: DAMAGE_VICTIM # Action: deals direct damage to the attacking enemy
    value: 4.0 # Damage amount inflicted (4.0 HP = 2 hearts)
  - on: ON_SHIELD_BLOCK # Trigger event: repeated trigger on the same shield block
    action: PUSH_VICTIM # Action: pushes the attacking enemy backwards
    value: 1.5 # Knockback impulse strength multiplier
  - on: ON_SHIELD_BLOCK # Trigger event: sound effect on block
    action: PLAY_SOUND # Action: plays an audio clip to nearby players
    value: 1.0 # Audio pitch value
    param: "entity.lightning_bolt.thunder" # Vanilla Minecraft sound identifier
```

#### Supported Triggers
- `ON_SHOOT`: Fired when shooting a weapon.
- `ON_BULLET_HIT`: Fired when a bullet hits an enemy living entity.
- `ON_DAMAGE_TAKEN`: Fired when the player takes incoming damage.
- `ON_SHIELD_BLOCK`: Fired when successfully blocking with the shield.
- `ON_RELOAD`: Fired when weapon reload completes.

#### Supported Actions
- `RESTORE_AMMO`: Restores ammo to the magazine (`value` = count).
- `HEAL`: Restores health to the player (`value` = HP amount).
- `DAMAGE_VICTIM`: Inflicts direct damage to the target (`value` = damage amount).
- `PUSH_SHOOTER`: Applies recoil impulse to the shooter (`value` = multiplier).
- `PUSH_VICTIM`: Knockbacks the target entity (`value` = multiplier).
- `PLAY_SOUND`: Plays an audio clip (`param` = sound name, `value` = pitch).
- `SPAWN_PARTICLE`: Spawns visual particles (`param` = particle type, `value` = count).
- `APPLY_POTION`: Applies a potion effect to the target (`param` = effect type, `value` = duration in seconds).

#### Step 3. Bake Description onto Card Art (Optional)
Run the texture generation tool:
```bash
python tools/generate_card_textures.py
```
This automatically bakes title and description text for all 118 cards at the standard 160x250 resolution (original art size, no resampling) onto card PNG textures in `cards/ru` and `cards/en` with high-contrast drop shadows without backdrop boxes. Each rarity variation gets its own texture `card<id>-<rarity>.png` with `{0}` placeholders filled from the variation's `values` list.


### Method 2. Creating a Card via Addon Plugin API (rounds-api)

To build complex abilities, custom projectiles, particles, or external integrations, create a standalone Spigot/Paper addon plugin using `rounds-api`.

#### Step 1. Add rounds-api to Your Build Tool

In your addon plugin's `build.gradle.kts`:
```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Include Rounds API in compileOnly scope (provided by the server at runtime)
    compileOnly("com.rounds:rounds-api:1.1.0")
    // Include Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}
```

In `plugin.yml`, declare dependency on Rounds:
```yaml
name: FrostBiteAddon
main: com.example.frostbite.FrostBiteAddon
version: 1.0.0
api-version: '1.20'
depend: [Rounds]
```

#### Step 2. Create and Register the Card in Java Code

Use `CardBuilder` to declare card properties and combat callbacks. In the example below, every single line includes an explanatory comment:

```java
package com.example.frostbite; // Addon plugin package

import com.rounds.api.RoundsAPI; // Main facade for interacting with the Rounds API
import com.rounds.api.card.CardBuilder; // Fluent builder for constructing custom cards
import com.rounds.api.card.CardRarity; // Enum of card rarity tiers (COMMON..LEGENDARY)
import com.rounds.api.card.CardRole; // Tactical role of the card (OFFENSIVE, DEFENSIVE, UTILITY, etc.)
import org.bukkit.Material; // Enum of Minecraft materials for the card icon item
import org.bukkit.Particle; // Enum of Minecraft visual particles for special effects
import org.bukkit.Sound; // Enum of vanilla Minecraft sound effects
import org.bukkit.entity.LivingEntity; // LivingEntity interface for safe target casting
import org.bukkit.plugin.java.JavaPlugin; // Base class for creating Paper/Spigot plugins

public final class FrostBiteAddon extends JavaPlugin { // Main addon plugin class

    @Override // Override server plugin startup method
    public void onEnable() { // Entry point executed when the plugin loads
        if (!RoundsAPI.isAvailable()) { // Verify that the Rounds API service is active on the server
            getLogger().warning("Rounds API not found! Disabling addon..."); // Log error to server console
            getServer().getPluginManager().disablePlugin(this); // Safely disable addon to prevent errors
            return; // Exit initialization method early
        } // End of API availability check

        // Create and register custom card using CardBuilder with comments on every line:
        CardBuilder.create(this, "frost_bite") // Create card builder with unique ID frost_bite
            .name("ru", "Ледяной Укус") // Localized card name in Russian
            .name("en", "Frost Bite") // Localized card name in English
            .description("ru", "Пули наносят +2 урона и замораживают цель") // Card description in Russian
            .description("en", "Bullets deal +2 bonus damage and freeze target") // Card description in English
            .rarity(CardRarity.UNCOMMON) // Set card rarity tier (UNCOMMON)
            .material(Material.PACKED_ICE) // Packed ice item used as the inventory icon
            .role(CardRole.OFFENSIVE) // Offensive card role for bot AI tactical prioritization
            .onBulletHit(event -> { // Combat event callback when a bullet hits an entity
                event.setDamage(event.getDamage() + 2.0); // Increase bullet damage by +2 HP (1 heart)
                if (event.getHitEntity() instanceof LivingEntity target) { // Check that hit target is a living entity
                    target.setFreezeTicks(140); // Apply powder snow freeze effect for 7 seconds
                } // End of living target check
                var loc = event.getHitLocation(); // Get 3D hit coordinates in the world
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 15, 0.3, 0.3, 0.3, 0.05); // Spawn snowflakes
                loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, SoundCategory.RECORDS, 1.0f, 1.4f); // Play glass breaking sound (RECORDS category for note blocks slider)
            }) // End of hit event handler lambda
            .register(); // Build card instance and register immediately into the Rounds draft

        getLogger().info("Card Frost Bite registered successfully in Rounds!"); // Log success message
    } // End of onEnable method

    @Override // Override server plugin shutdown method
    public void onDisable() { // Entry point executed on server stop or plugin reload
        if (RoundsAPI.isAvailable()) { // Check API availability before calling
            RoundsAPI.unregisterAllCards(this); // Automatically unregister all cards owned by this addon
        } // End of API check
    } // End of onDisable method
} // End of addon plugin class
```

#### CardBuilder Capabilities & Lifecycle:
- `CardBuilder.create(plugin, id)`: Registers the card with an automatic `NamespacedKey` bound to your plugin.
- `.onBulletHit(event -> ...)`: Intercepts bullet hits on players or blocks (adjust damage, spawn particles, freeze).
- `.onShoot(event -> ...)`: Customizes shooting parameters (bullet tags, recoil, sounds).
- `.onShieldBlock(event -> ...)`: Reacts to successful shield parries.
- `.onRoundStart(context -> ...)` / `.onRoundEnd((context, won) -> ...)`: Round lifecycle bonuses and cooldown resets.
- `RoundsAPI.unregisterAllCards(this)`: Safely unregisters all cards when the addon is disabled.


## 3. Code Standards & Pull Request Guidelines

1. **Bytecode Compatibility:** All compiled classes must produce Java 17 bytecode to ensure plugin core compatibility across server versions 1.20.4 - 26.3.
2. **NMS Isolation:** Direct imports of `net.minecraft.*` or `org.bukkit.craftbukkit.*` in `rounds-core` are strictly forbidden. Use the `NmsBridge` interface.
3. **Pass All Tests:** Ensure tests pass before opening a pull request:
   ```bash
   ./gradlew :rounds-api:test :rounds-core:test
   ```
   Automated continuous integration (CI) via GitHub Actions verifies build integrity, test suites, and resource pack packaging on every pull request (PR).
