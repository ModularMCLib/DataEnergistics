package com.fish_dan_.data_energistics.core.mixins.normal.hellfirepvp.astralsorcery.common.tile.network;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.integration.magic.astral.AstralDigitalSupplyReceiver;

import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.starlight.api.TransmissionReceiverNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;

/** Lets an existing Astral simple node deliver packets to a Digital Supply Interface at the same position. */
@Restriction(require = @Condition("astralsorcery"))
@Mixin(value = SimpleTransmissionNode.class, remap = false)
public abstract class SimpleTransmissionNodeMixin implements TransmissionReceiverNode {

    @Override
    public void receiveStarlight(ServerLevel level, StarlightTransmissionPacket packet) {
        SimpleTransmissionNode node = (SimpleTransmissionNode) (Object) this;
        if (DomainStasis.frozen(level, node.getNodePos())) return;
        if (level.getBlockEntity(node.getNodePos()) instanceof AstralDigitalSupplyReceiver receiver) {
            receiver.data_energistics$receiveAstralTransmission(level, packet);
        }
    }
}
