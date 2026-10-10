package com.fish_dan_.data_energistics.integration.magic.astral;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.linking.LinkContainer;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionReceiverNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;

/** Astral transmission node that forwards received packets to the block entity at its position. */
public final class DigitalSupplyTransmissionReceiverNode extends SimpleTransmissionNode
                                                         implements TransmissionReceiverNode {

    public DigitalSupplyTransmissionReceiverNode(BlockPos pos) {
        super(pos);
    }

    public DigitalSupplyTransmissionReceiverNode(BlockPos pos, LinkContainer links, float lossMultiplier) {
        super(pos, links, lossMultiplier);
    }

    @Override
    public void receiveStarlight(ServerLevel level, StarlightTransmissionPacket packet) {
        if (level.getBlockEntity(this.getNodePos()) instanceof AstralDigitalSupplyReceiver receiver) {
            receiver.data_energistics$receiveAstralTransmission(level, packet);
        }
    }
}
