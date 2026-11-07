package com.fish_dan_.data_energistics.core.mixins.com.extendedae_plus.util.uploadPattern;

import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternUploadScope;

import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("extendedae_plus"))
@Mixin(value = ProviderUploadUtil.class, remap = false)
@NullMarked
public abstract class ProviderUploadUtilMixin {

    @Inject(method = "recordProviderUpload", at = @At("RETURN"), remap = false, require = 1)
    private static void dataEnergistics$recordProviderUpload(ServerPlayer player, long providerId,
                                                             PatternContainer container, int slot, CallbackInfo ci) {
        EaepPatternUploadScope.recordProviderUpload(player, container);
    }

    @Inject(method = "recordMatrixUpload", at = @At("RETURN"), remap = false, require = 1)
    private static void dataEnergistics$recordMatrixUpload(ServerPlayer player, BlockPos position, String dimension,
                                                           boolean plus, int slot, CallbackInfo ci) {
        EaepPatternUploadScope.recordMatrixUpload(player, position, dimension, plus);
    }
}
