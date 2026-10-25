package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.world.level.Level;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkTickHelper;
import hellfirepvp.astralsorcery.common.starlight.api.ITransmissionTickable;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Astral node updates run in a separate level event rather than the block-entity ticker. */
@Mixin(value = StarlightNetworkTickHelper.class, remap = false)
abstract class DomainStarlightTickMixin {

    @WrapOperation(method = "lambda$onLevelTick$0", at = @At(value = "INVOKE", target = "Lhellfirepvp/astralsorcery/common/starlight/api/ITransmissionTickable;update(Lnet/minecraft/world/level/Level;)V"))
    private static void dataEnergistics$node(ITransmissionTickable node, Level level, Operation<Void> original) {
        if (!(node instanceof TransmissionNode spatial) || !DomainStasis.frozen(level, spatial.getNodePos())) original.call(node, level);
    }
}
