package com.fish_dan_.data_energistics.common.network.packets.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.menu.machine.anvil.MeDigitalAnvilMenu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DigitalAnvilSnapshotPayload(int containerId, CompoundTag snapshot) implements CustomPacketPayload {

    public static final Type<DigitalAnvilSnapshotPayload> TYPE = new Type<>(Data_Energistics.id("digital_anvil_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalAnvilSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeNbt(payload.snapshot);
            }, buffer -> {
                int id = buffer.readVarInt();
                CompoundTag data = buffer.readNbt();
                if (id < 0 || data == null) throw new IllegalArgumentException("Invalid digital anvil snapshot");
                return new DigitalAnvilSnapshotPayload(id, data);
            });

    @Override
    public Type<DigitalAnvilSnapshotPayload> type() {
        return TYPE;
    }

    public static void handle(DigitalAnvilSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MeDigitalAnvilMenu menu && menu.containerId == payload.containerId) menu.receive(payload.snapshot);
        });
    }
}
