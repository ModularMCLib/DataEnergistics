package com.fish_dan_.data_energistics.core.mixins.com.glodblock.github.extendedae.common.tileentities;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.upgrades.MachineUpgradesChanged;

import com.glodblock.github.extendedae.common.tileentities.TileExInscriber;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@RequireMods("extendedae")
@Pseudo
@Mixin(TileExInscriber.class)
@NullMarked
public abstract class TileExInscriberMixin {

    @ModifyArg(
               method = "<init>",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"),
               index = 2)
    private MachineUpgradesChanged dataEnergistics$wakeOnUpgradeChange(MachineUpgradesChanged original) {
        return () -> {
            original.onUpgradesChanged();
            ((TileExInscriber) (Object) this).getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        };
    }
}
