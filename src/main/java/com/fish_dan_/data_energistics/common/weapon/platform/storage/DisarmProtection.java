package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Optional protection groups are persisted on the platform; research is evaluated at the mutation boundary. */
@Builder(toBuilder = true)
public record DisarmProtection(boolean enabled, boolean armor, boolean curios, boolean mainHand, boolean offHand) {

    public static final DisarmProtection DEFAULT = new DisarmProtection(true, false, false, false, false);
    public static final Codec<DisarmProtection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(DisarmProtection::enabled),
            Codec.BOOL.optionalFieldOf("armor", false).forGetter(DisarmProtection::armor),
            Codec.BOOL.optionalFieldOf("curios", false).forGetter(DisarmProtection::curios),
            Codec.BOOL.optionalFieldOf("main_hand", false).forGetter(DisarmProtection::mainHand),
            Codec.BOOL.optionalFieldOf("off_hand", false).forGetter(DisarmProtection::offHand))
            .apply(instance, DisarmProtection::new));
}
