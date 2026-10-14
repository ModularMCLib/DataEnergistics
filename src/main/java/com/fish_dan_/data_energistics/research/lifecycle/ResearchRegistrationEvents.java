package com.fish_dan_.data_energistics.research.lifecycle;

import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.research.definition.DigitizedWeaponModeResearch;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Publishes Java research registration after item, fluid and AE key registries are ready. */
public final class ResearchRegistrationEvents {

    @SubscribeEvent
    public void registerNodes(RegisterResearchNodesEvent event) {
        DigitizedWeaponModeResearch.register(event);
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModLoader.postEvent(new RegisterResearchNodesEvent()));
    }
}
