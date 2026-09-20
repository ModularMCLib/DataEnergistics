package com.fish_dan_.data_energistics.integration.crafting.packaged.occultism;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import com.klikli_dev.occultism.common.block.SpiritFireBlock;
import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.crafting.recipe.conditionextension.RitualRecipeConditionContext;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * Native item-producing rituals, including their real sacrifice and item-use requirements.
 */
final class OccultismRitualAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("occultism", "ritual");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("occultism_ritual");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof GoldenSacrificialBowlBlockEntity;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = recipe(level, recipeId);
        if (holder == null) return null;
        if (!holder.value().requiresItemUse()) return encodedPattern;
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing == null) return null;
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(holder.value().getActivationItem());
        ingredients.addAll(holder.value().getIngredients());
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) supplied.add(input.what(), input.amount());
        var complete = new ObjectArrayList<>(ingredients);
        complete.add(holder.value().getItemToUse());
        if (PackagedIngredientAssignment.match(complete, new KeyCounter[] { supplied }) != null) return encodedPattern;
        if (PackagedIngredientAssignment.match(ingredients, new KeyCounter[] { supplied }) == null) return null;
        ItemStack[] candidates = holder.value().getItemToUse().getItems();
        if (candidates.length == 0) return null;
        var inputs = new ObjectArrayList<GenericStack>();
        for (var input : processing.sparseInputs()) if (input != null) inputs.add(input);
        inputs.add(new GenericStack(AEItemKey.of(candidates[0]), 1));
        ItemStack result = encodedPattern.copy();
        result.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(inputs, processing.sparseOutputs()));
        return result;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        RecipeHolder<RitualRecipe> holder = recipe(level, recipeId);
        if (holder == null) return null;
        Layout layout = layout(level, position, holder.value());
        if (layout == null || !layout.empty()) return null;
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(holder.value().getActivationItem());
        ingredients.addAll(holder.value().getIngredients());
        if (ingredients.size() > layout.inputs().size() + 1) return null;
        int bowlIngredients = ingredients.size();
        if (holder.value().requiresItemUse()) ingredients.add(holder.value().getItemToUse());
        KeyCounter totals = new KeyCounter();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 ||
                        entry.getLongValue() > Long.MAX_VALUE - total)
                    return null;
                total += entry.getLongValue();
                totals.add(entry.getKey(), entry.getLongValue());
            }
        }
        if (total == 0 || total % ingredients.size() != 0) return null;
        long cycles = total / ingredients.size();
        KeyCounter perCycle = new KeyCounter();
        for (var entry : totals) {
            if (entry.getLongValue() % cycles != 0) return null;
            perCycle.add(entry.getKey(), entry.getLongValue() / cycles);
        }
        var assigned = PackagedIngredientAssignment.match(ingredients, new KeyCounter[] { perCycle });
        if (assigned == null) return null;
        if (!holder.value().getRitual().matchesAdditionalIngredients(holder.value().getIngredients(), assigned.subList(1, bowlIngredients))) return null;
        if (!choosesRecipe(level, position, recipeId, assigned.getFirst(), assigned.subList(1, bowlIngredients))) return null;
        ItemStack result = RitualItemResult.preview(level, holder.value(), assigned.getFirst(), assigned.subList(1, bowlIngredients));
        if (result.isEmpty() || cycles > Long.MAX_VALUE / result.getCount() || pattern.getOutputs().size() != 1) return null;
        if (!PackagedOutputMatching.matches(pattern, result, cycles * result.getCount())) return null;
        var slots = new ListTag();
        for (int index = 1; index < bowlIngredients; index++) {
            var bowl = layout.inputs().get(index - 1);
            if (!bowl.itemStackHandler.insertItem(0, assigned.get(index), true).isEmpty()) return null;
            var slot = new CompoundTag();
            slot.putLong("position", bowl.getBlockPos().asLong());
            slot.put("input", assigned.get(index).save(level.registryAccess()));
            slots.add(slot);
        }
        var progress = new CompoundTag();
        progress.put("bowls", slots);
        progress.put("activation", assigned.getFirst().save(level.registryAccess()));
        progress.put("result", result.save(level.registryAccess()));
        progress.putLong("cycles", cycles);
        if (holder.value().requiresItemUse()) progress.put("use_item", assigned.getLast().save(level.registryAccess()));
        if (layout.output() != null) progress.putLong("output_bowl", layout.output().getBlockPos().asLong());
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        var slots = preparation.getList("bowls", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) positions.add(BlockPos.of(slots.getCompound(index).getLong("position")));
        if (preparation.contains("output_bowl", Tag.TAG_LONG)) positions.add(BlockPos.of(preparation.getLong("output_bowl")));
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        RecipeHolder<RitualRecipe> holder = recipe(operation.level(), operation.recipeId());
        if (holder == null) throw new IllegalStateException("Occultism ritual recipe is no longer supported");
        Layout layout = layout(operation.level(), operation.position(), holder.value());
        if (layout == null) return false;
        var progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid persisted ritual cycle count");
        boolean hasOutput = progress.contains("output_bowl", Tag.TAG_LONG);
        if (hasOutput != (layout.output() != null) || hasOutput &&
                progress.getLong("output_bowl") != layout.output().getBlockPos().asLong())
            return false;
        ItemStack expected = read(operation, progress, "result");
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        if (progress.getBoolean("delivered")) {
            if (layout.center().getCurrentRitualRecipe() != null) return RitualNativeActions.advance(operation, layout.center());
            return collect(operation, layout, slots, expected);
        }
        if (!layout.empty()) return false;
        ItemStack activation = read(operation, progress, "activation");
        var targets = new ObjectArrayList<SacrificialBowlBlockEntity>();
        var inputs = new ObjectArrayList<ItemStack>();
        var required = new KeyCounter();
        required.add(AEItemKey.of(activation), activation.getCount());
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag slot = slots.getCompound(index);
            var position = BlockPos.of(slot.getLong("position"));
            var bowl = layout.inputs().stream().filter(candidate -> candidate.getBlockPos().equals(position)).findFirst();
            if (bowl.isEmpty() || targets.contains(bowl.get())) return false;
            ItemStack stack = read(operation, slot, "input");
            if (stack.getCount() != 1 || !bowl.get().itemStackHandler.insertItem(0, stack, true).isEmpty()) return false;
            targets.add(bowl.get());
            inputs.add(stack);
            required.add(AEItemKey.of(stack), stack.getCount());
        }
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) {
                throw new IllegalStateException("Occultism ritual exceeds its owned materials");
            }
        }
        if (!PackagedOutputMatching.matches(operation, expected, RitualItemResult.preview(operation.level(), holder.value(), activation, inputs))) {
            throw new IllegalStateException("Occultism ritual result changed after preparation");
        }
        if (activation.getCount() != 1 || !holder.value().getActivationItem().test(activation) ||
                !holder.value().getRitual().matchesAdditionalIngredients(holder.value().getIngredients(), inputs)) {
            throw new IllegalStateException("Occultism ritual inputs changed after preparation");
        }
        if (!choosesRecipe(operation.level(), operation.position(), operation.recipeId(), activation, inputs)) return false;
        for (int index = 0; index < targets.size(); index++) {
            insert(operation, targets.get(index), inputs.get(index));
        }
        var chosen = operation.level().getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get()).stream()
                .filter(candidate -> candidate.value().matches(operation.level(), operation.position(), activation)).findFirst();
        if (chosen.isEmpty() || !chosen.get().id().equals(operation.recipeId())) {
            throw new IllegalStateException("Occultism golden bowl would select a different ritual");
        }
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> insert(operation, layout.center(), activation));
        var active = layout.center().getCurrentRitualRecipe();
        if (active == null || !active.id().equals(operation.recipeId())) {
            throw new IllegalStateException("Occultism ritual declined to start");
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static void insert(PackagedMachineOperation operation, SacrificialBowlBlockEntity bowl, ItemStack stack) {
        ItemStack rejected = bowl.itemStackHandler.insertItem(0, stack.copy(), false);
        int delivered = stack.getCount() - rejected.getCount();
        if (delivered > 0) operation.delivered(AEItemKey.of(stack), delivered);
        if (!rejected.isEmpty()) throw new IllegalStateException("Occultism bowl refused an accepted ingredient");
    }

    private static boolean collect(PackagedMachineOperation operation, Layout layout, ListTag slots, ItemStack expected) {
        if (layout.center().getCurrentRitualRecipe() != null || layout.center().ritualActive) return false;
        if (!layout.center().itemStackHandler.getStackInSlot(0).isEmpty()) {
            throw new IllegalStateException("Occultism ritual ended without consuming activation item");
        }
        for (int index = 0; index < slots.size(); index++) {
            BlockPos position = BlockPos.of(slots.getCompound(index).getLong("position"));
            var bowl = layout.inputs().stream().filter(candidate -> candidate.getBlockPos().equals(position)).findFirst();
            if (bowl.isEmpty()) return false;
            if (!bowl.get().itemStackHandler.getStackInSlot(0).isEmpty()) {
                throw new IllegalStateException("Occultism ritual left unconsumed materials");
            }
        }
        if (layout.output() != null) {
            var inventory = layout.output().itemStackHandler;
            ItemStack actual = inventory.getStackInSlot(0);
            if (actual.isEmpty()) return false;
            if (!PackagedOutputMatching.matches(operation, expected, actual)) throw new IllegalStateException("Unexpected Occultism output bowl contents");
            ItemStack extracted = inventory.extractItem(0, actual.getCount(), false);
            if (!extracted.isEmpty()) operation.returned(AEItemKey.of(extracted), extracted.getCount());
            if (!ItemStack.matches(extracted, actual)) throw new IllegalStateException("Incomplete Occultism output extraction");
        } else {
            var drops = operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(8),
                    item -> PackagedEntityCapture.ownedBy(item, operation.id()));
            if (drops.isEmpty()) return false;
            int count = 0;
            for (ItemEntity drop : drops) {
                if (!PackagedOutputMatching.sameKey(operation, expected, drop.getItem())) {
                    throw new IllegalStateException("Occultism ritual returned interrupted inputs or unexpected results");
                }
                count = Math.addExact(count, drop.getItem().getCount());
            }
            if (count != expected.getCount()) throw new IllegalStateException("Occultism ritual output quantity differs");
            for (ItemEntity drop : drops) {
                ItemStack actual = drop.getItem().copy();
                drop.discard();
                operation.returned(AEItemKey.of(actual), actual.getCount());
            }
        }
        long cycles = operation.progress().getLong("cycles") - 1;
        RitualNativeActions.returnHeld(operation);
        operation.progress().putLong("cycles", cycles);
        operation.progress().putBoolean("delivered", false);
        operation.changed();
        if (cycles == 0) operation.complete();
        return true;
    }

    private static @Nullable RecipeHolder<RitualRecipe> recipe(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof RitualRecipe recipe) ||
                recipe.getType() != OccultismRecipes.RITUAL_TYPE.get())
            return null;
        if (!RitualItemResult.supports(recipe)) return null;
        return new RecipeHolder<>(holder.get().id(), recipe);
    }

    private static boolean choosesRecipe(ServerLevel level, BlockPos position, ResourceLocation expected,
                                         ItemStack activation, List<ItemStack> inputs) {
        for (var candidate : level.getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get())) {
            RitualRecipe recipe = candidate.value();
            if (!recipe.getActivationItem().test(activation) ||
                    !recipe.getRitual().matchesAdditionalIngredients(recipe.getIngredients(), inputs))
                continue;
            var pentacle = recipe.getPentacle();
            if (pentacle == null || pentacle.getSize().getX() > 32 || pentacle.getSize().getZ() > 32) return false;
            int radius = Math.max(pentacle.getSize().getX(), pentacle.getSize().getZ());
            for (int x = (position.getX() - radius) >> 4; x <= (position.getX() + radius) >> 4; x++) {
                for (int z = (position.getZ() - radius) >> 4; z <= (position.getZ() + radius) >> 4; z++) {
                    if (!level.hasChunk(x, z)) return false;
                }
            }
            if (pentacle.validate(level, position) != null) return candidate.id().equals(expected);
        }
        return false;
    }

    private static @Nullable Layout layout(ServerLevel level, BlockPos position, RitualRecipe recipe) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof GoldenSacrificialBowlBlockEntity center)) return null;
        var pentacle = recipe.getPentacle();
        if (pentacle == null) return null;
        var size = pentacle.getSize();
        int radius = Math.max(8, Math.max(size.getX(), size.getZ()));
        if (radius > 32 || size.getY() > 32) return null;
        for (int x = (position.getX() - radius) >> 4; x <= (position.getX() + radius) >> 4; x++) {
            for (int z = (position.getZ() - radius) >> 4; z <= (position.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return null;
            }
        }
        if (pentacle.validate(level, position) == null) return null;
        if (recipe.getCondition() != null && !recipe.getCondition().test(RitualRecipeConditionContext.of(center))) return null;
        SacrificialBowlBlockEntity output = null;
        for (int height = 1; height <= 3; height++) {
            if (level.getBlockEntity(position.above(height)) instanceof SacrificialBowlBlockEntity bowl &&
                    !(bowl instanceof GoldenSacrificialBowlBlockEntity) && bowl.getBlockState().hasProperty(BlockStateProperties.FACING) &&
                    bowl.getBlockState().getValue(BlockStateProperties.FACING) == Direction.DOWN) {
                output = bowl;
                break;
            }
        }
        var inputs = new ObjectArrayList<SacrificialBowlBlockEntity>();
        for (var bowl : recipe.getRitual().getSacrificialBowls(level, position)) {
            if (bowl == output) continue;
            var below = level.getBlockState(bowl.getBlockPos().below());
            if (below.getBlock() instanceof SpiritFireBlock || below.is(OccultismBlocks.SPIRIT_CAMPFIRE.get())) return null;
            inputs.add(bowl);
        }
        return new Layout(center, inputs, output);
    }

    private static ItemStack read(PackagedMachineOperation operation, CompoundTag tag, String key) {
        return ItemStack.parse(operation.level().registryAccess(), tag.getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid persisted ritual " + key));
    }

    private record Layout(GoldenSacrificialBowlBlockEntity center, ObjectList<SacrificialBowlBlockEntity> inputs,
                          @Nullable SacrificialBowlBlockEntity output) {

        boolean empty() {
            return !this.center.ritualActive && this.center.currentRitualRecipe == null && this.center.currentRitualRecipeId == null &&
                    this.center.itemStackHandler.getStackInSlot(0).isEmpty() &&
                    (this.output == null || this.output.itemStackHandler.getStackInSlot(0).isEmpty()) &&
                    this.inputs.stream().allMatch(bowl -> bowl.itemStackHandler.getStackInSlot(0).isEmpty());
        }
    }
}
