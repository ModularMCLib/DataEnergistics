package com.fish_dan_.data_energistics.utils;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Collection;
import java.util.Map;

/** Centralized immutable-copy and small-map factories for project collection boundaries. */
public final class FastUtilCollections {

    private FastUtilCollections() {}

    public static <E> ObjectList<E> immutableList(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectList.of();
        if (source instanceof ObjectImmutableList<?>) {
            // Immutable snapshots can be shared covariantly because callers cannot add elements.
            @SuppressWarnings("unchecked")
            ObjectList<E> snapshot = (ObjectList<E>) source;
            return snapshot;
        }
        return new ObjectImmutableList<>(source);
    }

    public static <E> Codec<ObjectList<E>> objectListCodec(Codec<E> elementCodec) {
        return elementCodec.listOf().xmap(
                values -> new ObjectArrayList<>(values),
                values -> values);
    }

    public static <E> ObjectSet<E> immutableSet(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectSets.emptySet();
        if (source instanceof ImmutableSetSnapshot<?>) {
            // This snapshot owns its backing set and cannot accept elements through the wider type.
            @SuppressWarnings("unchecked")
            ObjectSet<E> snapshot = (ObjectSet<E>) source;
            return snapshot;
        }
        return new ImmutableSetSnapshot<>(new ObjectOpenHashSet<>(source));
    }

    public static <K, V> Object2ObjectMap<K, V> immutableMap(Map<? extends K, ? extends V> source) {
        if (source.isEmpty()) return Object2ObjectMaps.emptyMap();
        if (source instanceof ImmutableMapSnapshot<?, ?>) {
            // The snapshot owns its backing map and exposes neither key nor value mutation.
            @SuppressWarnings("unchecked")
            Object2ObjectMap<K, V> snapshot = (Object2ObjectMap<K, V>) source;
            return snapshot;
        }
        Object2ObjectMap<K, V> copied = source.size() <= 8 ? new Object2ObjectArrayMap<>(source) :
                new Object2ObjectLinkedOpenHashMap<>(source);
        return new ImmutableMapSnapshot<>(copied);
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
        return new ImmutableMapSnapshot<>(result);
    }

    private static final class ImmutableMapSnapshot<K, V> extends Object2ObjectMaps.UnmodifiableMap<K, V> {

        private ImmutableMapSnapshot(Object2ObjectMap<K, V> owned) {
            super(owned);
        }
    }

    private static final class ImmutableSetSnapshot<E> extends ObjectSets.UnmodifiableSet<E> {

        private ImmutableSetSnapshot(ObjectSet<E> owned) {
            super(owned);
        }
    }
}
