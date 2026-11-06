package com.fish_dan_.data_energistics.core.mixins.appeng.me.service;

import com.fish_dan_.data_energistics.integration.ae.ae2.grid.PathingTopologyRevision;
import com.fish_dan_.data_energistics.integration.ae.ae2.grid.VirtualGridBridge;

import appeng.me.Grid;
import appeng.me.service.PathingService;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents incoming virtual members from inflating controllerless physical channel-power accounting.
 */
@Mixin(PathingService.class)
@NullMarked
public abstract class PathingServiceMixin implements PathingTopologyRevision {

    @Shadow
    @Final
    private Grid grid;

    /**
     * Monotonic generation used to invalidate controller-geometry capacity snapshots after every repath request.
     */
    @Unique
    private long dataEnergistics$pathingTopologyRevision;

    /**
     * Advances the cache generation after AE2 records a new pathing reset.
     */
    @Inject(method = "repath", at = @At("TAIL"), require = 1)
    private void dataEnergistics$advancePathingTopologyRevision(CallbackInfo callback) {
        this.dataEnergistics$pathingTopologyRevision = Math.incrementExact(this.dataEnergistics$pathingTopologyRevision);
    }

    @Override
    public long dataEnergistics$pathingTopologyRevision() {
        return this.dataEnergistics$pathingTopologyRevision;
    }

    /**
     * Removes virtual members while preserving adjustments made by other node-count injectors.
     *
     * @param count node count returned through the original call chain
     * @return physical node count
     */
    @ModifyExpressionValue(
                           method = "onServerEndTick",
                           at = @At(value = "INVOKE", target = "Lappeng/me/Grid;size()I"),
                           require = 1)
    private int dataEnergistics$countPhysicalNodes(int count) {
        return count - this.grid.size() + ((VirtualGridBridge) this.grid).physicalNodeCount();
    }
}
