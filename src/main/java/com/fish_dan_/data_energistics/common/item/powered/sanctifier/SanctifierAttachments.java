package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.GameType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/** Server-owned combat state; only the spectator obligation survives player death. */
public final class SanctifierAttachments {

    private SanctifierAttachments() {}

    public record Charge(UUID blade, int ticks) {

        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("blade").forGetter(Charge::blade),
                Codec.intRange(0, 200).fieldOf("ticks").forGetter(Charge::ticks))
                .apply(instance, Charge::new));
    }

    public record Rewards(long end, int streak) {

        public static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative sanctifier reward end"))
                        .fieldOf("end").forGetter(Rewards::end),
                Codec.intRange(0, Integer.MAX_VALUE - 3).fieldOf("streak").forGetter(Rewards::streak))
                .apply(instance, Rewards::new));
    }

    public record Respawn(int ticks, GameType originalMode, boolean pending) {

        public static final Codec<Respawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 400).fieldOf("ticks").forGetter(Respawn::ticks),
                GameType.CODEC.fieldOf("original_mode").forGetter(Respawn::originalMode),
                Codec.BOOL.fieldOf("pending").forGetter(Respawn::pending))
                .apply(instance, Respawn::new));
    }
}
