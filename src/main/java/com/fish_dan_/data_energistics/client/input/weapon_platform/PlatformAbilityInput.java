package com.fish_dan_.data_energistics.client.input.weapon_platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformAbilityInput {

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        var motion = client.player.getData(DEAttachments.PLATFORM_MOTION);
        int active = PlatformClientState.snapshot.systems().active();
        motion.locked = (active & PlatformAbility.MOVEMENT_PROTECTION.flag) != 0;
        motion.maximumFlightSpeed = PlatformClientState.snapshot.systems().settings().maximumFlightSpeed();
        motion.flying = (active & PlatformAbility.FLIGHT.flag) != 0;
        motion.permanentFlight = motion.flying && PlatformClientState.snapshot.systems().settings().permanentFlight();
        boolean noclip = (active & PlatformAbility.NOCLIP.flag) != 0;
        motion.noclip = noclip || motion.noclip && !PlatformClientState.snapshot.present() && !client.player.level().noCollision(client.player, client.player.getBoundingBox().deflate(0.01));
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        var player = event.getEntity();
        if (!player.hasData(DEAttachments.PLATFORM_MOTION)) return;
        var motion = player.getData(DEAttachments.PLATFORM_MOTION);
        if (!motion.locked && !motion.flying) return;
        Minecraft client = Minecraft.getInstance();
        var keys = event.getInput();
        int input = client.screen == null ? (keys.up ? 1 : 0) | (keys.down ? 2 : 0) | (keys.left ? 4 : 0) | (keys.right ? 8 : 0) | (keys.jumping ? 16 : 0) | (keys.shiftKeyDown ? 32 : 0) | (client.options.keySprint.isDown() ? 64 : 0) : 0;
        motion.input = input;
        RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.INPUT, 0, input);
    }
}
