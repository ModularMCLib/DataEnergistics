package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ae.ae2lt.orbital.CelestweaveErasureHooks;
import com.fish_dan_.data_energistics.mixin.configuration.DataEnergisticsEarlyConfig;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.mixin.";
    private static final Object2ObjectMap<String, String> MOD_COMPAT_MIXINS = new Object2ObjectOpenHashMap<>();
    static {
        MOD_COMPAT_MIXINS.put("advancedae", "ae.advancedae.");
        MOD_COMPAT_MIXINS.put("ae2ct", "ae.ae2ct.");
        MOD_COMPAT_MIXINS.put("ae2cs", "ae.ae2cs.");
        MOD_COMPAT_MIXINS.put("appliedcreate", "ae.appliedcreate.");
        MOD_COMPAT_MIXINS.put("avaritia", "technology.avaritia.");
        MOD_COMPAT_MIXINS.put("draconicevolution", "technology.draconicevolution.");
        MOD_COMPAT_MIXINS.put("botania", "magic.botania.");
        MOD_COMPAT_MIXINS.put("astralsorcery", "magic.astral.");
        MOD_COMPAT_MIXINS.put("malum", "magic.malum.");
        MOD_COMPAT_MIXINS.put("naturesaura", "magic.naturesaura.");
        MOD_COMPAT_MIXINS.put("goety", "magic.goety.");
        MOD_COMPAT_MIXINS.put("embers", "technology.embers.");
        MOD_COMPAT_MIXINS.put("neovitae", "magic.neovitae.");
        MOD_COMPAT_MIXINS.put("forbidden_arcanus", "magic.forbiddenarcanus.");
        MOD_COMPAT_MIXINS.put("ae2lt", "ae.ae2lt.");
        MOD_COMPAT_MIXINS.put("extendedae", "ae.extendedae.");
        MOD_COMPAT_MIXINS.put("extendedae_plus", "ae.extendedaeplus.");
        MOD_COMPAT_MIXINS.put("extendedcrafting", "technology.extendedcrafting.");
        MOD_COMPAT_MIXINS.put("ae2jeiintegration", "ae.ae2jeiintegration.jei.");
        MOD_COMPAT_MIXINS.put("jei", "viewer.jei.");
        MOD_COMPAT_MIXINS.put("emi", "viewer.emi.");
        MOD_COMPAT_MIXINS.put("ftbchunks", "map.ftbchunks.");
        MOD_COMPAT_MIXINS.put("ftblibrary", "library.ftblibrary.");
        MOD_COMPAT_MIXINS.put("guideme", "guide.guideme.");
        MOD_COMPAT_MIXINS.put("neoecoae", "ae.neoecoae.");
        MOD_COMPAT_MIXINS.put("xaeroworldmap", "map.xaeroworldmap.");
    }

    public DataEnergisticsMixinPlugin() {
        DataEnergisticsEarlyConfig.initialize();
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

        for (var compatMod : MOD_COMPAT_MIXINS.object2ObjectEntrySet()) {
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
        if (mixinClassName.equals(MIXIN_PACKAGE + "ae.ae2lt.ErasureCelestweaveProtectionMixin")) {
            var missing = CelestweaveErasureHooks.missingMethods(targetClass);
            if (!missing.isEmpty()) {
                Data_Energistics.LOGGER.warn("LT orbital erasure compatibility skipped missing methods on {}: {}; vanilla termination remains active",
                        targetClassName, missing);
            }
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
