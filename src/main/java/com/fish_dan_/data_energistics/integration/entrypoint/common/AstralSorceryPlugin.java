package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryKeyType;

@DataEnergisticsEntrypoint(requiredMods = "astralsorcery")
public final class AstralSorceryPlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.aeKeyTypes().register(new AeKeyTypeRegistration(
                Data_Energistics.id("astral_sorcery"), AstralSorceryKeyType.TYPE,
                Data_Energistics.id("astral_sorcery")));
        AstralSorceryDigitalSupplyAdapter adapter = new AstralSorceryDigitalSupplyAdapter();
        registry.digitalSupplyInterfaces().register(new DigitalSupplyInterfaceRegistration(
                adapter.id(), adapter.resources(), adapter));
    }
}
