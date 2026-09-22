package com.fish_dan_.data_energistics.integration.magic.neovitae.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

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
import net.minecraft.world.phys.AABB;

import com.breakinblocks.neovitae.common.blockentity.HellfireForgeBlockEntity;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.NVItems;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeInput;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeRecipe;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.breakinblocks.neovitae.spiritus.SpiritusHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Runs native forge recipes with either a pre-installed gem or a counted supplied gem. Installed gems
 * remain in the machine for successive crafts. Supplied gems return with their real charged components;
 * their world-dependent charge is not a deterministic CPU reusable-input transition.
 */
public final class HellfireForgeAdapter implements PackagedMachineAdapter {

    private static final String INSTALLED_GEM_MARKER = "data_energistics_installed_forge_gem";
    private static final String OWNER = "data_energistics_forge_operation";
    private static final String INSERTED = "inserted_slots";

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("neovitae", "hellfire_forge");
    private static final String SLOTS = "slots";
    private static final String GEM = "gem";
    private static final String OUTPUT = "output";
    private static final String LOADED = "loaded";
    private static final String HARVEST_SLOT = "harvest_slot";
    private static final String INSTALLED_GEM = "installed_gem";

    /** Whether the current task delivered the gem; an unstarted claim never owns a player's installed gem. */
    public static boolean hasSuppliedGem(HellfireForgeBlockEntity forge) {
        return forge.getPersistentData().hasUUID(OWNER) && !forge.getPersistentData().getBoolean(INSTALLED_GEM_MARKER);
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("neovitae_hellfire_forge");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof HellfireForgeBlockEntity;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof HellfireForgeBlockEntity forge) ||
                !emptyInputsAndOutput(forge))
            return null;
        ForgeRecipe recipe = recipe(level, recipeId);
        if (recipe == null || !validCosts(recipe)) return null;
        ItemStack installed = forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT);
        Plan plan = installed.isEmpty() ? plan(level, recipeId, recipe, inputs) :
                assign(level, recipeId, recipe, inputs, installed);
        if (plan == null || pattern.getOutputs().size() != 1 ||
                !PackagedOutputMatching.matches(pattern, recipe.resultItem, recipe.resultItem.getCount()))
            return null;
        var result = new CompoundTag();
        result.put(SLOTS, saveStacks(plan.slots(), level));
        result.put(GEM, plan.gem().save(level.registryAccess()));
        result.put(OUTPUT, recipe.resultItem.save(level.registryAccess()));
        result.putBoolean(INSTALLED_GEM, !installed.isEmpty());
        return result;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) ||
                !(operation.level().getBlockEntity(operation.position()) instanceof HellfireForgeBlockEntity forge))
            return false;
        return operation.progress().getBoolean(LOADED) ? harvest(operation, forge) : load(operation, forge);
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        if (!operation.level().isLoaded(operation.position())) return false;
        if (operation.level().getBlockEntity(operation.position()) instanceof HellfireForgeBlockEntity forge &&
                forge.getPersistentData().hasUUID(OWNER) && operation.id().equals(forge.getPersistentData().getUUID(OWNER))) {
            int inserted = operation.progress().getInt(INSERTED);
            for (int slot = 0; slot <= HellfireForgeBlockEntity.OUTPUT_SLOT; slot++) {
                if (slot == HellfireForgeBlockEntity.GEM_SLOT && operation.progress().getBoolean(INSTALLED_GEM)) continue;
                if ((inserted & 1 << slot) == 0 && !(slot == HellfireForgeBlockEntity.OUTPUT_SLOT && operation.progress().getBoolean(LOADED))) continue;
                ItemStack actual = forge.inv.getStackInSlot(slot);
                if (actual.isEmpty()) continue;
                ItemStack recovered = forge.inv.extractItem(slot, actual.getCount(), false);
                if (!sameStack(actual, recovered)) throw new IllegalStateException("Hellfire Forge recovery extraction was incomplete");
                operation.returned(AEItemKey.of(recovered), recovered.getCount());
            }
            forge.getPersistentData().remove(OWNER);
            forge.setChanged();
        }
        for (var drop : operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(2),
                item -> PackagedEntityCapture.ownedBy(item, operation.id()))) {
            ItemStack actual = drop.getItem();
            if (!actual.isEmpty()) operation.returned(AEItemKey.of(actual), actual.getCount());
            drop.discard();
        }
        return true;
    }

    private static boolean load(PackagedMachineOperation operation, HellfireForgeBlockEntity forge) {
        if (!emptyInputsAndOutput(forge)) return false;
        ForgeRecipe recipe = recipe(operation.level(), operation.recipeId());
        if (recipe == null || !validCosts(recipe)) return false;
        ObjectList<ItemStack> slots = readStacks(operation, operation.progress().getList(SLOTS, Tag.TAG_COMPOUND));
        boolean installed = operation.progress().getBoolean(INSTALLED_GEM);
        ItemStack gem = installed ? forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT) : readStack(operation, GEM);
        if (!installed && !forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT).isEmpty()) return false;
        if (slots.size() != HellfireForgeBlockEntity.GEM_SLOT || !validGem(gem, recipe)) return false;
        ForgeInput nativeInput = new ForgeInput(slots, gem, HellfireForgeBlockEntity.GEM_SLOT);
        if (!recipe.matches(nativeInput, operation.level()) || !selects(operation.level(), operation.recipeId(), nativeInput))
            return false;

        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (operation.available(key).compareTo(BigInteger.ONE) < 0 ||
                    !forge.inv.insertItem(slot, stack.copy(), true).isEmpty())
                return false;
        }
        AEItemKey gemKey = AEItemKey.of(gem);
        if (!installed && (operation.available(gemKey).compareTo(BigInteger.ONE) < 0 ||
                !forge.inv.insertItem(HellfireForgeBlockEntity.GEM_SLOT, gem.copy(), true).isEmpty()))
            return false;

        forge.getPersistentData().putUUID(OWNER, operation.id());
        forge.getPersistentData().putBoolean(INSTALLED_GEM_MARKER, installed);
        forge.setChanged();
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = forge.inv.insertItem(slot, stack.copy(), false);
            if (!remainder.isEmpty() || !sameStack(stack, forge.inv.getStackInSlot(slot)))
                throw new IllegalStateException("Hellfire Forge rejected a preflighted ingredient");
            operation.delivered(AEItemKey.of(stack), 1);
            operation.progress().putInt(INSERTED, operation.progress().getInt(INSERTED) | 1 << slot);
        }
        if (!installed) {
            ItemStack gemRemainder = forge.inv.insertItem(HellfireForgeBlockEntity.GEM_SLOT, gem.copy(), false);
            if (!gemRemainder.isEmpty() || !sameStack(gem, forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT)))
                throw new IllegalStateException("Hellfire Forge rejected the preflighted Greater Spiritus Gem");
            operation.delivered(gemKey, 1);
            operation.progress().putInt(INSERTED, operation.progress().getInt(INSERTED) | 1 << HellfireForgeBlockEntity.GEM_SLOT);
        }
        operation.progress().putBoolean(LOADED, true);
        operation.changed();
        return true;
    }

    private static boolean harvest(PackagedMachineOperation operation, HellfireForgeBlockEntity forge) {
        CompoundTag progress = operation.progress();
        int harvestSlot = progress.getInt(HARVEST_SLOT);
        if (harvestSlot == 0) {
            ItemStack expected = readStack(operation, OUTPUT);
            ItemStack output = forge.inv.getStackInSlot(HellfireForgeBlockEntity.OUTPUT_SLOT);
            ItemStack gem = forge.inv.getStackInSlot(HellfireForgeBlockEntity.GEM_SLOT);
            if (!PackagedOutputMatching.matches(operation, expected, output) ||
                    !gem.is(NVItems.SPIRITUS_GEM_GREATER.get()) || !gem.has(NVDataComponents.SPIRITUS_AMOUNT))
                return false;
            if (!progress.getBoolean(INSTALLED_GEM)) {
                ItemStack extractedGem = forge.inv.extractItem(HellfireForgeBlockEntity.GEM_SLOT, gem.getCount(), false);
                if (!sameStack(gem, extractedGem)) throw new IllegalStateException("Hellfire Forge gem extraction was incomplete");
                operation.returned(AEItemKey.of(extractedGem), extractedGem.getCount());
            }
            ItemStack extractedOutput = forge.inv.extractItem(HellfireForgeBlockEntity.OUTPUT_SLOT, output.getCount(), false);
            if (!sameStack(output, extractedOutput)) throw new IllegalStateException("Hellfire Forge output extraction was incomplete");
            operation.returned(AEItemKey.of(extractedOutput), extractedOutput.getCount());
            progress.putInt(HARVEST_SLOT, 1);
            operation.changed();
            return true;
        }
        int inputSlot = harvestSlot - 1;
        if (inputSlot < HellfireForgeBlockEntity.GEM_SLOT) {
            ItemStack remainder = forge.inv.getStackInSlot(inputSlot);
            if (!remainder.isEmpty()) {
                ItemStack extracted = forge.inv.extractItem(inputSlot, remainder.getCount(), false);
                if (!sameStack(remainder, extracted))
                    throw new IllegalStateException("Hellfire Forge remainder extraction was incomplete");
                operation.returned(AEItemKey.of(extracted), extracted.getCount());
            }
            progress.putInt(HARVEST_SLOT, harvestSlot + 1);
            operation.changed();
            return true;
        }
        forge.getPersistentData().remove(OWNER);
        forge.setChanged();
        operation.complete();
        return true;
    }

    private static @Nullable Plan plan(ServerLevel level, ResourceLocation recipeId, ForgeRecipe recipe,
                                       KeyCounter[] inputs) {
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return null;
                ItemStack gem = key.toStack().copyWithCount(1);
                if (!validGem(gem, recipe)) continue;
                KeyCounter remaining = withoutOne(inputs, key);
                if (remaining == null) return null;
                Plan plan = assign(level, recipeId, recipe, new KeyCounter[] { remaining }, gem);
                if (plan != null) return plan;
            }
        }
        return null;
    }

    private static @Nullable Plan assign(ServerLevel level, ResourceLocation recipeId, ForgeRecipe recipe,
                                         KeyCounter[] inputs, ItemStack gem) {
        if (!validGem(gem, recipe)) return null;
        ObjectList<ItemStack> assigned = PackagedIngredientAssignment.match(
                new ObjectArrayList<Ingredient>(recipe.ingredients), inputs);
        if (assigned == null || assigned.size() > HellfireForgeBlockEntity.GEM_SLOT) return null;
        var slots = new ObjectArrayList<ItemStack>(assigned);
        while (slots.size() < HellfireForgeBlockEntity.GEM_SLOT) slots.add(ItemStack.EMPTY);
        ForgeInput nativeInput = new ForgeInput(slots, gem, HellfireForgeBlockEntity.GEM_SLOT);
        return recipe.matches(nativeInput, level) && selects(level, recipeId, nativeInput) ? new Plan(slots, gem) : null;
    }

    private static @Nullable KeyCounter withoutOne(KeyCounter[] inputs, AEItemKey removed) {
        var result = new KeyCounter();
        boolean removedOne = false;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) return null;
                long amount = entry.getLongValue();
                if (!removedOne && key.equals(removed)) {
                    amount--;
                    removedOne = true;
                }
                if (amount > 0) result.add(key, amount);
            }
        }
        return removedOne ? result : null;
    }

    private static boolean validGem(ItemStack gem, ForgeRecipe recipe) {
        if (!gem.is(NVItems.SPIRITUS_GEM_GREATER.get()) || !gem.has(NVDataComponents.SPIRITUS_AMOUNT) ||
                SpiritusHelper.resolveMaxSpiritus(gem) < recipe.minSpiritus)
            return false;
        return recipe.spiritusType.isEmpty() ||
                recipe.spiritusType.get() == SpiritusHelper.getCurrentType(gem);
    }

    private static boolean validCosts(ForgeRecipe recipe) {
        return Double.isFinite(recipe.minSpiritus) && recipe.minSpiritus >= 0 &&
                Double.isFinite(recipe.usedSpiritus) && recipe.usedSpiritus >= 0;
    }

    private static boolean selects(ServerLevel level, ResourceLocation recipeId, ForgeInput input) {
        var selected = level.getRecipeManager().getRecipeFor(NVRecipes.HELLFIRE_FORGE_TYPE.get(), input, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return false;
        if (selected.get().value().hasEnoughSpiritus(input)) return true;
        var slots = new ObjectArrayList<ItemStack>();
        int craftingGemSlot = -1;
        for (int slot = 0; slot < HellfireForgeBlockEntity.GEM_SLOT; slot++) {
            ItemStack stack = input.getItem(slot);
            slots.add(stack);
            if (stack.is(NVTags.Items.SPIRITUS_GEM)) craftingGemSlot = slot;
        }
        if (craftingGemSlot < 0) return true;
        var fallback = new ForgeInput(slots, slots.get(craftingGemSlot), craftingGemSlot);
        var alternative = level.getRecipeManager().getRecipeFor(NVRecipes.HELLFIRE_FORGE_TYPE.get(), fallback, level);
        return alternative.isEmpty() || !alternative.get().value().hasEnoughSpiritus(fallback) ||
                alternative.get().id().equals(recipeId);
    }

    private static @Nullable ForgeRecipe recipe(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() && holder.get().value() instanceof ForgeRecipe recipe &&
                recipe.getType() == NVRecipes.HELLFIRE_FORGE_TYPE.get() ? recipe : null;
    }

    private static boolean emptyInputsAndOutput(HellfireForgeBlockEntity forge) {
        for (int slot = 0; slot <= HellfireForgeBlockEntity.OUTPUT_SLOT; slot++) {
            if (slot == HellfireForgeBlockEntity.GEM_SLOT) continue;
            if (!forge.inv.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static boolean sameStack(ItemStack expected, ItemStack actual) {
        return expected.getCount() == actual.getCount() && ItemStack.isSameItemSameComponents(expected, actual);
    }

    private static ListTag saveStacks(ObjectList<ItemStack> stacks, ServerLevel level) {
        var result = new ListTag();
        for (ItemStack stack : stacks) result.add(stack.saveOptional(level.registryAccess()));
        return result;
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, ListTag encoded) {
        var result = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < encoded.size(); index++) {
            result.add(ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(index)));
        }
        return result;
    }

    private static ItemStack readStack(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Neo Vitae Hellfire Forge stack: " + key));
    }

    private record Plan(ObjectList<ItemStack> slots, ItemStack gem) {}
}
