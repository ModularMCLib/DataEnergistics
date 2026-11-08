package com.fish_dan_.data_energistics.core.mixins.normal.com.jerry.meklm.common.tile.machine;

import com.fish_dan_.data_energistics.common.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.core.mixins.target.LargeRecipeMachineTargets;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.polymixin.api.DynamicTargets;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import mekanism.common.tile.interfaces.IBoundingBlock;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shares the output-ownership gate across large recipe machines discovered at startup.
 * PolyMixin copies this interface into concrete output hooks; the server thread queries world claims
 * without consuming or changing them, so delegating overrides may safely encounter the gate again.
 */
@Restriction(require = { @Condition("mekanism"), @Condition("appmek") })
@DynamicTargets(value = LargeRecipeMachineTargets.class, relaxInjectionRequirements = false)
@Mixin(value = IBoundingBlock.class, remap = false)
@NullMarked
interface LargeMachineOutputOwnershipMixin {

    /** Cancels this output attempt while a provider owns the machine's products; unclaimed output proceeds. */
    @Dynamic("PolyMixin generates concrete class targets; the declared bounding-block interface has no output hook.")
    @Inject(method = "handleEject()V", at = @At("HEAD"), cancellable = true, require = 1)
    default void dataEnergistics$retainClaimedOutputs(CallbackInfo ci) {
        var tile = (BlockEntity) this;
        if (tile.getLevel() instanceof ServerLevel level && !PackagedMachineClaims.get(level).available(tile.getBlockPos())) ci.cancel();
    }
}
