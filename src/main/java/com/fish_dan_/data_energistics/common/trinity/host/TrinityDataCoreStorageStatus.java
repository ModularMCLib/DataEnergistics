package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.util.FormattingUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigInteger;

/** Client-facing storage text and capacity pixels, assembled from authoritative exact amounts on the server. */
public record TrinityDataCoreStorageStatus(int typeCount,
                                           int typeCapacity,
                                           String itemAmount,
                                           String fluidAmount,
                                           String otherKeyAmount,
                                           String amountCapacity,
                                           String totalAmount,
                                           boolean unlimited,
                                           TrinityStorageCapacitySegments segments) {

    public static final Codec<TrinityDataCoreStorageStatus> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("type_count").forGetter(TrinityDataCoreStorageStatus::typeCount),
                    Codec.INT.fieldOf("type_capacity").forGetter(TrinityDataCoreStorageStatus::typeCapacity),
                    Codec.STRING.fieldOf("item_amount").forGetter(TrinityDataCoreStorageStatus::itemAmount),
                    Codec.STRING.fieldOf("fluid_amount").forGetter(TrinityDataCoreStorageStatus::fluidAmount),
                    Codec.STRING.fieldOf("other_key_amount").forGetter(TrinityDataCoreStorageStatus::otherKeyAmount),
                    Codec.STRING.fieldOf("amount_capacity").forGetter(TrinityDataCoreStorageStatus::amountCapacity),
                    Codec.STRING.fieldOf("total_amount").forGetter(TrinityDataCoreStorageStatus::totalAmount),
                    Codec.BOOL.fieldOf("unlimited").forGetter(TrinityDataCoreStorageStatus::unlimited),
                    TrinityStorageCapacitySegments.CODEC.fieldOf("segments").forGetter(TrinityDataCoreStorageStatus::segments))
            .apply(instance, TrinityDataCoreStorageStatus::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreStorageStatus> STREAM_CODEC = StreamCodec.of(TrinityDataCoreStorageStatus::encode, TrinityDataCoreStorageStatus::decode);

    public static final TrinityDataCoreStorageStatus EMPTY = new TrinityDataCoreStorageStatus(
            0, 0, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, false);

    public TrinityDataCoreStorageStatus {
        if (typeCount < 0 || typeCapacity < 0) {
            throw new IllegalArgumentException("Storage type counts must not be negative");
        }
    }

    /** Formats exact server amounts once; aggregate fluid capacity remains expressed in native units. */
    public TrinityDataCoreStorageStatus(int typeCount, int typeCapacity,
                                        BigInteger itemAmount, BigInteger fluidAmount, BigInteger otherKeyAmount,
                                        BigInteger amountCapacity, boolean unlimited) {
        this(typeCount, typeCapacity,
                formatAmount(itemAmount), formatAmount(fluidAmount), formatAmount(otherKeyAmount),
                formatAmount(amountCapacity), FormattingUtils.format(itemAmount.add(fluidAmount).add(otherKeyAmount)),
                unlimited, TrinityStorageCapacitySegments.calculate(itemAmount, fluidAmount, otherKeyAmount, amountCapacity, unlimited));
    }

    private static String formatAmount(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Storage amounts must not be negative");
        }
        return FormattingUtils.format(value);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityDataCoreStorageStatus value) {
        buffer.writeVarInt(value.typeCount);
        buffer.writeVarInt(value.typeCapacity);
        buffer.writeUtf(value.itemAmount);
        buffer.writeUtf(value.fluidAmount);
        buffer.writeUtf(value.otherKeyAmount);
        buffer.writeUtf(value.amountCapacity);
        buffer.writeUtf(value.totalAmount);
        buffer.writeBoolean(value.unlimited);
        buffer.writeVarInt(value.segments.itemHeight());
        buffer.writeVarInt(value.segments.fluidHeight());
        buffer.writeVarInt(value.segments.otherHeight());
    }

    private static TrinityDataCoreStorageStatus decode(RegistryFriendlyByteBuf buffer) {
        return new TrinityDataCoreStorageStatus(
                buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readBoolean(),
                new TrinityStorageCapacitySegments(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
    }
}
