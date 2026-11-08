package com.fish_dan_.data_energistics.core.mixins.normal.de.ellpeck.naturesaura.blocks.tiles;

import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedRecoveryJournal;
import com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.BlockEntityWoodStandAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saves claimed native ritual progress using its registry ID instead of RecipeHolder.toString(). */
@Restriction(require = @Condition("naturesaura"))
@Mixin(value = BlockEntityWoodStand.class, remap = false)
abstract class BlockEntityWoodStandMixin implements BlockEntityWoodStandAccessor {

    @WrapMethod(method = "tick")
    private void recordConsumption(Operation<Void> original) {
        var stand = (BlockEntityWoodStand) (Object) this;
        var state = (BlockEntityWoodStandAccessor) stand;
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
        var before = new Long2ObjectLinkedOpenHashMap<ItemStack>();
        int timer = state.dataEnergistics$timer();
        if (level.getGameTime() % 5 == 0 && timer < recipe.value().time / 2 && timer + 5 >= recipe.value().time / 2 && timer + 5 < recipe.value().time) {
            Multiblocks.TREE_RITUAL.forEach(center, 'W', (pos, matcher) -> {
                if (level.getBlockEntity(pos) instanceof BlockEntityWoodStand material) before.put(pos.asLong(), material.items.getStackInSlot(0).copy());
                return true;
            });
        }
        original.call();
        if (!before.isEmpty() && state.dataEnergistics$timer() > timer) {
            var ledger = PackagedRecoveryJournal.get(level);
            var data = ledger.read(owner);
            var consumed = data.getList("consumed_materials", 10);
            for (Long2ObjectMap.Entry<ItemStack> entry : before.long2ObjectEntrySet()) {
                if (!entry.getValue().isEmpty() && level.getBlockEntity(BlockPos.of(entry.getLongKey())) instanceof BlockEntityWoodStand material && material.items.getStackInSlot(0).isEmpty()) consumed.add(entry.getValue().save(level.registryAccess()));
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
        var recipe = ((BlockEntityWoodStandAccessor) stand).dataEnergistics$recipe();
        return stand.getLevel() instanceof ServerLevel level && PackagedMachineClaims.get(level).owner(stand.getBlockPos()) != null && recipe != null ? recipe.id().toString() : original;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void persistClaimedProgress(CallbackInfo callback) {
        var stand = (BlockEntityWoodStand) (Object) this;
        if (stand.getLevel() instanceof ServerLevel level && level.getGameTime() % 5 == 0 && PackagedMachineClaims.get(level).owner(stand.getBlockPos()) != null) {
            var state = (BlockEntityWoodStandAccessor) stand;
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

    @Accessor("recipe")
    @Nullable
    public abstract RecipeHolder<TreeRitualRecipe> dataEnergistics$recipe();

    @Accessor("ritualPos")
    @Nullable
    public abstract BlockPos dataEnergistics$ritualPosition();

    @Accessor("timer")
    public abstract int dataEnergistics$timer();

    @Accessor("timer")
    public abstract void dataEnergistics$timer(int timer);

    @Accessor("recipe")
    public abstract void dataEnergistics$recipe(@Nullable RecipeHolder<TreeRitualRecipe> recipe);

    @Accessor("ritualPos")
    public abstract void dataEnergistics$ritualPosition(@Nullable BlockPos position);

    @Invoker("isRitualOkay")
    public abstract boolean dataEnergistics$isRitualOkay();
}
