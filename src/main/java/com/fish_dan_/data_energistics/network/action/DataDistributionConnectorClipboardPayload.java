package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorClipboard;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DataDistributionConnectorClipboardPayload(
        DataDistributionConnectorClipboardOperation operation, boolean offHand) implements CustomPacketPayload {

    public static final Type<DataDistributionConnectorClipboardPayload> TYPE = new Type<>(
            Data_Energistics.id("data_distribution_connector_clipboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataDistributionConnectorClipboardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, payload -> payload.operation().ordinal(),
                    ByteBufCodecs.BOOL, DataDistributionConnectorClipboardPayload::offHand,
                    (ordinal, offHand) -> new DataDistributionConnectorClipboardPayload(
                            DataDistributionConnectorClipboardOperation.fromOrdinal(ordinal), offHand));

    @Override
    public Type<DataDistributionConnectorClipboardPayload> type() {
        return TYPE;
    }

    public static void handle(DataDistributionConnectorClipboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!DataDistributionConnectorItem.isConnectorStack(stack)) {
                return;
            }
            switch (payload.operation()) {
                case SELECT_ALL -> player.displayClientMessage(Component.translatable(
                        "item.data_energistics.data_distribution_connector.clipboard.selected_all"), true);
                case COPY, CUT -> copy(player, stack, payload.operation() == DataDistributionConnectorClipboardOperation.CUT);
                case PASTE -> paste(player, stack);
            }
        });
    }

    private static void copy(Player player, ItemStack stack, boolean cut) {
        AdaptivePatternProviderLogic logic = resolveSelectedProvider(player, stack);
        if (logic == null) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.provider_invalid"), true);
            return;
        }
        var bindings = logic.adaptiveConnectorBindings();
        DataDistributionConnectorClipboard.write(stack, bindings);
        if (cut) {
            logic.clearConnectorTargets();
        }
        player.displayClientMessage(Component.translatable(
                "item.data_energistics.data_distribution_connector.clipboard." + (cut ? "cut" : "copied"),
                bindings.size()), true);
    }

    private static void paste(Player player, ItemStack stack) {
        DataDistributionConnectorItemData data = DataDistributionConnectorItem.readData(stack);
        AdaptivePatternProviderLogic logic = resolveSelectedProvider(player, stack);
        var clipboard = DataDistributionConnectorClipboard.read(stack);
        if (logic == null || !data.isAdaptiveProvider()) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.provider_invalid"), true);
            return;
        }
        if (clipboard.bindings().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "item.data_energistics.data_distribution_connector.clipboard.empty"), true);
            return;
        }
        int pasted = logic.replaceConnectorTargets(clipboard.bindings());
        player.displayClientMessage(Component.translatable(
                "item.data_energistics.data_distribution_connector.clipboard.pasted", pasted), true);
    }

    private static AdaptivePatternProviderLogic resolveSelectedProvider(Player player, ItemStack stack) {
        DataDistributionConnectorItemData data = DataDistributionConnectorItem.readData(stack);
        if (!data.isAdaptiveProvider()) {
            return null;
        }
        return DataDistributionConnectorItem.resolveProviderLogic(player.level(), data);
    }
}
