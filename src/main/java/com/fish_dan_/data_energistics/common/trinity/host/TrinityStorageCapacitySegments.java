package com.fish_dan_.data_energistics.common.trinity.host;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigInteger;

/** Exact server-side apportionment of the storage bar's fixed pixel budget, requiring no client-side large numbers. */
public record TrinityStorageCapacitySegments(int itemHeight, int fluidHeight, int otherHeight) {

    public static final int BAR_HEIGHT = 135;
    public static final Codec<TrinityStorageCapacitySegments> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.intRange(0, BAR_HEIGHT).fieldOf("items").forGetter(TrinityStorageCapacitySegments::itemHeight),
                    Codec.intRange(0, BAR_HEIGHT).fieldOf("fluids").forGetter(TrinityStorageCapacitySegments::fluidHeight),
                    Codec.intRange(0, BAR_HEIGHT).fieldOf("others").forGetter(TrinityStorageCapacitySegments::otherHeight))
            .apply(instance, TrinityStorageCapacitySegments::new));

    public TrinityStorageCapacitySegments {
        if (itemHeight < 0 || fluidHeight < 0 || otherHeight < 0 ||
                (long) itemHeight + fluidHeight + otherHeight > BAR_HEIGHT) {
            throw new IllegalArgumentException("Invalid synchronized Trinity storage capacity pixels");
        }
    }

    static TrinityStorageCapacitySegments calculate(BigInteger items, BigInteger fluids, BigInteger others,
                                                    BigInteger capacity, boolean unlimited) {
        BigInteger total = items.add(fluids).add(others);
        if (total.signum() == 0) {
            return new TrinityStorageCapacitySegments(0, 0, 0);
        }
        int filledHeight = unlimited || capacity.signum() == 0 ? BAR_HEIGHT :
                total.multiply(BigInteger.valueOf(BAR_HEIGHT)).divide(capacity)
                        .min(BigInteger.valueOf(BAR_HEIGHT)).intValueExact();
        BigInteger[] amounts = { items, fluids, others };
        int[] heights = new int[amounts.length];
        BigInteger[] remainders = new BigInteger[amounts.length];
        BigInteger scaledHeight = BigInteger.valueOf(filledHeight);
        int allocated = 0;
        for (int index = 0; index < amounts.length; index++) {
            BigInteger[] quotientAndRemainder = amounts[index].multiply(scaledHeight).divideAndRemainder(total);
            heights[index] = quotientAndRemainder[0].intValueExact();
            remainders[index] = quotientAndRemainder[1];
            allocated += heights[index];
        }
        boolean[] assigned = new boolean[amounts.length];
        for (int remaining = filledHeight - allocated; remaining > 0; remaining--) {
            int selected = -1;
            for (int index = 0; index < remainders.length; index++) {
                if (!assigned[index] && (selected < 0 || remainders[index].compareTo(remainders[selected]) > 0)) {
                    selected = index;
                }
            }
            heights[selected]++;
            assigned[selected] = true;
        }
        return new TrinityStorageCapacitySegments(heights[0], heights[1], heights[2]);
    }
}
