package com.fish_dan_.data_energistics.common.entity.projectile;

/**
 * Exposes the synchronized texture identity of vanilla trident projectiles to client rendering.
 * The trident mixin derives this value from the carried item on construction and save loading, because the carried
 * item itself is not synchronized to clients. Read it on the entity's owning thread, or the client render thread;
 * callers must not maintain an independent copy of this state.
 */
public interface DataTridentAppearance {

    /**
     * Returns whether this initialized projectile uses the data trident entity texture. This is a side-effect-free
     * query of synchronized entity data and remains valid after loading a saved projectile.
     */
    boolean usesDataTridentTexture();
}
