package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.worldenergy.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply.NaturesAuraKeyType;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.NatureAltarAdapter;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.NatureCrimsonAltarAdapter;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.NatureForestRitualAdapter;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.NatureOfferingTableAdapter;

import net.minecraft.resources.ResourceLocation;

@DataEnergisticsEntrypoint(requiredMods = "naturesaura")
public final class NaturesAuraPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new NatureAltarAdapter());
        registry.packagedCrafting().register(new NatureCrimsonAltarAdapter());
        registry.packagedCrafting().register(new NatureForestRitualAdapter());
        registry.packagedCrafting().register(new NatureOfferingTableAdapter());
        registry.aeKeyTypes().register(new AeKeyTypeRegistration(
                NaturesAuraKeyType.TYPE.getId(), NaturesAuraKeyType.TYPE,
                ResourceLocation.fromNamespaceAndPath("data_energistics", "natures_aura")));
        var adapter = new NaturesAuraDigitalSupplyAdapter();
        registry.digitalSupplyInterfaces().register(new DigitalSupplyInterfaceRegistration(adapter.id(), adapter.resources(), adapter));
    }
}
