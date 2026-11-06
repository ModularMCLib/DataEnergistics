package com.fish_dan_.data_energistics.config.persistence;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/** Common configuration boundaries: exact persisted counters and absent nullable fields. */
public final class ConfigurationCodecs {

    public static final Codec<Long> NON_NEGATIVE_LONG = Codec.PASSTHROUGH.comapFlatMap(
            value -> value.asNumber().flatMap(ConfigurationCodecs::nonNegativeLong),
            value -> new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(value)));

    private ConfigurationCodecs() {}

    private static DataResult<Long> nonNegativeLong(Number number) {
        try {
            long value = Long.parseLong(number.toString());
            return value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative configuration counter: " + number);
        } catch (NumberFormatException invalid) {
            return DataResult.error(() -> "Configuration counter must be an exact non-negative long: " + number);
        }
    }
}
