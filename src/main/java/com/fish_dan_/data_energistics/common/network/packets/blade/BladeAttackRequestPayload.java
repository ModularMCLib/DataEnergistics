package com.fish_dan_.data_energistics.common.network.packets.blade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeAttack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only the input and aimed entity are sent. The server selects the swing and resolves its hit. */
public record BladeAttackRequestPayload(boolean heavy, int targetId) implements CustomPacketPayload {

    public static final int NO_TARGET = -1;
    public static final Type<BladeAttackRequestPayload> TYPE = new Type<>(Data_Energistics.id("blade_attack_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BladeAttackRequestPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BladeAttackRequestPayload::write, BladeAttackRequestPayload::new);

    private BladeAttackRequestPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(heavy);
        buf.writeVarInt(targetId);
    }

    @Override
    public Type<BladeAttackRequestPayload> type() {
        return TYPE;
    }

    public static void handle(BladeAttackRequestPayload payload, IPayloadContext context) {
        BladeAttack.request((ServerPlayer) context.player(), payload.heavy, payload.targetId);
    }
}
