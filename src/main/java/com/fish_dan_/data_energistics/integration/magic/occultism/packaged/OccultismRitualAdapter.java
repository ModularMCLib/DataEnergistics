package com.fish_dan_.data_energistics.integration.magic.occultism.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

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
public final class OccultismRitualAdapter implements PackagedMachineAdapter {

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
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        var level = operation.level();
        for (var position : occupiedPositions(level, operation.position(), operation.progress())) {
            if (!level.isLoaded(position)) return false;
        }

        var progress = operation.progress();
        if (level.getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity center &&
                center.ritualActive && center.getCurrentRitualRecipe() != null &&
                !center.getCurrentRitualRecipe().id().equals(operation.recipeId())) {
            return false;
        }
        if (!progress.getBoolean("recovery_inspected")) {
            if (progress.getBoolean("delivery_started") &&
                    level.getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity center) {
                var current = center.getCurrentRitualRecipe();
                if (current == null || !center.ritualActive) {
                    // Native interruption drops these only while the recipe is still resolvable.
                    for (ItemStack consumed : center.consumedIngredients) {
                        if (!consumed.isEmpty()) operation.returned(AEItemKey.of(consumed), consumed.getCount());
                    }
                    center.consumedIngredients.clear();
                    center.setChanged();
                }
                if (center.ritualActive) {
                    PackagedEntityCapture.run(level, operation.id(), () -> center.stopRitual(false));
                    center.setChanged();
                }
            }
            progress.putBoolean("recovery_inspected", true);
            operation.changed();
        }

        if (!progress.getBoolean("recovery_collected")) {
            if (progress.getBoolean("delivery_started")) {
                if (level.getBlockEntity(operation.position()) instanceof SacrificialBowlBlockEntity center) {
                    if (!recoverStack(operation, center, read(operation, progress, "activation"), true)) return false;
                }
                var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
                for (int index = 0; index < slots.size(); index++) {
                    var slot = slots.getCompound(index);
                    var position = BlockPos.of(slot.getLong("position"));
                    if (level.getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl) {
                        if (!recoverStack(operation, bowl, read(operation, slot, "input"), true)) return false;
                    }
                }
                if (progress.contains("output_bowl", Tag.TAG_LONG)) {
                    var output = BlockPos.of(progress.getLong("output_bowl"));
                    if (level.getBlockEntity(output) instanceof SacrificialBowlBlockEntity bowl) {
                        if (!recoverStack(operation, bowl, read(operation, progress, "result"), false)) return false;
                    }
                }
                if (!progress.getBoolean("recovery_use_settled")) {
                    RitualNativeActions.returnHeld(operation);
                    progress.remove("use_item");
                    progress.putBoolean("recovery_use_settled", true);
                }
            }
            progress.putBoolean("recovery_collected", true);
            operation.changed();
        }

