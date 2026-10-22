package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

/** Persistent provenance for native explosives fired by a cannon, including damage after a fuse or reload. */
public final class CannonPayloadDamage {

    private static final String KEY = "data_energistics:cannon_payload";
    private static final String OWNER = "Owner";

    private CannonPayloadDamage() {}

    public static void mark(Entity payload, @Nullable Entity owner) {
        CompoundTag provenance = new CompoundTag();
        if (owner != null) provenance.putUUID(OWNER, owner.getUUID());
        payload.getPersistentData().put(KEY, provenance);
    }

    public static boolean isMarked(@Nullable Entity payload) {
        return payload != null && payload.getPersistentData().contains(KEY, Tag.TAG_COMPOUND);
    }

    /** Restores attribution without retaining a live shooter reference across delayed explosions. */
    public static DamageSource source(Entity payload, DamageSource nativeSource) {
        CompoundTag provenance = payload.getPersistentData().getCompound(KEY);
        if (!(payload.level() instanceof ServerLevel level) || !provenance.hasUUID(OWNER)) return nativeSource;
        Entity owner = level.getServer().getPlayerList().getPlayer(provenance.getUUID(OWNER));
        if (owner == null) owner = level.getEntity(provenance.getUUID(OWNER));
        return owner == null ? nativeSource : new DamageSource(nativeSource.typeHolder(), payload, owner);
    }
}
