package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralDigitalSupplyReceiver;

import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.starlight.api.TransmissionReceiverNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import org.spongepowered.asm.mixin.Mixin;

/** Lets an existing Astral simple node deliver packets to a Digital Supply Interface at the same position. */
@Mixin(value = SimpleTransmissionNode.class, remap = false)
public abstract class SimpleTransmissionNodeReceiverMixin implements TransmissionReceiverNode {

    @Override
    public void receiveStarlight(ServerLevel level, StarlightTransmissionPacket packet) {
        SimpleTransmissionNode node = (SimpleTransmissionNode) (Object) this;
        if (level.getBlockEntity(node.getNodePos()) instanceof AstralDigitalSupplyReceiver receiver) {
            receiver.data_energistics$receiveAstralTransmission(level, packet);
        }
    }
}
