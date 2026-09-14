package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.item.connector.RemoteLinkConnectorData;
import com.fish_dan_.data_energistics.item.connector.RemoteLinkConnectorItem;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

/** Server-validated Ctrl/Shift wheel actions for a held distribution connector. */
public record ConnectorScrollPayload(boolean reverse, boolean offHand, boolean control, boolean shift)
        implements CustomPacketPayload {

    public static final Type<ConnectorScrollPayload> TYPE = new Type<>(
            Data_Energistics.id("data_distribution_connector_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ConnectorScrollPayload::reverse,
            ByteBufCodecs.BOOL, ConnectorScrollPayload::offHand,
            ByteBufCodecs.BOOL, ConnectorScrollPayload::control,
            ByteBufCodecs.BOOL, ConnectorScrollPayload::shift,
            ConnectorScrollPayload::new);

    @Override
    public Type<ConnectorScrollPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!RemoteLinkConnectorItem.isConnectorStack(stack)) {
                return;
            }
            RemoteLinkConnectorData data = RemoteLinkConnectorItem.readData(stack);
            ConnectorEndpoint endpoint = RemoteLinkConnectorItem.resolveEndpoint(player.level(), data);
            if (payload.control() && payload.shift() && endpoint != null) {
                if (data.isInterface()) {
                    int slot = Math.floorMod(data.selectedSlot() + (payload.reverse() ? -1 : 1), endpoint.slotCount());
                    stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.withSelectedSlot(slot));
                    player.displayClientMessage(Component.translatable(
                            "item.data_energistics.data_distribution_connector.slot_selected", slot + 1), true);
                }
            } else if (payload.control() && endpoint != null) {
                var modes = ConnectorMode.values();
                var mode = modes[Math.floorMod(endpoint.mode().ordinal() + (payload.reverse() ? -1 : 1), modes.length)];
                endpoint.setMode(mode);
                player.displayClientMessage(Component.translatable(
                        "item.data_energistics.data_distribution_connector.mode_changed",
                        Component.translatable("item.data_energistics.data_distribution_connector.mode." + mode.name().toLowerCase(Locale.ROOT))), true);
            } else if (payload.shift() && endpoint != null) {
                int count = endpoint.bindingsFast().size();
                if (count > 0) {
                    int index = Math.floorMod(data.selectedBindingIndex() + (payload.reverse() ? -1 : 1), count);
                    var selection = data.withSelectedBindingIndex(index);
                    if (data.isInterface()) {
                        selection = selection.withSelectedSlot(endpoint.bindingsFast().get(index).slot());
                    }
                    stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), selection);
                    player.displayClientMessage(Component.translatable(
                            "item.data_energistics.data_distribution_connector.selection_changed", (index + 1) + "/" + count), true);
                }
            } else if (payload.control()) {
                player.displayClientMessage(Component.translatable(
                        "item.data_energistics.data_distribution_connector.mode_unavailable"), true);
            }
        });
    }
}
