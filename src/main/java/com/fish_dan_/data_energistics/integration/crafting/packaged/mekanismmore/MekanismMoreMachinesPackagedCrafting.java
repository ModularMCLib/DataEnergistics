package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;

/** Registers Mekanism More Machines only when Applied Mekanistics can represent chemical AE keys. */
@DataEnergisticsEntrypoint(requiredMods = { "mekanismmoremachine", "appmek" })
public final class MekanismMoreMachinesPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new ChemicalChemicalAdapter(ChemicalChemicalAdapter.Kind.INFUSER));
        registry.packagedCrafting().register(new ChemicalChemicalAdapter(ChemicalChemicalAdapter.Kind.PIGMENT_MIXER));
        registry.packagedCrafting().register(new ChemicalChemicalAdapter(ChemicalChemicalAdapter.Kind.SOLAR_ACTIVATOR));
    }
}
