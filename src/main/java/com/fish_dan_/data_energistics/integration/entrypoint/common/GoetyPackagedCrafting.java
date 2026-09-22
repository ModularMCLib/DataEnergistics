package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.goety.packaged.DarkAltarAdapter;

@DataEnergisticsEntrypoint(requiredMods = "goety")
public final class GoetyPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new DarkAltarAdapter());
    }
}
