package com.fish_dan_.data_energistics.mixin.ae.ae2.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;
import appeng.parts.AEBasePart;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nodes keep routing/storage access, while frozen machine work cannot use AE's independent tick queue. */
@Mixin(TickManagerService.class)
abstract class DomainGridTickMixin {

    @Inject(method = "unsafeTickingRequest", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(TickTracker tracker, int elapsed, CallbackInfoReturnable<TickRateModulation> cir) {
        var node = tracker.getNode();
        var owner = node.getOwner();
        var position = owner instanceof BlockEntity entity ? entity.getBlockPos() : owner instanceof AEBasePart part ? part.getBlockEntity().getBlockPos() : null;
        if (position != null && DomainStasis.frozen(node.getLevel(), position)) cir.setReturnValue(TickRateModulation.SAME);
    }
}
