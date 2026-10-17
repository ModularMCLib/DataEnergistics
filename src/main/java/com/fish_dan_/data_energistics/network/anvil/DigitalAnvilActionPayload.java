package com.fish_dan_.data_energistics.network.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.machine.anvil.MeDigitalAnvilMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded text and action requests; output, research entitlement and all prices remain server-owned. */
public record DigitalAnvilActionPayload(int containerId, String action, String value) implements CustomPacketPayload {

    public static final Type<DigitalAnvilActionPayload> TYPE = new Type<>(Data_Energistics.id("digital_anvil_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalAnvilActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeUtf(payload.action, 16);
                buffer.writeUtf(payload.value, 50);
            }, buffer -> new DigitalAnvilActionPayload(buffer.readVarInt(), buffer.readUtf(16), buffer.readUtf(50)));

    public DigitalAnvilActionPayload {
        if (containerId < 0 || action.length() > 16 || value.length() > 50) throw new IllegalArgumentException("Invalid digital anvil action");
    }

    @Override
    public Type<DigitalAnvilActionPayload> type() {
        return TYPE;
    }

    public static void handle(DigitalAnvilActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof MeDigitalAnvilMenu menu) ||
                    menu.containerId != payload.containerId || !menu.stillValid(player) ||
                    !player.mayInteract(player.serverLevel(), menu.host().getBlockPos()))
                return;
            try {
                menu.action(payload.action, payload.value);
            } catch (RuntimeException error) {
                menu.host().fault(error);
            }
        });
    }
}
