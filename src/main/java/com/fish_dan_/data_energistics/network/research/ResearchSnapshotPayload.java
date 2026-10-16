package com.fish_dan_.data_energistics.network.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.common.research.synchronization.ResearchClientCatalog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** -1 is the login/reload catalog; nonnegative IDs target the current research menu only. */
public record ResearchSnapshotPayload(int containerId, CompoundTag snapshot) implements CustomPacketPayload {

    public static final Type<ResearchSnapshotPayload> TYPE = new Type<>(Data_Energistics.id("research_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeNbt(payload.snapshot);
            },
            buffer -> {
                int id = buffer.readVarInt();
                CompoundTag tag = buffer.readNbt();
                if (id < -1 || tag == null || tag.getList("nodes", 10).size() > 4096) throw new IllegalArgumentException("Invalid research snapshot");
                return new ResearchSnapshotPayload(id, tag);
            });

    @Override
    public Type<ResearchSnapshotPayload> type() {
        return TYPE;
    }

    public static void handle(ResearchSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.containerId == -1) {
                ResearchClientCatalog.receive(payload.snapshot, context.player().registryAccess());
            } else if (context.player().containerMenu instanceof ResearchStationMenu menu && menu.containerId == payload.containerId) {
                menu.receive(payload.snapshot);
            }
        });
    }
}
