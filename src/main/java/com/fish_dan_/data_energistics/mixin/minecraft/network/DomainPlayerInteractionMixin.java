package com.fish_dan_.data_energistics.mixin.minecraft.network;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves chat, connection and close-container packets available while gameplay interactions are paused. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class DomainPlayerInteractionMixin {

    @Shadow
    public ServerPlayer player;
    @Shadow
    private boolean clientIsFloating;
    @Shadow
    private boolean clientVehicleIsFloating;

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$floating(CallbackInfo ci) {
        if (DomainStasis.frozen(player)) {
            clientIsFloating = false;
            clientVehicleIsFloating = false;
        }
    }

    @Inject(method = { "handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe", "handlePlayerAction", "handleUseItemOn", "handleUseItem", "handleInteract", "handlePlayerCommand", "handleMoveVehicle", "handlePlayerInput", "handleSetCarriedItem", "handlePickItem", "handlePlayerAbilities", "handleRenameItem", "handleSetBeaconPacket", "handleSelectTrade" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (DomainStasis.frozen(player)) ci.cancel();
    }
}
