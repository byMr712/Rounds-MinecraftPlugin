package com.rounds.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class TabCompat {

    private boolean active = false;
    private Object api;
    private Method mGetTabListFormatManager;
    private Method mGetNameTagManager;
    private Method mGetPlayer;
    private Method mSetName;
    private Method mSetPrefix;
    private Method mSetSuffix;
    private Method mHideNametag;
    private Method mShowNametag;

    public TabCompat() {
        try {
            if (Bukkit.getPluginManager().getPlugin("TAB") == null) return;
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return;
            mGetTabListFormatManager = apiClass.getMethod("getTabListFormatManager");
            mGetNameTagManager = apiClass.getMethod("getNameTagManager");
            mGetPlayer = apiClass.getMethod("getPlayer", UUID.class);
            Class<?> tabPlayerClass = mGetPlayer.getReturnType();
            Class<?> tlfClass = Class.forName("me.neznamy.tab.api.tablist.TabListFormatManager");
            Class<?> ntmClass = Class.forName("me.neznamy.tab.api.nametag.NameTagManager");
            Object tlf = mGetTabListFormatManager.invoke(api);
            Object ntm = mGetNameTagManager.invoke(api);
            if (tlf == null && ntm == null) return;
            mSetName = tlfClass.getMethod("setName", tabPlayerClass, String.class);
            mSetPrefix = ntmClass.getMethod("setPrefix", tabPlayerClass, String.class);
            mSetSuffix = ntmClass.getMethod("setSuffix", tabPlayerClass, String.class);
            mHideNametag = ntmClass.getMethod("hideNameTag", tabPlayerClass);
            mShowNametag = ntmClass.getMethod("showNameTag", tabPlayerClass);
            for (Method m : new Method[]{mGetTabListFormatManager, mGetNameTagManager, mGetPlayer,
                    mSetName, mSetPrefix, mSetSuffix, mHideNametag, mShowNametag}) {
                m.setAccessible(true);
            }
            active = true;
        } catch (Exception e) {
            active = false;
            Bukkit.getLogger().warning("[Rounds] TAB detected but integration failed: " + e);
        }
    }

    public boolean isActive() { return active; }

    private Object tabPlayer(Player player) {
        try {
            return mGetPlayer.invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    public void setColoredName(Player player, ChatColor color, String suffix) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null) mSetName.invoke(tlf, tp, color + player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                mSetPrefix.invoke(ntm, tp, color.toString());
                mSetSuffix.invoke(ntm, tp, suffix == null ? null : suffix);
            }
        } catch (Exception ignored) {}
    }

    public void resetName(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null) mSetName.invoke(tlf, tp, player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                mSetPrefix.invoke(ntm, tp, null);
                mSetSuffix.invoke(ntm, tp, null);
            }
        } catch (Exception ignored) {}
    }

    public void hideNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mHideNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void showNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) mShowNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }
}
