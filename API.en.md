# Rounds Plugin API Documentation

## Table of Contents

- [Overview](#overview)
- [API Version](#api-version)
- [Dependency Setup](#dependency-setup)
- [Entry Point](#entry-point)
- [Card Management](#card-management)
- [Player Data](#player-data)
- [Game State](#game-state)
- [Main Thread Synchronization](#main-thread-synchronization)
- [Events](#events)
- [Bot API](#bot-api)
- [Entity API](#entity-api)
- [Usage Examples](#usage-examples)
- [Important Notes](#important-notes)

## Overview

The Rounds API provides a controlled interface to extend the Rounds plugin. The public contract is defined in the `rounds-api` module. The implementation is provided by the `rounds-core` module via the `Bukkit ServicesManager`.

The main entry point is `com.rounds.api.RoundsAPI`. The actual provider is accessed through `RoundsService`.

## API Version

The API uses `com.rounds.api.ApiVersion`.

- Get version: `RoundsAPI.getVersion()` (current version: **1.1.0**)
- `ApiVersion` contains API version information to check addon compatibility against the current plugin version. Version 1.1.0 introduces custom font glyph support for cards (`glyph`), player deck queries (`getPlayerCards`), and model scale inspection (`getPlayerScale`).

It is recommended to verify the API version for features with strict compatibility requirements.

## Dependency Setup

Add `rounds-api` as `compileOnly`. Do not shade it into your addon.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    // Add Rounds maven repository if published
}

dependencies {
    compileOnly("com.rounds:rounds-api:<version>")
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    compileOnly 'com.rounds:rounds-api:<version>'
    compileOnly 'io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT'
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.rounds</groupId>
        <artifactId>rounds-api</artifactId>
        <version><version></version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.20.4-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### plugin.yml

```yaml
name: MyRoundsAddon
main: com.example.myrdaddon.MyRoundsAddon
version: 1.0.0
api-version: '1.20'
depend: [Rounds]        # Required dependency
# or
softdepend: [Rounds]   # Optional integration
```

## Entry Point

### RoundsAPI

`com.rounds.api.RoundsAPI` is a static facade over `RoundsService`. All calls are forwarded to the registered provider from `Bukkit.getServicesManager()`.

```java
public final class RoundsAPI {
    public static RoundsService getService();
    public static boolean isAvailable();
    public static ApiVersion getVersion();

    // Cards
    public static void registerCard(CustomCard card);
    public static void unregisterCard(Plugin plugin, NamespacedKey key);
    public static void unregisterAllCards(Plugin plugin);
    public static Optional<CustomCard> getCard(NamespacedKey key);
    public static List<CustomCard> getRegisteredCards();

    // Player data
    public static Optional<CustomDataContainer> getCustomData(Player player);
    public static Optional<CustomDataContainer> getCustomData(UUID playerUuid);

    // Game state
    public static int getCurrentRound();
    public static boolean isMatchRunning();

    // Synchronization
    public static boolean runSync(Runnable task);
}
```

### RoundsService

`com.rounds.api.RoundsService` defines the full provider contract.

```java
public interface RoundsService {
    ApiVersion getVersion();

    // Card management
    void registerCard(CustomCard card);
    void unregisterCard(Plugin plugin, NamespacedKey key);
    void unregisterAllCards(Plugin plugin);
    Optional<CustomCard> getCard(NamespacedKey key);
    List<CustomCard> getRegisteredCards();

    List<CustomCard> getPlayerCards(Player player);

    // Player data and attributes
    Optional<CustomDataContainer> getCustomData(Player player);
    Optional<CustomDataContainer> getCustomData(UUID playerUuid);
    double getPlayerScale(Player player);

    // Game state
    int getCurrentRound();
    boolean isMatchRunning();

    // Synchronization
    boolean runSync(Runnable task);
}
```

#### Availability check

```java
if (!RoundsAPI.isAvailable()) {
    getLogger().warning("Rounds API is not available. Disabling integration.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

## Card Management

### CustomCard

Custom cards must implement `com.rounds.api.card.CustomCard`.

```java
public interface CustomCard {
    @NotNull NamespacedKey getKey();
    @NotNull String getId();
    @NotNull Component getDisplayName();
    @NotNull List<Component> getDescription();
    @NotNull ItemStack getIcon();
    @NotNull CardRarity getRarity();

    default @NotNull CardRole getRole() { return CardRole.NEUTRAL; }

    void apply(@NotNull CardContext context);
}
```

| Method | Description |
| --- | --- |
| `getKey()` | Unique `NamespacedKey` of the card. Primary identifier in the registry. |
| `getId()` | String identifier of the card within the addon/registry. |
| `getDisplayName()` | Display name shown in the draft GUI. Using `Component` is recommended. |
| `getDescription()` | Card description. A list of components is used for multi-line tooltip rendering. |
| `getIcon()` | Item icon displayed in the card selection menu. |
| `getRarity()` | Card rarity. Affects bot auto-draft prioritization and visual styling. |
| `getRole()` | Card role. Defaults to `NEUTRAL`. Can be used by plugin logic for categorization. |
| `apply(CardContext context)` | Activation point. Called when the card is applied to the target player in the current round context. |

### CardRarity

`com.rounds.api.card.CardRarity` values:

| Value | Purpose |
| --- | --- |
| `COMMON` | Common |
| `UNCOMMON` | Uncommon |
| `RARE` | Rare |
| `EPIC` | Epic |
| `LEGENDARY` | Legendary |

### CardRole

`com.rounds.api.card.CardRole` values:

| Value | Purpose |
| --- | --- |
| `OFFENSIVE` | Offensive |
| `DEFENSIVE` | Defensive |
| `UTILITY` | Utility |
| `MOBILITY` | Mobility |
| `SUPPORT` | Support |
| `NEUTRAL` | Neutral/universal |

### CardContext

`com.rounds.api.card.CardContext` is passed to `apply(CardContext context)` and provides access to the application context.

Key aspects:
- Access to the target player
- Access to source/plugin context where applicable
- Access to round context when needed
- Integration with Rounds mechanics via the core implementation

Only rely on the public `CardContext` API. Do not reference internal implementation classes.

### CardBuilder

`com.rounds.api.card.CardBuilder` helps construct a `CustomCard` without implementing every method manually. It allows declarative setup of key, name, description, icon, rarity, role and the `apply` logic.

### Registration and unregistration

```java
// Register
CustomCard card = new MyCustomCard(new NamespacedKey(this, "my_card"));
RoundsAPI.registerCard(card);

// Retrieve
Optional<CustomCard> opt = RoundsAPI.getCard(new NamespacedKey(this, "my_card"));

// Unregister a specific card
RoundsAPI.unregisterCard(this, new NamespacedKey(this, "my_card"));

// Unregister all cards of this plugin
RoundsAPI.unregisterAllCards(this);

// Get all registered cards
List<CustomCard> all = RoundsAPI.getRegisteredCards();
```

#### Registry rules

- `registerCard(CustomCard card)` adds the card to the global pool. If a previously removed card is registered again, it is moved to the end of the list.
- Each card is bound to its `ownerPlugin`. Removing cards owned by other plugins is not allowed.
- If the owner plugin is disabled, its cards are not allowed to remain in the active pool by provider logic.
- `unregisterCard(Plugin plugin, NamespacedKey key)` throws `SecurityException` if the caller is not the card owner.
- `unregisterAllCards(Plugin plugin)` unregisters all cards belonging to that plugin.
- `getRegisteredCards()` returns an immutable snapshot. Changes to the registry after retrieval do not affect that list.
- `getCard(NamespacedKey key)` returns `Optional.empty()` if the card is not registered.

## Player Data

Session data is provided via `com.rounds.api.data.CustomDataContainer`.

### Accessing the container

```java
Optional<CustomDataContainer> data = RoundsAPI.getCustomData(player);
// or
Optional<CustomDataContainer> data = RoundsAPI.getCustomData(player.getUniqueId());
```

### CustomDataContainer

The container holds arbitrary data bound to the player's active session. It is strictly for the current match/session.

Principles:
- Only available for an active session.
- Automatically cleared on player disconnect. Once the session is closed (synchronously with `PlayerQuitEvent`), `getCustomData(UUID)` returns `Optional.empty()` immediately.
- Not intended for persistent storage across server restarts.
- Use addon-unique keys to avoid collisions.

Typical usage:

```java
RoundsAPI.getCustomData(player).ifPresent(data -> {
    data.set("myaddon.counter", 1);
    int counter = data.get("myaddon.counter", Integer.class, 0);
    data.remove("myaddon.counter");
    if (data.has("myaddon.counter")) {
        // ...
    }
});
```

Refer to the public `CustomDataContainer` interface in `rounds-api` for its exact method set.

## Game State

```java
int round = RoundsAPI.getCurrentRound(); // 0 - lobby, >=1 - active round, -1 - reset/error
boolean running = RoundsAPI.isMatchRunning();
```

These values reflect the current Rounds match/round state and can be used for conditional addon logic.

## Main Thread Synchronization

Some Bukkit/Paper operations must run strictly on the main server thread.

```java
RoundsAPI.runSync(() -> {
    // Actions that require the main thread
});
```

- `runSync(Runnable task)` executes the task on the main thread. If already on the main thread, the implementation may run it immediately.
- Returns `true` if the task was executed immediately or queued. Returns `false` if the Rounds core is disabled or the server is shutting down.
- `task` must not be `null`; `NullPointerException` is thrown otherwise.

Use `runSync` for any interaction with entities, blocks, inventories, scoreboards or other state tied to the Bukkit main thread.

## Events

All events are in `com.rounds.api.event` and extend `org.bukkit.event.Event`. Some implement `org.bukkit.event.Cancellable`.

Use a standard Bukkit `Listener` to handle them.

### Event list

| Event | Cancellable | Description |
| --- | --- | --- |
| `RoundsWeaponShootEvent` | Yes | Fired when a weapon is fired in Rounds context. Allows blocking or modifying the shot. |
| `RoundsBulletHitEvent` | No | Fired when a bullet produced by Rounds hits an entity or block. Provides bullet, target/block, damage and hit context. |
| `RoundsShieldBlockEvent` | No | Fired when damage is successfully blocked by a shield. |
| `RoundsCardDraftEvent` | No | Fired during the card draft phase. Provides phase/context and available cards. |
| `RoundsCardSelectEvent` | No | Fired when a player selects a card during draft. |
| `RoundsRoundStartEvent` | No | Fired at round start. Useful for per-round initialization. |
| `RoundsRoundEndEvent` | No | Fired at round end. Useful for cleanup or result processing. |

### Example listener

```java
public class RoundsListener implements Listener {
    @EventHandler
    public void onShoot(RoundsWeaponShootEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        // Custom logic
    }

    @EventHandler
    public void onBulletHit(RoundsBulletHitEvent e) {
        // Handle hit
    }
}
```

Register the listener in `onEnable()` via `PluginManager.registerEvents`.

## Bot API

The `rounds-api` provides minimal interfaces for bot integration.

### BotView

`com.rounds.api.bot.BotView` represents a bot view for external integrations. It exposes limited bot information without accessing internal NMS classes.

### BotArchetype

`com.rounds.api.bot.BotArchetype` describes the bot behavior archetype used by the Rounds AI system. Can be used for analysis/integration without directly manipulating decision logic.

Access to bot data is limited to what the public API exposes. If no direct getters exist on `RoundsAPI`, rely on events and provided context.

## Entity API

### RoundsBullet

`com.rounds.api.entity.RoundsBullet` represents a projectile created by the Rounds combat system. Details are available through relevant events, primarily `RoundsBulletHitEvent`.

Use `RoundsBulletHitEvent` to read projectile info. Do not create or manually control `RoundsBullet` instances through this API.

## Usage Examples

### Minimal addon with a custom card

```java
package com.example.myrdaddon;

import com.rounds.api.RoundsAPI;
import com.rounds.api.card.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MyRoundsAddon extends JavaPlugin {
    @Override
    public void onEnable() {
        if (!RoundsAPI.isAvailable()) {
            getLogger().warning("Rounds not found, disabling plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        CustomCard card = new CustomCard() {
            private final NamespacedKey key = new NamespacedKey(MyRoundsAddon.this, "example_card");

            @Override
            public NamespacedKey getKey() {
                return key;
            }

            @Override
            public String getId() {
                return "example_card";
            }

            @Override
            public Component getDisplayName() {
                return Component.text("Example Card");
            }

            @Override
            public List<Component> getDescription() {
                return List.of(
                    Component.text("Example custom card description"),
                    Component.text("Powered by Rounds API")
                );
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(Material.EMERALD);
            }

            @Override
            public CardRarity getRarity() {
                return CardRarity.RARE;
            }

            @Override
            public CardRole getRole() {
                return CardRole.UTILITY;
            }

            @Override
            public void apply(CardContext context) {
                // Card effect logic
            }
        };

        RoundsAPI.registerCard(card);
        getLogger().info("Custom card registered");
    }

    @Override
    public void onDisable() {
        if (RoundsAPI.isAvailable()) {
            RoundsAPI.unregisterAllCards(this);
        }
    }
}
```

### Player data and main thread sync

```java
@EventHandler
public void onRoundStart(RoundsRoundStartEvent event) {
    // Initialize session data via RoundsAPI.getCustomData
    // For Bukkit API calls that require main thread, use runSync
    RoundsAPI.runSync(() -> {
        // Synchronous actions on the main thread
    });
}
```

## Important Notes

- **Main thread only.** World, entity, inventory or scoreboard changes must run on the main thread. Use `RoundsAPI.runSync()` from async contexts.
- **Session-scoped data.** `CustomDataContainer` is only valid while the player is online. Data is removed on disconnect. Do not rely on cross-session persistence.
- **Card ownership.** Only the owner plugin can unregister its own cards. Violating this throws `SecurityException`.
- **Immutable snapshots.** `getRegisteredCards()` returns a snapshot of registry state at the time of call. Later registry changes do not affect it.
- **Availability first.** Always check `RoundsAPI.isAvailable()` before calling API methods.
- **API boundary.** Only use public types from `rounds-api`. Classes from `rounds-core` are internal and not part of the API contract; they may change without backward compatibility guarantees.
- **Events.** Some events are cancellable. Always check `isCancelled()` when dealing with cancellable events.


## Server Version Compatibility and the NmsBridge

The public API (`rounds-api`) is server-version independent: it holds no `net.minecraft.*` references and is compiled against the stable Paper API. Engine internals that touch NMS (fake player spawning, bot movement and rotation, entity physics, spectator glow packets) are isolated behind the `com.rounds.nms.NmsBridge` interface and shipped by version-specific adapter modules.

| Artifact | Server |
|---|---|
| `RoundsPlugin-1.20.4-v1.7-byMr712.jar` | Paper / Purpur 1.20.4 |
| `RoundsPlugin-1.20.5-v1.7-byMr712.jar` | Paper / Purpur 1.20.5 |
| `RoundsPlugin-1.20.6-v1.7-byMr712.jar` | Paper / Purpur 1.20.6 |
| `RoundsPlugin-1.21-v1.7-byMr712.jar` | Paper / Purpur 1.21 |
| `RoundsPlugin-1.21.1-v1.7-byMr712.jar` | Paper / Purpur 1.21.1 |
| `RoundsPlugin-1.21.10-v1.7-byMr712.jar` | Paper / Purpur 1.21.10 |
| `RoundsPlugin-1.21.11-v1.7-byMr712.jar` | Paper / Purpur 1.21.11 |
| `RoundsPlugin-1.21.3-v1.7-byMr712.jar` | Paper / Purpur 1.21.3 |
| `RoundsPlugin-1.21.4-v1.7-byMr712.jar` | Paper / Purpur 1.21.4 |
| `RoundsPlugin-1.21.5-v1.7-byMr712.jar` | Paper / Purpur 1.21.5 |
| `RoundsPlugin-1.21.6-v1.7-byMr712.jar` | Paper / Purpur 1.21.6 |
| `RoundsPlugin-1.21.7-v1.7-byMr712.jar` | Paper / Purpur 1.21.7 |
| `RoundsPlugin-1.21.8-v1.7-byMr712.jar` | Paper / Purpur 1.21.8 |
| `RoundsPlugin-1.21.9-v1.7-byMr712.jar` | Paper / Purpur 1.21.9 |
| `RoundsPlugin-26.1.1-v1.7-byMr712.jar` | Paper / Purpur 26.1.1 |
| `RoundsPlugin-26.1.2-v1.7-byMr712.jar` | Paper / Purpur 26.1.2 |
| `RoundsPlugin-26.2-v1.7-byMr712.jar` | Paper / Purpur 26.2 |
| `RoundsPlugin-26.3-v1.7-byMr712.jar` | Paper / Purpur 26.3 |

- Paper and Purpur of the same Minecraft version use the same jar (Purpur is a Paper fork; the NMS layer matches).
- Version 1.21.2 is not available from PaperMC upstream (the Fill API and maven repositories no longer serve its artifacts), so the 1.21.2 jar cannot be built.
- Loading a jar of a different version degrades gracefully: bots, fake player spawning and spectator glow are disabled with a log warning, while cards, commands and scoreboards keep working.
- Plugins consuming `rounds-api` work on every version: the API contract does not depend on the NMS adapter.
