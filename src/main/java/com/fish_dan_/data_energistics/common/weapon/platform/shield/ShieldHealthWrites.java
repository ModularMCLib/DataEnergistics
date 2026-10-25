package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import net.minecraft.world.damagesource.DamageSource;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Server-thread scope for health writes already settled by damage or explicitly restored from a snapshot. */
public final class ShieldHealthWrites {

    public int settled;
    public long rejectedDeathAt = Long.MIN_VALUE;
    public long sourceAt = Long.MIN_VALUE;
    public @Nullable DamageSource source;
    public final ObjectList<ShieldImpact> impacts = new ObjectArrayList<>();
}
