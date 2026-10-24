package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import com.fish_dan_.data_energistics.common.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.common.item.powered.cannon.ammunition.RailAmmunition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Full-charge parameters fixed at release, independent of later weapon or disk changes. */
public record RailShot(RailAmmunition ammunition, int cards, float charge) {

    private static final Codec<RailAmmunition> AMMUNITION_CODEC = Codec.STRING.comapFlatMap(
            RailShot::decodeAmmunition, RailAmmunition::name);
    public static final Codec<RailShot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AMMUNITION_CODEC.fieldOf("ammunition").forGetter(RailShot::ammunition),
            Codec.intRange(0, 2).fieldOf("cards").forGetter(RailShot::cards),
            Codec.floatRange(0, 1).validate(value -> value > 0 ? DataResult.success(value) : DataResult.error(() -> "Rail shot charge must be positive")).fieldOf("charge").forGetter(RailShot::charge)).apply(instance, RailShot::new));
    public static final StreamCodec<FriendlyByteBuf, RailShot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(value -> decodeAmmunition(value).getOrThrow(), RailAmmunition::name), RailShot::ammunition,
            ByteBufCodecs.VAR_INT, RailShot::cards,
            ByteBufCodecs.FLOAT, RailShot::charge,
            RailShot::new);

    public RailShot {
        AmmunitionRules.checkCards(cards);
        if (!Float.isFinite(charge) || charge <= 0 || charge > 1) throw new IllegalArgumentException("Invalid rail shot charge");
    }

    private static DataResult<RailAmmunition> decodeAmmunition(String name) {
        for (RailAmmunition ammunition : RailAmmunition.values()) {
            if (ammunition.name().equals(name)) return DataResult.success(ammunition);
        }
        return DataResult.error(() -> "Unknown rail ammunition: " + name);
    }
}
