package com.fish_dan_.data_energistics.common.network.packets.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Orders native-menu transitions around the saved client screen without replacing its host menu with a copy.
 */
public record TerminalNavigationPayload(int hostContainerId, UUID sessionId,
                                        String phase)
        implements CustomPacketPayload {

    public static final Type<TerminalNavigationPayload> TYPE = new Type<>(Data_Energistics.id("terminal_navigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalNavigationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalNavigationPayload::hostContainerId,
            UUIDUtil.STREAM_CODEC, TerminalNavigationPayload::sessionId,
            ByteBufCodecs.stringUtf8(16), TerminalNavigationPayload::phase,
            TerminalNavigationPayload::new);

    public TerminalNavigationPayload {
        if (hostContainerId < 0 || !(phase.equals("suspend") || phase.equals("suspend_provider") || phase.equals("resume") || phase.equals("abort")))
            throw new IllegalArgumentException("Invalid terminal navigation phase");
    }

    @Override
    public Type<TerminalNavigationPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalNavigationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerminalOverlayClient.navigation(payload.hostContainerId(), payload.sessionId(), payload.phase()));
    }
}
