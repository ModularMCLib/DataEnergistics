package com.fish_dan_.data_energistics.common.combat.erasure.lifecycle;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

/** Server-owned life state: deliberately neither serialized, synchronized nor copied on player death. */
public final class ErasureAttachments {

    private ErasureAttachments() {}

    public static @Nullable ErasureState find(Entity entity) {
        return entity.hasData(DEAttachments.ERASURE_LIFE) ? entity.getData(DEAttachments.ERASURE_LIFE) : null;
    }

    public static boolean blocksRecovery(Entity entity) {
        ErasureState state = find(entity);
        return state != null && state.blocksRecovery();
    }

    static ErasureState begin(Entity entity) {
        return entity.getData(DEAttachments.ERASURE_LIFE);
    }

    /** Explicit respawn/failure boundary; this does not alter any equipment or third-party data. */
    public static void clear(Entity entity) {
        entity.removeData(DEAttachments.ERASURE_LIFE);
    }
}
