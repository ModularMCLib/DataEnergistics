package com.fish_dan_.data_energistics.common.weapon.platform.ability;

/** Transient movement authority; no permission is restored from a player save. Owning side's game thread only. */
public final class PlatformMotionState {

    public boolean locked;
    public boolean noclip;
    public boolean detached;
    public int input;
    public int inputAt;
    public int permits;
}
