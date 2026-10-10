package com.fish_dan_.data_energistics.integration.magic.astral;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;

/** Registers Astral capability providers after the caller has confirmed that Astral Sorcery is loaded. */
public final class AstralSorceryRegistration {

    private AstralSorceryRegistration() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                ILumenHandler.BLOCK,
                DEBlockEntities.DIGITAL_SUPPLY_INTERFACE.get(),
                (blockEntity, context) -> new AstralLumenStorage(blockEntity::networkStorage, blockEntity::presenceKeys));
    }
}
