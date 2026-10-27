package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.math.BigInteger;

/** Bounded magnitude used for UI sorting; quantities that differ below 63 leading bits can tie. */
public record TrinityStatusSortKey(int bits, long leading) implements Comparable<TrinityStatusSortKey> {

    public static TrinityStatusSortKey of(BigInteger amount) {
        int bits = amount.bitLength();
        return new TrinityStatusSortKey(bits, amount.shiftRight(Math.max(0, bits - 63)).longValueExact());
    }

    @Override
    public int compareTo(TrinityStatusSortKey other) {
        int byBits = Integer.compare(this.bits, other.bits);
        return byBits != 0 ? byBits : Long.compare(this.leading, other.leading);
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.bits);
        buffer.writeVarLong(this.leading);
    }

    public static TrinityStatusSortKey read(RegistryFriendlyByteBuf buffer) {
        return new TrinityStatusSortKey(buffer.readVarInt(), buffer.readVarLong());
    }
}
