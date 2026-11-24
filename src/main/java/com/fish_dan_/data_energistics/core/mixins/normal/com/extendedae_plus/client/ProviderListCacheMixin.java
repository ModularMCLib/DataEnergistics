package com.fish_dan_.data_energistics.core.mixins.normal.com.extendedae_plus.client;

import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;

import appeng.client.gui.me.items.PatternEncodingTermScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.extendedae_plus.client.ProviderListCache;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("extendedae_plus"))
@Mixin(value = ProviderListCache.class, remap = false)
@NullMarked
public abstract class ProviderListCacheMixin {

    @Inject(method = "findUniqueMatch", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$preserveUploadSearchKey(@Nullable String rawSearchKey, CallbackInfoReturnable<@Nullable Component> cir) {
        // ExtendedAE Plus consumes the search key when this lookup returns an automatic upload target.
        if (Minecraft.getInstance().screen instanceof PatternEncodingTermScreen<?> screen && PatternEncodingUploadClient.enabled(screen.getMenu())) {
            cir.setReturnValue(null);
        }
    }
}
