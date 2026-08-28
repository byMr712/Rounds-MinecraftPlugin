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
    private Method mHideNametagToViewer;
    private Method mShowNametagToViewer;

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

            try {
                mSetName = tlfClass.getMethod("setName", tabPlayerClass, String.class);
                mSetName.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mSetPrefix = ntmClass.getMethod("setPrefix", tabPlayerClass, String.class);
                mSetPrefix.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mSetSuffix = ntmClass.getMethod("setSuffix", tabPlayerClass, String.class);
                mSetSuffix.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mHideNametag = ntmClass.getMethod("hideNameTag", tabPlayerClass);
                mHideNametag.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mShowNametag = ntmClass.getMethod("showNameTag", tabPlayerClass);
                mShowNametag.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mHideNametagToViewer = ntmClass.getMethod("hideNameTag", tabPlayerClass, tabPlayerClass);
                mHideNametagToViewer.setAccessible(true);
            } catch (Exception ignored) {}

            try {
                mShowNametagToViewer = ntmClass.getMethod("showNameTag", tabPlayerClass, tabPlayerClass);
                mShowNametagToViewer.setAccessible(true);
            } catch (Exception ignored) {}

            for (Method m : new Method[]{mGetTabListFormatManager, mGetNameTagManager, mGetPlayer}) {
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
            if (tlf != null && mSetName != null) mSetName.invoke(tlf, tp, color + player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                if (mSetPrefix != null) mSetPrefix.invoke(ntm, tp, color.toString());
                if (mSetSuffix != null) mSetSuffix.invoke(ntm, tp, suffix == null ? null : suffix);
            }
        } catch (Exception ignored) {}
    }

    public void resetName(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object tlf = mGetTabListFormatManager.invoke(api);
            if (tlf != null && mSetName != null) mSetName.invoke(tlf, tp, player.getName());
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null) {
                if (mSetPrefix != null) mSetPrefix.invoke(ntm, tp, null);
                if (mSetSuffix != null) mSetSuffix.invoke(ntm, tp, null);
            }
        } catch (Exception ignored) {}
    }

    public void hideNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mHideNametag != null) mHideNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void showNametag(Player player) {
        if (!active) return;
        try {
            Object tp = tabPlayer(player);
            if (tp == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mShowNametag != null) mShowNametag.invoke(ntm, tp);
        } catch (Exception ignored) {}
    }

    public void hideNametagFrom(Player target, Player viewer) {
        if (!active) return;
        try {
            Object tpTarget = tabPlayer(target);
            Object tpViewer = tabPlayer(viewer);
            if (tpTarget == null || tpViewer == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mHideNametagToViewer != null) {
                mHideNametagToViewer.invoke(ntm, tpTarget, tpViewer);
            }
        } catch (Exception ignored) {}
    }

    public void showNametagTo(Player target, Player viewer) {
        if (!active) return;
        try {
            Object tpTarget = tabPlayer(target);
            Object tpViewer = tabPlayer(viewer);
            if (tpTarget == null || tpViewer == null) return;
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm != null && mShowNametagToViewer != null) {
                mShowNametagToViewer.invoke(ntm, tpTarget, tpViewer);
            }
        } catch (Exception ignored) {}
    }

    public void resetAllNametagVisibilities() {
        if (!active) return;
        try {
            Object ntm = mGetNameTagManager.invoke(api);
            if (ntm == null) return;
            for (Player target : Bukkit.getOnlinePlayers()) {
                Object tpTarget = tabPlayer(target);
                if (tpTarget == null) continue;
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (target.equals(viewer)) continue;
                    Object tpViewer = tabPlayer(viewer);
                    if (tpViewer == null) continue;
                    if (mShowNametagToViewer != null) {
                        mShowNametagToViewer.invoke(ntm, tpTarget, tpViewer);
                    }
                }
                if (mShowNametag != null) {
                    mShowNametag.invoke(ntm, tpTarget);
                }
            }
        } catch (Exception ignored) {}
    }
}
