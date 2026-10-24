package com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration;

import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.function.Function;
import java.util.function.IntConsumer;

/** Binds item types to independent configuration components; unbound items receive the default component. */
public final class WeaponConfigurationBindings {

    private static final Object2ObjectOpenHashMap<Item, Function<IntConsumer, WeaponConfigurationPanel>> BINDINGS = new Object2ObjectOpenHashMap<>();
    static {
        BINDINGS.put(DEItems.DIGITAL_FLOATING_GUN.get(), FloatingGunConfigurationPanel::new);
    }

    private WeaponConfigurationBindings() {}

    /** Registers a component before menus are constructed. Factories create a fresh tree for each menu side. */
    public static void register(Item item, Function<IntConsumer, WeaponConfigurationPanel> factory) {
        if (BINDINGS.putIfAbsent(item, factory) != null) throw new IllegalArgumentException("Weapon configuration is already bound: " + item);
    }

    public static WeaponConfigurationPanel create(Item item, IntConsumer ammunitionSelection) {
        var factory = BINDINGS.get(item);
        return factory == null ? new WeaponConfigurationPanel() : factory.apply(ammunitionSelection);
    }
}
