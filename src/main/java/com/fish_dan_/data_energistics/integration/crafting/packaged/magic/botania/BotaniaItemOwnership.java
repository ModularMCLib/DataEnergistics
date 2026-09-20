package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.botania;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Applies only after Botania's original geometry and entity predicate have selected their candidates. */
public final class BotaniaItemOwnership {

    private BotaniaItemOwnership() {}

    public static boolean accepts(BlockEntity machine, ItemEntity item) {
        if (!(machine.getLevel() instanceof ServerLevel level)) return true;
        var owner = PackagedMachineClaims.get(level).owner(machine.getBlockPos());
        return owner == null || PackagedEntityCapture.ownedBy(item, owner);
    }

    public static List<ItemEntity> filter(BlockEntity machine, List<ItemEntity> candidates) {
        return candidates.stream().filter(item -> accepts(machine, item)).toList();
    }
}
