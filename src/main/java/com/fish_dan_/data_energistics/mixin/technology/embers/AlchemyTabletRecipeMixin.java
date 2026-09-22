package com.fish_dan_.data_energistics.mixin.technology.embers;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.integration.technology.embers.packaged.AlchemyTableAdapter;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rekindled.embers.blockentity.AlchemyTabletBlockEntity;
import com.rekindled.embers.recipe.AlchemyContext;
import com.rekindled.embers.recipe.IAlchemyRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the requested recipe after loading while native sparks still provide all Ember. */
@Mixin(value = AlchemyTabletBlockEntity.class, remap = false)
public abstract class AlchemyTabletRecipeMixin {

    @WrapMethod(method = "sparkProgress")
    private void dataEnergistics$selectSparkRecipe(BlockEntity source, double ember, Operation<Void> original) {
        var tablet = (AlchemyTabletBlockEntity) (Object) this;
        if (!dataEnergistics$restoreRecipe(tablet)) return;
        if (tablet.getLevel() instanceof ServerLevel level) {
            PackagedEntityCapture.machineTick(level, tablet.getBlockPos(), () -> original.call(source, ember));
        } else {
            original.call(source, ember);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$selectTickRecipe(Level level, BlockPos position, BlockState state,
                                                         AlchemyTabletBlockEntity tablet, CallbackInfo callback) {
        if (tablet.progress > 0 && !dataEnergistics$restoreRecipe(tablet)) callback.cancel();
    }

    @Unique
    private static boolean dataEnergistics$restoreRecipe(AlchemyTabletBlockEntity tablet) {
        if (!(tablet.getLevel() instanceof ServerLevel level)) return true;
        var claims = PackagedMachineClaims.get(level);
        var owner = claims.owner(tablet.getBlockPos());
        if (owner == null) return true;
        if (claims.structureRemoved(owner)) return false;
        String selected = tablet.getPersistentData().getString(AlchemyTableAdapter.SELECTED_RECIPE);
        if (selected.isEmpty()) return false;
        var holder = level.getRecipeManager().byKey(ResourceLocation.parse(selected));
        if (holder.isEmpty() || !(holder.get().value() instanceof IAlchemyRecipe recipe)) return false;
        var contents = AlchemyTabletBlockEntity.getPedestalContents(
                AlchemyTabletBlockEntity.getNearbyPedestals(level, tablet.getBlockPos()));
        var context = new AlchemyContext(tablet.inventory.getStackInSlot(0), contents, level.getSeed());
        if (!recipe.matchesCorrect(context, level)) return false;
        tablet.cachedRecipe = recipe;
        return true;
    }
}
