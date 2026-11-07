package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import net.minecraft.world.phys.Vec3;

/** Transient movement authority; no permission is restored from a player save. Owning side's game thread only. */
public final class PlatformMotionState {

    public boolean locked;
    public boolean noclip;
    public boolean detached;
    public boolean flying;
    public boolean permanentFlight;
    public boolean inertiaCancellation;
    public boolean miningPenaltyImmunity;
    public int input;
    public int inputAt;
    public int permits;
    public float maximumFlightSpeed = 1.2f;
    public float normalFlightSpeed = 0.28f;
    public Vec3 flightVelocity = Vec3.ZERO;
}
