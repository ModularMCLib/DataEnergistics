package com.fish_dan_.data_energistics.integration.crafting.packaged.extendedcrafting;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/** Real powered and alternator-driven crafting; optional classes load only with their owning mods. */
@DataEnergisticsEntrypoint(requiredMods = { "extendedcrafting", "cucumber" })
public final class ExtendedCraftingPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new TableCrafterAdapter(1));
        registry.packagedCrafting().register(new TableCrafterAdapter(2));
        registry.packagedCrafting().register(new TableCrafterAdapter(3));
        registry.packagedCrafting().register(new TableCrafterAdapter(4));
        registry.packagedCrafting().register(new CombinationCraftingAdapter());
        registry.packagedCrafting().register(new AlternatorCrafterAdapter(false));
        registry.packagedCrafting().register(new AlternatorCrafterAdapter(true));
        registry.packagedCrafting().register(new QuantumCompressionAdapter());
        registry.packagedCrafting().register(new PoweredTableAdapter());
    }
}
