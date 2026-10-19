package com.fish_dan_.data_energistics.client.terminal.magnet;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.network.terminal.TerminalPickBlockPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.NullMarked;

/** Only enabled player terminals consume a survival pick-block request missing from the inventory. */
@NullMarked
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class TerminalPickBlockClient {

    private static boolean enabled;

    private TerminalPickBlockClient() {}

    public static void enabled(boolean value) {
        enabled = value;
    }

    @SubscribeEvent
    public static void pickBlock(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (!enabled || !event.isPickBlock() || minecraft.screen != null || player == null || level == null ||
                player.isCreative() || player.isSpectator() || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        ItemStack stack = level.getBlockState(hit.getBlockPos()).getCloneItemStack(hit, level, hit.getBlockPos(), player);
        if (stack.isEmpty() || player.getInventory().findSlotMatchingItem(stack) >= 0) return;
        PacketDistributor.sendToServer(new TerminalPickBlockPayload(hit));
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
    }
}
