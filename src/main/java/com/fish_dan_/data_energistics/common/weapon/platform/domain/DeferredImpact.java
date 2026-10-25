package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import com.fish_dan_.data_energistics.common.combat.erasure.EntityErasure;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunTargeting;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.UUID;

/** Ordered, typed pending work. Replaying re-enters the original public damage and targeting boundaries. */
public record DeferredImpact(UUID subject, UUID life, ResourceKey<Level> dimension, Payload payload) {

    public static final Codec<DeferredImpact> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("subject").forGetter(DeferredImpact::subject), UUIDUtil.CODEC.fieldOf("life").forGetter(DeferredImpact::life), Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(DeferredImpact::dimension),
            Payload.CODEC.fieldOf("payload").forGetter(DeferredImpact::payload)).apply(instance, DeferredImpact::new));

    public sealed interface Payload permits Damage, Weapon, Erasure, Push {

        Codec<Payload> CODEC = Codec.STRING.dispatch("kind", Payload::kind, kind -> switch (kind) {
            case "damage" -> Damage.CODEC;
            case "weapon" -> Weapon.CODEC;
            case "erasure" -> Erasure.CODEC;
            case "push" -> Push.CODEC;
            default -> throw new IllegalArgumentException("Unknown delayed impact: " + kind);
        });

        String kind();

        void apply(Entity target, ServerLevel level);

        default boolean ready(ServerLevel level) {
            return true;
        }
    }

    public record Damage(DeferredSource source, double amount, boolean direct) implements Payload {

        static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(DeferredSource.CODEC.fieldOf("source").forGetter(Damage::source),
                Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("amount").forGetter(Damage::amount), Codec.BOOL.fieldOf("direct").forGetter(Damage::direct)).apply(instance, Damage::new));

        @Override
        public String kind() {
            return "damage";
        }

        @Override
        public boolean ready(ServerLevel level) {
            return !source.playerOwner() || source.owner().map(id -> level.getServer().getPlayerList().getPlayer(id) != null).orElse(false);
        }

        @Override
        public void apply(Entity target, ServerLevel level) {
            if (!(target instanceof LivingEntity living)) return;
            var damage = source.restore(level);
            if (target instanceof Player player && damage.getEntity() instanceof Player attacker && (!level.getServer().isPvpAllowed() || !attacker.canHarmPlayer(player))) return;
            if (direct) WeaponDamage.hurt(living, damage, amount);
            else living.hurt(damage, (float) amount);
        }
    }

    public record Weapon(UUID owner, boolean protect, int form, int ammunition, float damage) implements Payload {

        static final MapCodec<Weapon> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(UUIDUtil.CODEC.fieldOf("owner").forGetter(Weapon::owner), Codec.BOOL.fieldOf("protect").forGetter(Weapon::protect),
                Codec.intRange(0, 4).fieldOf("form").forGetter(Weapon::form), Codec.intRange(0, 4).fieldOf("ammunition").forGetter(Weapon::ammunition), Codec.floatRange(0, Float.MAX_VALUE).fieldOf("damage").forGetter(Weapon::damage)).apply(instance, Weapon::new));

        @Override
        public String kind() {
            return "weapon";
        }

        @Override
        public boolean ready(ServerLevel level) {
            var player = level.getServer().getPlayerList().getPlayer(owner);
            return player != null && player.level() == level;
        }

        @Override
        public void apply(Entity target, ServerLevel level) {
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player == null || player.level() != level || !(target instanceof LivingEntity living)) return;
            GunImpactEffects.hit(new GunTargeting(player, protect), living, GunForm.values()[form], GunAmmunition.values()[ammunition], damage);
        }
    }

    public record Erasure(ErasureStrike.Persistence strike) implements Payload {

        static final MapCodec<Erasure> CODEC = ErasureStrike.PERSISTENCE_CODEC.fieldOf("strike").xmap(Erasure::new, Erasure::strike);

        @Override
        public String kind() {
            return "erasure";
        }

        @Override
        public void apply(Entity target, ServerLevel level) {
            var attempts = new Object2ObjectOpenHashMap<>(strike.attempts());
            attempts.remove(target.getUUID());
            var ready = new ErasureStrike.Persistence(strike.strikeId(), strike.initiatorId(), strike.exemptions(), attempts, strike.encounters(), strike.usable());
            EntityErasure.eraseHit(target, ErasureStrike.restore(ready));
        }
    }

    public record Push(Vec3 velocity) implements Payload {

        static final MapCodec<Push> CODEC = Vec3.CODEC.fieldOf("velocity").xmap(Push::new, Push::velocity);

        @Override
        public String kind() {
            return "push";
        }

        @Override
        public void apply(Entity target, ServerLevel level) {
            target.push(velocity.x, velocity.y, velocity.z);
        }
    }
}
