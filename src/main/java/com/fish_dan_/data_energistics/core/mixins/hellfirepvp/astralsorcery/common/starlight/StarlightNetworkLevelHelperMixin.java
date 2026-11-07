package com.fish_dan_.data_energistics.core.mixins.hellfirepvp.astralsorcery.common.starlight;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralStarlightNodeLifecycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import hellfirepvp.astralsorcery.common.data.level.StarlightNetworkData;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkTickHelper;
import hellfirepvp.astralsorcery.common.starlight.api.ITransmissionTickable;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionLevelHelper;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Adds an explicit node lifecycle for non-Astral block entities. */
@Restriction(require = @Condition("astralsorcery"))
@Mixin(value = StarlightNetworkLevelHelper.class, remap = false)
public abstract class StarlightNetworkLevelHelperMixin implements AstralStarlightNodeLifecycle {

    @Shadow
    private StarlightNetworkData networkData;

    @Shadow
    private Level level;

    @Override
    public void dataEnergistics$addNode(TransmissionNode node) {
        this.networkData.addTransmissionNode(node);
        if (node instanceof ITransmissionTickable tickable) {
            StarlightNetworkTickHelper.getInstance()
                    .addNodeUpdate(this.level, tickable);
        }
        if (this.level instanceof ServerLevel serverLevel) {
            StarlightTransmissionLevelHelper.getInstance().getHandler(serverLevel)
                    .ifPresent(handler -> handler.notifyNodeChange(node));
        }
    }

    @Override
    public void dataEnergistics$removeNode(TransmissionNode node) {
        if (((StarlightNetworkLevelHelper) (Object) this).getNode(node.getNodePos()).orElse(null) != node ||
                !this.networkData.removeTransmissionNode(node.getNodePos())) {
            return;
        }
        if (this.level instanceof ServerLevel serverLevel) {
            StarlightTransmissionLevelHelper.getInstance().getHandler(serverLevel)
                    .ifPresent(handler -> handler.notifyNodeChange(node));
        }
    }
}
