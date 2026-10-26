package com.rounds.api.impl;

import com.rounds.RoundsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public class PluginDisableListener implements Listener {

    private final RoundsPlugin plugin;

    public PluginDisableListener(RoundsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabledPlugin = event.getPlugin();
        if (disabledPlugin == plugin) return;
        if (plugin.getRoundsService() != null) {
            try {
                plugin.getRoundsService().unregisterAllCards(disabledPlugin);
            } catch (Exception e) {
                plugin.getLogger().warning("Error unregistering cards for plugin " + disabledPlugin.getName() + ": " + e.getMessage());
            }
        }
    }
}
