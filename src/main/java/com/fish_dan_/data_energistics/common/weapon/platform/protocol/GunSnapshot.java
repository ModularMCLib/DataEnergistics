package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.weapon.platform.storage.GunSettings;

import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GunSnapshot(int slot, ItemStack item, GunSettings settings, double heat, boolean overheated, int actualAmmunition, int target, String state) {

    public static final Codec<GunSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 7).fieldOf("slot").forGetter(GunSnapshot::slot),
            ItemStack.CODEC.fieldOf("item").forGetter(GunSnapshot::item),
            GunSettings.CODEC.fieldOf("settings").forGetter(GunSnapshot::settings),
            Codec.doubleRange(0, 100).fieldOf("heat").forGetter(GunSnapshot::heat),
            Codec.BOOL.fieldOf("overheated").forGetter(GunSnapshot::overheated),
            Codec.intRange(0, 4).fieldOf("actual").forGetter(GunSnapshot::actualAmmunition),
            Codec.INT.fieldOf("target").forGetter(GunSnapshot::target),
            Codec.STRING.fieldOf("state").forGetter(GunSnapshot::state)).apply(instance, GunSnapshot::new));
}
