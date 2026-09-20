package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.integration.crafting.packaged.magic.malum.MalumMachineAdapter;
import com.fish_dan_.data_energistics.integration.crafting.packaged.magic.malum.MalumMachineKind;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

@DataEnergisticsEntrypoint(requiredMods = "malum")
public final class MalumPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new MalumMachineAdapter(MalumMachineKind.ALTAR));
        registry.packagedCrafting().register(new MalumMachineAdapter(MalumMachineKind.CRUCIBLE));
    }
}
