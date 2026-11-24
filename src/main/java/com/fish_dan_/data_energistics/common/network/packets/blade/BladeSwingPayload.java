package com.fish_dan_.data_energistics.common.network.packets.blade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeSwing;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeWeapon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record BladeSwingPayload(int entityId, UUID playerId, BladeWeapon weapon, BladeSwing swing) implements CustomPacketPayload {

    public static final Type<BladeSwingPayload> TYPE = new Type<>(Data_Energistics.id("blade_swing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BladeSwingPayload> STREAM_CODEC = CustomPacketPayload.codec(BladeSwingPayload::write, BladeSwingPayload::new);

    private BladeSwingPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUUID(), buf.readEnum(BladeWeapon.class), buf.readEnum(BladeSwing.class));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(playerId);
        buf.writeEnum(weapon);
        buf.writeEnum(swing);
    }

    @Override
    public Type<BladeSwingPayload> type() {
        return TYPE;
    }

    public static void handle(BladeSwingPayload payload, IPayloadContext context) {
        if (context.player().level().getEntity(payload.entityId) instanceof Player player && player.getUUID().equals(payload.playerId)) {
            BladeAttackPlayback.begin(player, payload.weapon, payload.swing);
        }
    }
}
