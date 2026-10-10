package com.fish_dan_.data_energistics.integration.magic.astral;

import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;

import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.linking.LinkContainer;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import org.jspecify.annotations.Nullable;

/**
 * Astral's native receiver and liquid-source contract for a Digital Supply Interface.
 *
 * <p>Callbacks run on the logical server thread. Network references are valid only for the current call;
 * consumers use grid identity to avoid counting multiple interfaces on the same network twice.</p>
 */
public interface AstralDigitalSupplyReceiver {

    /** Returns the current online grid, or null when the interface cannot transfer resources. */
    @Nullable
    IGrid data_energistics$grid();

    /** Returns ordinary network quantity storage; presence markers are never an extraction source. */
    @Nullable
    MEStorage data_energistics$networkStorage();

    /** Returns the links maintained by Astral's native Linking Tool. */
    LinkContainer data_energistics$astralLinkContainer();

    /** Receives a native constellation packet without storing consumable quantities on the interface. */
    void data_energistics$receiveAstralTransmission(ServerLevel level, StarlightTransmissionPacket packet);
}
