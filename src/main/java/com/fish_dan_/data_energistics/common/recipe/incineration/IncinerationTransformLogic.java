package com.fish_dan_.data_energistics.common.recipe.incineration;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;
import com.fish_dan_.data_energistics.common.data.DERecipes;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;

import appeng.api.stacks.AEItemKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class IncinerationTransformLogic {

    private static final int RECIPE_CANDIDATE_CACHE_LIMIT = 256;
    private static final double FIRE_SEARCH_RADIUS = 0.35D;
    private static final double INPUT_SEARCH_RADIUS = 0.35D;

    private final Object2ObjectLinkedOpenHashMap<RecipeCandidateKey, ObjectList<ResourceLocation>> recipeCandidateCache = new Object2ObjectLinkedOpenHashMap<>(RECIPE_CANDIDATE_CACHE_LIMIT, 0.75F);
    private long recipeCandidateCacheEpoch = Long.MIN_VALUE;

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ItemEntity trigger) || trigger.level().isClientSide()) {
            return;
        }

        if (trigger.isRemoved() || trigger.getItem().isEmpty()) {
            return;
        }

        tryTransform(trigger);
    }

    private void tryTransform(ItemEntity trigger) {
        Level level = trigger.level();
        ObjectList<ResourceLocation> candidateIds = findRecipeCandidateIds(level, trigger.getItem());
        if (candidateIds.isEmpty()) {
            return;
        }

        for (ResourceLocation candidateId : candidateIds) {
            IncinerationRecipe recipe = resolveRecipe(level, candidateId);
            if (recipe == null) {
                continue;
            }

            BlockPos firePos = findFireOverBase(level, trigger, recipe);
            if (firePos == null) {
                continue;
            }

            Reference2IntMap<ItemEntity> usedItems = findUsedItems(
                    recipe,
                    getNearbyItems(level, firePos),
                    trigger);
            if (usedItems == null) {
                continue;
            }

            BlockPos basePos = firePos.below();
            var transformedBlock = recipe.getTransformedBlock();
            if (transformedBlock.isPresent() && transformedBlock.get() != level.getBlockState(basePos).getBlock() &&
                    !level.setBlock(basePos, transformedBlock.get().defaultBlockState(), 3)) {
                continue;
            }
            consumeInputs(usedItems);
            spawnResults(level, firePos, recipe.getResults());
            return;
        }
    }

    private ObjectList<ResourceLocation> findRecipeCandidateIds(Level level, ItemStack stack) {
        long reloadEpoch = RecipeReloadEpoch.current();
        if (this.recipeCandidateCacheEpoch != reloadEpoch) {
            this.recipeCandidateCache.clear();
            this.recipeCandidateCacheEpoch = reloadEpoch;
        }

        RecipeCandidateKey key = new RecipeCandidateKey(reloadEpoch, AEItemKey.of(stack));
        ObjectList<ResourceLocation> cached = this.recipeCandidateCache.get(key);
        if (cached != null) {
            return cached;
        }

        ObjectArrayList<ResourceLocation> candidates = new ObjectArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(DERecipes.INCINERATION_TYPE.get())) {
            if (canBeIngredient(holder.value(), stack)) {
                candidates.add(holder.id());
            }
        }

        ObjectList<ResourceLocation> result = candidates.isEmpty() ? ObjectLists.emptyList() : ObjectLists.unmodifiable(candidates);
        this.recipeCandidateCache.put(key, result);
        if (this.recipeCandidateCache.size() > RECIPE_CANDIDATE_CACHE_LIMIT) {
            var eldest = this.recipeCandidateCache.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return result;
    }

    private static boolean canBeIngredient(IncinerationRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable IncinerationRecipe resolveRecipe(Level level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        return holder != null && holder.value() instanceof IncinerationRecipe recipe ? recipe : null;
    }

    private static @Nullable BlockPos findFireOverBase(Level level, ItemEntity trigger, IncinerationRecipe recipe) {
        AABB bounds = trigger.getBoundingBox().inflate(FIRE_SEARCH_RADIUS);
        BlockPos minimum = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos maximum = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
            if (level.getBlockState(position).is(Blocks.FIRE) && level.getBlockState(position.below()).is(recipe.getBaseBlock())) {
                return position.immutable();
            }
        }
        return null;
    }

    private static ObjectList<ItemEntity> getNearbyItems(Level level, BlockPos firePos) {
        return new ObjectArrayList<>(level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(firePos).inflate(INPUT_SEARCH_RADIUS),
                item -> !item.isRemoved() && !item.getItem().isEmpty()));
    }

    private static @Nullable Reference2IntMap<ItemEntity> findUsedItems(
                                                                        IncinerationRecipe recipe,
                                                                        ObjectList<ItemEntity> nearbyItems,
                                                                        ItemEntity trigger) {
        ObjectList<Ingredient> remaining = new ObjectArrayList<>(recipe.getIngredients());
        Reference2IntMap<ItemEntity> usedItems = new Reference2IntOpenHashMap<>();
        boolean triggerUsed = false;

        for (ItemEntity itemEntity : nearbyItems) {
            ItemStack stack = itemEntity.getItem();
            var iterator = remaining.iterator();
            int usedFromThisEntity = 0;
            while (iterator.hasNext() && stack.getCount() > usedFromThisEntity) {
                if (iterator.next().test(stack)) {
                    usedFromThisEntity++;
                    iterator.remove();
                }
            }
            if (usedFromThisEntity > 0) {
                usedItems.put(itemEntity, usedFromThisEntity);
                triggerUsed |= itemEntity == trigger;
            }
        }

        return remaining.isEmpty() && triggerUsed ? usedItems : null;
    }

    private static void consumeInputs(Reference2IntMap<ItemEntity> usedItems) {
        for (Reference2IntMap.Entry<ItemEntity> entry : usedItems.reference2IntEntrySet()) {
            ItemEntity itemEntity = entry.getKey();
            itemEntity.getItem().shrink(entry.getIntValue());
            if (itemEntity.getItem().isEmpty()) {
                itemEntity.discard();
            }
        }
    }

    private static void spawnResults(Level level, BlockPos firePos, List<ItemStack> results) {
        Vec3 outputPosition = findOutputPosition(level, firePos);
        for (ItemStack result : results) {
            if (result.isEmpty()) {
                continue;
            }

            ItemEntity output = new ItemEntity(level, outputPosition.x, outputPosition.y, outputPosition.z, result.copy());
            output.setDefaultPickUpDelay();
            level.addFreshEntity(output);
        }
    }

    private static Vec3 findOutputPosition(Level level, BlockPos firePos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = firePos.relative(direction);
            if (level.getBlockState(adjacent).isAir()) {
                return new Vec3(adjacent.getX() + 0.5D, adjacent.getY() + 0.2D, adjacent.getZ() + 0.5D);
            }
        }
        return new Vec3(firePos.getX() + 0.5D, firePos.getY() + 1.2D, firePos.getZ() + 0.5D);
    }

    private record RecipeCandidateKey(long reloadEpoch, AEItemKey item) {}
}
