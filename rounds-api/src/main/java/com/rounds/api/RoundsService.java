package com.rounds.api;

import com.rounds.api.card.CustomCard;
import com.rounds.api.data.CustomDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoundsService {
    ApiVersion getVersion();

    /**
     * Регистрирует кастомную карту в общем пуле.
     * Порядок добавления фиксируется в детерминированном реестре.
     * При повторной регистрации ранее удаленной карты она перемещается в конец списка.
     *
     * @throws IllegalStateException если карта с таким NamespacedKey уже зарегистрирована,
     *                               либо если плагин-владелец карты выключен.
     * @throws NullPointerException  если card == null.
     */
    void registerCard(CustomCard card);

    /**
     * Снимает карту с регистрации.
     *
     * @param plugin плагин, запрашивающий удаление.
     * @param key    ключ удаляемой карты.
     * @throws SecurityException    если вызывающий плагин не является владельцем (ownerPlugin) карты.
     * @throws NullPointerException если plugin == null или key == null.
     */
    void unregisterCard(Plugin plugin, NamespacedKey key);

    /**
     * Снимает с регистрации все карты, принадлежащие указанному плагину.
     *
     * @throws SecurityException    если вызывающий плагин пытается снять карты чужого аддона.
     * @throws NullPointerException если plugin == null.
     */
    void unregisterAllCards(Plugin plugin);

    Optional<CustomCard> getCard(NamespacedKey key);

    /**
     * Возвращает неизменяемый снимок (immutable snapshot) всех зарегистрированных карт.
     * Порядок элементов стабилен и соответствует порядку последней регистрации карт.
     */
    List<CustomCard> getRegisteredCards();

    // Сессионные данные игроков
    Optional<CustomDataContainer> getCustomData(Player player);

    /**
     * Возвращает контейнер данных игрока по UUID.
     * Сессия считается активной, пока игрок онлайн; при дисконнекте (синхронно с PlayerQuitEvent)
     * сессия закрывается и метод немедленно возвращает Optional.empty().
     */
    Optional<CustomDataContainer> getCustomData(UUID playerUuid);

    /**
     * Возвращает неизменяемый список карт, которыми владеет игрок в текущем матче.
     *
     * @param player игрок
     * @return список карт или пустой список, если игрок не в матче / не имеет карт.
     */
    List<CustomCard> getPlayerCards(Player player);

    /**
     * Возвращает текущий множитель размера модели игрока (нативный атрибут SCALE на 1.20.5+).
     *
     * @param player игрок
     * @return масштаб (1.0 по умолчанию).
     */
    double getPlayerScale(Player player);

    // Состояние игры
    int getCurrentRound(); // 0 - лобби, N >= 1 - активный раунд, -1 - сброс/ошибка
    boolean isMatchRunning();

    /**
     * Выполняет задачу в основном потоке сервера.
     *
     * @param task исполняемая задача.
     * @return true, если задача выполнена (немедленно) или поставлена в очередь;
     *         false, если ядро Rounds выключено или сервер останавливается.
     * @throws NullPointerException если task == null.
     */
    boolean runSync(Runnable task);
}
