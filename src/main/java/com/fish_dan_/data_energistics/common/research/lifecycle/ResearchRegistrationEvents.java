package com.fish_dan_.data_energistics.common.research.lifecycle;

import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.data.research.DigitizedToolUpgradeResearch;
import com.fish_dan_.data_energistics.data.research.DigitizedWeaponModeResearch;
import com.fish_dan_.data_energistics.data.research.MeDigitalAnvilResearch;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;
import com.fish_dan_.data_energistics.data.research.WeaponPlatformResearch;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Publishes Java research registration after item, fluid and AE key registries are ready. */
public final class ResearchRegistrationEvents {

    @SubscribeEvent
    public void registerNodes(RegisterResearchNodesEvent event) {
        DigitizedToolUpgradeResearch.register(event);
        DigitizedWeaponModeResearch.register(event);
        MeDigitalAnvilResearch.register(event);
        PlayerTerminalResearch.register(event);
        WeaponPlatformResearch.register(event);
    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModLoader.postEvent(new RegisterResearchNodesEvent()));
    }
}
