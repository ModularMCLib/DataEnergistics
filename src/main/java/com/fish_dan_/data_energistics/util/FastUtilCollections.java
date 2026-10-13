package com.fish_dan_.data_energistics.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Collection;
import java.util.Map;

/** Centralized immutable-copy and small-map factories for project collection boundaries. */
public final class FastUtilCollections {

    private FastUtilCollections() {}

    public static <E> ObjectList<E> immutableList(Collection<? extends E> source) {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(source));
    }

    public static <E> ObjectSet<E> immutableSet(Collection<? extends E> source) {
        return ObjectSets.unmodifiable(new ObjectOpenHashSet<>(source));
    }

    public static <K, V> Object2ObjectMap<K, V> immutableMap(Object2ObjectMap<K, V> source) {
        return Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(source));
    }

    public static <K, V> Object2ObjectMap<K, V> immutableMap(Map<? extends K, ? extends V> source) {
        return Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(source));
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf() {
        return Object2ObjectMaps.emptyMap();
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(K key, V value) {
        return Object2ObjectMaps.singleton(key, value);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(K key1, V value1, K key2, V value2) {
        return mapOfEntries(key1, value1, key2, value2);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(
                                                      K key1, V value1,
                                                      K key2, V value2,
                                                      K key3, V value3) {
        return mapOfEntries(key1, value1, key2, value2, key3, value3);
    }

    public static <K, V> Object2ObjectMap<K, V> mapOf(
                                                      K key1, V value1,
                                                      K key2, V value2,
                                                      K key3, V value3,
                                                      K key4, V value4) {
        return mapOfEntries(key1, value1, key2, value2, key3, value3, key4, value4);
    }

    /** Creates an immutable small map for call sites with more than four key/value pairs. */
    @SafeVarargs
    public static <K, V> Object2ObjectMap<K, V> mapOf(Object... entries) {
        return mapOfEntries(entries);
    }

    private static <K, V> Object2ObjectMap<K, V> mapOfEntries(Object... entries) {
        if ((entries.length & 1) != 0) {
            throw new IllegalArgumentException("Collection map entries must be key/value pairs");
        }
        Object2ObjectLinkedOpenHashMap<K, V> result = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) entries[index];
            @SuppressWarnings("unchecked")
            V value = (V) entries[index + 1];
            if (result.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate collection map key: " + key);
            }
        }
        return Object2ObjectMaps.unmodifiable(result);
    }
}
