package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

/** Client-selectable presentation geometry; it never changes plan semantics. */
public enum CraftingPlanLayoutMode implements StringRepresentable {

    LAYERED,
    RADIAL;

    public static final Codec<CraftingPlanLayoutMode> CODEC = StringRepresentable.fromEnum(CraftingPlanLayoutMode::values);

    @Override
    public String getSerializedName() {
        return name();
    }
}