        for (var drop : ownedDrops(operation)) {
            ItemStack stack = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        operation.changed();
        return true;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid persisted ritual cycle count");
        ItemStack expected = read(operation, progress, "result");
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        // delivery_started persists across cycles; physical assets identify a partial current cycle.
        if (!progress.getBoolean("delivered") && progress.getBoolean("delivery_started") &&
                hasPendingCycleAssets(operation, slots, expected)) {
            return retireInterrupted(operation);
        }
        if (progress.getBoolean("delivered")) {
            for (var position : occupiedPositions(operation.level(), operation.position(), progress)) {
                if (!operation.level().isLoaded(position)) return false;
            }
            if (!(operation.level().getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity center)) {
                return retireInterrupted(operation);
            }
            var active = center.getCurrentRitualRecipe();
            if (active != null) {
                if (!center.ritualActive || !active.id().equals(operation.recipeId())) return retireInterrupted(operation);
                return RitualNativeActions.advance(operation, center);
            }
            return collect(operation, center, slots, expected);
        }

        RecipeHolder<RitualRecipe> holder = recipe(operation.level(), operation.recipeId());
        if (holder == null) throw new IllegalStateException("Occultism ritual recipe is no longer supported");
        Layout layout = layout(operation.level(), operation.position(), holder.value());
        if (layout == null) return false;
        boolean hasOutput = progress.contains("output_bowl", Tag.TAG_LONG);
        if (hasOutput != (layout.output() != null) || hasOutput &&
                progress.getLong("output_bowl") != layout.output().getBlockPos().asLong())
            return false;
        if (!layout.empty()) {
            return progress.getBoolean("delivery_started") ? retireInterrupted(operation) : false;
        }
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

    private static boolean collect(PackagedMachineOperation operation, GoldenSacrificialBowlBlockEntity center,
                                   ListTag slots, ItemStack expected) {
        if (center.ritualActive) return retireInterrupted(operation);
        if (!center.itemStackHandler.getStackInSlot(0).isEmpty()) {
            return retireInterrupted(operation);
        }
        for (int index = 0; index < slots.size(); index++) {
            BlockPos position = BlockPos.of(slots.getCompound(index).getLong("position"));
            if (!(operation.level().getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl)) {
                return retireInterrupted(operation);
            }
            if (!bowl.itemStackHandler.getStackInSlot(0).isEmpty()) {
                return retireInterrupted(operation);
            }
        }
        if (operation.progress().contains("output_bowl", Tag.TAG_LONG)) {
            BlockPos outputPosition = BlockPos.of(operation.progress().getLong("output_bowl"));
            if (!(operation.level().getBlockEntity(outputPosition) instanceof SacrificialBowlBlockEntity output) ||
                    !output.getBlockState().hasProperty(BlockStateProperties.FACING) ||
                    output.getBlockState().getValue(BlockStateProperties.FACING) != Direction.DOWN) {
                return retireInterrupted(operation);
            }
            var inventory = output.itemStackHandler;
            ItemStack actual = inventory.getStackInSlot(0);
            if (actual.isEmpty()) return ownedDrops(operation).isEmpty() ? false : retireInterrupted(operation);
            if (!PackagedOutputMatching.matches(operation, expected, actual)) return retireInterrupted(operation);
            if (!ownedDrops(operation).isEmpty()) return retireInterrupted(operation);
            ItemStack extracted = inventory.extractItem(0, actual.getCount(), false);
            if (!extracted.isEmpty()) operation.returned(AEItemKey.of(extracted), extracted.getCount());
            if (!ItemStack.matches(extracted, actual)) throw new IllegalStateException("Incomplete Occultism output extraction");
        } else {
            var drops = ownedDrops(operation);
            if (drops.isEmpty()) return false;
            int count = 0;
            for (ItemEntity drop : drops) {
                if (!PackagedOutputMatching.sameKey(operation, expected, drop.getItem())) {
                    return retireInterrupted(operation);
                }
                count = Math.addExact(count, drop.getItem().getCount());
            }
            if (count != expected.getCount()) return retireInterrupted(operation);
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

    private static boolean retireInterrupted(PackagedMachineOperation operation) {
        PackagedMachineClaims.get(operation.level()).retireOperation(operation.id());
        return true;
    }

    private static boolean hasPendingCycleAssets(PackagedMachineOperation operation, ListTag slots, ItemStack expected) {
        var level = operation.level();
        if (level.getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity center &&
                (center.ritualActive || center.getCurrentRitualRecipe() != null ||
                        matchesSaved(center.itemStackHandler.getStackInSlot(0), read(operation, operation.progress(), "activation")))) {
            return true;
        }
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.getCompound(index);
            BlockPos position = BlockPos.of(slot.getLong("position"));
            if (level.isLoaded(position) && level.getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl &&
                    matchesSaved(bowl.itemStackHandler.getStackInSlot(0), read(operation, slot, "input"))) {
                return true;
            }
        }
        if (operation.progress().contains("output_bowl", Tag.TAG_LONG)) {
            BlockPos position = BlockPos.of(operation.progress().getLong("output_bowl"));
            if (level.isLoaded(position) && level.getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl &&
                    bowl.itemStackHandler.getStackInSlot(0).is(expected.getItem())) {
                return true;
            }
        }
        return !ownedDrops(operation).isEmpty();
    }

    private static boolean matchesSaved(ItemStack current, ItemStack expected) {
        return !current.isEmpty() && current.getCount() <= expected.getCount() &&
                AEItemKey.of(current).equals(AEItemKey.of(expected));
    }

    private static List<ItemEntity> ownedDrops(PackagedMachineOperation operation) {
        return operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(16),
                item -> PackagedEntityCapture.ownedBy(item, operation.id()));
    }

    private static boolean recoverStack(PackagedMachineOperation operation, SacrificialBowlBlockEntity bowl,
                                        ItemStack expected, boolean exactKey) {
        ItemStack current = bowl.itemStackHandler.getStackInSlot(0).copy();
        if (current.isEmpty() || current.getCount() > expected.getCount() ||
                (exactKey ? !matchesSaved(current, expected) : !current.is(expected.getItem()))) {
            return true;
        }
        ItemStack stack = bowl.itemStackHandler.extractItem(0, current.getCount(), false);
        if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
        return ItemStack.matches(stack, current);
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
