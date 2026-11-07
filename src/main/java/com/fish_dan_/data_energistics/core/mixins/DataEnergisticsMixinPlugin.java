package com.fish_dan_.data_energistics.core.mixins;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.core.config.DataEnergisticsEarlyConfig;
import com.fish_dan_.data_energistics.core.config.Option;
import com.fish_dan_.data_energistics.integration.ae.ae2lt.orbital.CelestweaveErasureHooks;

import me.fallenbreath.conditionalmixin.api.mixin.RestrictiveMixinConfigPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

@NullMarked
public final class DataEnergisticsMixinPlugin extends RestrictiveMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.core.mixins.";

    public DataEnergisticsMixinPlugin() {
        DataEnergisticsEarlyConfig.initialize();
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public @Nullable String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return true;
        }
        String relativeMixinClassName = mixinClassName.substring(MIXIN_PACKAGE.length());
        Option option = DataEnergisticsEarlyConfig.get().getEffectiveOptionForMixin(relativeMixinClassName);
        if (option != null && !option.isEnabled()) {
            return false;
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }

    @Override
    protected void onRestrictionCheckFailed(String mixinClassName, String reason) {
        Data_Energistics.LOGGER.debug("Skipping Mixin {}: {}", mixinClassName, reason);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        super.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        if (mixinClassName.equals(MIXIN_PACKAGE + "com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandlerMixin")) {
            List<String> missing = CelestweaveErasureHooks.missingMethods(targetClass);
            if (!missing.isEmpty()) {
                Data_Energistics.LOGGER.warn("LT orbital erasure compatibility skipped missing methods on {}: {}; vanilla termination remains active",
                        targetClassName, missing);
            }
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        super.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }
}
