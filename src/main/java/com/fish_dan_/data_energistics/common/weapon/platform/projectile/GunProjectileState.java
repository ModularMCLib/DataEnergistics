package com.fish_dan_.data_energistics.common.weapon.platform.projectile;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Optional;
import java.util.UUID;

/** Only projectile-owned payload; vanilla still serializes position and owner at its native boundary. */
public record GunProjectileState(int form, int ammunition, boolean protect, boolean anchored, int remaining,
                                 Optional<UUID> target, ObjectSet<UUID> pierced, Vec3 beam) {

    public static final Codec<GunProjectileState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 4).fieldOf("form").forGetter(GunProjectileState::form),
            Codec.intRange(0, 4).fieldOf("ammunition").forGetter(GunProjectileState::ammunition),
            Codec.BOOL.fieldOf("protect").forGetter(GunProjectileState::protect),
            Codec.BOOL.fieldOf("anchored").forGetter(GunProjectileState::anchored),
            Codec.intRange(0, 200).fieldOf("remaining").forGetter(GunProjectileState::remaining),
            UUIDUtil.CODEC.optionalFieldOf("target").forGetter(GunProjectileState::target),
            UUIDUtil.CODEC.listOf().<ObjectSet<UUID>>xmap(ObjectOpenHashSet::new, ObjectArrayList::new).fieldOf("pierced").forGetter(GunProjectileState::pierced),
            Vec3.CODEC.fieldOf("beam").forGetter(GunProjectileState::beam)).apply(instance, GunProjectileState::new));
}
