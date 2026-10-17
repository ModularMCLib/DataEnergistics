package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Snapshot for one acknowledged overlay generation; the client rejects other hosts and older responses.
 */
public record TerminalSnapshotPayload(int containerId, UUID sessionId, long sequence,
                                      CompoundTag snapshot) implements CustomPacketPayload {

    public static final Type<TerminalSnapshotPayload> TYPE = new Type<>(Data_Energistics.id("terminal_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.containerId());
                buffer.writeUUID(value.sessionId());
                buffer.writeVarLong(value.sequence());
                buffer.writeNbt(value.snapshot());
            },
            buffer -> {
                int id = buffer.readVarInt();
                UUID session = buffer.readUUID();
                long sequence = buffer.readVarLong();
                CompoundTag tag = buffer.readNbt();
                if (id < 0 || sequence < 0 || tag == null || tag.getLong("storage_revision") < 0 ||
                        tag.getList("storage", 10).size() + tag.getList("storage_removed", 10).size() > 512)
                    throw new IllegalArgumentException("Invalid terminal snapshot");
                return new TerminalSnapshotPayload(id, session, sequence, tag);
            });

    @Override
    public Type<TerminalSnapshotPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerminalOverlayClient.receive(payload));
    }
}
