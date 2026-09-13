package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.item.connector.RemoteLinkClipboard;
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

import java.util.List;

public record ConnectorClipboardPayload(
                                        ConnectorClipboardAction operation, boolean offHand)
        implements CustomPacketPayload {

    public static final Type<ConnectorClipboardPayload> TYPE = new Type<>(
            Data_Energistics.id("data_distribution_connector_clipboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorClipboardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, payload -> payload.operation().ordinal(),
            ByteBufCodecs.BOOL, ConnectorClipboardPayload::offHand,
            (ordinal, offHand) -> new ConnectorClipboardPayload(
                    ConnectorClipboardAction.fromOrdinal(ordinal), offHand));

    @Override
    public Type<ConnectorClipboardPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectorClipboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!RemoteLinkConnectorItem.isConnectorStack(stack)) {
                return;
            }
            switch (payload.operation()) {
                case SELECT_ALL -> selectAll(player, stack);
                case COPY, CUT -> copy(player, stack, payload.operation() == ConnectorClipboardAction.CUT);
                case PASTE -> paste(player, stack);
            }
        });
    }

    private static void selectAll(Player player, ItemStack stack) {
        var endpoint = resolveSelectedEndpoint(player, stack);
        if (endpoint == null || endpoint.bindings().isEmpty()) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.no_links"), true);
            return;
        }
        var data = RemoteLinkConnectorItem.readData(stack);
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.selectAllLinks());
        player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.selected_all"), true);
    }

    private static void copy(Player player, ItemStack stack, boolean cut) {
        ConnectorEndpoint endpoint = resolveSelectedEndpoint(player, stack);
        if (endpoint == null) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.provider_invalid"), true);
            return;
        }
        var data = RemoteLinkConnectorItem.readData(stack);
        var all = endpoint.bindings();
        if (all.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.no_links"), true);
            return;
        }
        var bindings = data.allLinksSelected() ? all : List.of(all.get(Math.floorMod(data.selectedBindingIndex(), all.size())));
        if (bindings.size() > 256) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.too_many"), true);
            return;
        }
        RemoteLinkClipboard.write(stack, data, bindings);
        if (cut) {
            endpoint.replace(all.stream().filter(link -> !bindings.contains(link)).toList());
            stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.withSelectedBindingIndex(0));
        }
        player.displayClientMessage(Component.translatable(
                "item.data_energistics.data_distribution_connector.clipboard." + (cut ? "cut" : "copied"),
                bindings.size()), true);
    }

    private static void paste(Player player, ItemStack stack) {
        RemoteLinkConnectorData data = RemoteLinkConnectorItem.readData(stack);
        ConnectorEndpoint endpoint = resolveSelectedEndpoint(player, stack);
        if (endpoint == null) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.provider_invalid"), true);
            return;
        }
        RemoteLinkClipboard.Snapshot clipboard;
        try {
            clipboard = RemoteLinkClipboard.read(stack);
        } catch (IllegalArgumentException exception) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.invalid"), true);
            return;
        }
        if (clipboard.bindings().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.clipboard.empty"), true);
            return;
        }
        if (clipboard.type() != data.targetType() || !clipboard.dimensionId().isEmpty() && !clipboard.dimensionId().equals(data.providerDimensionId())) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.incompatible"), true);
            return;
        }
        if (clipboard.bindings().stream().anyMatch(link -> data.isInterface() ? link.slot() < 0 || link.slot() >= endpoint.slotCount() : link.slot() != -1)) {
            player.displayClientMessage(Component.translatable("item.data_energistics.data_distribution_connector.clipboard.slot_locked"), true);
            return;
        }
        int pasted = endpoint.replace(clipboard.bindings());
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.withSelectedBindingIndex(0));
        player.displayClientMessage(Component.translatable(
                "item.data_energistics.data_distribution_connector.clipboard.pasted", pasted), true);
    }

    private static ConnectorEndpoint resolveSelectedEndpoint(Player player, ItemStack stack) {
        RemoteLinkConnectorData data = RemoteLinkConnectorItem.readData(stack);
        return RemoteLinkConnectorItem.resolveEndpoint(player.level(), data);
    }
}
