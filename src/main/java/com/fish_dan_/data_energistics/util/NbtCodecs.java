package com.fish_dan_.data_energistics.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/** Current registry-aware NBT and exact amount codecs shared by persistence and network payloads. */
public final class NbtCodecs {

    public static final int MAX_BYTES = 512;

    private NbtCodecs() {}

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

    /** Writes each stack as one registry-aware compound in list order. */
    public static ListTag encodeItemStacks(Iterable<? extends ItemStack> stacks,
                                           HolderLookup.Provider registries) {
        var encoded = new ListTag();
        for (ItemStack stack : stacks) {
            encoded.add(stack.saveOptional(registries));
        }
        return encoded;
    }

    /** Reads each current stack compound, preserving empty entries produced by optional decoding. */
    public static ObjectList<ItemStack> decodeItemStacks(ListTag encoded, HolderLookup.Provider registries) {
        var stacks = new ObjectArrayList<ItemStack>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            stacks.add(ItemStack.parseOptional(registries, encoded.getCompound(index)));
        }
        return stacks;
    }
}
