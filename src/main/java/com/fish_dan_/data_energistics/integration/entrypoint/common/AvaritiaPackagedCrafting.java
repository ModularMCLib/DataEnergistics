package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.avaritia.packaged.ExtremeSmithingAdapter;
import com.fish_dan_.data_energistics.integration.technology.avaritia.packaged.NeutronCompressionAdapter;
import com.fish_dan_.data_energistics.integration.technology.avaritia.packaged.TierCraftingAdapter;

import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;

/** Registers the four native Avaritia tier tables. */
@DataEnergisticsEntrypoint(requiredMods = "avaritia")
public final class AvaritiaPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new TierCraftingAdapter(ModCraftTier.SCULK));
        registry.packagedCrafting().register(new TierCraftingAdapter(ModCraftTier.NETHER));
        registry.packagedCrafting().register(new TierCraftingAdapter(ModCraftTier.END));
        registry.packagedCrafting().register(new TierCraftingAdapter(ModCraftTier.EXTREME));
        registry.packagedCrafting().register(new ExtremeSmithingAdapter());
        registry.packagedCrafting().register(new NeutronCompressionAdapter());
    }
}
