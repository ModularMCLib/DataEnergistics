package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.technology.mekanism.energy.MekanismEnergyEndpointIntegration;

@DataEnergisticsEntrypoint(requiredMods = "mekanism")
public final class MekanismEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(new MekanismEnergyEndpointIntegration());
    }
}
