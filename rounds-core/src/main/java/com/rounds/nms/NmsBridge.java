package com.rounds.nms;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Version-stable bridge over all NMS-dependent plugin features (fake players, packet glow).
 * Every Minecraft version is served by its own rounds-nms* module providing ONE
 * implementation under the fixed class name {@link NmsBridgeLoader#IMPL_CLASS_NAME}.
 * The core must never import net.minecraft.* / org.bukkit.craftbukkit.* directly.
 */
public interface NmsBridge {

    /**
     * Creates a fake player, registers it in the server PlayerList with a fake connection.
     *
     * @param world   world to spawn in
     * @param botName valid bot name (1-16 chars, [a-zA-Z0-9_])
     * @param botUuid random unique id
     * @param loc     initial spawn location (may be set after teleport too)
     * @return handle or null on NMS failure
     */
    NmsPlayerHandle createFakePlayer(World world, String botName, UUID botUuid, Location loc);

    /**
     * Fresh handle for the given player; must re-resolve from the server PlayerList
     * so respawned/recreated entities are picked up automatically.
     */
    NmsPlayerHandle getHandle(Player player);

    /**
     * Repairs a missing a fake connection on the given player's entity (no-op for real clients).
     */
    void ensureConnection(Player player);

    /**
     * Neutralizes an NMS entity: ride cleanup, container close, discard,
     * removal from entity slices, PlayerList and connection.
     */
    void despawnFakePlayer(NmsPlayerHandle handle);

    /**
     * Sends entity metadata flags (glow, fire, sneaking, ...) of target to spectator.
     * Used for the team-colored spectator glow and must be cheap.
     */
    void sendEntityFlags(Player spectator, Player target, byte flags);

    /**
     * Applies the card texture to the given carrier item and returns the modified item.
     * The default uses the legacy integer custom_model_data (Minecraft up to 1.21.1);
     * versions 1.21.2+ override this with the structured strings-based component.
     */
    default ItemStack applyCardTexture(ItemStack item, int familyId) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(4000 + familyId);
            item.setItemMeta(meta);
        }
        return item;
    }
}
