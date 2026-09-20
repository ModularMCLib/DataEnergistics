package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.integration.patternprovider.ae.ae2cs.Ae2CrystalScienceCapacity;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

@DataEnergisticsEntrypoint(requiredMods = "ae2cs")
public final class Ae2CrystalScienceCapacityRegistration implements DataEnergisticsPlugin {
    @Override
    public void register(DataEnergisticsRegistry registry) {
        for (String id : ObjectList.of("circuit_etcher", "crystal_aggregator", "crystal_pulverizer",
                "quartz_grindstone", "crystal_growth_chamber", "entropy_variation_reaction_chamber")) {
            registry.craftingMachines().registerCapacity(CraftingMachineCapacityRegistration.blockEntity(
                    Data_Energistics.id("ae2cs_" + id + "_capacity"),
                    ResourceLocation.fromNamespaceAndPath("ae2cs", id),
                    Ae2CrystalScienceCapacity::capture));
        }
    }
}
