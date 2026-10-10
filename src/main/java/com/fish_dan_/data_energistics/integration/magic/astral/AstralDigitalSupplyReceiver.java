package com.fish_dan_.data_energistics.integration.magic.astral;

import appeng.api.storage.MEStorage;

import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.linking.LinkContainer;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import org.jspecify.annotations.Nullable;

/** Receiver contract implemented by the optional Astral mixin on a Digital Supply Interface. */
public interface AstralDigitalSupplyReceiver {

    @Nullable
    MEStorage data_energistics$networkStorage();

    LinkContainer data_energistics$astralLinkContainer();

    void data_energistics$receiveAstralTransmission(ServerLevel level, StarlightTransmissionPacket packet);
}
