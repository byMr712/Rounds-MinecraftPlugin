# Документация API плагина Rounds

## Содержание

- [Обзор](#обзор)
- [Версия API](#версия-api)
- [Подключение](#подключение)
- [Точка входа](#точка-входа)
- [Управление картами](#управление-картами)
- [Данные игроков](#данные-игроков)
- [Состояние игры](#состояние-игры)
- [Синхронизация с главным потоком](#синхронизация-с-главным-потоком)
- [События](#события)
- [API для ботов](#api-для-ботов)
- [API для пуль](#api-для-пуль)
- [Примеры использования](#примеры-использования)
- [Важные замечания](#важные-замечания)

## Обзор

API Rounds предоставляет контролируемый интерфейс для расширения функциональности плагина. Основной контракт определён в модуле `rounds-api`. Реализация предоставляется модулем `rounds-core` через `Bukkit ServicesManager`.

Точкой входа является класс `com.rounds.api.RoundsAPI`. Фактический провайдер доступен через `RoundsService`.

## Версия API

API использует класс `com.rounds.api.ApiVersion`.

- Получение: `RoundsAPI.getVersion()` (текущая версия: **1.1.0**)
- `ApiVersion` содержит информацию о версии API для быстрой проверки совместимости аддона с текущей версией плагина. Версия 1.1.0 добавляет поддержку кастомных шрифтовых глифов карт (`glyph`), получение текущей колоды игрока (`getPlayerCards`) и инспекцию масштаба игрока (`getPlayerScale`).

Рекомендуется перед использованием критичного функционала сверять версию API и учитывать возможные изменения между релизами.

## Подключение

`rounds-api` вынесен в отдельный модуль. Для разработки аддона его необходимо подключить как `compileOnly`.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    // При публикации репозитория Rounds - добавить соответствующий maven
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
depend: [RoundsPlugin]        # Если аддон не имеет смысла без RoundsPlugin
# или
softdepend: [RoundsPlugin]   # Если аддон опционально интегрируется с RoundsPlugin
```

## Точка входа

### RoundsAPI

Класс `com.rounds.api.RoundsAPI` является статическим фасадом над `RoundsService`. Все методы проксируют вызов в зарегистрированный провайдер из `Bukkit.getServicesManager()`.

```java
public final class RoundsAPI {
    public static RoundsService getService();
    public static boolean isAvailable();
    public static ApiVersion getVersion();

    // Карты
    public static void registerCard(CustomCard card);
    public static void unregisterCard(Plugin plugin, NamespacedKey key);
    public static void unregisterAllCards(Plugin plugin);
    public static Optional<CustomCard> getCard(NamespacedKey key);
    public static List<CustomCard> getRegisteredCards();

    // Данные игроков
    public static Optional<CustomDataContainer> getCustomData(Player player);
    public static Optional<CustomDataContainer> getCustomData(UUID playerUuid);

    // Состояние игры
    public static int getCurrentRound();
    public static boolean isMatchRunning();

    // Синхронизация
    public static boolean runSync(Runnable task);
}
```

### RoundsService

Интерфейс `com.rounds.api.RoundsService` определяет полный контракт провайдера.

```java
public interface RoundsService {
    ApiVersion getVersion();

    // Управление картами
    void registerCard(CustomCard card);
    void unregisterCard(Plugin plugin, NamespacedKey key);
    void unregisterAllCards(Plugin plugin);
    Optional<CustomCard> getCard(NamespacedKey key);
    List<CustomCard> getRegisteredCards();

    List<CustomCard> getPlayerCards(Player player);

    // Данные и параметры игроков
    Optional<CustomDataContainer> getCustomData(Player player);
    Optional<CustomDataContainer> getCustomData(UUID playerUuid);
    double getPlayerScale(Player player);

    // Состояние игры
    int getCurrentRound();
    boolean isMatchRunning();

    // Синхронизация
    boolean runSync(Runnable task);
}
```

#### Проверка доступности

```java
if (!RoundsAPI.isAvailable()) {
    getLogger().warning("Rounds API недоступен. Интеграция отключена.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

## Управление картами

### CustomCard

Кастомные карты должны реализовывать интерфейс `com.rounds.api.card.CustomCard`.

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

| Метод | Описание |
| --- | --- |
| `getKey()` | Уникальный `NamespacedKey` карты. Используется как основной идентификатор в реестре. |
| `getId()` | Строковый идентификатор карты в рамках аддона/реестра. |
| `getDisplayName()` | Отображаемое название в GUI драфта. Рекомендуется использовать `Component`. |
| `getDescription()` | Описание карты. Список компонентов используется для построчного вывода в тултипе. |
| `getIcon()` | Предмет-иконка, отображаемая в меню выбора карт. |
| `getRarity()` | Редкость карты. Влияет на приоритизацию при автодрафте ботов и на визуальное оформление. |
| `getRole()` | Роль карты. По умолчанию `NEUTRAL`. Может использоваться логикой плагина для категоризации. |
| `apply(CardContext context)` | Точка активации эффекта карты. Вызывается при применении карты к целевому игроку в контексте текущего раунда. |

### CardRarity

`com.rounds.api.card.CardRarity` определяет редкость карты:

| Значение | Назначение |
| --- | --- |
| `COMMON` | Обычная |
| `UNCOMMON` | Необычная |
| `RARE` | Редкая |
| `EPIC` | Эпическая |
| `LEGENDARY` | Легендарная |

### CardRole

`com.rounds.api.card.CardRole` определяет роль карты:

| Значение | Назначение |
| --- | --- |
| `OFFENSIVE` | Атакующая |
| `DEFENSIVE` | Защитная |
| `UTILITY` | Вспомогательная |
| `MOBILITY` | Мобильность |
| `SUPPORT` | Поддержка |
| `NEUTRAL` | Универсальная/без явной роли |

### CardContext

`com.rounds.api.card.CardContext` передаётся в `apply(CardContext context)` и предоставляет доступ к контексту применения карты.

Ключевые возможности (на основе контракта API):

- Доступ к игроку, на которого применяется карта (`getPlayer()`)
- Доступ к плагину/источнику применения
- Доступ к игровому контексту раунда при необходимости
- Интеграция с механиками плагина через реализацию `CardContextImpl` в ядре

Рекомендуется полагаться только на публичный API `CardContext`, не обращаясь к внутренним классам реализации.

### CardBuilder

`com.rounds.api.card.CardBuilder` предназначен для упрощённого создания `CustomCard` без ручной реализации всех методов. Позволяет декларативно задать ключ, название, описание, иконку, редкость, роль, растровый глиф шрифта ресурспака (`.glyph(char)`) и логику `apply`.

#### Поддержка шрифтовых глифов и ресурспака

Каждая карта может ассоциироваться с глифом растрового шрифта ресурспака `rounds:default` (встроенные карты используют диапазон `\uE000` + familyId, например `\uE001` .. `\uE07F`). При наличии глифа карта отображается в 3D сцене выбора карт в мире (Text Display billboard) своей уникальной текстурой.

```java
CustomCard card = CardBuilder.create(plugin, "thunder_strike")
    .name("ru", "Удар грома")
    .name("en", "Thunder Strike")
    .description("ru", "Вызывает молнию при попадании")
    .rarity(CardRarity.RARE)
    .material(Material.NETHER_STAR)
    .role(CardRole.OFFENSIVE)
    .glyph('\uE025') // Растровый глиф в ресурспаке rounds:default
    .build();
```

### Регистрация и удаление карт

```java
// Регистрация
CustomCard card = new MyCustomCard(new NamespacedKey(this, "my_card"));
RoundsAPI.registerCard(card);

// Получение
Optional<CustomCard> opt = RoundsAPI.getCard(new NamespacedKey(this, "my_card"));

// Удаление конкретной карты
RoundsAPI.unregisterCard(this, new NamespacedKey(this, "my_card"));

// Удаление всех карт аддона
RoundsAPI.unregisterAllCards(this);

// Список всех зарегистрированных карт
List<CustomCard> all = RoundsAPI.getRegisteredCards();
```

#### Правила работы с реестром

- `registerCard(CustomCard card)` добавляет карту в общий пул. При повторной регистрации ранее удалённой карты она перемещается в конец списка.
- Карта привязывается к своему `ownerPlugin`. Удаление чужих карт запрещено.
- Если плагин-владелец отключён, попытка его карт остаться в активном пуле блокируется логикой провайдера.
- `unregisterCard(Plugin plugin, NamespacedKey key)` бросает `SecurityException`, если вызывающий плагин не является владельцем карты.
- `unregisterAllCards(Plugin plugin)` снимает с регистрации все карты, принадлежащие указанному плагину.
- `getRegisteredCards()` возвращает неизменяемый снимок списка. Порядок соответствует порядку последней регистрации.
- `getCard(NamespacedKey key)` возвращает `Optional.empty()`, если карта не зарегистрирована.

## Данные игроков

Сессионные данные игроков предоставляются через `com.rounds.api.data.CustomDataContainer`.

### Получение контейнера

```java
Optional<CustomDataContainer> data = RoundsAPI.getCustomData(player);
// или
Optional<CustomDataContainer> data = RoundsAPI.getCustomData(player.getUniqueId());
```

### CustomDataContainer

Контейнер хранит произвольные данные, привязанные к сессии игрока. Данные предназначены исключительно для текущего матча/сессии взаимодействия с плагином.

Общие принципы работы:

- Доступ только к данным текущей активной сессии.
- Данные автоматически очищаются при отключении игрока. Как только сессия закрывается (синхронно с `PlayerQuitEvent`), `getCustomData(UUID)` немедленно возвращает `Optional.empty()`.
- Контейнер не предназначен для долгосрочного хранения между запусками сервера.
- Рекомендуется использовать уникальные ключи в рамках своего аддона, чтобы избежать коллизий.

Типичный паттерн использования:

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

Точный набор методов определяется контрактом `CustomDataContainer` в `rounds-api`. При работе ориентируйтесь на публичные методы этого интерфейса.

## Состояние игры

```java
int round = RoundsAPI.getCurrentRound(); // 0 - лобби, >=1 - активный раунд, -1 - сброс/ошибка
boolean running = RoundsAPI.isMatchRunning();
```

Эти значения отражают текущее состояние матчмейкинга/раунда в Rounds и полезны для условной логики аддона.

## Синхронизация с главным потоком

Часть игрового состояния Bukkit/Paper должна изменяться строго в главном потоке сервера.

```java
RoundsAPI.runSync(() -> {
    // Действия, требующие синхронного контекста сервера
});
```

- `runSync(Runnable task)` выполняет задачу в основном потоке. Если ядро уже выполняется в главном потоке, реализация может выполнить задачу немедленно.
- Возвращает `true`, если задача выполнена немедленно или поставлена в очередь. `false`, если ядро Rounds выключено или сервер находится в процессе остановки.
- `task` не может быть `null` - при нарушении выбрасывается `NullPointerException`.

Используйте `runSync` при любом взаимодействии с сущностями, блоками, инвентарями, скорбордами или иным состоянием, критичным к потоку Bukkit.

## События

Все события находятся в пакете `com.rounds.api.event` и наследуются от `org.bukkit.event.Event`. Часть из них реализует `org.bukkit.event.Cancellable`.

Для прослушивания используйте стандартный `Listener` Bukkit.

### Список событий

| Событие | Отмена | Описание |
| --- | --- | --- |
| `RoundsWeaponShootEvent` | Да | Вызывается при попытке/фактическом выстреле из оружия в контексте Rounds. Позволяет модифицировать/заблокировать выстрел. |
| `RoundsBulletHitEvent` | Нет | Вызывается при попадании пули, выпущенной механикой Rounds, в сущность или блок. Содержит информацию о пуле, цели/блоке, уроне и контексте попадания. |
| `RoundsShieldBlockEvent` | Нет | Вызывается при успешной блокировке урона щитом. Позволяет отреагировать на блокирование. |
| `RoundsCardDraftEvent` | Нет | Вызывается во время фазы драфта карт. Информирует о начале/контексте фазы, списке доступных карт для игроков. |
| `RoundsCardSelectEvent` | Нет | Вызывается при выборе игроком карты в фазе драфта. Позволяет отследить выбор. |
| `RoundsRoundStartEvent` | Нет | Вызывается в начале раунда. Полезен для инициализации логики аддона под новый раунд. |
| `RoundsRoundEndEvent` | Нет | Вызывается в конце раунда. Полезен для подведения итогов/очистки временного состояния. |

### Пример прослушивания

```java
public class RoundsListener implements Listener {
    @EventHandler
    public void onShoot(RoundsWeaponShootEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        // Кастомная логика
    }

    @EventHandler
    public void onBulletHit(RoundsBulletHitEvent e) {
        // Обработка попадания
    }
}
```

Регистрируйте слушатель в `onEnable()` вашего аддона через `PluginManager.registerEvents`.

## API для ботов

В `rounds-api` предусмотрен минимальный интерфейс для взаимодействия с ботами Rounds.

### BotView

`com.rounds.api.bot.BotView` представляет представление бота для внешних интеграций. Позволяет получать ограниченную информацию о боте без обращения к внутренним NMS-классам.

### BotArchetype

`com.rounds.api.bot.BotArchetype` описывает архетип поведения бота, используемый системой ИИ Rounds. Может использоваться для анализа/интеграции, не вмешиваясь напрямую в логику принятия решений.

Доступ к данным ботов через публичный API предоставляется в рамках существующего контракта. При отсутствии прямых геттеров в `RoundsAPI` ориентируйтесь на события и передаваемый контекст.

## API для пуль

### RoundsBullet

`com.rounds.api.entity.RoundsBullet` представляет пулю, созданную боевой системой Rounds. Информация о пуле доступна в соответствующих событиях, в первую очередь в `RoundsBulletHitEvent`.

Используйте `RoundsBulletHitEvent` для получения деталей о снаряде, а не для ручного создания/управления пулями.

## Примеры использования

### Минимальный аддон с кастомной картой

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
            getLogger().warning("Rounds не найден, плагин отключается");
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
                return Component.text("Пример карты");
            }

            @Override
            public List<Component> getDescription() {
                return List.of(
                    Component.text("Пример описания кастомной карты"),
                    Component.text("Работает через Rounds API")
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
                // Логика применения карты
            }
        };

        RoundsAPI.registerCard(card);
        getLogger().info("Кастомная карта зарегистрирована");
    }

    @Override
    public void onDisable() {
        if (RoundsAPI.isAvailable()) {
            RoundsAPI.unregisterAllCards(this);
        }
    }
}
```

### Работа с данными игрока и синхронизацией

```java
@EventHandler
public void onRoundStart(RoundsRoundStartEvent event) {
    // Пример: инициализация сессионных данных
    // Получение данных через RoundsAPI.getCustomData
    // Для действий с Bukkit API использовать runSync при необходимости
    RoundsAPI.runSync(() -> {
        // Синхронные действия в главном потоке
    });
}
```

## Важные замечания

- **Только главный поток.** Изменения мира, сущностей, инвентарей выполняйте в главном потоке. При вызове из асинхронного контекста используйте `RoundsAPI.runSync()`.
- **Сессионность данных.** `CustomDataContainer` действителен только пока игрок онлайн. При отключении данные удаляются. Не рассчитывайте на сохранение между сессиями.
- **Владение картами.** Только плагин-владелец может снимать свою карту с регистрации. Нарушение этого правила вызывает `SecurityException`.
- **Неизменяемые снимки.** `getRegisteredCards()` возвращает снимок состояния реестра. Изменения реестра после получения списка на него не влияют.
- **Проверка доступности.** Всегда проверяйте `RoundsAPI.isAvailable()` перед обращением к API.
- **API-контракт.** Ориентируйтесь строго на публичные типы из `rounds-api`. Внутренние классы `rounds-core` не являются частью API и могут изменяться без сохранения обратной совместимости.
- **События.** Часть событий может быть отменяема. Всегда учитывайте `isCancelled()`, если событие реализует `Cancellable`.


## Совместимость версий сервера и мост NmsBridge

Публичный API (`rounds-api`) не зависит от версии сервера: он не содержит ссылок на `net.minecraft.*` и компилируется против стабильного Paper API. Внутренние NMS-операции движка (спавн фейк-игроков, движение и повороты ботов, физика сущностей, пакеты подсветки спектаторов) изолированы за интерфейсом `com.rounds.nms.NmsBridge` и поставляются версионными модулями.

| Артефакт | Сервер |
|---|---|
| `RoundsPlugin-1.20.4-v1.8-byMr712.jar` | Paper / Purpur 1.20.4 |
| `RoundsPlugin-1.20.5-v1.8-byMr712.jar` | Paper / Purpur 1.20.5 |
| `RoundsPlugin-1.20.6-v1.8-byMr712.jar` | Paper / Purpur 1.20.6 |
| `RoundsPlugin-1.21-v1.8-byMr712.jar` | Paper / Purpur 1.21 |
| `RoundsPlugin-1.21.1-v1.8-byMr712.jar` | Paper / Purpur 1.21.1 |
| `RoundsPlugin-1.21.10-v1.8-byMr712.jar` | Paper / Purpur 1.21.10 |
| `RoundsPlugin-1.21.11-v1.8-byMr712.jar` | Paper / Purpur 1.21.11 |
| `RoundsPlugin-1.21.3-v1.8-byMr712.jar` | Paper / Purpur 1.21.3 |
| `RoundsPlugin-1.21.4-v1.8-byMr712.jar` | Paper / Purpur 1.21.4 |
| `RoundsPlugin-1.21.5-v1.8-byMr712.jar` | Paper / Purpur 1.21.5 |
| `RoundsPlugin-1.21.6-v1.8-byMr712.jar` | Paper / Purpur 1.21.6 |
| `RoundsPlugin-1.21.7-v1.8-byMr712.jar` | Paper / Purpur 1.21.7 |
| `RoundsPlugin-1.21.8-v1.8-byMr712.jar` | Paper / Purpur 1.21.8 |
| `RoundsPlugin-1.21.9-v1.8-byMr712.jar` | Paper / Purpur 1.21.9 |
| `RoundsPlugin-26.1.1-v1.8-byMr712.jar` | Paper / Purpur 26.1.1 |
| `RoundsPlugin-26.1.2-v1.8-byMr712.jar` | Paper / Purpur 26.1.2 |
| `RoundsPlugin-26.2-v1.8-byMr712.jar` | Paper / Purpur 26.2 |
| `RoundsPlugin-26.3-v1.8-byMr712.jar` | Paper / Purpur 26.3 |

- На Paper и Purpur одной версии Minecraft ставится один и тот же jar (Purpur - форк Paper, слой NMS совпадает).
- Версия 1.21.2 недоступна у PaperMC upstream (Fill API и maven-репозитории не отдают артефакты), поэтому jar для 1.21.2 не собирается.
- Запуск jar не той версии деградирует мягко: боты, спавн фейк-игроков и spectator glow отключаются с предупреждением в логе, остальной функционал (карты, команды, скорборд) продолжает работать.
- Плагины, использующие `rounds-api`, работают на любой версии: контракт API не зависит от NMS-адаптера.
