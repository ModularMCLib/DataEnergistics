package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.actuallyadditions.packaged.AtomicReconstructorAdapter;
import com.fish_dan_.data_energistics.integration.technology.actuallyadditions.packaged.EmpowererAdapter;

@DataEnergisticsEntrypoint(requiredMods = "actuallyadditions")
public final class ActuallyAdditionsPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new EmpowererAdapter());
        registry.packagedCrafting().register(new AtomicReconstructorAdapter());
    }
}
