package com.fish_dan_.data_energistics.mixin.malum;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sammy.malum.common.block.curiosities.spirit_altar.AltarCraftingHelper;
import com.sammy.malum.common.block.storage.IMalumSpecialItemAccessPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Native discovery is unchanged unless a packaged operation exclusively owns the altar. */
@Mixin(value = AltarCraftingHelper.class, remap = false)
abstract class SpiritAltarPedestalOwnershipMixin {

    @ModifyReturnValue(method = "capturePedestals(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;III)Ljava/util/List;", at = @At("RETURN"))
    private static List<IMalumSpecialItemAccessPoint> ownedPedestals(List<IMalumSpecialItemAccessPoint> original,
                                                                     Level level, BlockPos position, int x, int y, int z) {
        if (!(level instanceof ServerLevel server)) return original;
        var claims = PackagedMachineClaims.get(server);
        var owner = claims.owner(position);
        return owner == null ? original : original.stream().filter(access -> owner.equals(claims.owner(access.getAccessPointBlockPos()))).toList();
    }
}
