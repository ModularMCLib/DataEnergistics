package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.jspecify.annotations.NullMarked;

@NullMarked
@Mod(value = Data_Energistics.MOD_ID, dist = Dist.CLIENT)
public final class DataEnergisticsClient {

    public DataEnergisticsClient(IEventBus modBus) {
        ClientProxy.init(modBus);
    }
}
