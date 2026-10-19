package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The server resolves the looked-at block's item; the client never supplies an inventory stack. */
public record TerminalPickBlockPayload(BlockHitResult hit) implements CustomPacketPayload {

    public static final Type<TerminalPickBlockPayload> TYPE = new Type<>(Data_Energistics.id("terminal_pick_block"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalPickBlockPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeBlockHitResult(value.hit()), buffer -> new TerminalPickBlockPayload(buffer.readBlockHitResult()));

    @Override
    public Type<TerminalPickBlockPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalPickBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) TerminalSessions.pickBlock(player, payload.hit());
        });
    }
}
