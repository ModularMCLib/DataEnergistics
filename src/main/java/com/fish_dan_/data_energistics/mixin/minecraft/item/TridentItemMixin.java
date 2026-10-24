package com.fish_dan_.data_energistics.mixin.minecraft.item;

import com.fish_dan_.data_energistics.common.item.trident.DataTridentItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps vanilla throwing, loyalty and riptide while the data trident replaces durability with energy.
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {

    @Inject(method = "isTooDamagedToUse", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$energyInsteadOfDurability(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof DataTridentItem) callback.setReturnValue(false);
    }

    @ModifyConstant(method = "releaseUsing", constant = @Constant(floatValue = 2.5F))
    private float dataEnergistics$dataTridentThrowSpeed(float original) {
        return (Object) this instanceof DataTridentItem ? original * DataTridentItem.FLIGHT_SPEED_MULTIPLIER : original;
    }

    @ModifyArg(method = "releaseUsing",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;startAutoSpinAttack(IFLnet/minecraft/world/item/ItemStack;)V"),
               index = 1)
    private float dataEnergistics$dataTridentRiptideDamage(float original) {
        return (Object) this instanceof DataTridentItem ? DataTridentItem.DAMAGE : original;
    }
}
