package com.fish_dan_.data_energistics.item.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RemoteLinkConnectorData(
                                      String dimensionId,
                                      long towerPos,
                                      boolean selected,
                                      ConnectorHostType targetType,
                                      String providerDimensionId,
                                      long providerPos,
                                      int providerSide,
                                      int selectedBindingIndex,
                                      int selectedSlot,
                                      boolean allLinksSelected) {

    public static final RemoteLinkConnectorData EMPTY = new RemoteLinkConnectorData(
            "", 0L, false, ConnectorHostType.TOWER, "", 0L, -1, 0, 0, false);

    public static final Codec<RemoteLinkConnectorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension_id", "").forGetter(RemoteLinkConnectorData::dimensionId),
            Codec.LONG.optionalFieldOf("tower_pos", 0L).forGetter(RemoteLinkConnectorData::towerPos),
            Codec.BOOL.optionalFieldOf("selected", false).forGetter(RemoteLinkConnectorData::selected),
            Codec.STRING.optionalFieldOf("target_type", ConnectorHostType.TOWER.name())
                    .xmap(ConnectorHostType::valueOf, ConnectorHostType::name)
                    .forGetter(RemoteLinkConnectorData::targetType),
            Codec.STRING.optionalFieldOf("provider_dimension_id", "")
                    .forGetter(RemoteLinkConnectorData::providerDimensionId),
            Codec.LONG.optionalFieldOf("provider_pos", 0L).forGetter(RemoteLinkConnectorData::providerPos),
            Codec.INT.optionalFieldOf("provider_side", -1).forGetter(RemoteLinkConnectorData::providerSide),
            Codec.INT.optionalFieldOf("selected_binding_index", 0).forGetter(RemoteLinkConnectorData::selectedBindingIndex),
            Codec.INT.optionalFieldOf("selected_slot", 0).forGetter(RemoteLinkConnectorData::selectedSlot),
            Codec.BOOL.optionalFieldOf("all_links_selected", false).forGetter(RemoteLinkConnectorData::allLinksSelected))
            .apply(instance, RemoteLinkConnectorData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteLinkConnectorData> STREAM_CODEC = StreamCodec.of(
            RemoteLinkConnectorData::encode,
            RemoteLinkConnectorData::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, RemoteLinkConnectorData data) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.dimensionId());
        ByteBufCodecs.VAR_LONG.encode(buffer, data.towerPos());
        ByteBufCodecs.BOOL.encode(buffer, data.selected());
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.targetType().name());
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.providerDimensionId());
        ByteBufCodecs.VAR_LONG.encode(buffer, data.providerPos());
        ByteBufCodecs.VAR_INT.encode(buffer, data.providerSide());
        ByteBufCodecs.VAR_INT.encode(buffer, data.selectedBindingIndex());
        buffer.writeVarInt(data.selectedSlot());
        buffer.writeBoolean(data.allLinksSelected());
    }

    private static RemoteLinkConnectorData decode(RegistryFriendlyByteBuf buffer) {
        return new RemoteLinkConnectorData(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                ConnectorHostType.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    public RemoteLinkConnectorData {
        if (providerSide < -1 || providerSide > 5) {
            throw new IllegalArgumentException("Invalid connector host side: " + providerSide);
        }
        if (selectedBindingIndex < 0) {
            throw new IllegalArgumentException("Connector binding index must not be negative");
        }
        if (selectedSlot < 0) {
            throw new IllegalArgumentException("Connector interface slot must not be negative");
        }
    }

    public boolean hasSelection() {
        return this.selected && (this.targetType != ConnectorHostType.TOWER ? !this.providerDimensionId.isEmpty() : !this.dimensionId.isEmpty());
    }

    public BlockPos getTowerPos() {
        return BlockPos.of(this.towerPos);
    }

    public RemoteLinkConnectorData withTower(String dimensionId, BlockPos towerPos) {
        return new RemoteLinkConnectorData(
                dimensionId, towerPos.asLong(), true, ConnectorHostType.TOWER, "", 0L, -1, 0, 0, false);
    }

    public RemoteLinkConnectorData withAdaptiveProvider(
                                                        String dimensionId, BlockPos providerPos, int providerSide) {
        return new RemoteLinkConnectorData(
                this.dimensionId,
                this.towerPos,
                true,
                ConnectorHostType.ADAPTIVE_PROVIDER,
                dimensionId,
                providerPos.asLong(),
                providerSide,
                0, 0, false);
    }

    public RemoteLinkConnectorData clear() {
        return EMPTY;
    }

    public BlockPos getProviderPos() {
        return BlockPos.of(this.providerPos);
    }

    public boolean isAdaptiveProvider() {
        return this.targetType == ConnectorHostType.ADAPTIVE_PROVIDER;
    }

    public boolean isInterface() {
        return this.targetType == ConnectorHostType.EXTREME_INTERFACE;
    }

    public RemoteLinkConnectorData withInterface(String dimensionId, BlockPos position, int side) {
        return new RemoteLinkConnectorData("", 0L, true, ConnectorHostType.EXTREME_INTERFACE,
                dimensionId, position.asLong(), side, 0, 0, false);
    }

    public RemoteLinkConnectorData withSelectedSlot(int slot) {
        return new RemoteLinkConnectorData(dimensionId, towerPos, selected, targetType,
                providerDimensionId, providerPos, providerSide, selectedBindingIndex, slot, allLinksSelected);
    }

    public RemoteLinkConnectorData selectAllLinks() {
        return new RemoteLinkConnectorData(dimensionId, towerPos, selected, targetType,
                providerDimensionId, providerPos, providerSide, selectedBindingIndex, selectedSlot, true);
    }

    public RemoteLinkConnectorData withSelectedBindingIndex(int index) {
        return new RemoteLinkConnectorData(
                this.dimensionId, this.towerPos, this.selected, this.targetType,
                this.providerDimensionId, this.providerPos, this.providerSide, index, selectedSlot, false);
    }
}
