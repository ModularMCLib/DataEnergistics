package com.fish_dan_.data_energistics.common.network.packets.sanctifier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCarryState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Small S2C cosmetic snapshot; UUID prevents applying a delayed packet to a reused entity id. */
public record SanctifierCarryPayload(int entityId, UUID playerId, int flags) implements CustomPacketPayload {

    public static final Type<SanctifierCarryPayload> TYPE = new Type<>(Data_Energistics.id("sanctifier_carry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SanctifierCarryPayload> STREAM_CODEC = CustomPacketPayload.codec(SanctifierCarryPayload::write, SanctifierCarryPayload::new);

    public SanctifierCarryPayload {
        if (entityId < 0) throw new IllegalArgumentException("Invalid sanctifier carrier entity id: " + entityId);
        SanctifierCarryState.fromFlags(flags);
    }

    private SanctifierCarryPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(), buffer.readUnsignedByte());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUUID(playerId);
        buffer.writeByte(flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SanctifierCarryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId) instanceof Player player && player.getUUID().equals(payload.playerId)) {
                SanctifierCarryState.fromFlags(payload.flags).accept(player);
            }
        });
    }
}
