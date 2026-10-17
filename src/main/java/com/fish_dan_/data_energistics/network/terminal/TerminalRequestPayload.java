package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Per-open identity and sequence keep independent overlay mutations separate from the host menu protocol.
 */
public record TerminalRequestPayload(int containerId, UUID sessionId, long sequence, String action,
                                     CompoundTag data) implements CustomPacketPayload {

    public static final Type<TerminalRequestPayload> TYPE = new Type<>(Data_Energistics.id("terminal_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.containerId());
                buffer.writeUUID(value.sessionId());
                buffer.writeVarLong(value.sequence());
                buffer.writeUtf(value.action(), 64);
                buffer.writeNbt(value.data());
            }, buffer -> {
                int container = buffer.readVarInt();
                UUID session = buffer.readUUID();
                long sequence = buffer.readVarLong();
                String action = buffer.readUtf(64);
                CompoundTag data = buffer.readNbt();
                if (data == null) throw new IllegalArgumentException("Missing terminal request data");
                return new TerminalRequestPayload(container, session, sequence, action, data);
            });

    public TerminalRequestPayload {
        if (containerId < 0 || sequence < 0 || action.isBlank() || action.length() > 64 || data.size() > 32)
            throw new IllegalArgumentException("Invalid terminal request envelope");
    }

    @Override
    public Type<TerminalRequestPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) TerminalSessions.handle(player, payload);
        });
    }
}
