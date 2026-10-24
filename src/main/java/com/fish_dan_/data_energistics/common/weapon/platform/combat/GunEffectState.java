package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.UUIDUtil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Optional;
import java.util.UUID;

/** Immutable entity attachment shared by persistent combat effects and radix reboot snapshots. */
public record GunEffectState(Optional<Stellar> stellar, ObjectList<Echo> echoes) {

    public static final GunEffectState EMPTY = new GunEffectState(Optional.empty(), ObjectList.of());
    public static final Codec<GunEffectState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stellar.CODEC.optionalFieldOf("stellar").forGetter(GunEffectState::stellar),
            Echo.CODEC.listOf().<ObjectList<Echo>>xmap(ObjectImmutableList::new, values -> values).fieldOf("echoes")
                    .forGetter(GunEffectState::echoes))
            .apply(instance, GunEffectState::new));

    public GunEffectState {
        echoes = echoes.isEmpty() ? ObjectList.of() : new ObjectImmutableList<>(echoes);
    }

    public record Stellar(UUID owner, String ownerName, boolean protect, int tick) {

        public static final Codec<Stellar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Stellar::owner),
                Codec.STRING.fieldOf("owner_name").forGetter(Stellar::ownerName),
                Codec.BOOL.fieldOf("protect").forGetter(Stellar::protect),
                Codec.intRange(0, 199).fieldOf("tick").forGetter(Stellar::tick)).apply(instance, Stellar::new));
    }

    public record Echo(UUID owner, int remaining, GunForm form, boolean protect) {

        public static final Codec<Echo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Echo::owner),
                Codec.intRange(1, 60).fieldOf("remaining").forGetter(Echo::remaining),
                Codec.intRange(0, GunForm.MEDICAL.ordinal() - 1).xmap(value -> GunForm.values()[value], GunForm::ordinal)
                        .fieldOf("form").forGetter(Echo::form),
                Codec.BOOL.fieldOf("protect").forGetter(Echo::protect)).apply(instance, Echo::new));
    }
}
