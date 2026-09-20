package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.energy.access.VerifiedUnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.technology.industrialforegoing.energy.IndustrialForegoingEnergyEndpointIntegration;

@DataEnergisticsEntrypoint(requiredMods = "industrialforegoing")
public final class IndustrialForegoingEnergyRegistration implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.towerEnergyIntegrations().register(
                new IndustrialForegoingEnergyEndpointIntegration(new VerifiedUnlimitedEnergyAccess()));
    }
}
