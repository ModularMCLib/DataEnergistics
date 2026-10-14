package com.fish_dan_.data_energistics.research.lifecycle;

import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Publishes Java research registration after item, fluid and AE key registries are ready. */
public final class ResearchRegistrationEvents {

    private final IEventBus bus;

    public ResearchRegistrationEvents(IEventBus bus) {
        this.bus = bus;
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> bus.post(new RegisterResearchNodesEvent()));
    }
}
