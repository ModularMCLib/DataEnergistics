package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.packaged.DraconicFusionAdapter;

/** Keeps Draconic Evolution classes behind the mod-gated plugin boundary. */
@DataEnergisticsEntrypoint(requiredMods = "draconicevolution")
public final class DraconicEvolutionPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new DraconicFusionAdapter());
    }
}
