package com.fish_dan_.data_energistics.common.data;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.effect.DataOverloadMobEffect;
import com.fish_dan_.data_energistics.common.effect.LifeReductionMobEffect;
import com.fish_dan_.data_energistics.common.effect.RadixRedundancyMobEffect;
import com.fish_dan_.data_energistics.common.effect.RadixStasisMobEffect;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixRebootCooldownMobEffect;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixRebootMobEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DEMobEffects {

    private DEMobEffects() {}

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Data_Energistics.MODID);

    public static final DeferredHolder<MobEffect, RadixRedundancyMobEffect> RADIX_REDUNDANCY = MOB_EFFECTS.register(
            "radix_redundancy", RadixRedundancyMobEffect::new);
    public static final DeferredHolder<MobEffect, RadixRebootMobEffect> RADIX_REBOOT = MOB_EFFECTS.register(
            "radix_reboot", RadixRebootMobEffect::new);
    public static final DeferredHolder<MobEffect, RadixRebootCooldownMobEffect> RADIX_REBOOT_COOLDOWN = MOB_EFFECTS.register(
            "radix_reboot_cooldown", RadixRebootCooldownMobEffect::new);
    public static final DeferredHolder<MobEffect, RadixStasisMobEffect> RADIX_STASIS = MOB_EFFECTS.register(
            "radix_stasis", RadixStasisMobEffect::new);
    public static final DeferredHolder<MobEffect, LifeReductionMobEffect> LIFE_REDUCTION = MOB_EFFECTS.register(
            "life_reduction", LifeReductionMobEffect::new);
    public static final DeferredHolder<MobEffect, DataOverloadMobEffect> DATA_OVERLOAD = MOB_EFFECTS.register(
            "data_overload", DataOverloadMobEffect::new);

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
