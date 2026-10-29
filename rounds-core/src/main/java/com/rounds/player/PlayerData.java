package com.rounds.player;

import com.rounds.DefaultStats;
import com.rounds.util.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerData {

    public static final double MAX_HEALTH = 1024.0;

    public String playerName = "";
    public double playerUse = 0;

    private final List<Integer> ownedCards = new ArrayList<>();
    private final List<com.rounds.cards.CardTriggerAction> triggerActions = new ArrayList<>();
    public boolean recalculateOnLogin = false;

    public double cardSelect1 = 0;
    public double cardSelect2 = 0;
    public double cardSelect3 = 0;
    public double cardSelect4 = 0;
    public double cardSelect5 = 0;
    public double cardUses = 0;
    public double rareCard = 0;

    public double dmgPlayer = 0;
    public double atksPlayer = 0;
    public double atkrPlayer = 0;

    public double dmg;
    public double atks;
    public double atkSpeed;
    public double atkr;
    public double bouncePl = 0;
    public double ammo;
    public double maxAmmo;
    public double atksReload = 0;
    public double pistolAnim = 0;
    public double bullets;
    public double cold = 0;
    public double poison = 0;
    public double toxicCloud = 0;
    public double leech = 0;
    public double tgBounce = 0;
    public double homing = 0;
    public double homingOnBlock = 0;
    public double poisonLvl = 0;
    public double coldLvl = 0;
    public double parazitLvl = 0;
    public double parazit = 0;
    public double hp;
    public double shieldCooldown = 0;
    public double bombBullet = 0;
    public double bombOnBlock = 0;
    public double bulletSpeed;
    public double empower = 0;
    public double empowerCharge = 0;
    public double darkStrength = 0;
    public double bigBullet = 0;
    public double grow = 0;
    public double trusterLvl = 0;
    public double jumpHeight = 0;
    public double dark = 0;

    public double speed = 0;
    public double speedBoost = 0;
    public double stun = 0;
    public double blockCd = 0;
    public double blockCdMult = 1.0;
    public double reloadSpeed;
    public double heal = 0;
    public double damagePerBounce = 0;
    public double doubleBlock = 0;
    public double shieldsUp = 0;
    public double shieldCharge = 0;
    public double autoReload = 0;
    public double autoReloadDelay = 0;
    public int zeroAmmoTicks = 0;
    public double saw = 0;
    public double shockwave = 0;
    public double silence = 0;
    public double silenceAura = 0;
    public double sneaky = 0;
    public double emp = 0;
    public double overpower = 0;
    public double refresh = 0;
    public double radiance = 0;
    public double highlight = 0;
    public double lifestealAura = 0;
    public double phoenix = 0;
    public double abyssal = 0;
    public int abyssalTicks = 0;
    public double implode = 0;
    public double drill = 0;
    public double splash = 0;
    public double teleport = 0;
    public double tacticalReload = 0;
    public double ammoPerHit = 0;
    public double hpBoostOnHit = 0;
    public double decay = 0;
    public double echo = 0;
    public double pristinePerseverance = 0;
    public double hpCost = 0;
    public double bloodFurry = 0;
    public double executioner = 0;
    public double stormCaller = 0;
    public double evasion = 0;
    public double chameleon = 0;
    public double snowball = 0;
    public int snowballWins = 0;
    public double skyfall = 0;
    public double berserk = 0;
    public double overheat = 0;
    public double secondWind = 0;
    public double spikes = 0;
    public double chikibamboni = 0;
    public double bulletRain = 0;
    public double frostArmor = 0;
    public double noParty = 0;
    public double bloodCartridge = 0;
    public double bloodCartridgeHpCost = 0;
    public double bloodCartridgeDmgBonus = 0;
    public double spiteRazorwire = 0;
    public double razorwireShards = 0;
    public double razorwireRadius = 0;
    public double razorwireDamage = 0;
    public double razorwireHoming = 0;
    public double razorwireLifesteal = 0;
    public double temporalRecall = 0;
    public double bulletTime = 0;

    public double x = 0;
    public double y = 0;
    public double z = 0;

    public boolean shieldActive = false;
    public double shieldHp = 0;
    public int phoenixUses = 0;

    public PlayerData() {
        DefaultStats d = DefaultStats.get();
        dmg = d.dmg;
        atks = d.atks;
        atkSpeed = d.atkSpeed;
        atkr = d.atkr;
        ammo = d.ammo;
        maxAmmo = d.maxAmmo;
        bullets = d.bullets;
        hp = d.hp;
        bulletSpeed = d.bulletSpeed;
        reloadSpeed = d.reloadSpeed;
    }

    public boolean getCard(int id) {
        return ownedCards.contains(id);
    }

    public int getCardCount(int id) {
        int count = 0;
        for (int cid : ownedCards) {
            if (cid == id) count++;
        }
        return count;
    }

    public void addCard(int id) {
        ownedCards.add(id);
    }

    public void setCard(int id, boolean value) {
        if (value) {
            ownedCards.add(id);
        } else {
            ownedCards.removeIf(i -> i == id);
        }
    }

    public boolean removeCardAtIndex(int index) {
        if (index >= 0 && index < ownedCards.size()) {
            ownedCards.remove(index);
            return true;
        }
        return false;
    }

    public boolean removeOneCard(int id) {
        int idx = ownedCards.indexOf(id);
        if (idx != -1) {
            ownedCards.remove(idx);
            return true;
        }
        return false;
    }

    public List<Integer> getOwnedCards() {
        return Collections.unmodifiableList(ownedCards);
    }

    public void resetAllCards() {
        ownedCards.clear();
        triggerActions.clear();
    }

    public void resetStats() {
        DefaultStats d = DefaultStats.get();
        dmg = d.dmg;
        atks = d.atks;
        atkSpeed = d.atkSpeed;
        atkr = d.atkr;
        bouncePl = 0;
        ammo = d.ammo;
        maxAmmo = d.maxAmmo;
        bullets = d.bullets;
        cold = 0;
        poison = 0;
        toxicCloud = 0;
        leech = 0;
        tgBounce = 0;
        homing = 0;
        homingOnBlock = 0;
        poisonLvl = 0;
        coldLvl = 0;
        parazitLvl = 0;
        parazit = 0;
        hp = d.hp;
        shieldCooldown = 0;
        bombBullet = 0;
        bombOnBlock = 0;
        bulletSpeed = d.bulletSpeed;
        empower = 0;
        empowerCharge = 0;
        darkStrength = 0;
        bigBullet = 0;
        grow = 0;
        trusterLvl = 0;
        jumpHeight = 0;
        dark = 0;
        speed = 0;
        speedBoost = 0;
        stun = 0;
        blockCd = 0;
        blockCdMult = 1.0;
        reloadSpeed = d.reloadSpeed;
        atksReload = 0;
        heal = 0;
        damagePerBounce = 0;
        doubleBlock = 0;
        shieldsUp = 0;
        shieldCharge = 0;
        autoReload = 0;
        autoReloadDelay = 0;
        zeroAmmoTicks = 0;
        saw = 0;
        shockwave = 0;
        silence = 0;
        silenceAura = 0;
        sneaky = 0;
        emp = 0;
        overpower = 0;
        refresh = 0;
        radiance = 0;
        highlight = 0;
        lifestealAura = 0;
        phoenix = 0;
        abyssal = 0;
        abyssalTicks = 0;
        implode = 0;
        drill = 0;
        splash = 0;
        teleport = 0;
        tacticalReload = 0;
        ammoPerHit = 0;
        hpBoostOnHit = 0;
        pristinePerseverance = 0;
        hpCost = 0;
        bloodFurry = 0;
        executioner = 0;
        stormCaller = 0;
        evasion = 0;
        chameleon = 0;
        snowball = 0;
        snowballWins = 0;
        skyfall = 0;
        berserk = 0;
        overheat = 0;
        secondWind = 0;
        spikes = 0;
        chikibamboni = 0;
        bulletRain = 0;
        frostArmor = 0;
        noParty = 0;
        bloodCartridge = 0;
        bloodCartridgeHpCost = 0;
        bloodCartridgeDmgBonus = 0;
        spiteRazorwire = 0;
        razorwireShards = 0;
        razorwireRadius = 0;
        razorwireDamage = 0;
        razorwireHoming = 0;
        razorwireLifesteal = 0;
        temporalRecall = 0;
        bulletTime = 0;
        decay = 0;
        echo = 0;
        x = 0;
        y = 0;
        z = 0;
        shieldActive = false;
        shieldHp = 0;
        phoenixUses = 0;
        triggerActions.clear();
    }

    public double getEffectiveDamage() {
        double base = Math.max(dmg, 0.5);
        if (darkStrength > 0) {
            base += darkStrength * 0.5;
        }
        if (empower > 0 && empowerCharge > 0) {
            base *= (1.0 + empower * 0.5);
        }
        return round2(Math.max(base, 0.5));
    }

    public void consumeEmpowerCharge() {
        if (empower > 0 && empowerCharge > 0) {
            empowerCharge--;
        }
    }

    public double getMaxHealth() {
        return Math.max(2, Math.min(Math.max(hp, 2), MAX_HEALTH));
    }

    public static double clampMaxHealth(double value) {
        return Math.max(2, Math.min(value, MAX_HEALTH));
    }

    public void addTriggerActions(List<com.rounds.cards.CardTriggerAction> actions) {
        if (actions != null && !actions.isEmpty()) {
            triggerActions.addAll(actions);
        }
    }

    public List<com.rounds.cards.CardTriggerAction> getTriggerActions() {
        return Collections.unmodifiableList(triggerActions);
    }

    public void executeTriggers(com.rounds.cards.CardTrigger trigger, org.bukkit.entity.Player player, org.bukkit.entity.Entity target, double contextValue) {
        if (triggerActions.isEmpty()) return;
        for (int i = 0; i < triggerActions.size(); i++) {
            com.rounds.cards.CardTriggerAction act = triggerActions.get(i);
            if (act.trigger() == trigger) {
                applyTriggerAction(act, player, target, contextValue);
            }
        }
    }

    private void applyTriggerAction(com.rounds.cards.CardTriggerAction act, org.bukkit.entity.Player player, org.bukkit.entity.Entity target, double contextValue) {
        switch (act.action()) {
            case RESTORE_AMMO -> {
                int toAdd = (int) Math.max(1, act.value());
                this.ammo = Math.min(this.maxAmmo, this.ammo + toAdd);
                if (player != null && player.isOnline()) {
                    player.sendActionBar(org.bukkit.ChatColor.GRAY + Messages.get("gun.ammo-display", (int) this.ammo, (int) this.maxAmmo));
                }
            }
            case HEAL -> {
                if (player != null && !player.isDead()) {
                    double maxHp = player.getMaxHealth();
                    player.setHealth(Math.min(maxHp, player.getHealth() + act.value()));
                }
            }
            case DAMAGE_VICTIM -> {
                if (target instanceof org.bukkit.entity.LivingEntity living && !living.isDead()) {
                    living.damage(act.value(), player);
                }
            }
            case PUSH_SHOOTER -> {
                if (player != null) {
                    org.bukkit.util.Vector dir = player.getLocation().getDirection();
                    player.setVelocity(player.getVelocity().add(dir.multiply(act.value())));
                }
            }
            case PUSH_VICTIM -> {
                if (target != null && player != null) {
                    org.bukkit.util.Vector dir = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    target.setVelocity(target.getVelocity().add(dir.multiply(act.value())));
                }
            }
            case PLAY_SOUND -> {
                if (player != null && !act.param().isEmpty()) {
                    player.playSound(player.getLocation(), act.param(), 1.0f, (float) (act.value() > 0 ? act.value() : 1.0f));
                }
            }
            case SPAWN_PARTICLE -> {
                if (player != null && !act.param().isEmpty()) {
                    try {
                        org.bukkit.Particle p = org.bukkit.Particle.valueOf(act.param().toUpperCase());
                        int count = (int) Math.max(1, act.value());
                        org.bukkit.Location loc = target != null ? target.getLocation() : player.getLocation();
                        loc.getWorld().spawnParticle(p, loc.clone().add(0, 1.0, 0), count, 0.3, 0.3, 0.3, 0.05);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            case APPLY_POTION -> {
                if (target instanceof org.bukkit.entity.LivingEntity living && !act.param().isEmpty()) {
                    try {
                        org.bukkit.potion.PotionEffectType pet = org.bukkit.potion.PotionEffectType.getByName(act.param().toUpperCase());
                        if (pet != null) {
                            int ticks = (int) Math.max(20, act.value() * 20.0);
                            living.addPotionEffect(new org.bukkit.potion.PotionEffect(pet, ticks, 0));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
