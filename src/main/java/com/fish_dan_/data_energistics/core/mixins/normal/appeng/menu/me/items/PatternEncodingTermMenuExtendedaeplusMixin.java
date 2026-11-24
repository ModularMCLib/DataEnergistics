package com.fish_dan_.data_energistics.core.mixins.normal.appeng.menu.me.items;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternencoding.source.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.patternencoding.EaepPatternUploadScope;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.bawnorton.mixinsquared.TargetHandler;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync;
import com.extendedae_plus.api.upload.IPatternEncodeRequestSync.EncodeRequest;
import com.extendedae_plus.api.upload.IPatternUploadMenu;
import com.extendedae_plus.api.upload.UploadPreferences;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil;
import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.UploadOutcome;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Restriction(require = @Condition("extendedae_plus"))
@Mixin(value = PatternEncodingTermMenu.class, priority = 1500)
@NullMarked
public abstract class PatternEncodingTermMenuExtendedaeplusMixin implements EaepPatternEncodingHandoff {

    @Unique
    private @Nullable EncodeRequest dataEnergistics$eaepUploadRequest;

    @TargetHandler(
                   mixin = "com.extendedae_plus.mixin.ae2.menu.ContainerPatternEncodingTermMenuMixin",
                   name = "eap$serverUploadAfterEncode(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void dataEnergistics$useEncodingHandoff(CallbackInfo ci) {
        var menu = (PatternEncodingTermMenu) (Object) this;
        if (menu instanceof PatternEncodingPreferenceMenu) {
            ci.cancel();
        }
    }

    @Override
    public void data_energistics$beginEaepEncodeHandoff(boolean dataEnergisticsUploadEnabled) {
        this.dataEnergistics$eaepUploadRequest = null;
        EncodeRequest request = ((IPatternEncodeRequestSync) this).eap$consumeEncodeRequest();
        if (!dataEnergisticsUploadEnabled && (request == null || !request.shiftDown())) {
            this.dataEnergistics$eaepUploadRequest = request == null ? new EncodeRequest(false, null, UploadPreferences.defaults()) : request;
        }
    }

    @Override
    public void data_energistics$finishEaepEncodeHandoff(boolean encodedSuccessfully) {
        EncodeRequest request = this.dataEnergistics$eaepUploadRequest;
        this.dataEnergistics$eaepUploadRequest = null;
        if (request == null || !encodedSuccessfully) {
            return;
        }

        try {
            var menu = (PatternEncodingTermMenu) (Object) this;
            EncodingMode mode = menu.getMode();
            boolean processing = mode == EncodingMode.PROCESSING;
            Component provider = request.uniqueProvider();
            if (processing && provider == null) {
                return;
            }

            Player player = menu.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            PatternEncodingRankingContext rankingContext = processing ?
                    ((PatternEncodingPreferenceMenu) menu).data_energistics$getPreferenceSession().rankingContext() :
                    Objects.requireNonNull(PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                            mode, PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode)),
                            "Missing fixed-mode ranking context for " + mode);
            EaepPatternUploadScope.UploadSnapshot snapshot = EaepPatternUploadScope.capture(
                    serverPlayer, menu, rankingContext);
            serverPlayer.server.execute(() -> {
                try (EaepPatternUploadScope.ScopeToken ignored = EaepPatternUploadScope.open(snapshot)) {
                    if (provider != null && ProviderUploadUtil.uploadFromEncodingMenuToNamedProvider(
                            serverPlayer, (IPatternUploadMenu) menu, provider) != UploadOutcome.FAILED) {
                        return;
                    }
                    if (!processing) {
                        ProviderUploadUtil.uploadFromEncodingMenuByPriority(serverPlayer, menu, request.prefs());
                    }
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "ExtendedAE-Plus upload failed after pattern encoding", exception);
                }
            });
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Could not schedule ExtendedAE-Plus upload after pattern encoding", exception);
        }
    }
}
