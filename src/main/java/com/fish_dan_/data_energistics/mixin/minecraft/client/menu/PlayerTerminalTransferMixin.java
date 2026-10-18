package com.fish_dan_.data_energistics.mixin.minecraft.client.menu;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes inventory quick moves before vanilla prediction, preserving the host operation in inventory mode. */
@NullMarked
@Mixin(MultiPlayerGameMode.class)
public abstract class PlayerTerminalTransferMixin {

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$quickMoveToTerminal(int containerId, int slotId, int button, ClickType clickType, Player player, CallbackInfo callback) {
        if (TerminalOverlayClient.quickMoveToNetwork(containerId, slotId, button, clickType, player)) callback.cancel();
    }
}
