package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Strict NBT codec for the ownership-preserving dynamic borrowing ledger.
 */
public final class TrinityBorrowingLedgerNbtCodec {

    private static final int MAX_BIG_INTEGER_BYTES = 512;
    private static final String ENTRIES_TAG = "entries";
    private static final String KEY_TAG = "key";
    private static final String RESERVED_TAG = "reserved";
    private static final String COMMITTED_TAG = "committed";
    private static final String RELEASED_TAG = "released";

    private TrinityBorrowingLedgerNbtCodec() {}

    /**
     * Encodes the complete ledger history.
     *
     * @param entries    immutable borrowing balances
     * @param registries server registry lookup used by AE key codecs
     * @return strict ledger NBT
     */
    public static CompoundTag encode(Object2ObjectMap<AEKey, TrinityBorrowingLedger.Balances> entries,
                                     HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        ListTag encodedEntries = new ListTag();
        entries.forEach((key, balances) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, key.toTagGeneric(registries));
            putBigInteger(entry, RESERVED_TAG, balances.reserved());
            putBigInteger(entry, COMMITTED_TAG, balances.committed());
            putBigInteger(entry, RELEASED_TAG, balances.released());
            encodedEntries.add(entry);
        });
        root.put(ENTRIES_TAG, encodedEntries);
        return root;
    }

    /**
     * Decodes a ledger while rejecting unknown fields, damaged types and duplicate keys.
     *
     * @param tag        strict ledger NBT
     * @param registries server registry lookup used by AE key codecs
     * @return immutable ordered borrowing balances
     */
    public static Object2ObjectMap<AEKey, TrinityBorrowingLedger.Balances> decode(
                                                                                  CompoundTag tag,
                                                                                  HolderLookup.Provider registries) {
        ListTag encodedEntries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);

        Object2ObjectLinkedOpenHashMap<AEKey, TrinityBorrowingLedger.Balances> restored = new Object2ObjectLinkedOpenHashMap<>();
        for (Tag encoded : encodedEntries) {
            CompoundTag entry = (CompoundTag) encoded;
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(KEY_TAG));
            if (key == null) {
                throw new IllegalArgumentException("A Trinity borrowing ledger contains an unknown AE key");
            }
            TrinityBorrowingLedger.Balances balances = new TrinityBorrowingLedger.Balances(
                    readBigInteger(entry, RESERVED_TAG),
                    readBigInteger(entry, COMMITTED_TAG),
                    readBigInteger(entry, RELEASED_TAG));
            if (balances.total().signum() <= 0 || restored.putIfAbsent(key, balances) != null) {
                throw new IllegalArgumentException("A Trinity borrowing ledger requires unique non-empty entries");
            }
        }
        return FastUtilCollections.immutableMap(restored);
    }

    private static void putBigInteger(CompoundTag tag, String field, BigInteger value) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("Trinity borrowing balance exceeds the persistence byte limit");
        }
        tag.putByteArray(field, encoded);
    }

    private static BigInteger readBigInteger(CompoundTag tag, String field) {
        byte[] encoded = tag.getByteArray(field);
        if (encoded.length == 0 || encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("Trinity borrowing balance has invalid persistence bytes");
        }
        return new BigInteger(encoded);
    }
}
