package com.fish_dan_.data_energistics.core.mixins.dev.client;

import com.fish_dan_.data_energistics.utils.dev.GradleResourceReload;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@NullMarked
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @WrapOperation(method = "handleDebugKeys",
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> dataEnergistics$rebuildResourcesBeforeReload(Minecraft minecraft,
                                                                                 Operation<CompletableFuture<Void>> original) {
        return GradleResourceReload.rebuildAndReload(minecraft, () -> original.call(minecraft));
    }
}
