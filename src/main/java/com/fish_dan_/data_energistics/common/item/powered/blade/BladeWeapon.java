package com.fish_dan_.data_energistics.common.item.powered.blade;

import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

public enum BladeWeapon {
    LIGHT_SABER, SANCTIFIER;

    public static @Nullable BladeWeapon of(ItemStack stack) {
        if (stack.is(DEItems.DATA_LIGHT_SABER.get())) return LIGHT_SABER;
        if (stack.is(DEItems.DATA_SANCTIFIER.get())) return SANCTIFIER;
        return null;
    }

    public boolean matches(ItemStack stack) {
        return stack.is(this == SANCTIFIER ? DEItems.DATA_SANCTIFIER.get() : DEItems.DATA_LIGHT_SABER.get());
    }
}
