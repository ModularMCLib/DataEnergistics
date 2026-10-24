package com.fish_dan_.data_energistics.common.effect.reboot;

import com.fish_dan_.data_energistics.common.data.DEMobEffects;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Only bodily state is rewound: inventory, equipment, experience and other player data stay on this player. */
public record RadixRebootSnapshot(ResourceKey<Level> dimension, Vec3 position, Vec3 velocity, float yaw, float pitch,
                                  Vitals vitals, ObjectList<MobEffectInstance> effects) {

    public static final Codec<RadixRebootSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RadixRebootSnapshot::dimension),
            Vec3.CODEC.fieldOf("position").forGetter(RadixRebootSnapshot::position),
            Vec3.CODEC.fieldOf("velocity").forGetter(RadixRebootSnapshot::velocity),
            Codec.FLOAT.fieldOf("yaw").forGetter(RadixRebootSnapshot::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(RadixRebootSnapshot::pitch),
            Vitals.CODEC.fieldOf("vitals").forGetter(RadixRebootSnapshot::vitals),
            MobEffectInstance.CODEC.listOf().<ObjectList<MobEffectInstance>>xmap(ObjectImmutableList::new, values -> values).fieldOf("effects")
                    .forGetter(RadixRebootSnapshot::effects))
            .apply(instance, RadixRebootSnapshot::new));

    public static RadixRebootSnapshot capture(ServerPlayer player) {
        var effects = new ObjectArrayList<MobEffectInstance>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.is(DEMobEffects.RADIX_REBOOT) && !effect.is(DEMobEffects.RADIX_REBOOT_COOLDOWN)) effects.add(copyEffect(effect));
        }
        FoodData food = player.getFoodData();
        return new RadixRebootSnapshot(player.level().dimension(), player.position(), player.getDeltaMovement(), player.getYRot(), player.getXRot(),
                new Vitals(player.getHealth(), player.getAbsorptionAmount(), player.getAirSupply(), player.getRemainingFireTicks(), player.getTicksFrozen(),
                        player.fallDistance, food.getFoodLevel(), food.getSaturationLevel(), food.getExhaustionLevel()),
                new ObjectImmutableList<>(effects));
    }

    /** Runs before death settlement on the same player object; no player NBT or respawn copy is loaded. */
    public void restore(ServerPlayer player, ServerLevel level) {
        player.teleportTo(level, position.x, position.y, position.z, yaw, pitch);
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) player.addEffect(copyEffect(effect));
        player.setHealth(Math.min(vitals.health, player.getMaxHealth()));
        player.setAbsorptionAmount(vitals.absorption);
        player.setAirSupply(vitals.air);
        player.setRemainingFireTicks(vitals.fire);
        player.setTicksFrozen(vitals.frozen);
        player.fallDistance = vitals.fallDistance;
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        FoodData food = player.getFoodData();
        food.setFoodLevel(vitals.food);
        food.setSaturation(vitals.saturation);
        food.setExhaustion(vitals.exhaustion);
    }

    private static MobEffectInstance copyEffect(MobEffectInstance effect) {
        var copy = new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(),
                effect.showIcon(), effect.hiddenEffect == null ? null : copyEffect(effect.hiddenEffect));
        copy.getCures().clear();
        copy.getCures().addAll(effect.getCures());
        return copy;
    }

    public record Vitals(float health, float absorption, int air, int fire, int frozen, float fallDistance, int food, float saturation, float exhaustion) {

        public static final Codec<Vitals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).fieldOf("health").forGetter(Vitals::health),
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("absorption").forGetter(Vitals::absorption),
                Codec.INT.fieldOf("air").forGetter(Vitals::air),
                Codec.INT.fieldOf("fire").forGetter(Vitals::fire),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("frozen").forGetter(Vitals::frozen),
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("fall_distance").forGetter(Vitals::fallDistance),
                Codec.intRange(0, 20).fieldOf("food").forGetter(Vitals::food),
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("saturation").forGetter(Vitals::saturation),
                Codec.floatRange(0, Float.MAX_VALUE).fieldOf("exhaustion").forGetter(Vitals::exhaustion)).apply(instance, Vitals::new));
    }
}
