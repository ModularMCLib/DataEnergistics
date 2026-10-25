package com.fish_dan_.data_energistics.common.effect.reboot;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.effect.ChromaticGlowState;
import com.fish_dan_.data_energistics.common.effect.WeaponBurnState;
import com.fish_dan_.data_energistics.common.network.packets.action.ChromaticGlowPayload;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunEffectState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Optional;

/** Restores effect-owned state together with visible potions so persistent damage cannot survive the rewind. */
public record RadixRebootEffects(ObjectList<MobEffectInstance> potions, WeaponBurnState burn, Optional<Glow> glow, GunEffectState gun) {

    public static final Codec<RadixRebootEffects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MobEffectInstance.CODEC.listOf().<ObjectList<MobEffectInstance>>xmap(ObjectImmutableList::new, values -> values).fieldOf("potions")
                    .forGetter(RadixRebootEffects::potions),
            WeaponBurnState.CODEC.fieldOf("burn").forGetter(RadixRebootEffects::burn),
            Glow.CODEC.optionalFieldOf("glow").forGetter(RadixRebootEffects::glow),
            GunEffectState.CODEC.fieldOf("gun").forGetter(RadixRebootEffects::gun)).apply(instance, RadixRebootEffects::new));

    public static RadixRebootEffects capture(ServerPlayer player) {
        var potions = new ObjectArrayList<MobEffectInstance>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.is(DEMobEffects.RADIX_REBOOT) && !effect.is(DEMobEffects.RADIX_REBOOT_COOLDOWN)) potions.add(copyEffect(effect));
        }
        WeaponBurnState burn = player.hasData(DEAttachments.WEAPON_BURN) ? player.getData(DEAttachments.WEAPON_BURN) : WeaponBurnState.EMPTY;
        Optional<Glow> glow = Optional.empty();
        if (player.hasData(DEAttachments.CHROMATIC_GLOW)) {
            ChromaticGlowState state = player.getData(DEAttachments.CHROMATIC_GLOW);
            long remaining = state.until() - player.level().getGameTime();
            if (state.color() >= 0 && remaining > 0) glow = Optional.of(new Glow(state.color(), remaining));
        }
        GunEffectState gun = player.hasData(DEAttachments.GUN_EFFECTS) ? player.getData(DEAttachments.GUN_EFFECTS) : GunEffectState.EMPTY;
        return new RadixRebootEffects(new ObjectImmutableList<>(potions), burn, glow, gun);
    }

    public void restore(ServerPlayer player) {
        player.removeAllEffects();
        for (MobEffectInstance effect : potions) player.forceAddEffect(copyEffect(effect), null);
        if (burn.remaining() > 0) player.setData(DEAttachments.WEAPON_BURN, burn);
        else player.removeData(DEAttachments.WEAPON_BURN);
        int color = glow.map(Glow::color).orElse(-1);
        long until = glow.map(value -> player.level().getGameTime() + value.remaining).orElse(0L);
        if (glow.isPresent()) player.setData(DEAttachments.CHROMATIC_GLOW, new ChromaticGlowState(color, until));
        else player.removeData(DEAttachments.CHROMATIC_GLOW);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ChromaticGlowPayload(player.getId(), color, until));
        if (gun.stellar().isPresent() || !gun.echoes().isEmpty()) player.setData(DEAttachments.GUN_EFFECTS, gun);
        else player.removeData(DEAttachments.GUN_EFFECTS);
    }

    private static MobEffectInstance copyEffect(MobEffectInstance effect) {
        var copy = new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(),
                effect.showIcon(), effect.hiddenEffect == null ? null : copyEffect(effect.hiddenEffect));
        copy.getCures().clear();
        copy.getCures().addAll(effect.getCures());
        return copy;
    }

    public record Glow(int color, long remaining) {

        public static final Codec<Glow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 0xFFFFFF).fieldOf("color").forGetter(Glow::color),
                Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) : DataResult.error(() -> "Glow duration must be positive"))
                        .fieldOf("remaining").forGetter(Glow::remaining))
                .apply(instance, Glow::new));
    }
}
