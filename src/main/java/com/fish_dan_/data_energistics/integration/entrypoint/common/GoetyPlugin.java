package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.worldenergy.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetyDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply.GoetySoulKeyType;
import com.fish_dan_.data_energistics.integration.magic.goety.packaged.DarkAltarAdapter;

/** Registers Goety's soul and ritual-level resources when Goety is present. */
@DataEnergisticsEntrypoint(requiredMods = "goety")
public final class GoetyPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new DarkAltarAdapter());
        registry.aeKeyTypes().register(new AeKeyTypeRegistration(
                GoetySoulKeyType.ID,
                GoetySoulKeyType.INSTANCE,
                GoetySoulKeyType.ID));
        GoetyDigitalSupplyAdapter adapter = new GoetyDigitalSupplyAdapter();
        registry.digitalSupplyInterfaces().register(new DigitalSupplyInterfaceRegistration(
                adapter.id(), adapter.resources(), adapter));
    }
}
