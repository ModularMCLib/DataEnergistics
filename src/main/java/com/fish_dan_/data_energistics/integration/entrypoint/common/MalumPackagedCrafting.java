package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.MalumMachineAdapter;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.MalumMachineKind;
import com.fish_dan_.data_energistics.integration.magic.malum.packaged.RunicWorkbenchAdapter;
import com.fish_dan_.data_energistics.integration.magic.malum.reusable.MalumReusableInputs;

@DataEnergisticsEntrypoint(requiredMods = "malum")
public final class MalumPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.reusableInputs().register(new MalumReusableInputs());
        registry.packagedCrafting().register(new MalumMachineAdapter(MalumMachineKind.ALTAR));
        registry.packagedCrafting().register(new MalumMachineAdapter(MalumMachineKind.CRUCIBLE));
        registry.packagedCrafting().register(new RunicWorkbenchAdapter());
    }
}
