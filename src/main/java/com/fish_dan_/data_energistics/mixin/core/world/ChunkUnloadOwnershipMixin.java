package com.fish_dan_.data_energistics.mixin.core.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents an obsolete unload callback from requeueing itself after its holder becomes active again. */
@Mixin(ChunkMap.class)
abstract class ChunkUnloadOwnershipMixin {

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Inject(method = "lambda$scheduleUnload$12", at = @At("HEAD"), cancellable = true)
    private void discardObsoleteUnload(ChunkHolder holder, long position, CallbackInfo ci) {
        // Check ownership before vanilla's readiness retry. A completed save future otherwise requeues the
        // callback immediately, preventing the server from processing generation work that releases its refs.
        // The active/replacement holder retains its normal save lifecycle; no chunk or reference is discarded.
        if (this.pendingUnloads.get(position) != holder) ci.cancel();
    }
}
