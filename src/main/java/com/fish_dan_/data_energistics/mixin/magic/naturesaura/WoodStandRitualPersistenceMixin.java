package com.fish_dan_.data_energistics.mixin.magic.naturesaura;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saves claimed native ritual progress using its registry ID instead of RecipeHolder.toString(). */
@Mixin(value = BlockEntityWoodStand.class, remap = false)
abstract class WoodStandRitualPersistenceMixin {

    @WrapMethod(method = "tick")
    private void recordConsumption(Operation<Void> original) {
        var stand = (BlockEntityWoodStand) (Object) this;
        var state = (WoodStandRitualAccessor) stand;
        var recipe = state.dataEnergistics$recipe();
        var center = state.dataEnergistics$ritualPosition();
        if (!(stand.getLevel() instanceof ServerLevel level) || recipe == null || center == null) {
            original.call();
            return;
        }
        var claims = PackagedMachineClaims.get(level);
        var owner = claims.owner(stand.getBlockPos());
        if (owner == null) {
            original.call();
            return;
        }
        if (claims.structureRemoved(owner) || !Multiblocks.TREE_RITUAL.forEach(center, '\0', (pos, matcher) -> level.isLoaded(pos))) return;
        if (!state.dataEnergistics$isRitualOkay()) {
            claims.blockReplaced(stand.getBlockPos());
            return;
        }
        var before = new Object2ObjectLinkedOpenHashMap<BlockPos, ItemStack>();
        int timer = state.dataEnergistics$timer();
        if (level.getGameTime() % 5 == 0 && timer < recipe.value().time / 2 && timer + 5 >= recipe.value().time / 2 && timer + 5 < recipe.value().time) {
            Multiblocks.TREE_RITUAL.forEach(center, 'W', (pos, matcher) -> {
                if (level.getBlockEntity(pos) instanceof BlockEntityWoodStand material) before.put(pos.immutable(), material.items.getStackInSlot(0).copy());
                return true;
            });
        }
        original.call();
        if (!before.isEmpty() && state.dataEnergistics$timer() > timer) {
            var ledger = PackagedRecoveryJournal.get(level);
            var data = ledger.read(owner);
            var consumed = data.getList("consumed_materials", 10);
            for (var entry : before.entrySet()) {
                if (!entry.getValue().isEmpty() && level.getBlockEntity(entry.getKey()) instanceof BlockEntityWoodStand material && material.items.getStackInSlot(0).isEmpty()) consumed.add(entry.getValue().save(level.registryAccess()));
            }
            data.put("consumed_materials", consumed);
            ledger.write(owner, data);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean recordResult(Level level, Entity entity, Operation<Boolean> original) {
        var stand = (BlockEntityWoodStand) (Object) this;
        var owner = level instanceof ServerLevel server ? PackagedMachineClaims.get(server).owner(stand.getBlockPos()) : null;
        if (owner != null && entity instanceof ItemEntity item) {
            PackagedEntityCapture.claim(item, owner);
            item.getPersistentData().putBoolean("data_energistics_ritual_result", true);
        }
        boolean added = original.call(level, entity);
        if (owner != null && level instanceof ServerLevel server && entity instanceof ItemEntity item) {
            var ledger = PackagedRecoveryJournal.get(server);
            var data = ledger.read(owner);
            data.putBoolean("completed", true);
            data.remove("consumed_materials");
            if (!added) data.put("unspawned_result", item.getItem().save(server.registryAccess()));
            ledger.write(owner, data);
        }
        return added;
    }

    @ModifyArg(method = "writeNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;putString(Ljava/lang/String;Ljava/lang/String;)V"), index = 1)
    private String saveRecipeId(String original) {
        var stand = (BlockEntityWoodStand) (Object) this;
        var recipe = ((WoodStandRitualAccessor) stand).dataEnergistics$recipe();
        return stand.getLevel() instanceof ServerLevel level && PackagedMachineClaims.get(level).owner(stand.getBlockPos()) != null && recipe != null ? recipe.id().toString() : original;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void persistClaimedProgress(CallbackInfo callback) {
        var stand = (BlockEntityWoodStand) (Object) this;
        if (stand.getLevel() instanceof ServerLevel level && level.getGameTime() % 5 == 0 && PackagedMachineClaims.get(level).owner(stand.getBlockPos()) != null) {
            var state = (WoodStandRitualAccessor) stand;
            var recipe = state.dataEnergistics$recipe();
            var position = state.dataEnergistics$ritualPosition();
            if (recipe != null && position != null) {
                var data = stand.getPersistentData();
                data.putUUID("packaged_ritual_owner", PackagedMachineClaims.get(level).owner(stand.getBlockPos()));
                data.putInt("packaged_ritual_timer", state.dataEnergistics$timer());
                data.putString("packaged_ritual_recipe", recipe.id().toString());
            }
            stand.setChanged();
        }
    }
}
