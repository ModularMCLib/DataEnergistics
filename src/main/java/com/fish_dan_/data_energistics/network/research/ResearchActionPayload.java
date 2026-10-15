package com.fish_dan_.data_energistics.network.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.research.definition.ResearchDefinitions;
import com.fish_dan_.data_energistics.research.progression.ResearchExecution;
import com.fish_dan_.data_energistics.research.team.ResearchTeams;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client supplies IDs and revisions only; costs, prerequisites and owners are resolved on the server. */
public record ResearchActionPayload(int containerId, long revision, long definitionsRevision, String action, String argument) implements CustomPacketPayload {

    public static final Type<ResearchActionPayload> TYPE = new Type<>(Data_Energistics.id("research_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.containerId);
                buffer.writeLong(value.revision);
                buffer.writeLong(value.definitionsRevision);
                buffer.writeUtf(value.action, 64);
                buffer.writeUtf(value.argument, 256);
            },
            buffer -> new ResearchActionPayload(buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readUtf(64), buffer.readUtf(256)));

    public ResearchActionPayload {
        if (containerId < 0 || action.length() > 64 || argument.length() > 256) throw new IllegalArgumentException("Invalid research action envelope");
    }

    @Override
    public Type<ResearchActionPayload> type() {
        return TYPE;
    }

    public static void handle(ResearchActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof ResearchStationMenu menu) ||
                    menu.containerId != payload.containerId || !menu.stillValid(player) ||
                    !player.mayInteract(player.serverLevel(), menu.host().getBlockPos()))
                return;
            try {
                if (payload.action.startsWith("team_")) {
                    player.sendSystemMessage(ResearchTeams.execute(player, payload.action.substring(5), payload.argument));
                } else if (payload.revision != menu.host().state().revision || payload.definitionsRevision != ResearchDefinitions.revision()) {
                    player.sendSystemMessage(Component.translatable("research.data_energistics.stale"));
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(payload.argument);
                    if (id == null) throw new IllegalArgumentException("Invalid research node ID");
                    player.sendSystemMessage(ResearchExecution.action(menu.host(), player, payload.action, id));
                }
                menu.sendSnapshot(true);
            } catch (RuntimeException error) {
                Data_Energistics.LOGGER.error("Research action {} failed for station {} and player {}", payload.action, menu.host().stationId(), player.getUUID(), error);
                player.sendSystemMessage(Component.translatable("research.data_energistics.faulted"));
            }
        });
    }
}
