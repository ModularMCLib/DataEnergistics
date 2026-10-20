package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.magnet.TerminalPickBlockClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Pick-block availability is synchronized outside container sessions, including login and research changes. */
public record TerminalPickBlockStatePayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<TerminalPickBlockStatePayload> TYPE = new Type<>(Data_Energistics.id("terminal_pick_block_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalPickBlockStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TerminalPickBlockStatePayload::enabled, TerminalPickBlockStatePayload::new);

    @Override
    public Type<TerminalPickBlockStatePayload> type() {
        return TYPE;
    }

    public static void handle(TerminalPickBlockStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerminalPickBlockClient.enabled(payload.enabled()));
    }
}
