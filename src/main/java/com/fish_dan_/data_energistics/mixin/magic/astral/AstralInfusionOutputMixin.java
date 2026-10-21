package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** Binds the actual native infusion result to the claimed operation before another entity tick can merge it. */
@Mixin(value = TileInfuser.class, remap = false)
abstract class AstralInfusionOutputMixin {

    // Astral creates this drop inside the crafting callback, which is compiled to a synthetic method.
    @ModifyExpressionValue(method = "lambda$doCraftingCycle$0",
                           at = @At(value = "INVOKE",
                                    target = "Lhellfirepvp/astralsorcery/common/util/ItemUtil;dropItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;"))
    private Optional<ItemEntity> claimNativeOutput(Optional<ItemEntity> output) {
        var infuser = (TileInfuser) (Object) this;
        if (infuser.getLevel() instanceof ServerLevel level) {
            var owner = PackagedMachineClaims.get(level).owner(infuser.getBlockPos());
            if (owner != null) output.ifPresent(entity -> PackagedEntityCapture.claim(entity, owner));
        }
        return output;
    }
}
