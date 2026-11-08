package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.world.item;

import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentMutation;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentProtection;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/** In-place stack mutations cannot bypass the guarded equipment list or return a duplicated rejected item. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = { "split", "copyAndClear" }, at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$extract(CallbackInfoReturnable<ItemStack> cir) {
        if (EquipmentProtection.denyExtraction((ItemStack) (Object) this)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @WrapMethod(method = "setCount")
    private void dataEnergistics$count(int count, Operation<Void> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (count < stack.getCount() && EquipmentProtection.denyExtraction(stack)) return;
        original.call(count);
    }

    @WrapMethod(method = "set")
    private <T> T dataEnergistics$contents(DataComponentType<T> type, @Nullable T value, Operation<T> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (EquipmentProtection.denyContents(stack, type, value)) return stack.get(type);
        return original.call(type, value);
    }

    @WrapMethod(method = "remove")
    private <T> T dataEnergistics$removeContents(DataComponentType<T> type, Operation<T> original) {
        ItemStack stack = (ItemStack) (Object) this;
        if (EquipmentProtection.denyContents(stack, type, null)) return stack.get(type);
        return original.call(type);
    }

    @WrapMethod(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")
    private void dataEnergistics$durability(int damage, ServerLevel level, @Nullable LivingEntity entity, Consumer<Item> onBreak, Operation<Void> original) {
        if (entity instanceof Player player) {
            try (var ignored = new EquipmentMutation(player)) {
                original.call(damage, level, entity, onBreak);
            }
        } else original.call(damage, level, entity, onBreak);
    }
}
