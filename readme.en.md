#RoundsPlugin v1. 0

Minecraft plugin mini-game "Rounds". 4 teams, card system with the possibility of addition and modification, up to 20 rounds, system special. blocks for faster implementation of the plugin on the card.

## Requirements

- Purpur/Paper 1.20.4 - 26. 2.
- Java 17 +
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.13006/) to support placeholders
Optional: [TAB](https://www.spigotmc.org/resources/tab.57806/) to display score and game status

## Installation

1. Copy 'RoundsPlugin-1.0.jar' to 'plugins/' folder
2. Restart the server
3. Configure 'plugins/RoundsPlugin/config.yml' (already configured by default)
4. Configure maps in `plugins/RoundsPlugin/cards/` (by default 65 cards)
5. Get blocks with the command /rdebug giveblocks and arrange in the world

## Quick start

###1. Get the blocks.

The administrator must receive the command:

``
/rdebug giveblocks
``

This will add all the special blocks to your inventory and switch you to Creative mode.
Then, place the blocks in the world (an explanation of how they work will be provided later).

###Blocks of joining teams

| Block | Material | What does |
|------|--------|---------|
| **Blue Entrance** | Blue Wool | Join Blue Team |
| **Red Entrance** | Red wool | Join the Red Team |
| **Yellow Entrance** | Yellow wool | Join the yellow team |
| **Green Entrance** |Green wool | Join the Green Team |

###Blocks map and spawn

| Block | Material | What does |
|------|--------|---------|
| **Lobby block** | Emerald block | Lobby center. Only 1 block. At launch, everyone teleports here. If you put the second, the second becomes the main |
| **Card block 50x50** |Diamond block |Center zone 50x50 blocks. Spawns are being searched inside this zone |
| **Map block 100x100** |Emerald block |Map zone center 100x100 blocks |
| **Spawn block** | Beacon | Spawn point for team inside map zone |

#### Control unit

| Block | Material | What does |
|------|--------|---------|
| **Start block** | Iron block | Start game (only for 'rounds.admin') |
| **Block Endgame** | Redstone Block | End Game (rounds.admin' only) |
| **Block of Blue Team for Lobby** | Blue Wool | Adds player to Blue Team |
| **Red team block for the lobby** |Red wool | Adds player to red team |
| **Block yellow team for lobby** | Yellow wool | Adds player to yellow team |
| **Green team block for lobby** |Green wool | Adds player to green team |

###2. Place blocks of maps

1. Put the **lobby block** - the center of your lobby zone
2. Place **blocks of the map** (50x50 or 100x100) - the centers of the zones on which the game will go
3. Inside the zone of each block of the map, put **blocks spawn** - points where commands will appear
4. There can be several blocks of the card – in each round, the plugin will select a random zone.

###3. Players join teams

Players just **step on a colored wool block to join the team.
You can change the team before the game starts (just step on another block).
Couldown of the team change - 1 second.

###4. Launch the game.

``
rdebug start
``

Or step on the **Tagshit CD* block in the world.

###5. How the game goes

1. All players are teleported to **lobby block** (+1 in height)
2. The chat is **timer 5 → 1** (seconds)
3. At each tick, all players become **spectators** and teleport to a random block of a preview spawn.
4. After the timer, each team is teleported to its own spawn unit** (+1 in height), players receive a survival mode** and a gun.
5. *Selection of cards** - each player chooses 1 card out of 5
6. The round begins, the players shoot each other.
7. The last survivor of the round wins
8. **Next round** – the plugin selects the random zone of the map** (with replays), distributes the spawns again, all teleported
9. When the team scores the right number of wins, it teleports everyone to the lobby.

## Setting up

### config.yml

`Yaml'
Language: id file from plugins/RoundsPlugin/lang/ without .txt (old en/en are also accepted)
language: RU ru

# Default Player Stats (reset each round)
defaults
damage: 3.0 # Basic damage per bullet
attack-speed: 20 #couldown attacks in ticks (20 = 1 sec)
ammo: 3 # Current ammunition
max-ammo: 3 # Maximum bullets
Bullets: 1 Number of bullets per shot
hp: 20 #Health
bullet-speed: 1.0 speed of bullets
reload-speed: 0 # Recharge rate (0 = base 3 seconds)

game:
default-rounds: 5 # Rounds to win by default
max-rounds: 20 # Maximum rounds
card-selection-time: 200 # card selection time (200 ticks = 10 seconds)
respawn-delay: 5 # Respawn delay

teams:
enabled:
- Blue.
- Red.
- YELLOW
- Green.

gun:
material: STICK # Material of the gun
base-cooldown: 20 #Basic cooldown

cards:
selection-count: 5 # Number of cards
weighted-rarity: true # Weighted selection by rarity

# Rules of Peace (Automatically Apply During Play)
game-rules:
enabled: true #Global switch
instant-respawn: true #Instant respawn (no screen of death)
Keep-inventory: True #Saving things at death
freeze-time: true time freeze (always day)
Disable-weather: True #Change weather
disable-mob-spawning: true

# Painting nicknames in team color
color-nicknames: true

# Built-in scoreboard (deactivated, conflicts with TAB)
builtin-scoreboard:
enabled: false
title: "&6&lROUNDS"
``

###

The cards are stored as **separate YAML files** in the folder `plugins/RoundsPlugin/cards/original/`. Custom maps are added to 'plugins/RoundsPlugin/cards/custom/'.

### The format of a regular card (example 'burst.yml'):

`Yaml'
id: 8
name:
En: "&a queue"
"&aBurst"
description:
+2 bullets, +3 rounds, -60% damage, +10% reloading
+2 Bullets, +3 Ammo, -60% DMG, +10% Reload time
material: Arrow
enabled: true
variations:
- rarity: COMMON
effects:
bullets: 2
ammo: 3
damage: -0.6
reload: 1
``

#### Map format with variation (example `combine.yml`):
`Yaml'
id: 13
name:
en: "&4 Association"
en: "&4Combine"
description:
ru: "+{0} damage, -{1} cartridge, +{2} recharge"
+{0} DMG, -{1} Ammo, +{2} Reload time
material: Redstone
enabled: true
variations:
- rarity: RARE
values: [”100%”, “2”, “10%”]
effects:
damage: 1.0
ammo: -2
reload: 1
- rarity: COMMON
values: [”50%”, “1”, “5%”]
effects:
damage: 0.5
ammo: -1
reload: 0.5
- rarity: UNCOMMON
values: [“75%”, “2”, “8%”]
effects:
damage: 0.75
ammo: -2
reload: 0.8
- rarity: EPIC
values: [“125%”, “3”, “13%”]
effects:
damage: 1.25
ammo: -3
reload: 1.3
``


#### Effect parameters:

| Key| Description |
|------|---------- |
| `damage` | Additional damage (multiplifier: -0.7 = -70%) |
| `attack-speed' | Attack speed (less = faster) |
| `attack-speed-reload' Recharge time (less = faster) |
| `attack-range' | Range of fire |
| `bullets' | Number of bullets per shot |
| `ammo' | Ammo |
| `bullet-speed' | Bullet speed |
| `bounce' | Ricochet on the walls |
| `target-bounce' | Ricochet on targets (closest enemy) |
| `hp` | Maximum health |
| `cold` + `cold-level` | Chance and freezing level |
| `poison` + `poison-level` | Chance and level of poisoning |
| `parazit` + `parazit-level` | Chance and level of exhaustion (wither) |
| `leech' | Suction of health |
| `homing' | Bullet homing |
| `homing-on-block` | Bullet homing for N seconds on block |
| `empower` + `empower-charge` | Damage amplification (consumed per shot) |
| `dark-strength' | The power of dark energy (+0.5 damage per stack) |
| `big-bullet` | Large bullet (knocks): +70% visual size and hit radius per stack, recharge +30% per stack |
| `bomb-bullet` | Explosive bullets (TNT hit) |
| `bomb-on-block' | Shield-locked bomb |
| `explode-bullets' | Explosive bullets (AoE) |
| `shield' | Shield (lockdown) |
| `shield-charge' | Shield charges |
| `shields-up' | Automatic shield with cartridges = 0 |
| `truster' | Reinforced waste |
| 'grow' | Increasing maximum HP |
| `speed` + `speed-boost` | Speed of movement |
| `stun' | Stun when hit |
| `block-cd` | Couldown of shield lock |
| `reload-speed' | Recharging speed |
| `heal' | Treatment |
| `damage-per-bounce' | Damage for each ricochet |
| `double-block' | Double shield block |
| `auto-reload' | Automatic recharging |
| `saw' | Saw (AoE block damage) |
| `shockwave` | Uсы when blocked (discarded) |
| `silence` | Silence (prohibition of shooting and blocking) |
| `sneaky' | Secrecy |
| `emp` | Delaying enemies when blocked |
| `overpower` | Force (damage in % of HP on lockdown) |
| `refresh` | Update of lockdown cooldown |
| `radiance' | Glow (enemies see you) |
| `lifesteal-aura' | Health-sucking aura |
| `phoenix' | Phoenix (resurrection after death) |
| `abyssal` | Bottomless (calls for a phantom inactive for 30 seconds) |
| `implode' | Explosion to death |
| `echo' | Echo (second volley in 0.25 seconds) |
| `drill` | Drill cartridges (passing through walls) |
| 'splash' | Sharp damage |
| `teleport` | Teleport when blocked |
| `tactical-reload' | Tactical recharge (instant recharge when locked) |
| `ammo-per-hit' | Ammo for hitting |
| 'hp-boost-on-hit' | Increased HP per hit |

#### Rare maps:

| Rare | Chance of falling out |
|---------|------------- |
| COMMON | 40
| UNCOMMON | 30 |
| RARE | 18 |
| EPIC | 9 |
| LEGENDARY | 3 |

####The Wheel of Cards (Wheel)

The administrator may enable automatic card rotation at the time of selection:

``
/rdebug wheel on
``

Every 6 seconds, all open GUI card selections are updated with new random maps.

## Card block system

###Blocks of the map

The blocks of the map define the zones on the map. The zone is a cube: '[centerX ± size/2, centerZ ± size/2]', Y from -64 to 320.

- There may be **multiple* blocks of map on different parts of the map
In each round, the plugin **randomly selects** one of the zones (with the possibility of repeating)
- Inside the zone, all the **blocks of the spawn**

##Blocks of spawn

Spawn blocks define the points where commands appear:

At the beginning of the round, the plugin finds all the spawn blocks within the selected zone of the map.
- Team A is issued a **random **block spawn.
- Team B - **accidental** of the remaining
If spawns are less than teams, spawns are reused.

### Lobby Block

One block on the map indicates the center of the lobby.
If you put the second, the second becomes the main
At /rdebug start all players teleport to the lobby (+1Y)
At the end of the game, all players return to the lobby.

-

## Teams

All game management teams are assembled in /rdebug.

###Game management

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug start` | Start the game (teleport in the lobby → timer → spawn distribution) |
| `/rdebug stop' | Stop the game and drop everything |
| `/rdebug status' | | Show the state of play |
| `/rdebug rounds <number>` | Set rounds to win (1-20) |
| `/rdebug info' | Information about the plugin |
| `/rdebug join` | | Join the team while playing |
| `/rdebug test` | Check - "RoundsPlugin is working!" |

###Blocks of the map

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug giveblocks [player]` | To issue all special blocks (teams, lobbies, maps, spawns) |

###Maps

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug cards` | Open GUI card selection |
| `/rdebug cards reload' | Reboot cards from files |
| `/rdebug cards test [id]`| `[id]` | Apply ID card or open GUI |
| `/rdebug cards giveall` | Unblock all cards |
| `/rdebug applycard <name>` | Apply the card by name |

### Items

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug givegun [player\|@a]' | Give the gun to yourself, the player or everyone |
| `/rdebug giveall' | Issue all cards |
| `/rdebug wheel on\|off' | Turn on/off rotation of maps |

###Scoreboard

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug tab on\|off' | Enable/deactivate built-in scoreboard |
| `/rdebug tab name <title> | | Amend the title of scoreboard |

The built-in scoreboard shows the current round, the player’s team and the victories of all active teams (player teams only).

###Debugging

| Subcommand | Arguments | Description |
|----------- |-------- |-------- |
| `/rdebug help` | Information on all sub-commands |
| `/rdebug stats [player]` | Show all player stats |
| `/rdebug setstat <stat> <value> [player]` | | Set the status |
| `/rdebug setteam <color> [player]` | Install the command |
| `/rdebug setlanguage <language> | | Change language; tab kit shows all found languages from folder 'lang/' |
| `/rdebug effect <type> <ur> <longs> | | Put the potion on |
| `/rdebug heal [number]' | Cured to maximum |
| `/rdebug spawnbomb/heal/toxic/shield' | | Create an entity |
| `/rdebug entities` | | Show custom entities within a radius of 20 blocks |
| `/rdebug resetstats` | Reset all stats and maps |
| `/rdebug reload` | Reboot config.yml, maps and language packs (lang/*.txt) |
| `/rdebug version` | | Plugin and server version |
| `/rdebug killround` | | Kill all enemies (only during the round) |
| `/rdebug iteminfo' | Information about the item in hand |

** Statue for `setstat':**
`dmg`, `atks`, `atks reload`, `atkr`, `bounce`, `ammo`, `bullets`, `cold`, `poison`, `leech`, `homing`, `poison lvl`, `cold lvl`, `parazit`, `hp`, `bomb bullets`, `explode speed`, `embullets`, `embullets', `empower charge`, `dark strang`, `b', `bullet', `b', `bullet', `b', `bullet', `b', `bullet', `bullet',`b', `b', `b', `b', `bullet', `b', `bullet'

** Potion for 'effect':**
`SPEED', `SLOW', `Fast DIGGING', `SLOW DIGGING', `INCREASE DAMAGE', `HEAL', `HARM', `JUMP', `CONFUSION', `BLINDNESS', `NIGHT VISION', `FIRE RESISTANCE', `WATER BREATHING', `INVISIBILITY', `POISON', `RENETHRATION', `VISION', `FION', `LINATION', `W',`A', `LINATION', `VISINA', `F',`A', `W', `FORD',`LITATION',`A', `W',`A', `W', `W',`A', ``A'LINA',`A', ``A'LINA', ``LINA

-

Placeholders (PlaceholderAPI)

The plugin provides placeholders for game information and player statistics.
The full list can be viewed in the game: /rdebug placeholders

### Information about the game

| Placeholder | Returns |
|------------- |--------- |
| `%rounds round%' | Current round number |
| '%rounds rounds to win%' | Rounds to win |
| `%rounds round display%' | Format "3/5" |
| `%rounds state%' | State of play (PLAYING, CARDS, WAITING, etc.) |
| `%rounds team%' | Player team name |
| `%rounds team color%' | Team colour code |
| `%rounds team adjective%' | Adjective commands (blue, red, etc.) |
| `%rounds team wins%` | Player team wins |
| `%rounds blue wins%` | Blue team wins |
| `%rounds red wins%' | Red team victories |
| `%rounds yellow wins%` | Yellow team victories |
| `%rounds green wins%' | Green team wins |
| `%rounds blue name%' | Localized Blue Team name |
| `%rounds red name%' | Localized name of red team |
| `%rounds yellow name%' | Localized yellow team name |
| `%rounds green name%' | Localized name of green team |

### Player Status

| Placeholder | Returns |
|------------- |--------- |
| `%rounds stat hp%' | Health |
| `%rounds stat dmg%' | Damage |
| `%rounds stat atk speed%' | Attack speed |
| `%rounds stat atkr%'| Attack radius |
| `%rounds stat ammo%' | Cartridges |
| `%rounds stat max ammo%' | Max.
| `%rounds stat bullets%' | Bullets |
| `%rounds stat bullet speed%' | Bullet speed |
| `%rounds stat bounce%' | Rebound |
| `%rounds stat homing%' | Self-homing |
| `%rounds stat big bullet%` | Big Bullet |
| `%rounds stat cold%' | Frost |
| `%rounds stat cold lvl%' | Ur frost |
| `%rounds stat poison%' | Poison |
| `%rounds stat poison lvl%' | Ur. poison |
| `%rounds stat parazit%' | Suction |
| `%rounds stat parazit lvl%' | Ur. suction |
| `%rounds stat leech%' | Suction |
| `%rounds stat truster%' | Truster |
| `%rounds stat empower%' | Strengthening |
| `%rounds stat empower charge%'| Gain charge |
| `%rounds stat dark strength%' | Force of Darkness |
| `%rounds stat dark%' | Darkness |
| `%rounds stat grow%' | Growth
| `%rounds stat bomb bullet%' | Bombs |
| `%rounds stat bomb on block%' | Bombs on the block |
| `%rounds stat shield active%' | Shield active (1/0) |
| `%rounds stat shield hp%' | Health of the shield |
| `%rounds stat shield cd%' | Couldown shield |
| `%rounds stat speed%' | Speed |
| `%rounds stat stun%' | Stun
| `%rounds stat saw%' | Saw
| `%rounds stat silence%' | Silence |
| `%rounds stat emp%' | EMF |
| `%rounds stat sneaky%' | Secrecy |
| `%rounds stat phoenix%' | Phoenix |
| `%rounds stat abyssal%' | Abyssal |

-

## Integration with TAB

The ‘TAB/’ folder contains a ready-made configuration file for the TAB plugin. He shows:

Sidebar team count (only during the game)
Displaying the round and progress
Indication of the player's team with color
Setting up tablist, nametag and header/footer

-

## Preserving the condition

The plugin automatically saves:

**State of the game** (game-state.yml) - round number, team wins, players killed
- **Player data** - Stats, maps, team (via PersistentDataContainer + `active-players.yml`)
**Card blocks** (rounds-map-blocks.yml`) – Lobby, map and spa positions

When restarting the server during an active game, the state is automatically restored.

-

## Localization

Language packets are stored in the folder 'plugins/RoundsPlugin/lang/' - ** one file 'Name>.txt' = one language** (for example, 'RU ru.txt', 'EN en.txt'). Inside is a YAML-like interface string format; the file name without '.txt' is the language id.

Language change:

1. Command `/rdebug setlanguage <language>> - tab-compilation shows all found languages
2. Or in "config.yml" specify "language: RU ru" and restart the server.

Adding your own language:

1. Copy 'lang/RU ru.txt', rename 'Name' and translate the lines
2. Put the file in 'plugins/RoundsPlugin/lang/'
3. Perform /rdebug reload or restart the server - the new language will appear in the tablet /rdebug setlanguage

The plugin scans the folder at each run and /rdebug reload, so third-party language packs are picked up automatically. Default 'RU ru.txt' / 'EN en.txt' recovers from jar if removed. If the current language file is not available, the plugin rolls back to EN en.

Maps support localization in the format `name.ru` / `name.en` and `description.ru` / `description.en`. The language code for cards is taken from the part id to  (`DE de` → `de`); if there is no such section in the card, the English text is used.

## Rights

| Right | Description |
|------|----------- |
| `rounds.admin' | All game management and debugging teams |
| `rounds.join' | Ability to join teams through blocks (all by default) |

## Files

| File | Description |
|------|---------- |
| `plugins/RoundsPlugin/config.yml` | Main configuration |
| `plugins/RoundsPlugin/lang/' | Language packs (`*.txt`; one file = one language, third-party pick-ups automatically) |
| `plugins/RoundsPlugin/cards/original/' | Standard maps (65 files) |
| `plugins/RoundsPlugin/cards/custom/' | User maps |
| `plugins/RoundsPlugin/playerdata/' | Player data (automatically) |
| `plugins/RoundsPlugin/game-state.yml' | Current state of play |
| `plugins/RoundsPlugin/active-players.yml' | Active players in session |
| `< world >/rounds-blocks.yml` | Team Joining Units |
| `< world >/rounds-map-blocks.yml` | Lobby blocks, maps and spawns |

##Assembly

Requirements: Java 17, Gradle 8. 9+

*
. gradlew clean build
``

Output: 'build/libs/RoundsPlugin-1.0.jar'
