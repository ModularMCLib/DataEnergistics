package com.fish_dan_.data_energistics.integration.magic.occultism.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;

import com.klikli_dev.occultism.common.block.SpiritFireBlock;
import com.klikli_dev.occultism.crafting.recipe.SpiritFireRecipe;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Invokes the actual fire collision on a physical input entity and harvests only causally tagged output entities. */
public final class SpiritFireAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("occultism", "spirit_fire");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("occultism_spirit_fire");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).getBlock() instanceof SpiritFireBlock;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof SpiritFireRecipe recipe) ||
                recipe.getType() != OccultismRecipes.SPIRIT_FIRE_TYPE.get())
            return null;
        var combined = new KeyCounter();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 ||
                        entry.getLongValue() > Long.MAX_VALUE - total)
                    return null;
                total += entry.getLongValue();
                combined.add(entry.getKey(), entry.getLongValue());
            }
        }
        if (combined.size() != 1) return null;
        AEItemKey key = (AEItemKey) combined.iterator().next().getKey();
        ItemStack input = key.toStack();
        var recipeInput = new SingleRecipeInput(input);
        if (!recipe.matches(recipeInput, level)) return null;
        var selected = level.getRecipeManager().getRecipeFor(OccultismRecipes.SPIRIT_FIRE_TYPE.get(), recipeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack result = recipe.assemble(recipeInput, level.registryAccess());
        if (result.isEmpty() || result.getCount() != 1 || pattern.getOutputs().size() != 1) return null;
        if (!PackagedOutputMatching.matches(pattern, result, total)) return null;
        var progress = new CompoundTag();
        progress.put("input", input.save(level.registryAccess()));
        progress.put("result", result.save(level.registryAccess()));
        progress.putLong("cycles", total);
        return progress;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        var progress = prepare(level, position, face, recipeId, pattern, prototype);
        if (progress == null) return 0;
        var input = ItemStack.parse(level.registryAccess(), progress.getCompound("input")).orElseThrow();
        var result = ItemStack.parse(level.registryAccess(), progress.getCompound("result")).orElseThrow();
        return Math.min(requestedCount, Math.min(input.getMaxStackSize(), result.getMaxStackSize()) / progress.getLong("cycles"));
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position())) return false;
        CompoundTag progress = operation.progress();
        ItemStack input = read(operation, "input");
        ItemStack result = read(operation, "result");
        long cycles = progress.getLong("cycles");
        if (cycles <= 0 || input.getCount() != 1 || result.getCount() != 1) throw new IllegalArgumentException("Invalid spirit fire plan");
        if (progress.getBoolean("waiting")) {
            int batch = progress.contains("batch") ? progress.getInt("batch") : 1;
            if (batch <= 0 || batch > cycles) throw new IllegalArgumentException("Invalid spirit fire batch");
            if (!harvest(operation, result.copyWithCount(batch))) return false;
            progress.putBoolean("waiting", false);
            progress.putLong("cycles", cycles - batch);
            progress.remove("entity");
            operation.changed();
            if (cycles == batch) operation.complete();
            return true;
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        var recipeInput = new SingleRecipeInput(input);
        if (holder.isEmpty() || !(holder.get().value() instanceof SpiritFireRecipe recipe) ||
                !recipe.matches(recipeInput, operation.level()) ||
                !PackagedOutputMatching.matches(operation, result, recipe.assemble(recipeInput, operation.level().registryAccess()))) {
            throw new IllegalStateException("Spirit fire recipe changed after preparation");
        }
        var selected = operation.level().getRecipeManager().getRecipeFor(OccultismRecipes.SPIRIT_FIRE_TYPE.get(), recipeInput, operation.level());
        if (selected.isEmpty() || !selected.get().id().equals(operation.recipeId())) return false;
        AEItemKey key = AEItemKey.of(input);
        int batch = (int) Math.min(cycles, Math.min(input.getMaxStackSize(), result.getMaxStackSize()));
        if (operation.available(key).compareTo(BigInteger.valueOf(batch)) < 0) throw new IllegalStateException("Missing spirit fire input");
        BlockPos position = operation.position();
        var entity = new ItemEntity(operation.level(), position.getX() + 0.5, position.getY() + 0.25,
                position.getZ() + 0.5, input.copyWithCount(batch));
        entity.setNoPickUpDelay();
        if (!operation.level().addFreshEntity(entity)) return false;
        operation.delivered(key, batch);
        progress.putInt("batch", batch);
        progress.putUUID("entity", entity.getUUID());
        progress.putBoolean("waiting", true);
        operation.changed();
        var state = operation.level().getBlockState(position);
        var fire = (SpiritFireBlock) state.getBlock();
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> fire.entityInside(state, operation.level(), position, entity));
        if (!entity.isRemoved()) throw new IllegalStateException("Spirit fire refused the validated physical input");
        // Fire recipes are synchronous. Recover now so the output cannot enter a second fire recipe on the next entity
        // tick.
        if (harvest(operation, result.copyWithCount(batch))) {
            progress.putBoolean("waiting", false);
            progress.putLong("cycles", cycles - batch);
            progress.remove("entity");
            operation.changed();
            if (cycles == batch) operation.complete();
        }
        return true;
    }

    private static boolean harvest(PackagedMachineOperation operation, ItemStack expected) {
        var entities = operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(4),
                entity -> PackagedEntityCapture.ownedBy(entity, operation.id()));
        if (entities.isEmpty()) return false;
        int count = 0;
        for (var entity : entities) {
            if (!PackagedOutputMatching.sameKey(operation, expected, entity.getItem())) {
                throw new IllegalStateException("Unexpected causally captured spirit fire result");
            }
            count = Math.addExact(count, entity.getItem().getCount());
        }
        if (count != expected.getCount()) throw new IllegalStateException("Unexpected spirit fire output quantity");
        for (var entity : entities) {
            ItemStack actual = entity.getItem().copy();
            entity.discard();
            operation.returned(AEItemKey.of(actual), actual.getCount());
        }
        return true;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid spirit fire " + key));
    }
}
