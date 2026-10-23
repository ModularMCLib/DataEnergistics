package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import net.minecraft.core.UUIDUtil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/** Attachment presence marks cannon provenance even when the delayed explosive has no owner. */
public record CannonPayloadSource(Optional<UUID> owner) {

    public static final Codec<CannonPayloadSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(CannonPayloadSource::owner)).apply(instance, CannonPayloadSource::new));
}
