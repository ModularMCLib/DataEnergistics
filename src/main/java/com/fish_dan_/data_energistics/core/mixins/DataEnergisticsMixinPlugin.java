package com.fish_dan_.data_energistics.core.mixins;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.core.config.DataEnergisticsEarlyConfig;
import com.fish_dan_.data_energistics.core.config.Option;
import com.fish_dan_.data_energistics.core.mixin.condition.MixinConditionEvaluator;
import com.fish_dan_.data_energistics.core.mixin.condition.MixinLoadingEnvironment;
import com.fish_dan_.data_energistics.integration.ae.ae2lt.orbital.CelestweaveErasureHooks;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@NullMarked
public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.core.mixins.";
    private final MixinConditionEvaluator conditionEvaluator;

    public DataEnergisticsMixinPlugin() {
        DataEnergisticsEarlyConfig.initialize();
        this.conditionEvaluator = new MixinConditionEvaluator(new MixinLoadingEnvironment(
                readModVersions(),
                FMLLoader.isProduction(),
                DatagenModLoader.isRunningDataGen()));
    }

    private static Object2ObjectOpenHashMap<String, String> readModVersions() {
        Object2ObjectOpenHashMap<String, String> versions = new Object2ObjectOpenHashMap<>();
        for (var mod : LoadingModList.get().getMods()) {
            versions.put(mod.getModId(), mod.getVersion().toString());
        }
        return versions;
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
        ClassNode mixinClass = readMixinClass(mixinClassName);
        if (mixinClass == null) {
            return true;
        }
        try {
            return this.conditionEvaluator.shouldApply(mixinClass);
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.error("Invalid Mixin condition on {}; allowing injection", mixinClassName, exception);
            return true;
        }
    }

    private static @Nullable ClassNode readMixinClass(String mixinClassName) {
        String resourceName = mixinClassName.replace('.', '/') + ".class";
        ClassLoader loader = DataEnergisticsMixinPlugin.class.getClassLoader();
        try (InputStream stream = loader == null ? ClassLoader.getSystemResourceAsStream(resourceName) : loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                Data_Energistics.LOGGER.error("Could not read Mixin class metadata for {}", mixinClassName);
                return null;
            }
            ClassNode classNode = new ClassNode();
            new ClassReader(stream).accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return classNode;
        } catch (IOException | RuntimeException exception) {
            Data_Energistics.LOGGER.error("Could not parse Mixin class metadata for {}", mixinClassName, exception);
            return null;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.equals(MIXIN_PACKAGE + "com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandlerMixin")) {
            List<String> missing = CelestweaveErasureHooks.missingMethods(targetClass);
            if (!missing.isEmpty()) {
                Data_Energistics.LOGGER.warn("LT orbital erasure compatibility skipped missing methods on {}: {}; vanilla termination remains active",
                        targetClassName, missing);
            }
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
