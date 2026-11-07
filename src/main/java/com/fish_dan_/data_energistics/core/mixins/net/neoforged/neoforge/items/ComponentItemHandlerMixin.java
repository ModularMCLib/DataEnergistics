package com.fish_dan_.data_energistics.core.mixins.net.neoforged.neoforge.items;

import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentProtection;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import net.neoforged.neoforge.items.ComponentItemHandler;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Component-backed extraction must fail before it returns a copy, including views created by other mods. */
@Mixin(ComponentItemHandler.class)
public abstract class ComponentItemHandlerMixin {

    @Shadow
    @Final
    protected MutableDataComponentHolder parent;
    @Shadow
    @Final
    protected DataComponentType<ItemContainerContents> component;

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (parent instanceof ItemStack stack && EquipmentProtection.denyExtraction(stack, component)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
