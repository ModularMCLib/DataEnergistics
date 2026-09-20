package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.mekanismmore.packaged.LargeMachineAdapter;
import com.fish_dan_.data_energistics.integration.technology.mekanismmore.packaged.LargeMachineKind;

/** Registers Mekanism More Machines only when Applied Mekanistics can represent chemical AE keys. */
@DataEnergisticsEntrypoint(requiredMods = { "mekmm", "appmek" })
public final class MekanismMoreMachinesPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        for (var kind : LargeMachineKind.values()) registry.packagedCrafting().register(new LargeMachineAdapter(kind));
    }
}
