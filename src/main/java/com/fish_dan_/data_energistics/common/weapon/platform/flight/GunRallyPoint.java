package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A world-fixed rally command owned by each gun item, independent of transient flight entities. */
public record GunRallyPoint(ResourceKey<Level> dimension, Vec3 position) {

    public static final Codec<GunRallyPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(GunRallyPoint::dimension),
            Vec3.CODEC.validate(position -> Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z) ? DataResult.success(position) : DataResult.error(() -> "Rally position must be finite"))
                    .fieldOf("position").forGetter(GunRallyPoint::position))
            .apply(instance, GunRallyPoint::new));

    public Vec3 formation(int slot) {
        int pair = slot / 2;
        double angle = pair * Math.PI / 4 + slot % 2 * Math.PI;
        return position.add(Math.cos(angle), pair * 0.16, Math.sin(angle));
    }
}
