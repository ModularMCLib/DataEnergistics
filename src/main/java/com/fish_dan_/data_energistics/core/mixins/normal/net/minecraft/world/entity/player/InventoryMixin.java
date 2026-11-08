package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.world.entity.player;

import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentMutation;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentProtection;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.ProtectedEquipmentList;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Public vanilla inventory lists and returned extractions share the same pre-transfer protection. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;withSize(ILjava/lang/Object;)Lnet/minecraft/core/NonNullList;", ordinal = 0))
    private NonNullList<ItemStack> dataEnergistics$inventory(NonNullList<ItemStack> contents, @Local(argsOnly = true) Player owner) {
        return ProtectedEquipmentList.wrap(owner, contents, EquipmentProtection.INVENTORY);
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;withSize(ILjava/lang/Object;)Lnet/minecraft/core/NonNullList;", ordinal = 1))
    private NonNullList<ItemStack> dataEnergistics$armor(NonNullList<ItemStack> contents, @Local(argsOnly = true) Player owner) {
        return ProtectedEquipmentList.wrap(owner, contents, EquipmentProtection.ARMOR);
    }

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;withSize(ILjava/lang/Object;)Lnet/minecraft/core/NonNullList;", ordinal = 2))
    private NonNullList<ItemStack> dataEnergistics$offHand(NonNullList<ItemStack> contents, @Local(argsOnly = true) Player owner) {
        return ProtectedEquipmentList.wrap(owner, contents, EquipmentProtection.OFF_HAND);
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$extract(int index, CallbackInfoReturnable<ItemStack> cir) {
        if (EquipmentProtection.denyExtraction(((Inventory) (Object) this).getItem(index))) cir.setReturnValue(ItemStack.EMPTY);
    }

    @WrapMethod(method = "load")
    private void dataEnergistics$load(ListTag tag, Operation<Void> original) {
        try (var ignored = new EquipmentMutation(player)) {
            original.call(tag);
        }
    }

    @WrapMethod(method = "replaceWith")
    private void dataEnergistics$replace(Inventory inventory, Operation<Void> original) {
        try (var ignored = new EquipmentMutation(player)) {
            original.call(inventory);
        }
    }
}
