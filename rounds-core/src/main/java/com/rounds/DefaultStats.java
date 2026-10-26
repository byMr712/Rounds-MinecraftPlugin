package com.rounds;

public class DefaultStats {

    public double dmg = 3.0;
    public double atks = 20;
    public double atkSpeed = 0;
    public double atkr = 0;
    public double ammo = 3;
    public double maxAmmo = 3;
    public double bullets = 1;
    public double hp = 20;
    public double bulletSpeed = 1.0;
    public double reloadSpeed = 0;

    private static DefaultStats instance = new DefaultStats();

    public static DefaultStats get() { return instance; }
    public static void set(DefaultStats s) { instance = s; }
}
