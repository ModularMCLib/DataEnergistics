package com.fish_dan_.data_energistics.core.mixins.ae.extendedae;

import appeng.api.upgrades.MachineUpgradesChanged;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;

import com.glodblock.github.extendedae.api.IRecipeMachine;
import com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter;
import com.glodblock.github.extendedae.common.tileentities.TileCrystalAssembler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin({ TileCircuitCutter.class, TileCrystalAssembler.class })
public abstract class ExtendedAeMachineUpgradesMixin {

    @ModifyArg(
               method = "<init>",
               at = @At(
                        value = "INVOKE",
                        target = "Lappeng/api/upgrades/UpgradeInventories;forMachine(Lnet/minecraft/world/level/ItemLike;ILappeng/api/upgrades/MachineUpgradesChanged;)Lappeng/api/upgrades/IUpgradeInventory;"),
               index = 2)
    private MachineUpgradesChanged dataEnergistics$retryOnUpgradeChange(MachineUpgradesChanged original) {
        return () -> {
            original.onUpgradesChanged();
            ((IRecipeMachine<?, ?>) (Object) this).getContext().onInvChange();
            ((AENetworkedPoweredBlockEntity) (Object) this).getMainNode()
                    .ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        };
    }
}
