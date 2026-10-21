package com.fish_dan_.data_energistics.common.data;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.effect.DataOverloadMobEffect;
import com.fish_dan_.data_energistics.common.effect.LifeReductionMobEffect;
import com.fish_dan_.data_energistics.common.effect.RadixLossMobEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DEMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Data_Energistics.MODID);

    public static final DeferredHolder<MobEffect, RadixLossMobEffect> RADIX_LOSS = MOB_EFFECTS.register(
            "radix_loss",
            RadixLossMobEffect::new);

    public static final DeferredHolder<MobEffect, LifeReductionMobEffect> LIFE_REDUCTION = MOB_EFFECTS.register(
            "life_reduction", LifeReductionMobEffect::new);
    public static final DeferredHolder<MobEffect, DataOverloadMobEffect> DATA_OVERLOAD = MOB_EFFECTS.register(
            "data_overload", DataOverloadMobEffect::new);

    private DEMobEffects() {}

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
