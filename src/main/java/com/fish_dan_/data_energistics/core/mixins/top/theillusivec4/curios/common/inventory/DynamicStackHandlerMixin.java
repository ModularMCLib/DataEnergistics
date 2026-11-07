package com.fish_dan_.data_energistics.core.mixins.top.theillusivec4.curios.common.inventory;

import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentProtection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.inventory.DynamicStackHandler;

import java.util.function.Function;

/** The native handler owns the wearer context; inherited setters also need the same transfer guard. */
@Restriction(require = @Condition("curios"))
@Mixin(value = DynamicStackHandler.class, remap = false)
@NullMarked
public abstract class DynamicStackHandlerMixin extends ItemStackHandler {

    @Shadow
    protected Function<Integer, SlotContext> ctxBuilder;

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack stack = super.getStackInSlot(slot);
        SlotContext context = ctxBuilder.apply(slot);
        if (context.entity() instanceof ServerPlayer player) EquipmentProtection.bind(player, stack, EquipmentProtection.CURIO, slot, context.identifier(), context.cosmetic());
        return stack;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        ItemStack previous = getStackInSlot(slot);
        SlotContext context = ctxBuilder.apply(slot);
        if (context.entity() instanceof ServerPlayer player && EquipmentProtection.denyReplacement(player, EquipmentProtection.CURIO, slot, previous, stack)) return;
        super.setStackInSlot(slot, stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void dataEnergistics$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (EquipmentProtection.denyExtraction(getStackInSlot(slot))) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "shrink", at = @At("HEAD"), cancellable = true, remap = false)
    private void dataEnergistics$resize(int amount, CallbackInfo ci) {
        for (int slot = Math.max(0, getSlots() - amount); slot < getSlots(); slot++) {
            if (EquipmentProtection.denyExtraction(getStackInSlot(slot))) {
                ci.cancel();
                return;
            }
        }
    }
}
