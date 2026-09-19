package com.fish_dan_.data_energistics.integration.crafting.packaged.draconicevolution;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/** Keeps Draconic Evolution classes behind the mod-gated plugin boundary. */
@DataEnergisticsEntrypoint(requiredMods = "draconicevolution")
public final class DraconicEvolutionPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new DraconicFusionAdapter());
    }
}
