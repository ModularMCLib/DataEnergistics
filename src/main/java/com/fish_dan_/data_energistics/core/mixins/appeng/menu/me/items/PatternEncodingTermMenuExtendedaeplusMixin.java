package com.fish_dan_.data_energistics.core.mixins.appeng.menu.me.items;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternencoding.source.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternUploadScope;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync;
import com.extendedae_plus.util.uploadPattern.MatrixUploadUtil;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("extendedae_plus"))
@Mixin(value = PatternEncodingTermMenu.class, priority = 1500)
@NullMarked
public abstract class PatternEncodingTermMenuExtendedaeplusMixin implements EaepPatternEncodingHandoff {

    @Unique
    private boolean dataEnergistics$eaepUploadPending;

    @TargetHandler(
                   mixin = "com.extendedae_plus.mixin.ae2.menu.ContainerPatternEncodingTermMenuMixin",
                   name = "eap$serverUploadAfterEncode(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void dataEnergistics$useEncodingHandoff(CallbackInfo originalCallback, CallbackInfo ci) {
        if (this instanceof PatternEncodingPreferenceMenu) {
            ci.cancel();
        }
    }

    @Override
    public void data_energistics$beginEaepEncodeHandoff(boolean dataEnergisticsUploadEnabled) {
        this.dataEnergistics$eaepUploadPending = false;
        boolean shiftUpload = ((IPatternEncodeRequestSync) this).eap$consumeEncodeRequest() instanceof IPatternEncodeRequestSync.EncodeRequest request && request.shiftDown();
        this.dataEnergistics$eaepUploadPending = !dataEnergisticsUploadEnabled && !shiftUpload;
    }

    @Override
    public void data_energistics$finishEaepEncodeHandoff(boolean encodedSuccessfully) {
        boolean shouldUpload = this.dataEnergistics$eaepUploadPending;
        this.dataEnergistics$eaepUploadPending = false;
        if (!shouldUpload || !encodedSuccessfully) {
            return;
        }

        try {
            PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
            EncodingMode mode = menu.getMode();
            if (mode != EncodingMode.CRAFTING && mode != EncodingMode.SMITHING_TABLE && mode != EncodingMode.STONECUTTING) {
                return;
            }

            Player player = menu.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            PatternEncodingRankingContext rankingContext = PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                    mode, PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode));
            EaepPatternUploadScope.UploadSnapshot snapshot = EaepPatternUploadScope.capture(
                    serverPlayer, menu, rankingContext);
            serverPlayer.server.execute(() -> {
                try (EaepPatternUploadScope.ScopeToken ignored = EaepPatternUploadScope.open(snapshot)) {
                    MatrixUploadUtil.uploadFromEncodingMenuToMatrix(serverPlayer, menu);
                } catch (RuntimeException exception) {
                    this.dataEnergistics$eaepUploadPending = false;
                    Data_Energistics.LOGGER.error(
                            "ExtendedAE-Plus matrix upload failed after pattern encoding", exception);
                }
            });
        } catch (RuntimeException exception) {
            this.dataEnergistics$eaepUploadPending = false;
            Data_Energistics.LOGGER.error(
                    "Could not schedule ExtendedAE-Plus matrix upload after pattern encoding", exception);
        }
    }
}
