package com.fish_dan_.data_energistics.network.sanctifier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.sanctifier.SanctifierCarryState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-to-client visual presence only; this payload grants no inventory or combat actions. */
public record SanctifierCarryPayload(int entityId, SanctifierCarryState state) implements CustomPacketPayload {

    public static final Type<SanctifierCarryPayload> TYPE = new Type<>(Data_Energistics.id("sanctifier_carry"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SanctifierCarryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SanctifierCarryPayload::entityId,
            ByteBufCodecs.VAR_INT.map(SanctifierCarryState::fromFlags, SanctifierCarryState::flags),
            SanctifierCarryPayload::state, SanctifierCarryPayload::new);

    public SanctifierCarryPayload {
        if (entityId < 0) throw new IllegalArgumentException("Negative sanctifier carry entity ID");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SanctifierCarryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof Player player) {
                payload.state().accept(player);
            }
        });
    }
}
