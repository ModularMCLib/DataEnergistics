package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Orders native-menu transitions around the saved client screen without replacing its host menu with a copy.
 */
public record TerminalCpuNavigationPayload(int hostContainerId, UUID sessionId,
                                           String phase)
        implements CustomPacketPayload {

    public static final Type<TerminalCpuNavigationPayload> TYPE = new Type<>(Data_Energistics.id("terminal_cpu_navigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCpuNavigationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.hostContainerId());
                buffer.writeUUID(value.sessionId());
                buffer.writeUtf(value.phase(), 16);
            },
            buffer -> new TerminalCpuNavigationPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(16)));

    public TerminalCpuNavigationPayload {
        if (hostContainerId < 0 || !(phase.equals("suspend") || phase.equals("resume") || phase.equals("abort")))
            throw new IllegalArgumentException("Invalid terminal navigation phase");
    }

    @Override
    public Type<TerminalCpuNavigationPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalCpuNavigationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerminalOverlayClient.navigation(payload.hostContainerId(), payload.sessionId(), payload.phase()));
    }
}
