package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.packaged.PackagedAdaptiveRoute;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.stacks.AEItemKey;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/** Registers the digital packaged provider as one adaptive profile handling all installed machine adapters. */
@DataEnergisticsEntrypoint
public final class PackagedPatternProviderRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        var matcher = AdaptivePatternProviderRegistrationFactory.itemIds(
                "data_energistics:digital_packaged_pattern_provider");
        registry.adaptivePatternProviders().register(new AdaptivePatternProviderRegistration(
                Data_Energistics.id("adaptive_pattern_provider/digital_packaged"),
                providerStack -> resolveProfile(matcher, providerStack),
                new PackagedAdaptiveRoute()));
    }

    private static @Nullable AdaptivePatternProviderProfile resolveProfile(
                                                                           Predicate<ItemStack> matcher,
                                                                           ItemStack providerStack) {
        if (!matcher.test(providerStack)) {
            return null;
        }
        ItemStack icon = providerStack.copyWithCount(1);
        AEItemKey terminalIcon = AEItemKey.of(icon);
        if (terminalIcon == null) {
            throw new IllegalStateException("Adaptive pattern provider item has no AE item key");
        }
        var packagedCrafting = DataEnergisticsEntrypointLoader.snapshot().packagedCrafting();
        return new AdaptivePatternProviderProfile(
                36,
                icon,
                terminalIcon,
                icon.getHoverName(),
                packagedCrafting.recipeCategoryIds(),
                packagedCrafting.workstationItemIds(),
                AdaptivePatternProviderRegistrationFactory.capabilities());
    }
}
