# Rounds Plugin API Documentation

## Overview
The Rounds API provides a controlled interface for developers to extend the Rounds plugin functionality, including custom cards, data management, and event handling.

The entry point for all API interactions is the `com.rounds.api.RoundsAPI` class.

## Getting Started

### Availability Check
Before invoking API methods, ensure the Rounds service is available:

```java
if (RoundsAPI.isAvailable()) {
    // API is ready
}
```

## Core Functionality

### 1. Card Management
Use `RoundsAPI` to register custom cards. Cards must implement the `CustomCard` interface.

- **Register:** `RoundsAPI.registerCard(CustomCard card)`
- **Unregister:** `RoundsAPI.unregisterCard(Plugin plugin, NamespacedKey key)`
- **Retrieve:** `RoundsAPI.getCard(NamespacedKey key)`

### 2. Player Data
Session data is managed via `CustomDataContainer`.

- **Access:** `RoundsAPI.getCustomData(Player player)` or `RoundsAPI.getCustomData(UUID playerUuid)`
- **Note:** Data is cleared automatically upon player disconnection.

### 3. Game State
- **Current Round:** `RoundsAPI.getCurrentRound()`
- **Match Status:** `RoundsAPI.isMatchRunning()`

## Events
The API exposes several Bukkit events for monitoring gameplay:

- `RoundsBulletHitEvent`: Triggered when a bullet hits an entity or block.
- `RoundsCardDraftEvent`: Triggered during the card draft phase.
- `RoundsCardSelectEvent`: Triggered when a player selects a card.
- `RoundsRoundStartEvent`: Triggered at the start of a round.
- `RoundsRoundEndEvent`: Triggered at the end of a round.
- `RoundsShieldBlockEvent`: Triggered when a shield blocks damage.
- `RoundsWeaponShootEvent`: Triggered when a player fires a weapon.

## Usage Example

```java
public class MyRoundsAddon extends JavaPlugin {
    @Override
    public void onEnable() {
        if (RoundsAPI.isAvailable()) {
            CustomCard myCard = new MyCustomCard();
            RoundsAPI.registerCard(myCard);
            getLogger().info("Registered custom card: " + myCard.getKey());
        }
    }
}
```
