# Руководство для контрибьюторов Rounds-MinecraftPlugin

Благодарим за интерес к развитию открытого плагина Rounds. Этот документ поможет быстро настроить рабочее окружение, создать свою первую карту или внести вклад в код проекта.


## 1. Быстрый старт и сборка

Проект использует Gradle с мультиязычной поддержкой и мультимодульной структурой NMS.

### Требования к окружению
- **JDK 25** (для сборки с последними NMS маппингами).
- Весь исходный код компилируется строго в байткод **Java 17** (`release = 17`).

### Основные команды Gradle

- **Быстрая сборка для локального тестирования (Quick Build):**
  Собирает только ядро `rounds-core` и целевой NMS-модуль (по умолчанию Purpur 26.2):
  ```bash
  ./gradlew quickBuild
  ```
  Для сборки под другую версию сервера используйте флаг `-PtargetNms`:
  ```bash
  ./gradlew quickBuild -PtargetNms=1.20.4
  ```

- **Запуск юнит-тестов:**
  ```bash
  ./gradlew :rounds-api:test :rounds-core:test
  ```

- **Сборка кроссплатформенных ресурспаков:**
  Генерирует `Rounds-CardPack-RU.zip`, `Rounds-CardPack-EN.zip` и `Rounds-CardPack.zip` в папке `build/distributions/cardpacks/`:
  ```bash
  ./gradlew buildCardPack
  ```
  Для синхронизации на рабочий стол и в клиенты ATLauncher добавьте флаги:
  ```bash
  ./gradlew buildCardPack -PdeployDesktop -PdeployInstances
  ```


## 2. Создание новой карточки за 2 минуты (Data-Driven Cards)

Для добавления новой карты не требуется писать Java-код. Достаточно создать YAML-файл.

### Шаг 1. Создайте YAML-файл
Создайте файл в папке `rounds-core/src/main/resources/cards/custom/my-card.yml`.

### Шаг 2. Заполните конфигурацию с автодополнением
Укажите в первой строке директиву схемы, чтобы ваша IDE (IntelliJ IDEA, VS Code) автоматически подсказывала доступные ключи:

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

# Базовые модификаторы характеристик
effects:
  bullets: 1
  ammo: 2

# Декларативные боевые триггеры (выполняются без тикового оверхеда)
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

### Доступные триггеры
- `ON_SHOOT` — выстрел из оружия.
- `ON_BULLET_HIT` — попадание пули по живому противнику.
- `ON_DAMAGE_TAKEN` — получение входящего урона.
- `ON_SHIELD_BLOCK` — успешное блокирование щитом.
- `ON_RELOAD` — завершение перезарядки оружия.

### Доступные действия
- `RESTORE_AMMO` — восполнить патроны (параметр `value`).
- `HEAL` — восстановить здоровье стрелку (параметр `value`).
- `DAMAGE_VICTIM` — нанести урон цели (параметр `value`).
- `PUSH_SHOOTER` — придать импульс отдачи стрелку (параметр `value`).
- `PUSH_VICTIM` — оттолкнуть цель (параметр `value`).
- `PLAY_SOUND` — воспроизвести звук (`param`: имя звука, `value`: питч).
- `SPAWN_PARTICLE` — создать частицы (`param`: тип частицы, `value`: количество).
- `APPLY_POTION` — наложить эффект зелья на цель (`param`: тип эффекта, `value`: секунды).

### Шаг 3. Запекание описания на текстуру (опционально)
Запустите скрипт генерации текстур:
```bash
python tools/generate_card_textures.py
```
Скрипт автоматически отрендерит название и описание карты на PNG-текстуры в папках `ru` и `en`.


## 3. Стандарты кода и правила пулл-реквестов

1. **Версионная совместимость:** Байткод всех компилируемых классов обязан быть Java 17.
2. **Изоляция NMS:** Прямой импорт `net.minecraft.*` и `org.bukkit.craftbukkit.*` в модуле `rounds-core` запрещен. Все низкоуровневые взаимодействия выносятся за интерфейс `NmsBridge`.
3. **Строгий запрет эмодзи в документации:** В файлах `readme.md`, `AGENTS.md`, `CONTRIBUTING.md` и любых других markdown-документах репозитория строго запрещено использование эмодзи и символов-смайлов.
4. **Прохождение тестов:** Перед отправкой пулл-реквеста убедитесь, что все тесты проходят:
   ```bash
   ./gradlew :rounds-api:test :rounds-core:test
   ```
   В проекте настроен автоматический GitHub Actions CI, который проверяет компиляцию, тесты и сборку ресурспака при каждом PR.
