package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DESounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Data_Energistics.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHARD_CHARGE = register("star_shard_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHARD_RAIL_SHOT = register("star_shard_rail_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHARD_GRENADE_SHOT = register("star_shard_grenade_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_SHARD_COOLDOWN = register("star_shard_cooldown");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_HAMMER_CORE_COOLDOWN = register("heavy_hammer_core_cooldown");

    private DESounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Data_Energistics.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
