package com.fish_dan_.data_energistics.integration.magic.malum.packaged;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.sammy.malum.common.block.curiosities.artifice.spirit_crucible.SpiritCrucibleCoreBlockEntity;
import com.sammy.malum.common.block.curiosities.sorcery.spirit_altar.SpiritAltarBlockEntity;

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
