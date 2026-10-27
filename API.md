# Документация API плагина Rounds

## Обзор
API Rounds предоставляет контролируемый интерфейс для разработчиков, позволяющий расширять функциональность плагина Rounds, включая создание пользовательских карт, управление данными и обработку игровых событий.

Точкой входа для всех взаимодействий с API является класс `com.rounds.api.RoundsAPI`.

## Начало работы

### Проверка доступности
Перед вызовом методов API убедитесь, что сервис Rounds доступен:

```java
if (RoundsAPI.isAvailable()) {
    // API готово к использованию
}
```

## Основные возможности

### 1. Управление картами
Используйте `RoundsAPI` для регистрации пользовательских карт. Карты должны реализовывать интерфейс `CustomCard`.

- **Регистрация:** `RoundsAPI.registerCard(CustomCard card)`
- **Удаление:** `RoundsAPI.unregisterCard(Plugin plugin, NamespacedKey key)`
- **Получение:** `RoundsAPI.getCard(NamespacedKey key)`

### 2. Данные игроков
Данные сессии управляются через `CustomDataContainer`.

- **Доступ:** `RoundsAPI.getCustomData(Player player)` или `RoundsAPI.getCustomData(UUID playerUuid)`
- **Примечание:** Данные автоматически очищаются при отключении игрока.

### 3. Состояние игры
- **Текущий раунд:** `RoundsAPI.getCurrentRound()`
- **Статус матча:** `RoundsAPI.isMatchRunning()`

## События
API предоставляет несколько событий Bukkit для отслеживания игрового процесса:

- `RoundsBulletHitEvent`: Вызывается при попадании пули в сущность или блок.
- `RoundsCardDraftEvent`: Вызывается во время фазы драфта карт.
- `RoundsCardSelectEvent`: Вызывается при выборе игроком карты.
- `RoundsRoundStartEvent`: Вызывается в начале раунда.
- `RoundsRoundEndEvent`: Вызывается в конце раунда.
- `RoundsShieldBlockEvent`: Вызывается при блокировке урона щитом.
- `RoundsWeaponShootEvent`: Вызывается при выстреле из оружия.

## Пример использования

```java
public class MyRoundsAddon extends JavaPlugin {
    @Override
    public void onEnable() {
        if (RoundsAPI.isAvailable()) {
            CustomCard myCard = new MyCustomCard();
            RoundsAPI.registerCard(myCard);
            getLogger().info("Зарегистрирована пользовательская карта: " + myCard.getKey());
        }
    }
}
```
