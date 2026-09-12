package com.fish_dan_.data_energistics.integration.ae.appliedcreate;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Applied Create mechanical-crafting route registered by the Applied Create integration.
 *
 * <p>
 * The adaptive provider only supplies generic provider operations through
 * {@link AdaptivePatternProviderDispatchTarget};
 * this class owns Create-specific chain discovery, recipe indexing, grid matching, and insertion.
 * </p>
 */
public final class AppliedCreateAdaptiveRoute implements AdaptivePatternProviderDispatch {

    private static final Object RECIPE_INDEX_LOCK = new Object();
    private static volatile RecipeIndex recipeIndex = RecipeIndex.empty();

    @Override
    public boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean handles(AdaptivePatternProviderDispatchContext context) {
        return true;
    }

    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext dispatchContext) {
        var context = dispatchContext.target();
        IPatternDetails patternDetails = dispatchContext.patternDetails();
        KeyCounter[] inputHolder = dispatchContext.inputHolder();
        if (context.isBusy() || !context.isActive() || !context.hasPattern(patternDetails) || context.isCraftingLocked()) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        Level level = context.level();
        if (level == null) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        GenericStack primaryOutput = patternDetails.getPrimaryOutput();
        if (primaryOutput == null || !(primaryOutput.what() instanceof AEItemKey primaryOutputKey)) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        List<CrafterCandidate> candidates = collectCrafterCandidates(context, level);
        if (candidates.isEmpty()) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        List<RecipeInfo> recipes = recipesFor(level, primaryOutputKey);
        if (recipes.isEmpty()) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        List<ItemStack> flattenedInputs = flattenInputs(inputHolder);
        if (flattenedInputs.isEmpty()) {
            return context.pushDefault(patternDetails, inputHolder);
        }

        for (CrafterCandidate candidate : candidates) {
            if (tryPushRecipe(context, recipes, candidate.chain(), candidate.grid(), flattenedInputs)) {
                context.patternSuccess(patternDetails);
                return true;
            }
        }

        return context.pushDefault(patternDetails, inputHolder);
    }

    private static List<CrafterCandidate> collectCrafterCandidates(
                                                                   AdaptivePatternProviderDispatchTarget context,
                                                                   Level level) {
        ObjectArrayList<CrafterCandidate> candidates = new ObjectArrayList<>();
        for (Direction side : context.targetSides()) {
            BlockPos position = context.providerPos().relative(side);
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (!(blockEntity instanceof MechanicalCrafterBlockEntity crafter)) {
                continue;
            }

            List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(crafter);
            if (chain == null || chain.isEmpty()) {
                continue;
            }

            Object2ObjectMap<GridCoord, MechanicalCrafterBlockEntity> grid = computeGrid(chain);
            if (grid != null) {
                candidates.add(new CrafterCandidate(chain, grid));
            }
        }
        return candidates;
    }

    private static List<RecipeInfo> recipesFor(Level level, AEItemKey expectedOutput) {
        long reloadEpoch = RecipeReloadEpoch.current();
        RecipeIndex current = recipeIndex;
        if (current.reloadEpoch() == reloadEpoch) {
            return current.recipesFor(expectedOutput);
        }

        synchronized (RECIPE_INDEX_LOCK) {
            reloadEpoch = RecipeReloadEpoch.current();
            current = recipeIndex;
            if (current.reloadEpoch() != reloadEpoch) {
                current = buildRecipeIndex(level, reloadEpoch);
                recipeIndex = current;
            }
            return current.recipesFor(expectedOutput);
        }
    }

    private static RecipeIndex buildRecipeIndex(Level level, long reloadEpoch) {
        Object2ObjectMap<AEItemKey, List<RecipeInfo>> recipesByOutput = new Object2ObjectOpenHashMap<>();
        HolderLookup.Provider registries = level.registryAccess();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof MechanicalCraftingRecipe recipe) {
                AEItemKey output = AEItemKey.of(recipe.getResultItem(registries));
                if (output != null) {
                    addRecipe(recipesByOutput, output, new RecipeInfo(recipe.getWidth(), recipe.getHeight(), recipe.getIngredients()));
                }
            }
        }

        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof ShapedRecipe recipe)) {
                continue;
            }
            AEItemKey output = AEItemKey.of(recipe.getResultItem(registries));
            if (output != null) {
                addRecipe(recipesByOutput, output, new RecipeInfo(recipe.getWidth(), recipe.getHeight(), recipe.getIngredients()));
            }
        }
        return new RecipeIndex(reloadEpoch, recipesByOutput);
    }

    private static void addRecipe(Object2ObjectMap<AEItemKey, List<RecipeInfo>> recipesByOutput, AEItemKey output, RecipeInfo recipe) {
        recipesByOutput.computeIfAbsent(output, ignored -> new ObjectArrayList<>()).add(recipe);
    }

    private static List<ItemStack> flattenInputs(KeyCounter[] inputHolder) {
        ObjectArrayList<ItemStack> stacks = new ObjectArrayList<>();
        for (KeyCounter input : inputHolder) {
            for (var entry : input) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                int amount = (int) Math.min(Integer.MAX_VALUE, entry.getLongValue());
                for (int index = 0; index < amount; index++) {
                    stacks.add(itemKey.toStack(1));
                }
            }
        }
        return stacks;
    }

    @Nullable
    private static Object2ObjectMap<GridCoord, MechanicalCrafterBlockEntity> computeGrid(List<MechanicalCrafterBlockEntity> crafters) {
        ObjectSet<MechanicalCrafterBlockEntity> crafterSet = new ObjectOpenHashSet<>(crafters);
        Object2ObjectMap<MechanicalCrafterBlockEntity, MechanicalCrafterBlockEntity> parentByCrafter = new Object2ObjectOpenHashMap<>();
        for (MechanicalCrafterBlockEntity crafter : crafters) {
            MechanicalCrafterBlockEntity target = RecipeGridHandler.getTargetingCrafter(crafter);
            parentByCrafter.put(crafter, target != null && crafterSet.contains(target) ? target : null);
        }

        MechanicalCrafterBlockEntity root = null;
        for (MechanicalCrafterBlockEntity crafter : crafters) {
            if (parentByCrafter.get(crafter) == null) {
                root = crafter;
                break;
            }
        }
        if (root == null) {
            return null;
        }

        Object2ObjectMap<MechanicalCrafterBlockEntity, GridCoord> rawPositions = new Object2ObjectOpenHashMap<>();
        ObjectArrayFIFOQueue<MechanicalCrafterBlockEntity> queue = new ObjectArrayFIFOQueue<>();
        ObjectSet<MechanicalCrafterBlockEntity> visited = new ObjectOpenHashSet<>();
        rawPositions.put(root, new GridCoord(0, 0));
        queue.enqueue(root);
        visited.add(root);
        while (!queue.isEmpty()) {
            MechanicalCrafterBlockEntity current = queue.dequeue();
            GridCoord currentCoord = rawPositions.get(current);
            if (currentCoord == null) {
                continue;
            }
            for (MechanicalCrafterBlockEntity candidate : crafters) {
                if (visited.contains(candidate) || parentByCrafter.get(candidate) != current) {
                    continue;
                }
                var pointing = candidate.getBlockState().getValue(MechanicalCrafterBlock.POINTING);
                int dx = switch (pointing) {
                    case RIGHT -> 1;
                    case LEFT -> -1;
                    default -> 0;
                };
                int dy = switch (pointing) {
                    case UP -> 1;
                    case DOWN -> -1;
                    default -> 0;
                };
                rawPositions.put(candidate, new GridCoord(currentCoord.x() + dx, currentCoord.y() + dy));
                visited.add(candidate);
                queue.enqueue(candidate);
            }
        }
        if (rawPositions.size() != crafters.size()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (GridCoord coord : rawPositions.values()) {
            minX = Math.min(minX, coord.x());
            minY = Math.min(minY, coord.y());
        }
        Object2ObjectMap<GridCoord, MechanicalCrafterBlockEntity> normalized = new Object2ObjectOpenHashMap<>();
        for (var entry : rawPositions.object2ObjectEntrySet()) {
            GridCoord coord = entry.getValue();
            normalized.put(new GridCoord(coord.x() - minX, coord.y() - minY), entry.getKey());
        }
        return normalized;
    }

    private static boolean tryPushRecipe(
                                         AdaptivePatternProviderDispatchTarget context,
                                         List<RecipeInfo> recipes,
                                         List<MechanicalCrafterBlockEntity> crafterChain,
                                         Object2ObjectMap<GridCoord, MechanicalCrafterBlockEntity> crafterGrid,
                                         List<ItemStack> flattenedInputs) {
        int gridWidth = 0;
        int gridHeight = 0;
        for (GridCoord coord : crafterGrid.keySet()) {
            gridWidth = Math.max(gridWidth, coord.x() + 1);
            gridHeight = Math.max(gridHeight, coord.y() + 1);
        }

        for (RecipeInfo recipe : recipes) {
            if (recipe.width() > gridWidth || recipe.height() > gridHeight) {
                continue;
            }
            for (int offsetX = 0; offsetX <= gridWidth - recipe.width(); offsetX++) {
                for (int offsetY = 0; offsetY <= gridHeight - recipe.height(); offsetY++) {
                    ObjectArrayList<SlotAssignment> assignments = new ObjectArrayList<>();
                    ObjectArrayList<ItemStack> remainingInputs = new ObjectArrayList<>(flattenedInputs);
                    boolean matched = true;
                    for (int row = 0; row < recipe.height() && matched; row++) {
                        for (int col = 0; col < recipe.width(); col++) {
                            int ingredientIndex = col + row * recipe.width();
                            Ingredient ingredient = ingredientIndex < recipe.ingredients().size() ? recipe.ingredients().get(ingredientIndex) : null;
                            if (ingredient == null || ingredient.isEmpty()) {
                                continue;
                            }
                            MechanicalCrafterBlockEntity crafter = crafterGrid.get(new GridCoord(col + offsetX, row + offsetY));
                            if (crafter == null) {
                                matched = false;
                                break;
                            }
                            int inputIndex = findMatchingInput(remainingInputs, ingredient);
                            if (inputIndex < 0) {
                                matched = false;
                                break;
                            }
                            ItemStack inputStack = remainingInputs.get(inputIndex);
                            if (!crafter.getInventory().insertItem(0, inputStack.copy(), true).isEmpty()) {
                                matched = false;
                                break;
                            }
                            assignments.add(new SlotAssignment(crafter, inputStack.copy()));
                            remainingInputs.remove(inputIndex);
                        }
                    }
                    if (!matched) {
                        continue;
                    }
                    for (SlotAssignment assignment : assignments) {
                        assignment.crafter().getInventory().insertItem(0, assignment.stack(), false);
                    }
                    if (!crafterChain.isEmpty()) {
                        crafterChain.getFirst().checkCompletedRecipe(true);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static int findMatchingInput(List<ItemStack> inputs, Ingredient ingredient) {
        for (int index = 0; index < inputs.size(); index++) {
            if (ingredient.test(inputs.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private record CrafterCandidate(List<MechanicalCrafterBlockEntity> chain, Object2ObjectMap<GridCoord, MechanicalCrafterBlockEntity> grid) {}

    private record GridCoord(int x, int y) {}

    private record RecipeInfo(int width, int height, List<Ingredient> ingredients) {

        private RecipeInfo {
            ingredients = ObjectLists.unmodifiable(new ObjectArrayList<>(ingredients));
        }
    }

    private record SlotAssignment(MechanicalCrafterBlockEntity crafter, ItemStack stack) {}

    private record RecipeIndex(long reloadEpoch, Object2ObjectMap<AEItemKey, List<RecipeInfo>> recipesByOutput) {

        private RecipeIndex {
            recipesByOutput = Object2ObjectMaps.unmodifiable(new Object2ObjectOpenHashMap<>(recipesByOutput));
        }

        private static RecipeIndex empty() {
            return new RecipeIndex(Long.MIN_VALUE, Object2ObjectMaps.emptyMap());
        }

        private List<RecipeInfo> recipesFor(AEItemKey output) {
            return recipesByOutput.getOrDefault(output, ObjectList.of());
        }
    }
}
