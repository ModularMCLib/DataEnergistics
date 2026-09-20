package com.fish_dan_.data_energistics.mixin.core.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;

import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/** Dropped outputs may merge only with items owned by the same operation. */
@Mixin(ItemEntity.class)
abstract class PackagedItemMergeMixin {

    @Inject(method = "tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void preserveOwner(ItemEntity other, CallbackInfo callback) {
        var self = (ItemEntity) (Object) this;
        if (!Objects.equals(PackagedEntityCapture.owner(self), PackagedEntityCapture.owner(other))) callback.cancel();
    }
}
