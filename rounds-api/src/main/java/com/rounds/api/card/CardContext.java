package com.rounds.api.card;

import com.rounds.api.data.CustomDataContainer;
import org.bukkit.entity.Player;
import java.util.Collection;

public interface CardContext {
    /**
     * Номер активного раунда (1..N). Вне активного матча возвращает 0.
     */
    int getRoundNumber();

    boolean isMatchRunning();
    Player getPlayer();

    /**
     * Союзники игрока. Вне активного матча возвращает пустую коллекцию (не null).
     */
    Collection<Player> getTeammates();

    /**
     * Противники игрока. Вне активного матча возвращает пустую коллекцию (не null).
     */
    Collection<Player> getEnemies();

    CustomDataContainer getData();
}
