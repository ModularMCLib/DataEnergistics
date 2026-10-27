package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;
import com.fish_dan_.data_energistics.util.FormattingUtils;

import appeng.api.stacks.AEKey;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Bounded server-formatted display units plus AE2's non-authoritative long projection. */
public record TrinityStatusAmount(String text, long amount) {

    public static TrinityStatusAmount of(AEKey key, BigInteger amount) {
        return new TrinityStatusAmount(FormattingUtils.format(new BigDecimal(amount).divide(
                BigDecimal.valueOf(key.getAmountPerUnit()), MathContext.DECIMAL128)),
                TrinityAe2AmountProjection.toAe2Amount(amount));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.text);
        buffer.writeVarLong(this.amount);
    }

    public static TrinityStatusAmount read(RegistryFriendlyByteBuf buffer) {
        return new TrinityStatusAmount(buffer.readUtf(), buffer.readVarLong());
    }
}
