package com.fish_dan_.data_energistics.common.weapon.platform.model;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum HudModule implements StringRepresentable {

    COMPASS(7),
    WEAPONS(49),
    NETWORK(7),
    TARGETS(975),
    GUIDES(7),
    ORES(3);

    public static final Codec<HudModule> CODEC = StringRepresentable.fromEnum(HudModule::values);
    public final int fields;

    HudModule(int fields) {
        this.fields = fields;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
