package com.fish_dan_.data_energistics.item.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DataDistributionConnectorItemData(
                                                String dimensionId,
                                                long towerPos,
                                                boolean selected,
                                                DataDistributionConnectorTargetType targetType,
                                                String providerDimensionId,
                                                long providerPos,
                                                int providerSide) {

    public static final DataDistributionConnectorItemData EMPTY = new DataDistributionConnectorItemData(
            "", 0L, false, DataDistributionConnectorTargetType.TOWER, "", 0L, -1);

    public static final Codec<DataDistributionConnectorItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension_id", "").forGetter(DataDistributionConnectorItemData::dimensionId),
            Codec.LONG.optionalFieldOf("tower_pos", 0L).forGetter(DataDistributionConnectorItemData::towerPos),
            Codec.BOOL.optionalFieldOf("selected", false).forGetter(DataDistributionConnectorItemData::selected),
            Codec.STRING.optionalFieldOf("target_type", DataDistributionConnectorTargetType.TOWER.name())
                    .xmap(DataDistributionConnectorTargetType::valueOf, DataDistributionConnectorTargetType::name)
                    .forGetter(DataDistributionConnectorItemData::targetType),
            Codec.STRING.optionalFieldOf("provider_dimension_id", "")
                    .forGetter(DataDistributionConnectorItemData::providerDimensionId),
            Codec.LONG.optionalFieldOf("provider_pos", 0L).forGetter(DataDistributionConnectorItemData::providerPos),
            Codec.INT.optionalFieldOf("provider_side", -1).forGetter(DataDistributionConnectorItemData::providerSide))
            .apply(instance, DataDistributionConnectorItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DataDistributionConnectorItemData> STREAM_CODEC = StreamCodec.of(
            DataDistributionConnectorItemData::encode,
            DataDistributionConnectorItemData::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, DataDistributionConnectorItemData data) {
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.dimensionId());
        ByteBufCodecs.VAR_LONG.encode(buffer, data.towerPos());
        ByteBufCodecs.BOOL.encode(buffer, data.selected());
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.targetType().name());
        ByteBufCodecs.STRING_UTF8.encode(buffer, data.providerDimensionId());
        ByteBufCodecs.VAR_LONG.encode(buffer, data.providerPos());
        ByteBufCodecs.VAR_INT.encode(buffer, data.providerSide());
    }

    private static DataDistributionConnectorItemData decode(RegistryFriendlyByteBuf buffer) {
        return new DataDistributionConnectorItemData(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                DataDistributionConnectorTargetType.valueOf(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer));
    }

    public DataDistributionConnectorItemData {
        dimensionId = dimensionId == null ? "" : dimensionId;
        targetType = targetType == null ? DataDistributionConnectorTargetType.TOWER : targetType;
        providerDimensionId = providerDimensionId == null ? "" : providerDimensionId;
        if (providerSide < -1 || providerSide > 5) {
            throw new IllegalArgumentException("Invalid adaptive provider binding side: " + providerSide);
        }
    }

    public boolean hasSelection() {
        return this.selected && (this.targetType == DataDistributionConnectorTargetType.ADAPTIVE_PROVIDER ? !this.providerDimensionId.isEmpty() : !this.dimensionId.isEmpty());
    }

    public BlockPos getTowerPos() {
        return BlockPos.of(this.towerPos);
    }

    public DataDistributionConnectorItemData withTower(String dimensionId, BlockPos towerPos) {
        return new DataDistributionConnectorItemData(
                dimensionId, towerPos.asLong(), true, DataDistributionConnectorTargetType.TOWER, "", 0L, -1);
    }

    public DataDistributionConnectorItemData withAdaptiveProvider(
                                                                  String dimensionId, BlockPos providerPos, int providerSide) {
        return new DataDistributionConnectorItemData(
                this.dimensionId,
                this.towerPos,
                true,
                DataDistributionConnectorTargetType.ADAPTIVE_PROVIDER,
                dimensionId,
                providerPos.asLong(),
                providerSide);
    }

    public DataDistributionConnectorItemData clear() {
        return EMPTY;
    }

    public BlockPos getProviderPos() {
        return BlockPos.of(this.providerPos);
    }

    public boolean isAdaptiveProvider() {
        return this.targetType == DataDistributionConnectorTargetType.ADAPTIVE_PROVIDER;
    }
}
