package com.fish_dan_.data_energistics.common.weapon.platform.shield;

/** Server-thread scope for health writes already settled by damage or explicitly restored from a snapshot. */
public final class ShieldHealthWrites {

    public int settled;
    public long rejectedDeathAt = Long.MIN_VALUE;
}
