package com.fish_dan_.data_energistics.common.crafting.display;

import com.fish_dan_.data_energistics.util.FormattingUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.math.BigInteger;

/** Immutable display text and sign; exact quantities stay in the server's executable plan. */
public record CraftingDisplayAmount(String text, int signum) {

    public static final CraftingDisplayAmount ZERO = new CraftingDisplayAmount("0", 0);

    /** Formats once at the projection boundary without retaining the arbitrary-precision value. */
    public static CraftingDisplayAmount of(BigInteger amount) {
        return new CraftingDisplayAmount(FormattingUtils.format(amount), amount.signum());
    }

    /** Adapts AE2's primitive compatibility counters for the same display consumer. */
    public static CraftingDisplayAmount of(long amount) {
        return new CraftingDisplayAmount(FormattingUtils.format(amount), Long.signum(amount));
    }

    /** Uses the supplied sign to remove the display minus, without parsing the formatted number. */
    public String absoluteText() {
        return this.signum < 0 ? this.text.substring(1) : this.text;
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.text);
        buffer.writeByte(this.signum);
    }

    /** Reads the display value without reconstructing an exact quantity. */
    public static CraftingDisplayAmount read(RegistryFriendlyByteBuf buffer) {
        return new CraftingDisplayAmount(buffer.readUtf(), buffer.readByte());
    }
}
