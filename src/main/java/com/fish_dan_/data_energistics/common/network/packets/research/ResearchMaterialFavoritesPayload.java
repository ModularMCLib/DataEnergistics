package com.fish_dan_.data_energistics.common.network.packets.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.client.crafting.research.ResearchMaterialFavorites;

import appeng.api.stacks.GenericStack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Server-confirmed unavailable materials; favorites can arrive after the research screen closes. */
public record ResearchMaterialFavoritesPayload(ObjectList<GenericStack> materials) implements CustomPacketPayload {

    public static final int MAX_MATERIALS = 64;
    public static final Type<ResearchMaterialFavoritesPayload> TYPE = new Type<>(Data_Energistics.id("research_material_favorites"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchMaterialFavoritesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.materials.size());
                payload.materials.forEach(stack -> GenericStack.STREAM_CODEC.encode(buffer, stack));
            },
            buffer -> {
                int count = buffer.readVarInt();
                if (count < 1 || count > MAX_MATERIALS) throw new IllegalArgumentException("Invalid research material favorites count");
                ObjectList<GenericStack> materials = new ObjectArrayList<>(count);
                for (int i = 0; i < count; i++) materials.add(GenericStack.STREAM_CODEC.decode(buffer));
                return new ResearchMaterialFavoritesPayload(materials);
            });

    public ResearchMaterialFavoritesPayload {
        if (materials.isEmpty() || materials.size() > MAX_MATERIALS) throw new IllegalArgumentException("Invalid research material favorites count");
        for (GenericStack material : materials) new ResearchInput(material.what(), material.amount());
        materials = new ObjectImmutableList<>(materials);
    }

    @Override
    public Type<ResearchMaterialFavoritesPayload> type() {
        return TYPE;
    }

    public static void handle(ResearchMaterialFavoritesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ResearchMaterialFavorites.receive(payload.materials, context.player()));
    }
}
