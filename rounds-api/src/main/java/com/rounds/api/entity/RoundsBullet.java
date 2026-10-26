package com.rounds.api.entity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoundsBullet {
    int MAX_TAGS = 32;
    int MAX_TAG_LENGTH = 64;

    UUID getBulletId();

    /**
     * Возвращает неизменяемый UUID стрелка, создавшего снаряд.
     * Доступен всегда, даже если игрок отключился от сервера во время полета пули.
     */
    UUID getShooterId();

    /**
     * Возвращает сущность стрелка, если он все еще находится на сервере.
     * Если игрок отключился до попадания снаряда, возвращает Optional.empty().
     */
    Optional<Player> getShooter();

    Location getCurrentLocation();
    Vector getVelocity();
    void setVelocity(Vector velocity);

    /**
     * Добавляет тег к пуле.
     * Лимиты: максимум 32 тега на пулю, длина строки до 64 символов.
     * При превышении лимита тегов метод молча игнорирует добавление (без выброса исключения).
     */
    void addTag(String tag);
    boolean hasTag(String tag);
    void removeTag(String tag);

    /**
     * Возвращает неизменяемый снимок (read-only snapshot) текущих тегов в порядке добавления.
     * Попытка модификации сета выбрасывает UnsupportedOperationException.
     */
    Set<String> getTags();

    boolean isBouncing();
    void setBouncing(boolean bouncing);
    int getRemainingBounces();
    void setRemainingBounces(int bounces);
}
