package com.fish_dan_.data_energistics.util;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;

/** Bounded binary codec shared by exact quantities in persistence and network payloads. */
public final class ExactAmountCodec {

    public static final int MAX_BYTES = 512;

    private ExactAmountCodec() {}

    /** Encodes one exact value after enforcing the common transport width. */
    public static byte[] encode(BigInteger value, String role) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("Exact " + role + " exceeds the quantity encoding limit");
        }
        return encoded;
    }

    /** Decodes one exact value at an untrusted data boundary. */
    public static BigInteger decode(byte[] encoded, String role) {
        if (encoded.length == 0 || encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("Exact " + role + " has an invalid quantity encoding");
        }
        return new BigInteger(encoded);
    }

    /** Reads one current exact quantity from a compound tag. */
    public static BigInteger readTag(CompoundTag tag, String field, String role) {
        if (!(tag.get(field) instanceof ByteArrayTag value)) {
            throw new IllegalArgumentException("Exact " + role + " has an invalid amount tag");
        }
        return decode(value.getAsByteArray(), role);
    }
}
