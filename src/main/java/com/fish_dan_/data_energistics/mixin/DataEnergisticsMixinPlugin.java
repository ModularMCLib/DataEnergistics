package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ae.ae2lt.orbital.CelestweaveErasureHooks;
import com.fish_dan_.data_energistics.mixin.configuration.DataEnergisticsEarlyConfig;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.mixin.";
    private static final Map<String, String> MOD_COMPAT_MIXINS = new Object2ObjectOpenHashMap<>();

    static {
        addModCompatMixin("advancedae", "ae.advancedae.");
        addModCompatMixin("ae2ct", "ae.ae2ct.");
        addModCompatMixin("ae2cs", "ae.ae2cs.");
        addModCompatMixin("appliedcreate", "ae.appliedcreate.");
        addModCompatMixin("avaritia", "technology.avaritia.");
        addModCompatMixin("draconicevolution", "technology.draconicevolution.");
        addModCompatMixin("botania", "magic.botania.");
        addModCompatMixin("malum", "magic.malum.");
        addModCompatMixin("naturesaura", "magic.naturesaura.");
        addModCompatMixin("goety", "magic.goety.");
        addModCompatMixin("embers", "technology.embers.");
        addModCompatMixin("neovitae", "magic.neovitae.");
        addModCompatMixin("forbidden_arcanus", "magic.forbiddenarcanus.");
        addModCompatMixin("ae2lt", "ae.ae2lt.");
        addModCompatMixin("extendedae", "ae.extendedae.");
        addModCompatMixin("extendedae_plus", "ae.extendedaeplus.");
        addModCompatMixin("extendedcrafting", "technology.extendedcrafting.");
        addModCompatMixin("ae2jeiintegration", "ae.ae2jeiintegration.jei.");
        addModCompatMixin("jei", "viewer.jei.");
        addModCompatMixin("emi", "viewer.emi.ae2.");
        addModCompatMixin("ftbchunks", "map.ftbchunks.");
        addModCompatMixin("ftblibrary", "library.ftblibrary.");
        addModCompatMixin("guideme", "guide.guideme.");
        addModCompatMixin("neoecoae", "ae.neoecoae.");
        addModCompatMixin("xaeroworldmap", "map.xaeroworldmap.");
        addModCompatMixin("useless_mod", "useless.");
    }

    public DataEnergisticsMixinPlugin() {
        DataEnergisticsEarlyConfig.initialize();
    }

    private static void addModCompatMixin(String modId, String packageName) {
        MOD_COMPAT_MIXINS.put(modId, packageName);
    }

    private static boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return LoadingModList.get().getModFileById(modId) != null;
        }
        return ModList.get().isLoaded(modId);
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
        mixinClassName = mixinClassName.substring(MIXIN_PACKAGE.length());
        if (mixinClassName.equals("technology.avaritia.emi.ExtremeSmithingEmiInputsMixin")) {
            return isModLoaded("avaritia") && isModLoaded("emi");
        }
        if (mixinClassName.startsWith("technology.mekanismmore.appmek.")) {
            return isModLoaded("mekmm") && isModLoaded("appmek");
        }

        if (mixinClassName.startsWith("dev.")) {
            if (FMLLoader.isProduction()) {
                return false;
            }
            mixinClassName = mixinClassName.substring("dev.".length());
            if (mixinClassName.startsWith("datagen.")) {
                return DatagenModLoader.isRunningDataGen();
            }
            return true;
        }

        for (var compatMod : MOD_COMPAT_MIXINS.entrySet()) {
            if (mixinClassName.toLowerCase().startsWith(compatMod.getValue())) {
                return isModLoaded(compatMod.getKey());
            }
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.equals(MIXIN_PACKAGE + "ae.ae2lt.OrbitalCelestweaveProtectionMixin")) {
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
