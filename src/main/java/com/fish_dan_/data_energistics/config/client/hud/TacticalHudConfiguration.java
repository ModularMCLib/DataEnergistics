package com.fish_dan_.data_energistics.config.client.hud;

import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalPlaytimeHistory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.With;

/** Complete HUD file snapshot; visual edits and playtime checkpoints preserve each other's data. */
@With
public record TacticalHudConfiguration(TacticalHudAppearance appearance, TacticalPlaytimeHistory playtime) {

    public static final TacticalHudConfiguration DEFAULT = new TacticalHudConfiguration(TacticalHudAppearance.DEFAULT, TacticalPlaytimeHistory.EMPTY);
    private static final Codec<TacticalHudConfiguration> STRUCTURED = RecordCodecBuilder.create(instance -> instance.group(
            TacticalHudAppearance.CODEC.fieldOf("appearance").forGetter(TacticalHudConfiguration::appearance),
            TacticalPlaytimeHistory.CODEC.optionalFieldOf("playtime", TacticalPlaytimeHistory.EMPTY).forGetter(TacticalHudConfiguration::playtime))
            .apply(instance, TacticalHudConfiguration::new));
    public static final Codec<TacticalHudConfiguration> CODEC = Codec.withAlternative(STRUCTURED,
            TacticalHudAppearance.CODEC.xmap(appearance -> new TacticalHudConfiguration(appearance, TacticalPlaytimeHistory.EMPTY), TacticalHudConfiguration::appearance));
}
