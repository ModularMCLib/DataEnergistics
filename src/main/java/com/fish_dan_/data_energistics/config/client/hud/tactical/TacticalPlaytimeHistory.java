package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.fish_dan_.data_energistics.config.persistence.ConfigurationCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.regex.Pattern;

/** Immutable elapsed milliseconds per world path or server endpoint, identified by a stable digest. */
public record TacticalPlaytimeHistory(Object2LongMap<String> elapsedMillis) {

    private static final Pattern PROFILE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Codec<String> PROFILE = Codec.STRING.validate(value -> PROFILE_DIGEST.matcher(value).matches() ? DataResult.success(value) : DataResult.error(() -> "Invalid playtime profile digest: " + value));
    private static final Codec<Object2LongMap<String>> ELAPSED = Codec.unboundedMap(PROFILE, ConfigurationCodecs.NON_NEGATIVE_LONG)
            .xmap(Object2LongOpenHashMap::new, values -> values);
    public static final TacticalPlaytimeHistory EMPTY = new TacticalPlaytimeHistory(Object2LongMaps.emptyMap());
    public static final Codec<TacticalPlaytimeHistory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ELAPSED.optionalFieldOf("elapsedMillis", Object2LongMaps.emptyMap()).forGetter(TacticalPlaytimeHistory::elapsedMillis))
            .apply(instance, TacticalPlaytimeHistory::new));

    public TacticalPlaytimeHistory {
        elapsedMillis = Object2LongMaps.unmodifiable(new Object2LongOpenHashMap<>(elapsedMillis));
    }
}
