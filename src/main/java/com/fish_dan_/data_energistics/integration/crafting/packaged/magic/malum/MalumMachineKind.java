package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.malum;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.sammy.malum.common.block.curiosities.spirit_altar.SpiritAltarBlockEntity;
import com.sammy.malum.common.block.curiosities.spirit_crucible.SpiritCrucibleCoreBlockEntity;

public enum MalumMachineKind {

    ALTAR("spirit_infusion"),
    CRUCIBLE("spirit_focusing");

    final ResourceLocation type;

    MalumMachineKind(String path) {
        this.type = ResourceLocation.fromNamespaceAndPath("malum", path);
    }

    boolean accepts(BlockEntity tile) {
        return this == ALTAR ? tile instanceof SpiritAltarBlockEntity : tile instanceof SpiritCrucibleCoreBlockEntity;
    }
}
