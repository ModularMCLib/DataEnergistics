package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Persistent provenance for native explosives fired by a cannon, including damage after a fuse or reload. */
public final class CannonPayloadDamage {

    private CannonPayloadDamage() {}

    public static void mark(Entity payload, @Nullable Entity owner) {
        payload.setData(DEAttachments.CANNON_PAYLOAD_SOURCE,
                new CannonPayloadSource(owner == null ? Optional.empty() : Optional.of(owner.getUUID())));
    }

    public static boolean isMarked(@Nullable Entity payload) {
        return payload != null && payload.hasData(DEAttachments.CANNON_PAYLOAD_SOURCE);
    }

    /** Restores attribution without retaining a live shooter reference across delayed explosions. */
    public static DamageSource source(Entity payload, DamageSource nativeSource) {
        if (!(payload.level() instanceof ServerLevel level) || !payload.hasData(DEAttachments.CANNON_PAYLOAD_SOURCE)) return nativeSource;
        CannonPayloadSource provenance = payload.getData(DEAttachments.CANNON_PAYLOAD_SOURCE);
        if (provenance.owner().isEmpty()) return nativeSource;
        Entity owner = level.getServer().getPlayerList().getPlayer(provenance.owner().get());
        if (owner == null) owner = level.getEntity(provenance.owner().get());
        return owner == null ? nativeSource : new DamageSource(nativeSource.typeHolder(), payload, owner);
    }
}
