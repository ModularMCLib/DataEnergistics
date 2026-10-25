package com.fish_dan_.data_energistics.mixin.minecraft.network;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlatformMovementPacketMixin {

    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleMovePlayer")
    private void dataEnergistics$inputMovement(ServerboundMovePlayerPacket packet, Operation<Void> original) {
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
        if (DomainStasis.frozen(player)) return;
        if (!PlatformMotion.locked(player)) {
            original.call(packet);
            return;
        }
        var state = player.getData(DEAttachments.PLATFORM_MOTION);
        Vec3 requested = new Vec3(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())).subtract(player.position());
        Vec3 expected = PlatformMotion.inputMotion(player, player.tickCount - state.inputAt <= 5 ? state.input : 0);
        if (requested.lengthSqr() > 1e-6 && (expected.lengthSqr() == 0 || requested.lengthSqr() > expected.lengthSqr() * 1.21 + 1e-4 || requested.dot(expected) <= 0)) return;
        state.permits++;
        try {
            original.call(packet);
        } finally {
            state.permits--;
        }
    }

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$blockExternalTeleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> relative, CallbackInfo ci) {
        if (PlatformMotion.blocked(player)) ci.cancel();
    }
}
