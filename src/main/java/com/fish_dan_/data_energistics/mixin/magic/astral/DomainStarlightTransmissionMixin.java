package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.starlight.api.ITransmissionNotifiable;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionReceiverNode;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionSourceNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionLevelHandler;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(value = StarlightTransmissionLevelHandler.class, remap = false)
abstract class DomainStarlightTransmissionMixin {

    @WrapOperation(method = "lambda$tick$3", at = @At(value = "INVOKE", target = "Lhellfirepvp/astralsorcery/common/starlight/api/TransmissionSourceNode;produceStarlight(Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional<StarlightTransmissionPacket> dataEnergistics$source(TransmissionSourceNode node, Level level, Operation<Optional<StarlightTransmissionPacket>> original) {
        return DomainStasis.frozen(level, node.getNodePos()) ? Optional.empty() : original.call(node, level);
    }

    @WrapOperation(method = "lambda$tick$0", at = @At(value = "INVOKE", target = "Lhellfirepvp/astralsorcery/common/starlight/api/TransmissionReceiverNode;receiveStarlight(Lnet/minecraft/server/level/ServerLevel;Lhellfirepvp/astralsorcery/common/starlight/transmission/StarlightTransmissionPacket;)V"))
    private static void dataEnergistics$receiver(TransmissionReceiverNode node, ServerLevel level, StarlightTransmissionPacket packet, Operation<Void> original) {
        if (!DomainStasis.frozen(level, node.getNodePos())) original.call(node, level, packet);
    }

    @WrapOperation(method = "lambda$tick$1", at = @At(value = "INVOKE", target = "Lhellfirepvp/astralsorcery/common/starlight/api/ITransmissionNotifiable;updateTransmission(Lnet/minecraft/server/level/ServerLevel;Lhellfirepvp/astralsorcery/common/starlight/transmission/StarlightTransmissionPacket;)V"))
    private static void dataEnergistics$notify(ITransmissionNotifiable node, ServerLevel level, StarlightTransmissionPacket packet, Operation<Void> original) {
        if (!(node instanceof TransmissionNode spatial) || !DomainStasis.frozen(level, spatial.getNodePos())) original.call(node, level, packet);
    }

    @WrapOperation(method = "lambda$tick$2", at = @At(value = "INVOKE", target = "Lhellfirepvp/astralsorcery/common/recipe/focal/place/ActiveTransmutationHandler;receiveStarlight(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lhellfirepvp/astralsorcery/common/starlight/transmission/StarlightTransmissionPacket;)Lnet/neoforged/neoforge/common/util/TriState;"))
    private static TriState dataEnergistics$transmutation(ServerLevel level, BlockPos pos, StarlightTransmissionPacket packet, Operation<TriState> original) {
        return DomainStasis.frozen(level, pos) ? TriState.FALSE : original.call(level, pos, packet);
    }
}
