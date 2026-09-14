package com.fish_dan_.data_energistics.recipe.timeshift;

import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class TimeShiftTransformLogic {

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int RECIPE_CANDIDATE_CACHE_LIMIT = 256;
    private static final double SEARCH_RADIUS = 1.0D;

    private final Object2ObjectLinkedOpenHashMap<RecipeCandidateKey, ObjectList<ResourceLocation>> recipeCandidateCache = new Object2ObjectLinkedOpenHashMap<>(RECIPE_CANDIDATE_CACHE_LIMIT, 0.75F);
    private long recipeCandidateCacheEpoch = Long.MIN_VALUE;

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity) || itemEntity.level().isClientSide()) {
            return;
        }

        if (itemEntity.isRemoved() || itemEntity.getItem().isEmpty() || itemEntity.getAge() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        tryTransform(itemEntity);
    }

    @SubscribeEvent
    public void onItemExpire(ItemExpireEvent event) {
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity.level().isClientSide()) {
            return;
        }

        int extraLife = getNeededExtraLife(itemEntity);
        if (extraLife > event.getExtraLife()) {
            event.setExtraLife(extraLife);
        }
    }

    private int getNeededExtraLife(ItemEntity itemEntity) {
        int age = itemEntity.getAge();
        int extraLife = 0;
        Level level = itemEntity.level();
        ObjectList<ResourceLocation> candidateIds = findRecipeCandidateIds(level, itemEntity.getItem());
        if (candidateIds.isEmpty()) {
            return 0;
        }

        ObjectList<ItemEntity> nearbyItems = getNearbyItems(level, itemEntity);
        for (ResourceLocation candidateId : candidateIds) {
            TimeShiftRecipe recipe = resolveRecipe(level, candidateId);
            if (recipe == null) {
                continue;
            }

            Reference2IntMap<ItemEntity> usedItems = findUsedItems(recipe, nearbyItems, false);
            if (usedItems == null || !usedItems.containsKey(itemEntity)) {
                continue;
            }

            extraLife = Math.max(extraLife, CHECK_INTERVAL_TICKS);
            if (age < recipe.getDurationTicks()) {
                extraLife = Math.max(extraLife, recipe.getDurationTicks() - age + CHECK_INTERVAL_TICKS);
            }
        }

        return extraLife;
    }

    private static boolean canBeIngredient(TimeShiftRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }

        return false;
    }

    private void tryTransform(ItemEntity trigger) {
        Level level = trigger.level();
        ObjectList<ResourceLocation> candidateIds = findRecipeCandidateIds(level, trigger.getItem());
        if (candidateIds.isEmpty()) {
            return;
        }

        ObjectList<ItemEntity> nearbyItems = null;
        for (ResourceLocation candidateId : candidateIds) {
            TimeShiftRecipe recipe = resolveRecipe(level, candidateId);
            if (recipe == null || trigger.getAge() < recipe.getDurationTicks() || !recipe.canRunAt(level)) {
                continue;
            }

            if (nearbyItems == null) {
                nearbyItems = getNearbyItems(level, trigger);
            }
            Reference2IntMap<ItemEntity> usedItems = findUsedItems(recipe, nearbyItems, true);
            if (usedItems == null) {
                continue;
            }

            consumeInputs(usedItems);
            spawnResults(level, trigger, recipe.getResults());
            return;
        }
    }

    private static ObjectList<ItemEntity> getNearbyItems(Level level, ItemEntity trigger) {
        AABB bounds = new AABB(
                trigger.getX() - SEARCH_RADIUS,
                trigger.getY() - SEARCH_RADIUS,
                trigger.getZ() - SEARCH_RADIUS,
                trigger.getX() + SEARCH_RADIUS,
                trigger.getY() + SEARCH_RADIUS,
                trigger.getZ() + SEARCH_RADIUS);

        return new ObjectArrayList<>(level.getEntitiesOfClass(
                ItemEntity.class,
                bounds,
                item -> !item.isRemoved() && !item.getItem().isEmpty()));
    }

    private ObjectList<ResourceLocation> findRecipeCandidateIds(Level level, ItemStack stack) {
        long reloadEpoch = RecipeReloadEpoch.current();
        if (this.recipeCandidateCacheEpoch != reloadEpoch) {
            this.recipeCandidateCache.clear();
            this.recipeCandidateCacheEpoch = reloadEpoch;
        }

        RecipeCandidateKey key = new RecipeCandidateKey(reloadEpoch, AEItemKey.of(stack), stack.getCount());
        ObjectList<ResourceLocation> cached = this.recipeCandidateCache.get(key);
        if (cached != null) {
            return cached;
        }

        ObjectArrayList<ResourceLocation> candidates = new ObjectArrayList<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(DERecipes.TIME_SHIFT_TYPE.get())) {
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

    private static @Nullable TimeShiftRecipe resolveRecipe(Level level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        return holder != null && holder.value() instanceof TimeShiftRecipe recipe ? recipe : null;
    }

    private static Reference2IntMap<ItemEntity> findUsedItems(TimeShiftRecipe recipe, ObjectList<ItemEntity> nearbyItems, boolean requireDuration) {
        ObjectList<Ingredient> remaining = new ObjectArrayList<>(recipe.getIngredients());
        Reference2IntMap<ItemEntity> usedItems = new Reference2IntOpenHashMap<>();

        for (ItemEntity itemEntity : nearbyItems) {
            if (requireDuration && itemEntity.getAge() < recipe.getDurationTicks()) {
                continue;
            }

            ItemStack stack = itemEntity.getItem();
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                int usedFromThisEntity = usedItems.getInt(itemEntity);
                if (stack.getCount() - usedFromThisEntity <= 0) {
                    break;
                }

                if (iterator.next().test(stack)) {
                    usedItems.put(itemEntity, usedFromThisEntity + 1);
                    iterator.remove();
                }
            }

            if (remaining.isEmpty()) {
                return usedItems;
            }
        }

        return null;
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

    private static void spawnResults(Level level, ItemEntity trigger, List<ItemStack> results) {
        for (ItemStack result : results) {
            if (result.isEmpty()) {
                continue;
            }

            ItemEntity output = new ItemEntity(level, trigger.getX(), trigger.getY(), trigger.getZ(), result.copy());
            output.setDeltaMovement(trigger.getDeltaMovement().scale(0.25D));
            level.addFreshEntity(output);
        }
    }

    private record RecipeCandidateKey(long reloadEpoch, AEItemKey item, int count) {}
}
