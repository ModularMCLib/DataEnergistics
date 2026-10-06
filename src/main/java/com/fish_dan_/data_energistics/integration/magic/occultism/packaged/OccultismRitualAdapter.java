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
import net.minecraft.core.registries.BuiltInRegistries;
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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
    public boolean supportsRecipe(ServerLevel level, BlockPos position, ResourceLocation recipeTypeId,
                                  ResourceLocation recipeId) {
        if (!TYPE.equals(recipeTypeId)) return false;
        var holder = recipe(level, recipeId);
        if (holder == null) return false;
        var layout = layout(level, position, holder.value());
        return layout != null && layout.centerEmpty();
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
        if (layout == null || !layout.centerEmpty()) return null;
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(holder.value().getActivationItem());
        ingredients.addAll(holder.value().getIngredients());
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
        var additional = assigned.subList(1, bowlIngredients);
        if (!holder.value().getRitual().matchesAdditionalIngredients(holder.value().getIngredients(), additional)) return null;
        var selected = selectInputs(level, layout, additional);
        if (selected == null) return null;
        if (!nativeInputSelection(layout, selected, holder.value().getIngredients())) return null;
        if (!choosesRecipe(level, position, recipeId, assigned.getFirst(), selected)) return null;
        ItemStack result = RitualItemResult.preview(level, holder.value(), assigned.getFirst(), additional);
        if (result.isEmpty() || cycles > Long.MAX_VALUE / result.getCount() || pattern.getOutputs().size() != 1) return null;
        if (!PackagedOutputMatching.matches(pattern, result, cycles * result.getCount())) return null;
        var slots = new ListTag();
        for (int index = 0; index < selected.size(); index++) {
            var binding = selected.get(index);
            var bowl = binding.bowl();
            var slot = new CompoundTag();
            slot.putLong("position", bowl.getBlockPos().asLong());
            slot.putString("role", "input");
            slot.putInt("index", index);
            slot.putString("bowl", bowlFingerprint(bowl));
            slot.put("input", binding.stack().save(level.registryAccess()));
            slots.add(slot);
        }
        var progress = new CompoundTag();
        progress.putString("ritual_recipe_type", TYPE.toString());
        progress.putString("ritual_recipe", recipeId.toString());
        progress.putString("pentacle_id", holder.value().getPentacleId().toString());
        progress.putString("dimension", level.dimension().location().toString());
        progress.putString("layout_fingerprint", fingerprint(level, position, holder.value(), selected));
        progress.put("bowls", slots);
        progress.put("activation", assigned.getFirst().save(level.registryAccess()));
        progress.put("result", result.save(level.registryAccess()));
        progress.putLong("cycles", cycles);
        if (holder.value().requiresItemUse()) progress.put("use_item", assigned.getLast().save(level.registryAccess()));
        progress.put("output_candidates", saveOutputCandidates(layout.outputCandidates()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        var slots = preparation.getList("bowls", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) positions.add(BlockPos.of(slots.getCompound(index).getLong("position")));
        return positions;
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        var level = operation.level();
        for (var position : occupiedPositions(level, operation.position(), operation.progress())) {
            if (!level.isLoaded(position)) return false;
        }

        var progress = operation.progress();
        var holder = recipe(level, operation.recipeId());
        if (holder == null) return false;
        var center = level.getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity golden ? golden : null;
        var active = center == null ? null : center.getCurrentRitualRecipe();
        if (center != null && center.ritualActive && (active == null || !active.id().equals(operation.recipeId()))) return false;
        if (!progress.getBoolean("recovery_inspected")) {
            if (progress.getBoolean("delivery_started")) {
                if (center != null && (active == null || !center.ritualActive)) {
                    // Native interruption drops these only while the recipe is still resolvable.
                    for (ItemStack consumed : center.consumedIngredients) {
                        if (!consumed.isEmpty()) operation.returned(AEItemKey.of(consumed), consumed.getCount());
                    }
                    center.consumedIngredients.clear();
                    center.setChanged();
                }
                if (center != null && center.ritualActive) {
                    PackagedEntityCapture.run(level, operation.id(), () -> center.stopRitual(false));
                    center.setChanged();
                }
            }
            progress.putBoolean("recovery_inspected", true);
            operation.changed();
        }

        if (!progress.getBoolean("recovery_collected")) {
            if (progress.getBoolean("delivery_started")) {
                if (center != null && !recoverStack(operation, center, read(operation, progress, "activation"))) return false;
                var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
                for (int index = 0; index < slots.size(); index++) {
                    var slot = slots.getCompound(index);
                    var position = BlockPos.of(slot.getLong("position"));
                    if (!"input".equals(slot.getString("role")) || slot.getInt("index") != index) return false;
                    if (level.getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl) {
                        if (bowl instanceof GoldenSacrificialBowlBlockEntity ||
                                !bowlFingerprint(bowl).equals(slot.getString("bowl")))
                            return false;
                        if (!recoverStack(operation, bowl, read(operation, slot, "input"))) return false;
                    }
                }
                if (!recoverOutput(operation, read(operation, progress, "result"))) return false;
                if (!progress.getBoolean("recovery_use_settled")) {
                    RitualNativeActions.returnHeld(operation);
                    progress.remove("use_item");
                    progress.putBoolean("recovery_use_settled", true);
                }
            }
            progress.putBoolean("recovery_collected", true);
            operation.changed();
        }

        var drops = ownedDrops(operation);
        for (var drop : drops) if (!knownRitualStack(operation, drop.getItem())) return false;
        for (var drop : drops) {
            ItemStack stack = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
        }
        return true;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid persisted ritual cycle count");
        ItemStack expected = read(operation, progress, "result");
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        RecipeHolder<RitualRecipe> holder = recipe(operation.level(), operation.recipeId());
        if (holder == null) throw new IllegalStateException("Occultism ritual recipe is no longer supported");
        // delivery_started persists across cycles; physical assets identify a partial current cycle.
        if (!progress.getBoolean("delivered") && progress.getBoolean("delivery_started") &&
                hasPendingCycleAssets(operation, slots, expected)) {
            return retireInterrupted(operation);
        }
        if (progress.getBoolean("delivered")) {
            for (var position : occupiedPositions(operation.level(), operation.position(), progress)) {
                if (!operation.level().isLoaded(position)) return false;
            }
            var layout = persistedLayout(operation, holder.value(), false);
            if (layout == null) return retireInterrupted(operation);
            var center = layout.center();
            var active = center.getCurrentRitualRecipe();
            if (active != null) {
                if (!center.ritualActive || !active.id().equals(operation.recipeId())) return retireInterrupted(operation);
                return RitualNativeActions.advance(operation, center);
            }
            return collect(operation, layout, expected);
        }

        var layout = persistedLayout(operation, holder.value(), true);
        if (layout == null) return false;
        if (!layout.centerEmpty()) {
            return progress.getBoolean("delivery_started") && retireInterrupted(operation);
        }
        ItemStack activation = read(operation, progress, "activation");
        var targets = new ObjectArrayList<SacrificialBowlBlockEntity>();
        var inputs = new ObjectArrayList<ItemStack>();
        var bindings = new ObjectArrayList<InputBinding>();
        var required = new KeyCounter();
        required.add(AEItemKey.of(activation), activation.getCount());
        if (slots.size() != holder.value().getIngredients().size()) return false;
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag slot = slots.getCompound(index);
            var position = BlockPos.of(slot.getLong("position"));
            var bowl = layout.bowls().stream().filter(candidate -> candidate.getBlockPos().equals(position)).findFirst();
            if (bowl.isEmpty() || targets.contains(bowl.get())) return false;
            if (!"input".equals(slot.getString("role")) || slot.getInt("index") != index ||
                    !bowlFingerprint(bowl.get()).equals(slot.getString("bowl")))
                return false;
            ItemStack stack = read(operation, slot, "input");
            if (stack.getCount() != 1 || isSpiritFireInput(operation.level(), bowl.get()) ||
                    !bowl.get().itemStackHandler.insertItem(0, stack, true).isEmpty())
                return false;
            targets.add(bowl.get());
            inputs.add(stack);
            bindings.add(new InputBinding(bowl.get(), stack));
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
        var nativeItems = nativeItems(layout.bowls(), bindings);
        if (activation.getCount() != 1 || !holder.value().getActivationItem().test(activation) ||
                !holder.value().getRitual().matchesAdditionalIngredients(holder.value().getIngredients(), nativeItems)) {
            throw new IllegalStateException("Occultism ritual inputs changed after preparation");
        }
        if (!nativeInputSelection(layout, bindings, holder.value().getIngredients())) {
            throw new IllegalStateException("Occultism ritual would consume an unowned sacrificial bowl");
        }
        if (!choosesRecipe(operation.level(), operation.position(), operation.recipeId(), activation, bindings)) return false;
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

    private static boolean collect(PackagedMachineOperation operation, Layout layout, ItemStack expected) {
        if (!(operation.level().getBlockEntity(operation.position()) instanceof GoldenSacrificialBowlBlockEntity center) ||
                layout.center() != center)
            return retireInterrupted(operation);
        if (center.ritualActive) return retireInterrupted(operation);
        if (!center.itemStackHandler.getStackInSlot(0).isEmpty()) {
            return retireInterrupted(operation);
        }
        var slots = operation.progress().getList("bowls", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.getCompound(index);
            var bowl = layout.bowls().stream()
                    .filter(candidate -> candidate.getBlockPos().asLong() == slot.getLong("position"))
                    .findFirst();
            if (bowl.isEmpty() || !bowl.get().itemStackHandler.getStackInSlot(0).isEmpty()) {
                return retireInterrupted(operation);
            }
        }
        return collectOutput(operation, expected, outputCandidates(operation, layout), ownedDrops(operation));
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
                                         ItemStack activation, List<InputBinding> selected) {
        for (var candidate : level.getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get())) {
            RitualRecipe recipe = candidate.value();
            if (!recipe.getActivationItem().test(activation)) continue;
            var pentacle = recipe.getPentacle();
            if (pentacle == null) continue;
            var bowls = recipe.getRitual().getSacrificialBowls(level, position);
            var items = nativeItems(bowls, selected);
            if (!recipe.getRitual().matchesAdditionalIngredients(recipe.getIngredients(), items)) continue;
            if (pentacle.validate(level, position) != null) return candidate.id().equals(expected);
        }
        return false;
    }

    private static boolean collectOutput(PackagedMachineOperation operation, ItemStack expected,
                                         @Nullable ObjectList<OutputBinding> candidates,
                                         List<ItemEntity> drops) {
        if (candidates == null) return false;
        SacrificialBowlBlockEntity output = null;
        ItemStack bowlStack = ItemStack.EMPTY;
        boolean blockedByEmptyCandidate = false;
        for (var candidate : candidates) {
            if (!candidate.emptyAtPreparation()) continue;
            var current = candidate.bowl().itemStackHandler.getStackInSlot(0);
            if (current.isEmpty()) {
                blockedByEmptyCandidate = true;
                break;
            }
            if (PackagedOutputMatching.sameKey(operation, expected, current)) {
                output = candidate.bowl();
                bowlStack = current.copy();
                break;
            }
        }
        if (blockedByEmptyCandidate && !drops.isEmpty()) return retireInterrupted(operation);
        int count = bowlStack.getCount();
        for (ItemEntity drop : drops) {
            if (!PackagedOutputMatching.sameKey(operation, expected, drop.getItem())) return retireInterrupted(operation);
            count = Math.addExact(count, drop.getItem().getCount());
        }
        if (count == 0 || count < expected.getCount()) return false;
        if (count > expected.getCount()) return retireInterrupted(operation);
        if (!bowlStack.isEmpty()) {
            if (output == null) return false;
            ItemStack extracted = output.itemStackHandler.extractItem(0, bowlStack.getCount(), false);
            if (extracted.isEmpty() || !ItemStack.matches(extracted, bowlStack))
                throw new IllegalStateException("Incomplete Occultism output extraction");
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
        }
        for (ItemEntity drop : drops) {
            ItemStack actual = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(actual), actual.getCount());
        }
        long cycles = operation.progress().getLong("cycles") - 1;
        RitualNativeActions.returnHeld(operation);
        operation.progress().putLong("cycles", cycles);
        operation.progress().putBoolean("delivered", false);
        operation.changed();
        if (cycles == 0) operation.complete();
        return true;
    }

    private static @Nullable Layout layout(ServerLevel level, BlockPos position, RitualRecipe recipe) {
        return layout(level, position, recipe, true);
    }

    private static @Nullable Layout layout(ServerLevel level, BlockPos position, RitualRecipe recipe,
                                           boolean checkCondition) {
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
        if (checkCondition && recipe.getCondition() != null &&
                !recipe.getCondition().test(RitualRecipeConditionContext.of(center)))
            return null;
        var outputCandidates = new ObjectArrayList<SacrificialBowlBlockEntity>();
        for (int height = 1; height <= 3; height++) {
            if (level.getBlockEntity(position.above(height)) instanceof SacrificialBowlBlockEntity bowl &&
                    bowl.getBlockState().hasProperty(BlockStateProperties.FACING) &&
                    bowl.getBlockState().getValue(BlockStateProperties.FACING) == Direction.DOWN) {
                outputCandidates.add(bowl);
            }
        }
        return new Layout(center, new ObjectArrayList<>(recipe.getRitual().getSacrificialBowls(level, position)),
                outputCandidates);
    }

    private static @Nullable Layout persistedLayout(PackagedMachineOperation operation, RitualRecipe recipe,
                                                    boolean checkCondition) {
        var progress = operation.progress();
        if (!TYPE.toString().equals(progress.getString("ritual_recipe_type")) ||
                !operation.recipeId().toString().equals(progress.getString("ritual_recipe")) ||
                !operation.level().dimension().location().toString().equals(progress.getString("dimension")) ||
                !recipe.getPentacleId().toString().equals(progress.getString("pentacle_id")))
            return null;
        var layout = layout(operation.level(), operation.position(), recipe, checkCondition);
        var currentFingerprint = fingerprint(operation.level(), operation.position(), recipe, progress);
        if (layout == null || currentFingerprint == null ||
                !currentFingerprint.equals(progress.getString("layout_fingerprint")))
            return null;
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        if (slots.size() != recipe.getIngredients().size()) return null;
        var inputPositions = new LongOpenHashSet();
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.getCompound(index);
            if (!"input".equals(slot.getString("role")) || slot.getInt("index") != index ||
                    !slot.contains("position", Tag.TAG_LONG))
                return null;
            long position = slot.getLong("position");
            if (!inputPositions.add(position) || layout.outputCandidates().stream()
                    .anyMatch(candidate -> candidate.getBlockPos().asLong() == position))
                return null;
            var bowl = layout.bowls().stream()
                    .filter(candidate -> candidate.getBlockPos().asLong() == position)
                    .findFirst();
            if (bowl.isEmpty() || bowl.get() instanceof GoldenSacrificialBowlBlockEntity ||
                    !bowlFingerprint(bowl.get()).equals(slot.getString("bowl")))
                return null;
        }
        return layout;
    }

    private static @Nullable ObjectList<InputBinding> selectInputs(ServerLevel level, Layout layout,
                                                                   List<ItemStack> assigned) {
        var selected = new ObjectArrayList<InputBinding>();
        for (ItemStack stack : assigned) {
            SacrificialBowlBlockEntity chosen = null;
            for (var bowl : layout.bowls()) {
                if (selected.stream().anyMatch(binding -> binding.bowl().getBlockPos().equals(bowl.getBlockPos())) ||
                        layout.outputCandidates().stream().anyMatch(candidate -> candidate.getBlockPos().equals(bowl.getBlockPos())) ||
                        !bowl.itemStackHandler.getStackInSlot(0).isEmpty() || isSpiritFireInput(level, bowl))
                    continue;
                chosen = bowl;
                break;
            }
            if (chosen == null) return null;
            selected.add(new InputBinding(chosen, stack.copy()));
        }
        return selected;
    }

    /**
     * Replays native first-match consumption and ensures every consumed bowl belongs to this operation.
     * Extra bowls remain visible to recipe matching, but a matching extra item may not be consumed silently.
     */
    private static boolean nativeInputSelection(Layout layout, List<InputBinding> selected,
                                                List<Ingredient> ingredients) {
        if (selected.size() != ingredients.size()) return false;
        boolean[] consumed = new boolean[layout.bowls().size()];
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            var expected = selected.get(ingredientIndex).bowl().getBlockPos();
            int found = -1;
            for (int bowlIndex = 0; bowlIndex < layout.bowls().size(); bowlIndex++) {
                if (consumed[bowlIndex]) continue;
                var bowl = layout.bowls().get(bowlIndex);
                var planned = selected.stream()
                        .filter(binding -> binding.bowl().getBlockPos().equals(bowl.getBlockPos()))
                        .findFirst()
                        .map(InputBinding::stack)
                        .orElseGet(() -> bowl.itemStackHandler.getStackInSlot(0));
                if (ingredients.get(ingredientIndex).test(planned)) {
                    found = bowlIndex;
                    break;
                }
            }
            if (found < 0 || !layout.bowls().get(found).getBlockPos().equals(expected)) return false;
            consumed[found] = true;
        }
        return true;
    }

    private static boolean isSpiritFireInput(ServerLevel level, SacrificialBowlBlockEntity bowl) {
        var below = level.getBlockState(bowl.getBlockPos().below());
        return below.getBlock() instanceof SpiritFireBlock || below.is(OccultismBlocks.SPIRIT_CAMPFIRE.get());
    }

    private static List<ItemStack> nativeItems(List<SacrificialBowlBlockEntity> bowls,
                                               List<InputBinding> selected) {
        var items = new ObjectArrayList<ItemStack>();
        for (var bowl : bowls) {
            var assigned = selected.stream()
                    .filter(binding -> binding.bowl().getBlockPos().equals(bowl.getBlockPos()))
                    .findFirst();
            if (assigned.isPresent()) {
                items.add(assigned.get().stack().copy());
            } else {
                var stack = bowl.itemStackHandler.getStackInSlot(0);
                if (!stack.isEmpty()) items.add(stack.copy());
            }
        }
        return items;
    }

    private static ListTag saveOutputCandidates(ObjectList<SacrificialBowlBlockEntity> candidates) {
        var result = new ListTag();
        for (int index = 0; index < candidates.size(); index++) {
            var bowl = candidates.get(index);
            var entry = new CompoundTag();
            entry.putString("role", "output");
            entry.putInt("index", index);
            entry.putLong("position", bowl.getBlockPos().asLong());
            entry.putString("bowl", bowlFingerprint(bowl));
            entry.putBoolean("empty", bowl.itemStackHandler.getStackInSlot(0).isEmpty());
            result.add(entry);
        }
        return result;
    }

    private static @Nullable ObjectList<OutputBinding> outputCandidates(PackagedMachineOperation operation,
                                                                        Layout layout) {
        var progress = operation.progress();
        var saved = progress.getList("output_candidates", Tag.TAG_COMPOUND);
        var result = new ObjectArrayList<OutputBinding>();
        for (int index = 0; index < saved.size(); index++) {
            var entry = saved.getCompound(index);
            if (!"output".equals(entry.getString("role")) || entry.getInt("index") != index ||
                    !entry.contains("position", Tag.TAG_LONG))
                return null;
            var bowl = layout.outputCandidates().stream()
                    .filter(candidate -> candidate.getBlockPos().asLong() == entry.getLong("position"))
                    .findFirst();
            if (bowl.isEmpty() || !bowlFingerprint(bowl.get()).equals(entry.getString("bowl"))) continue;
            result.add(new OutputBinding(bowl.get(), entry.getBoolean("empty")));
        }
        return result;
    }

    private static String fingerprint(ServerLevel level, BlockPos position, RitualRecipe recipe,
                                      ObjectList<InputBinding> selected) {
        var result = new StringBuilder(recipe.getPentacleId().toString())
                .append('|').append(level.dimension().location())
                .append('|').append(position.asLong());
        appendFingerprint(result, (SacrificialBowlBlockEntity) level.getBlockEntity(position));
        for (var binding : selected) appendFingerprint(result, binding.bowl());
        return result.toString();
    }

    private static @Nullable String fingerprint(ServerLevel level, BlockPos position, RitualRecipe recipe,
                                                CompoundTag progress) {
        if (!(level.getBlockEntity(position) instanceof GoldenSacrificialBowlBlockEntity center)) return null;
        var result = new StringBuilder(recipe.getPentacleId().toString())
                .append('|').append(level.dimension().location())
                .append('|').append(position.asLong());
        appendFingerprint(result, center);
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.getCompound(index);
            if (!slot.contains("position", Tag.TAG_LONG)) return null;
            if (!(level.getBlockEntity(BlockPos.of(slot.getLong("position"))) instanceof SacrificialBowlBlockEntity bowl)) return null;
            appendFingerprint(result, bowl);
        }
        return result.toString();
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
        var holder = recipe(level, operation.recipeId());
        if (holder != null) {
            var layout = layout(level, operation.position(), holder.value(), false);
            var candidates = layout == null ? null : outputCandidates(operation, layout);
            if (candidates != null) {
                for (var candidate : candidates) {
                    if (candidate.emptyAtPreparation() &&
                            matchesOutput(operation, candidate.bowl().itemStackHandler.getStackInSlot(0), expected))
                        return true;
                }
            }
        }
        return !ownedDrops(operation).isEmpty();
    }

    private static boolean matchesSaved(ItemStack current, ItemStack expected) {
        return !current.isEmpty() && current.getCount() <= expected.getCount() &&
                AEItemKey.of(current).equals(AEItemKey.of(expected));
    }

    private static List<ItemEntity> ownedDrops(PackagedMachineOperation operation) {
        var holder = recipe(operation.level(), operation.recipeId());
        int radius = 16;
        if (holder != null && holder.value().getPentacle() != null) {
            var size = holder.value().getPentacle().getSize();
            radius = Math.max(radius, Math.max(size.getX(), size.getZ()) + 2);
        }
        return operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(radius),
                item -> PackagedEntityCapture.ownedBy(item, operation.id()));
    }

    private static boolean recoverStack(PackagedMachineOperation operation, SacrificialBowlBlockEntity bowl,
                                        ItemStack expected) {
        ItemStack current = bowl.itemStackHandler.getStackInSlot(0).copy();
        if (current.isEmpty() || current.getCount() > expected.getCount() ||
                !matchesSaved(current, expected)) {
            return true;
        }
        ItemStack stack = bowl.itemStackHandler.extractItem(0, current.getCount(), false);
        if (!stack.isEmpty()) operation.returned(AEItemKey.of(stack), stack.getCount());
        return ItemStack.matches(stack, current);
    }

    private static boolean matchesOutput(PackagedMachineOperation operation, ItemStack current, ItemStack expected) {
        return !current.isEmpty() && current.getCount() <= expected.getCount() &&
                PackagedOutputMatching.sameKey(operation, expected, current);
    }

    private static boolean recoverOutput(PackagedMachineOperation operation, ItemStack expected) {
        var level = operation.level();
        var saved = operation.progress().getList("output_candidates", Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            var entry = saved.getCompound(index);
            if (!"output".equals(entry.getString("role")) || entry.getInt("index") != index ||
                    !entry.contains("position", Tag.TAG_LONG))
                return false;
            if (!entry.getBoolean("empty")) continue;
            var position = BlockPos.of(entry.getLong("position"));
            if (!level.isLoaded(position)) return false;
            if (!(level.getBlockEntity(position) instanceof SacrificialBowlBlockEntity bowl) ||
                    !bowlFingerprint(bowl).equals(entry.getString("bowl")))
                continue;
            ItemStack current = bowl.itemStackHandler.getStackInSlot(0).copy();
            if (!matchesOutput(operation, current, expected)) continue;
            ItemStack extracted = bowl.itemStackHandler.extractItem(0, current.getCount(), false);
            if (extracted.isEmpty() || !ItemStack.matches(extracted, current)) return false;
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
            break;
        }
        return true;
    }

    private static boolean knownRitualStack(PackagedMachineOperation operation, ItemStack stack) {
        if (stack.isEmpty()) return false;
        var progress = operation.progress();
        if (matchesSaved(stack, read(operation, progress, "activation"))) return true;
        var expected = read(operation, progress, "result");
        if (matchesOutput(operation, stack, expected)) return true;
        var slots = progress.getList("bowls", Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            if (matchesSaved(stack, read(operation, slots.getCompound(index), "input"))) return true;
        }
        var useItem = progress.contains("use_item", Tag.TAG_COMPOUND) ? read(operation, progress, "use_item") : ItemStack.EMPTY;
        return !useItem.isEmpty() && matchesSaved(stack, useItem);
    }

    private record Layout(GoldenSacrificialBowlBlockEntity center,
                          ObjectList<SacrificialBowlBlockEntity> bowls,
                          ObjectList<SacrificialBowlBlockEntity> outputCandidates) {

        boolean centerEmpty() {
            return !this.center.ritualActive && this.center.currentRitualRecipe == null &&
                    this.center.currentRitualRecipeId == null &&
                    this.center.itemStackHandler.getStackInSlot(0).isEmpty();
        }
    }

    private record InputBinding(SacrificialBowlBlockEntity bowl, ItemStack stack) {}

    private record OutputBinding(SacrificialBowlBlockEntity bowl, boolean emptyAtPreparation) {}

    private static void appendFingerprint(StringBuilder result, SacrificialBowlBlockEntity bowl) {
        var state = bowl.getBlockState();
        result.append('|').append(bowl.getBlockPos().asLong())
                .append(':').append(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (state.hasProperty(BlockStateProperties.FACING))
            result.append(':').append(state.getValue(BlockStateProperties.FACING).getName());
    }

    private static String bowlFingerprint(SacrificialBowlBlockEntity bowl) {
        var result = new StringBuilder();
        appendFingerprint(result, bowl);
        return result.toString();
    }
}
