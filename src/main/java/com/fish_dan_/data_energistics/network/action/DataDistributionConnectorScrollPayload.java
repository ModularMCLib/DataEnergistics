package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorMode;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;
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

/** Server-validated Ctrl/Shift wheel actions for a held distribution connector. */
public record DataDistributionConnectorScrollPayload(boolean reverse, boolean offHand, boolean control, boolean shift)
        implements CustomPacketPayload {

    public static final Type<DataDistributionConnectorScrollPayload> TYPE = new Type<>(
            Data_Energistics.id("data_distribution_connector_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataDistributionConnectorScrollPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DataDistributionConnectorScrollPayload::reverse,
                    ByteBufCodecs.BOOL, DataDistributionConnectorScrollPayload::offHand,
                    ByteBufCodecs.BOOL, DataDistributionConnectorScrollPayload::control,
                    ByteBufCodecs.BOOL, DataDistributionConnectorScrollPayload::shift,
                    DataDistributionConnectorScrollPayload::new);

    @Override
    public Type<DataDistributionConnectorScrollPayload> type() {
        return TYPE;
    }

    public static void handle(DataDistributionConnectorScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!DataDistributionConnectorItem.isConnectorStack(stack)) {
                return;
            }
            DataDistributionConnectorItemData data = DataDistributionConnectorItem.readData(stack);
            AdaptivePatternProviderLogic logic = DataDistributionConnectorItem.resolveProviderLogic(player.level(), data);
            if (payload.control() && logic != null) {
                AdaptiveProviderConnectorMode mode = logic.connectorMode() == AdaptiveProviderConnectorMode.INPUT
                        ? AdaptiveProviderConnectorMode.PULL : AdaptiveProviderConnectorMode.INPUT;
                logic.setConnectorMode(mode);
                player.displayClientMessage(Component.translatable(
                        "item.data_energistics.data_distribution_connector.mode_changed", mode.name()), true);
            } else if (payload.shift() && logic != null) {
                int count = logic.connectorTargets().size();
                if (count > 0) {
                    int index = Math.floorMod(data.selectedBindingIndex() + (payload.reverse() ? -1 : 1), count);
                    stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.withSelectedBindingIndex(index));
                    player.displayClientMessage(Component.translatable(
                            "item.data_energistics.data_distribution_connector.selection_changed", (index + 1) + "/" + count), true);
                }
            }
        });
    }
}
