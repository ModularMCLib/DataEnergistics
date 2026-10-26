package com.fish_dan_.data_energistics.common.network.packets.input;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMapping;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** GTM-style input delta; resolves the complete packet before invoking any server callback. */
public record KeyStatePayload(Int2BooleanMap states) implements CustomPacketPayload {

    private static final int MAX_CHANGES = 256;
    public static final Type<KeyStatePayload> TYPE = new Type<>(Data_Energistics.id("key_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyStatePayload> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public KeyStatePayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_CHANGES) throw new DecoderException("Invalid key-state count: " + count);
            Int2BooleanMap states = new Int2BooleanOpenHashMap(count);
            for (int index = 0; index < count; index++) {
                int id = buffer.readVarInt();
                if (id < 0 || states.containsKey(id)) throw new DecoderException("Invalid or duplicate key id: " + id);
                states.put(id, buffer.readBoolean());
            }
            return new KeyStatePayload(states);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, KeyStatePayload payload) {
            buffer.writeVarInt(payload.states.size());
            for (var entry : payload.states.int2BooleanEntrySet()) {
                buffer.writeVarInt(entry.getIntKey());
                buffer.writeBoolean(entry.getBooleanValue());
            }
        }
    };

    public KeyStatePayload {
        if (states.isEmpty() || states.size() > MAX_CHANGES) throw new IllegalArgumentException("Invalid key-state count: " + states.size());
        states = Int2BooleanMaps.unmodifiable(new Int2BooleanOpenHashMap(states));
    }

    @Override
    public Type<KeyStatePayload> type() {
        return TYPE;
    }

    public static void handle(KeyStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ObjectList<KeyUpdate> resolved = new ObjectArrayList<>(payload.states.size());
            for (var entry : payload.states.int2BooleanEntrySet()) {
                SyncedKeyMapping mapping = SyncedKeyMapping.getFromSyncId(entry.getIntKey());
                if (mapping == null) {
                    Data_Energistics.LOGGER.warn("Rejecting unknown key id {} from {}", entry.getIntKey(), player.getGameProfile().getName());
                    return;
                }
                resolved.add(new KeyUpdate(mapping, entry.getBooleanValue()));
            }
            for (KeyUpdate update : resolved) update.mapping.serverActivate(update.down, player);
        });
    }

    private record KeyUpdate(SyncedKeyMapping mapping, boolean down) {}
}
