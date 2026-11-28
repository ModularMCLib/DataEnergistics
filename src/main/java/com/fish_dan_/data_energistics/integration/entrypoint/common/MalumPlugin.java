package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.MalumMachineAdapter;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.MalumMachineKind;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.RunicWorkbenchAdapter;

@DataEnergisticsEntrypoint(requiredMods = "malum")
public final class MalumPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        var crucible = new MalumMachineAdapter(MalumMachineKind.CRUCIBLE);
        registry.packagedCrafting().register(new MalumMachineAdapter(MalumMachineKind.ALTAR));
        registry.packagedCrafting().register(crucible);
        registry.reusableInputs().register(crucible);
        registry.packagedCrafting().register(new RunicWorkbenchAdapter());
    }
}
