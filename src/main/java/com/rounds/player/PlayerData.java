package com.rounds.player;

import com.rounds.DefaultStats;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;

public class PlayerData {

    public static final double MAX_HEALTH = 1024.0;

    public String playerName = "";
    public double playerUse = 0;

    private final Set<Integer> ownedCards = new HashSet<>();

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
    public double reloadSpeed;
    public double heal = 0;
    public double damagePerBounce = 0;
    public double doubleBlock = 0;
    public double shieldsUp = 0;
    public double shieldCharge = 0;
    public double autoReload = 0;
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
    public double remote = 0;
    public double splash = 0;
    public double teleport = 0;
    public double tacticalReload = 0;
    public double ammoPerHit = 0;
    public double hpBoostOnHit = 0;
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

    public void setCard(int id, boolean value) {
        if (value) {
            ownedCards.add(id);
        } else {
            ownedCards.remove(id);
        }
    }

    public Set<Integer> getOwnedCards() {
        return Collections.unmodifiableSet(ownedCards);
    }

    public void resetAllCards() {
        ownedCards.clear();
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
        reloadSpeed = d.reloadSpeed;
        atksReload = 0;
        heal = 0;
        damagePerBounce = 0;
        doubleBlock = 0;
        shieldsUp = 0;
        shieldCharge = 0;
        autoReload = 0;
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
        remote = 0;
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
        x = 0;
        y = 0;
        z = 0;
        shieldActive = false;
        shieldHp = 0;
        phoenixUses = 0;
    }

    public double getEffectiveDamage() {
        double base = Math.max(dmg, 0.01);
        if (darkStrength > 0) {
            base += darkStrength * 0.5;
        }
        if (empower > 0 && empowerCharge > 0) {
            base *= (1.0 + empower * 0.5);
        }
        return round2(base);
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

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
